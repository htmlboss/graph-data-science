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
package org.neo4j.gds.core.utils.io.file.csv;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.core.loading.ImmutableStaticCapabilities;

import java.io.IOException;
import java.util.List;

import static org.neo4j.gds.core.utils.io.file.csv.CsvGraphCapabilitiesWriter.GRAPH_CAPABILITIES_FILE_NAME;

class CsvGraphCapabilitiesWriterTest extends CsvVisitorTest {

    @Test
    void shouldExportGraphCapabilities() throws IOException {
        var capabilities = ImmutableStaticCapabilities.builder()
            .canWriteToDatabase(true)
            .build();

        var graphCapabilitiesWriter = new CsvGraphCapabilitiesWriter(tempDir);
        graphCapabilitiesWriter.writeCapabilities(capabilities);

        assertCsvFiles(List.of(GRAPH_CAPABILITIES_FILE_NAME));
        assertDataContent(
            GRAPH_CAPABILITIES_FILE_NAME,
            List.of(
                defaultHeaderColumns(),
                List.of("true")
            )
        );
    }

    @Override
    protected List<String> defaultHeaderColumns() {
        return List.of("canWriteToDatabase");
    }


}
