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
package org.neo4j.gds.leiden;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class NodesQueueTest {

    @Test
    void queueOperations() {
        var queueSize = 19L;
        var queue = new NodesQueue(queueSize);

        assertThat(queue.isEmpty()).isTrue();

        for (long i = 0; i < queueSize; i++) {
            assertThat(queue.contains(i)).isFalse();
            queue.add(i);
            assertThat(queue.isEmpty()).isFalse();
            assertThat(queue.contains(i)).isTrue();
        }

        for (long i = 0; i < queueSize; i++) {
            var removed = queue.remove();
            assertThat(queue.contains(removed)).isFalse();
        }
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    @Disabled
    void shouldFailWhenElementCountIsExceeded() {
        var queue = new NodesQueue(1L);

        assertThatNoException().isThrownBy(() -> queue.add(42L));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
            .isThrownBy(() -> queue.add(43L))
            .withMessageContaining("Queue is full.");
    }

}
