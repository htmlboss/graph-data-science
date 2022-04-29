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
package org.neo4j.gds.ml.pipeline;

import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.ml.metrics.BestModelStats;
import org.neo4j.gds.ml.metrics.CandidateStats;
import org.neo4j.gds.ml.metrics.Metric;
import org.neo4j.gds.ml.models.TrainerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TrainingStatistics {

    List<CandidateStats> candidateStats;
    private final List<Metric> metrics;
    private final Map<Metric, Double> testScores;
    private final Map<Metric, Double> outerTrainScores;

    public TrainingStatistics(List<Metric> metrics) {
        this.candidateStats = new ArrayList<>();
        this.metrics = metrics;
        this.testScores = new HashMap<>();
        this.outerTrainScores = new HashMap<>();
    }

    @TestOnly
    public List<BestModelStats> getTrainStats(Metric metric) {
        return candidateStats.stream().map(stats -> stats.trainingStats().get(metric)).collect(Collectors.toList());
    }

    @TestOnly
    public List<BestModelStats> getValidationStats(Metric metric) {
        return candidateStats.stream().map(stats -> stats.validationStats().get(metric)).collect(Collectors.toList());
    }

    /**
     * Turns this class into a Cypher map, to be returned in a procedure YIELD field.
     * This is intentionally omitting the test scores.
     * These can be added to extend the return surface later.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "bestParameters", bestParameters().toMap(),
            "bestTrial", getBestTrialIdx(),
            "modelCandidates", candidateStats.stream().map(CandidateStats::toMap).collect(Collectors.toList())
        );
    }

    public double getMainMetric(int trial) {
        return candidateStats.get(trial).validationStats().get(evaluationMetric()).avg();
    }

    public Map<Metric, Double> validationMetricsAvg(int trial) {
        return extractAverage(candidateStats.get(trial).validationStats());
    }

    public Map<Metric, Double> trainMetricsAvg(int trial) {
        return extractAverage(candidateStats.get(trial).trainingStats());
    }

    private Map<Metric, Double> extractAverage(Map<Metric, BestModelStats> statsMap) {
        return statsMap.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().avg()
            ));
    }

    public Metric evaluationMetric() {
        return metrics.get(0);
    }

    public void addCandidateStats(CandidateStats statistics) {
        candidateStats.add(statistics);
    }

    public void addTestScore(Metric metric, double score) {
        testScores.put(metric, score);
    }

    public void addOuterTrainScore(Metric metric, double score) {
        outerTrainScores.put(metric, score);
    }

    public Map<Metric, Double> winningModelTestMetrics() {
        return testScores;
    }

    public Map<Metric, Double> winningModelOuterTrainMetrics() {
        return outerTrainScores;
    }

    public int getBestTrialIdx() {
        return candidateStats
            .stream()
            .map(stats -> stats.validationStats().get(evaluationMetric()).avg())
            .collect(Collectors.toList())
            .indexOf(getBestTrialScore());
    }
    public CandidateStats bestCandidate() {
        return candidateStats.get(getBestTrialIdx());
    }

    public double getBestTrialScore() {
        return candidateStats
            .stream()
            .map(stats -> stats.validationStats().get(evaluationMetric()).avg())
            .max(evaluationMetric().comparator())
            .get();
    }

    public TrainerConfig bestParameters() {
        return bestCandidate().trainerConfig();
    }
}
