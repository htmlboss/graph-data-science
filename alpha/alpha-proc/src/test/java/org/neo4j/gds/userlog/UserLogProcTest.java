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
package org.neo4j.gds.userlog;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.TestLog;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.core.utils.progress.ProgressFeatureSettings;
import org.neo4j.gds.core.utils.progress.TaskRegistryExtension;
import org.neo4j.gds.core.utils.progress.TaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.core.utils.warnings.UserLogRegistryFactory;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.wcc.WccStreamProc;
import org.neo4j.logging.Level;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


class UserLogProcTest extends BaseProcTest {

    @Neo4jGraph
    static final String DB_CYPHER =
        " CREATE (a : node {name: 'a'})\n" +
        " CREATE (b : node {name: 'b'})\n" +
        " CREATE (c : node {name: 'c'})\n" +

        "CREATE" +
        "(a)-[:EDGE1 {w: 3.0 , foo: 4.0 }]->(b),\n" +
        "(a)-[:EDGE2 {w: 2.0, foo: 4.0}]->(c)";

    @Inject
    IdFunction idFunction;


    private static final String GRAPH_NAME = "graph";
    @Override
    @ExtensionCallback
    protected void configuration(TestDatabaseManagementServiceBuilder builder) {
        super.configuration(builder);
        builder.setConfig(GraphDatabaseSettings.store_internal_log_level, Level.DEBUG);
        builder.setConfig(ProgressFeatureSettings.progress_tracking_enabled, true);
        // make sure that we 1) have our extension under test and 2) have it only once
        builder.removeExtensions(ex -> ex instanceof TaskRegistryExtension);
        builder.addExtension(new TaskRegistryExtension());


    }


    @BeforeEach
    void setupGraph() throws Exception {
        registerProcedures(FakeTaskProc.class, UserLogProc.class, WccStreamProc.class, GraphProjectProc.class);
        var createQuery = GdsCypher.call(GRAPH_NAME)
            .graphProject()
            .withRelationshipProperty("foo")
            .withRelationshipProperty("w")
            .loadEverything()
            .yields();
        runQuery(createQuery);
    }

    @Test
    void shouldNotFailWhenThereAreNoWarnings() {
        assertDoesNotThrow(() -> runQuery("CALL gds.alpha.userLog()"));
    }

    @Test
    void userLogOutputMessages() {

        runQuery("CALL gds.test.fakewarnproc('foo')");

        assertCypherResult(
            "CALL gds.alpha.userLog() " +
            "YIELD taskName, message RETURN taskName, message ",
            List.of(
                Map.of(
                    "taskName", "foo",
                    "message","This is a test warning"),
                Map.of(
                    "taskName", "foo",
                    "message","This is another test warning")
                )
        );
    }

    @Test
    void userLogOutputMessageOnMultipleTasks() {

        runQuery("CALL gds.test.fakewarnproc('foo')");
        runQuery("CALL gds.test.fakewarnproc('foo2')");

        assertCypherResult(
            "CALL gds.alpha.userLog() " +
            "YIELD taskName, message RETURN taskName, message  ORDER BY taskName ",
            List.of(
                Map.of(
                    "taskName", "foo",
                    "message", "This is a test warning"
                ),
                Map.of(
                    "taskName", "foo",
                    "message", "This is another test warning"
                ),
                Map.of(
                    "taskName", "foo2",
                    "message", "This is a test warning"
                ),
                Map.of(
                    "taskName", "foo2",
                    "message", "This is another test warning"
                )
            )

        );
    }

    @Test
    void userLogWorksWithWCCmodified() {
        var createQuery = GdsCypher.call(GRAPH_NAME)
            .algo("gds.wcc")
            .streamMode()
            .addParameter("relationshipWeightProperty", "foo")
            .yields();
        runQuery(createQuery);

        runQuery("CALL gds.test.fakewarnproc('foo')");

        assertCypherResult(
            "CALL gds.alpha.userLog() " +
            "YIELD taskName, message  RETURN taskName,message ORDER BY taskName",
            List.of(
                Map.of(
                    "taskName", "WCC",
                    "message", "Specifying a `relationshipWeightProperty` has no effect unless `threshold` is also set."
                ),
                Map.of(
                    "taskName", "foo",
                    "message", "This is a test warning"
                ),
                Map.of(
                    "taskName", "foo",
                    "message", "This is another test warning"
                )


            )
        );
    }


    public static class FakeTaskProc {

        @Context
        public TaskRegistryFactory taskRegistryFactory;
        @Context
        public UserLogRegistryFactory userLogRegistryFactory;

        @Procedure("gds.test.fakewarnproc")
        public Stream<FakeResult> foo(
            @Name(value = "taskName") String taskName,
            @Name(value = "withMemoryEstimation", defaultValue = "false") boolean withMemoryEstimation,
            @Name(value = "withConcurrency", defaultValue = "false") boolean withConcurrency
        ) {
            var task = Tasks.task(taskName, Tasks.leaf("leaf", 3));

            var taskProgressTracker = new TaskProgressTracker(task, new TestLog(), 1, taskRegistryFactory,
                userLogRegistryFactory
            );
            taskProgressTracker.beginSubTask();
            taskProgressTracker.beginSubTask();
            taskProgressTracker.logProgress(1);
            taskProgressTracker.logWarning("This is a test warning");
            taskProgressTracker.logWarning("This is another test warning");

            return Stream.empty();
        }
    }

    public static class FakeResult {
        public final String fakeField;

        public FakeResult(String fakeField) {this.fakeField = fakeField;}
    }
}
