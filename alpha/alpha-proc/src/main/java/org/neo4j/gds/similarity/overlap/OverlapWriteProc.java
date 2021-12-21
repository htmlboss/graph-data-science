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
package org.neo4j.gds.similarity.overlap;

import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.impl.similarity.OverlapAlgorithm;
import org.neo4j.gds.impl.similarity.OverlapConfig;
import org.neo4j.gds.impl.similarity.SimilarityAlgorithmResult;
import org.neo4j.gds.similarity.AlphaSimilaritySummaryResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.WRITE_RELATIONSHIP;
import static org.neo4j.gds.similarity.overlap.OverlapProc.DESCRIPTION;
import static org.neo4j.procedure.Mode.WRITE;

@GdsCallable(name = "gds.alpha.similarity.overlap.write", description = DESCRIPTION, executionMode = WRITE_RELATIONSHIP)
public class OverlapWriteProc extends OverlapProc<AlphaSimilaritySummaryResult> {

    @Procedure(name = "gds.alpha.similarity.overlap.write", mode = WRITE)
    @Description(DESCRIPTION)
    public Stream<AlphaSimilaritySummaryResult> overlapWrite(
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return write(configuration);
    }

    @Override
    public ComputationResultConsumer<OverlapAlgorithm, SimilarityAlgorithmResult, OverlapConfig, Stream<AlphaSimilaritySummaryResult>> computationResultConsumer() {
        return writeResultConsumer();
    }
}
