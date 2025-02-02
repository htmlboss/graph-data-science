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
package org.neo4j.gds.ml.models.automl;

import org.neo4j.gds.ml.models.TrainerConfig;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.automl.hyperparameter.DoubleRangeParameter;
import org.neo4j.gds.ml.models.automl.hyperparameter.IntegerRangeParameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

public class RandomSearch implements HyperParameterOptimizer {
    private final List<TunableTrainerConfig> concreteConfigs;
    private final List<TunableTrainerConfig> tunableConfigs;
    private final int totalNumberOfTrials;
    private final SplittableRandom random;
    private int numberOfFinishedTrials;

    public RandomSearch(Map<TrainingMethod, List<TunableTrainerConfig>> parameterSpace, int totalNumberOfTrials, long randomSeed) {
        this(parameterSpace, totalNumberOfTrials, Optional.of(randomSeed));
    }

    public RandomSearch(
        Map<TrainingMethod, List<TunableTrainerConfig>> parameterSpace,
        int totalNumberOfTrials,
        Optional<Long> randomSeed
    ) {
        this.concreteConfigs = parameterSpace.values().stream()
            .flatMap(List::stream)
            .filter(TunableTrainerConfig::isConcrete)
            .collect(Collectors.toList());
        this.tunableConfigs = parameterSpace.values().stream()
            .flatMap(List::stream)
            .filter(tunableTrainerConfig -> !tunableTrainerConfig.isConcrete())
            .collect(Collectors.toList());
        this.totalNumberOfTrials = totalNumberOfTrials;
        this.random = randomSeed.map(SplittableRandom::new).orElseGet(SplittableRandom::new);
        this.numberOfFinishedTrials = 0;
    }


    @Override
    public boolean hasNext() {
        return numberOfFinishedTrials < totalNumberOfTrials;
    }

    @Override
    public TrainerConfig next() {
        if (!hasNext()) {
            throw new IllegalStateException("RandomSearch has already exhausted the maximum trials or the parameter space.");
        }
        if (numberOfFinishedTrials < concreteConfigs.size()) {
            return concreteConfigs.get(numberOfFinishedTrials++).materialize(Map.of());
        }
        numberOfFinishedTrials++;
        var tunableConfig = tunableConfigs.get(random.nextInt(tunableConfigs.size()));
        return sample(tunableConfig);
    }

    private TrainerConfig sample(TunableTrainerConfig tunableConfig) {
        var hyperParameterValues = new HashMap<String, Object>();
        tunableConfig.doubleRanges.forEach((name, range) ->
            hyperParameterValues.put(name, sampleDouble(range)));
        tunableConfig.integerRanges.forEach((name, range) ->
            hyperParameterValues.put(name, sampleInteger(range)));
        return tunableConfig.materialize(hyperParameterValues);
    }

    private int sampleInteger(IntegerRangeParameter range) {
        return random.nextInt(range.min(), range.max());
    }

    private double sampleDouble(DoubleRangeParameter range) {
        if (range.logScale()) {
            var min = range.min() < 1e-20 ? Math.log(1e-20) : Math.log(range.min());
            var max = Math.log(range.max());
            return Math.exp(random.nextDouble(min, max));
        } else {
            return random.nextDouble(range.min(), range.max());
        }
    }
}
