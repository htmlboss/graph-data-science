/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.kmeans;

import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.RunWithConcurrency;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.partition.PartitionUtils;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;

public class Kmeans extends Algorithm<KmeansResult> {

    private static final int UNASSIGNED = -1;

    private HugeIntArray bestCommunities;
    private final Graph graph;
    private final int k;
    private final int concurrency;
    private final ExecutorService executorService;
    private final SplittableRandom random;
    private final NodePropertyValues nodePropertyValues;
    private final int dimensions;

    private double[][] bestCenters;

    private HugeDoubleArray distanceFromCenter;
    private final KmeansIterationStopper kmeansIterationStopper;

    private final int maximumNumberOfRestarts;

    public static Kmeans createKmeans(Graph graph, KmeansBaseConfig config, KmeansContext context) {
        String nodeWeightProperty = config.nodeProperty();
        NodePropertyValues nodeProperties = graph.nodeProperties(nodeWeightProperty);
        return new Kmeans(
            context.progressTracker(),
            context.executor(),
            graph,
            config.k(),
            config.concurrency(),
            config.maxIterations(),
            config.numberOfRestarts(),
            config.deltaThreshold(),
            nodeProperties,
            getSplittableRandom(config.randomSeed())
        );
    }


    Kmeans(
        ProgressTracker progressTracker,
        ExecutorService executorService,
        Graph graph,
        int k,
        int concurrency,
        int maxIterations,
        int maximumNumberOfRestarts,
        double deltaThreshold,
        NodePropertyValues nodePropertyValues,
        SplittableRandom random
    ) {
        super(progressTracker);
        this.executorService = executorService;
        this.graph = graph;
        this.k = k;
        this.concurrency = concurrency;
        this.random = random;
        this.bestCommunities = HugeIntArray.newArray(graph.nodeCount());
        this.nodePropertyValues = nodePropertyValues;
        this.dimensions = nodePropertyValues.doubleArrayValue(0).length;
        this.kmeansIterationStopper = new KmeansIterationStopper(
            deltaThreshold,
            maxIterations,
            graph.nodeCount()
        );
        this.maximumNumberOfRestarts = maximumNumberOfRestarts;
        this.distanceFromCenter = HugeDoubleArray.newArray(graph.nodeCount());

    }

    @Override
    public KmeansResult compute() {
        progressTracker.beginSubTask();
        if (k > graph.nodeCount()) {
            // Every node in its own community. Warn and return early.
            progressTracker.logWarning("Number of requested clusters is larger than the number of nodes.");
            bestCommunities.setAll(v -> (int) v);
            distanceFromCenter.setAll(v -> 0d);
            progressTracker.endSubTask();
            bestCenters = new double[(int) graph.nodeCount()][dimensions];
            for (int i = 0; i < (int) graph.nodeCount(); ++i) {
                bestCenters[i] = nodePropertyValues.doubleArrayValue(i);
            }
            return ImmutableKmeansResult.of(bestCommunities, distanceFromCenter, bestCenters, 0.0);
        }
        long nodeCount = graph.nodeCount();

        var currentCommunities = HugeIntArray.newArray(nodeCount);
        var currentDistanceFromCenter = HugeDoubleArray.newArray(nodeCount);

        double bestDistance = Double.POSITIVE_INFINITY;
        bestCommunities.setAll(v -> UNASSIGNED);

        KmeansSampler sampler = new KmeansUniformSampler();

        for (int restartIteration = 0; restartIteration < maximumNumberOfRestarts; ++restartIteration) {
            ClusterManager clusterManager = ClusterManager.createClusterManager(nodePropertyValues, dimensions, k);
            currentCommunities.setAll(v -> UNASSIGNED);

            var tasks = PartitionUtils.rangePartition(
                concurrency,
                nodeCount,
                partition -> KmeansTask.createTask(
                    clusterManager,
                    nodePropertyValues,
                    currentCommunities,
                    currentDistanceFromCenter,
                    k,
                    dimensions,
                    partition,
                    progressTracker
                ),
                Optional.of((int) nodeCount / concurrency)
            );
            int numberOfTasks = tasks.size();

            assert numberOfTasks <= concurrency;

            //Initialization do initial center computation and assignment
            //Temporary:

            List<Long> initialCenterIds = sampler.sampleClusters(random, nodePropertyValues, nodeCount, k);
            clusterManager.initializeCenters(initialCenterIds);

            //
            int iteration = 0;
            while (true) {
                long numberOfSwaps = 0;
                //assign each node to a center
                RunWithConcurrency.builder()
                    .concurrency(concurrency)
                    .tasks(tasks)
                    .executor(executorService)
                    .run();

                for (KmeansTask task : tasks) {
                    numberOfSwaps += task.getSwaps();
                }
                recomputeCenters(clusterManager, tasks);

                if (kmeansIterationStopper.shouldQuit(numberOfSwaps, ++iteration)) {
                    break;
                }
            }
            for (KmeansTask task : tasks) {
                task.switchToDistanceCalculation();
            }
            RunWithConcurrency.builder()
                .concurrency(concurrency)
                .tasks(tasks)
                .executor(executorService)
                .run();
            double distanceFromClusterCentre = 0;
            for (KmeansTask task : tasks) {
                distanceFromClusterCentre += task.getDistanceFromClusterNormalized();
            }
            if (restartIteration >= 1) {
                if (distanceFromClusterCentre < bestDistance) {
                    bestDistance = distanceFromClusterCentre;
                    ParallelUtil.parallelForEachNode(graph, concurrency, v -> {
                        bestCommunities.set(v, currentCommunities.get(v));
                        distanceFromCenter.set(v, currentDistanceFromCenter.get(v));
                    });
                    bestCenters = clusterManager.getCenters();
                }
            } else {
                bestCommunities = currentCommunities;
                distanceFromCenter = currentDistanceFromCenter;
                bestCenters = clusterManager.getCenters();
                bestDistance = distanceFromClusterCentre;

            }
        }
        progressTracker.endSubTask();
        return ImmutableKmeansResult.of(bestCommunities, distanceFromCenter, bestCenters, bestDistance);
    }

    private void recomputeCenters(ClusterManager clusterManager, List<KmeansTask> tasks) {
        clusterManager.reset();

        for (KmeansTask task : tasks) {
            clusterManager.updateFromTask(task);
        }
        clusterManager.normalizeClusters();
    }

    @Override
    public void release() {

    }

    @NotNull
    private static SplittableRandom getSplittableRandom(Optional<Long> randomSeed) {
        return randomSeed.map(SplittableRandom::new).orElseGet(SplittableRandom::new);
    }


}
