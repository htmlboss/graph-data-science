/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.beta.pregel;

import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;

public class SyncQueueMessenger implements Messenger<SyncQueueMessenger.Iterator> {

    private final PrimitiveSyncDoubleQueues queues;

    SyncQueueMessenger(long nodeCount, AllocationTracker tracker) {
        this.queues = PrimitiveSyncDoubleQueues.of(nodeCount, tracker);
    }

    // TODO
    static MemoryEstimation memoryEstimation() {
        return MemoryEstimations.empty();
    }

    @Override
    public void initIteration(int iteration) {
        queues.init(iteration);
    }

    @Override
    public void sendTo(long targetNodeId, double message) {
        queues.push(targetNodeId, message);
    }

    @Override
    public Iterator messageIterator() {
        return new Iterator();
    }

    @Override
    public void initMessageIterator(Iterator messageIterator, long nodeId, boolean isFirstIteration) {
        queues.initIterator(messageIterator, nodeId);
    }

    @Override
    public void release() {
        queues.release();
    }

    public static class Iterator implements Messages.MessageIterator {

        double[] queue;
        private int length;
        private int pos;

        void init(double[] queue, int length) {
            this.queue = queue;
            this.pos = 0;
            this.length = length;
        }

        @Override
        public boolean hasNext() {
            return pos < length;
        }

        @Override
        public double nextDouble() {
            return queue[pos++];
        }

        @Override
        public boolean isEmpty() {
            return length == 0;
        }
    }
}
