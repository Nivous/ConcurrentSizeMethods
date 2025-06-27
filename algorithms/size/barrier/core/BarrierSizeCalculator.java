package algorithms.size.barrier.core;

/**
 * This is an implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 * 
 * The current file implements the core of the HandshakeSizeCalculator from the Handshakes methodology,
 * which extends the original SizeCalculator with an efficient handshaking mechanism for
 * synchronizing operations during size calculations.
 * 
 * Copyright (C) 2025 Hen Kas-Sharir
 * Contact: henshar12@gmail.com
 * 
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import algorithms.size.barrier.core.IdleTimeDynamicBarrier;
import algorithms.size.barrier.core.IdleTimeDynamicBarrierImpl;

import algorithms.size.core.UpdateInfo;
import algorithms.size.core.UpdateInfoHolder;
import algorithms.size.core.UpdateOperations;
import measurements.support.ThreadID;
import measurements.support.Padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class BarrierSizeCalculator<V> {
    // Each long is 8 bytes; PADDING longs provide 8 * PADDING bytes of padding
    // to prevent false sharing
    private static final int PADDING = Padding.PADDING;

    // Core data structures for tracking operations
    // Adding +1 to array sizes provides padding before the array to prevent false sharing with thread 0
    private final long[][] metadataCounters = new long[ThreadID.MAX_THREADS + 1][PADDING]; 
    private final long[][] fastMetadataCounters = new long[ThreadID.MAX_THREADS + 1][PADDING];
    private volatile CountersSnapshot countersSnapshot = new CountersSnapshot().deactivate();
    private final IdleTimeDynamicBarrier barrier = new IdleTimeDynamicBarrierImpl();

    /**
     * Initializes a new HandshakeSizeCalculator with default state.
     */
    public BarrierSizeCalculator() {
    }

    /**
     * Attempts to compute size by collecting fastMetadataCounters values and metadata.
     * 
     * @return computed size
     */
    public long tryCompute() {
        CountersSnapshot activeCountersSnapshot = (CountersSnapshot) COUNTERS_SNAPSHOT.getVolatile(this);
        
        // Sum up all fastMetadataCounters values across threads
        int count = 0;
        int nextId = ThreadID.nextId.get();
        for (int i = 0; i <= nextId; i++) {
            count += (long) FAST_METADATA_COUNTERS.getVolatile(fastMetadataCounters[i], 0);
        }
        
        // Set fast size and collect metadata
        activeCountersSnapshot.setFastSize(count);
        collect(activeCountersSnapshot);

        barrier.trigger(); // Trigger next size phase, to reveret threads to fast path
        barrier.await(); // Wait for all threads to reach next size phase

        // Deactivate snapshot (this is size's linearization point)
        activeCountersSnapshot.deactivate();
        
        return activeCountersSnapshot.computeSize();
    }

    /**
     * Collects operation counts from all threads into the target snapshot.
     * 
     * @param targetCountersSnapshot the snapshot to populate
     */
    public void collect(CountersSnapshot targetCountersSnapshot) {
        int prevId, nextId = ThreadID.nextId.get();
        int tid = 0;
        while (true) {
            // Collect data from all existing threads
            for (; tid < nextId; ++tid) {
                for (int opKind = 0; opKind < UpdateOperations.OPS_NUM; ++opKind) {
                    targetCountersSnapshot.add(tid, opKind, getThreadUpdateCounter(tid, opKind));
                }
            }
            
            // Check if new threads registered during collection
            prevId = nextId;
            if (prevId != (nextId = ThreadID.nextId.get())) {
                tid = prevId;
                continue;
            } else {
                break;
            }
        }   
    }

    /**
     * Updates metadata in the fast path.
     * 
     * @param opKind the operation kind (INSERT or REMOVE)
     * @param tid thread ID
     */
    public void fast_updateMetadata(int opKind, int tid) {
        FAST_METADATA_COUNTERS.setVolatile(fastMetadataCounters[tid + 1], 0, opKind + (long) FAST_METADATA_COUNTERS.getVolatile(fastMetadataCounters[tid + 1], 0));
    }

    /**
     * Updates metadata in the slow path.
     * 
     * @param opKind the operation kind
     * @param updateInfoHolder holds thread ID and counter
     */
    public void updateMetadata(int opKind, UpdateInfoHolder updateInfoHolder) {
        int tid = updateInfoHolder.getTid();
        long newCounter = updateInfoHolder.getCounter();
        
        // Update the thread's counter if appropriate
        if (getThreadUpdateCounter(tid, opKind) == newCounter - 1) {
            METADATA_COUNTERS.compareAndSet(metadataCounters[tid + 1], opKind, (long) newCounter - 1, (long) newCounter);
        }
        
        // Forward update to any active collection
        CountersSnapshot currentCountersSnapshot = (CountersSnapshot) COUNTERS_SNAPSHOT.getVolatile(this);
        if (currentCountersSnapshot.isCollecting() && getThreadUpdateCounter(tid, opKind) == newCounter) {
            currentCountersSnapshot.forward(tid, opKind, newCounter);
        }
    }

    /**
     * Creates an update info object with next counter value.
     * 
     * @param opKind the operation kind
     * @return the created UpdateInfo
     */
    public UpdateInfo createUpdateInfo(int opKind) {
        int tid = ThreadID.threadID.get();
        return new UpdateInfo(tid, getThreadUpdateCounter(tid, opKind) + 1);
    }

    /**
     * Gets the current update counter for a specific thread and operation.
     * 
     * @param tid thread ID
     * @param opKind operation kind
     * @return the current counter value
     */
    public long getThreadUpdateCounter(int tid, int opKind) {
        return (long) METADATA_COUNTERS.getVolatile(metadataCounters[tid + 1], opKind);
    }

    /**
     * Sets the current phase for the calling thread.
     * 
     * @param modifiedOpPhase the phase to set
     */
    public void registerToBarrier() {
        barrier.register(); // Register to barrier
    }

    /**
     * Gets the current size computation phase.
     * 
     * @return the current size phase
     */
    public long getSizePhase() {
        return (long) barrier.getThreadPhase(); // Get current phase
    }

    /**
     * Helper method for computing size when another thread is already collecting.
     * 
     * @param currentCountersSnapshot active snapshot
     * @return computed size
     */
    private int waitForSizeComputation(CountersSnapshot currentCountersSnapshot) {
        while (true) {
            long currentSize = currentCountersSnapshot.retrieveSize();
            if (currentSize != CountersSnapshot.INVALID_SIZE) {
                return (int) currentSize;
            }
        }
    }

    /**
     * Compute the current size of the data structure.
     * This is the main entry point for size operations.
     * 
     * @return the computed size
     */
    public int compute() {
        long currentSizePhase;
        CountersSnapshot currentCountersSnapshot = (CountersSnapshot) COUNTERS_SNAPSHOT.getVolatile(this);
        
        // If another thread is already computing size, help it
        if (currentCountersSnapshot.isCollecting()) {
            return helpHandshakesAndComputeSize(currentCountersSnapshot);
        } else {
            // Try to take charge of size computation
            CountersSnapshot newCountersSnapshot = new CountersSnapshot();
            CountersSnapshot witnessedCountersSnapshot = (CountersSnapshot) COUNTERS_SNAPSHOT.compareAndExchange(
                    this, currentCountersSnapshot, newCountersSnapshot);

            if (witnessedCountersSnapshot == currentCountersSnapshot) {
                // We're in charge of size computation
                barrier.trigger(); // Trigger next size phase, to move the threads to slow path
                barrier.register(); // Register to barrier                
                
                // Compute size and return to idle state
                int sz = (int) tryCompute();
                return sz;
            } else {
                // Another thread took control, help it
                return waitForSizeComputation(witnessedCountersSnapshot);
            }
        }
    }

    /**
     * Inner class for tracking snapshots of operation counters.
     */
    public static class CountersSnapshot {
        public static final long INVALID_COUNTER = Long.MAX_VALUE;
        public static final long INVALID_SIZE = Long.MAX_VALUE;
        
        private final long[][] snapshot = new long[ThreadID.MAX_THREADS][UpdateOperations.OPS_NUM];
        private volatile boolean collecting;
        private volatile long size;
        private volatile long fast_size;

        /**
         * Initializes a new CountersSnapshot in collecting state.
         */
        public CountersSnapshot() {
            // Initialize all counters to invalid
            for (int tid = 0; tid < ThreadID.MAX_THREADS; ++tid) {
                for (int opKind = 0; opKind < UpdateOperations.OPS_NUM; ++opKind) {
                    SNAPSHOT.setVolatile(this.snapshot[tid], opKind, INVALID_COUNTER);
                }
            }

            COLLECTING.setVolatile(this, true);
            SIZE.setVolatile(this, INVALID_SIZE);
            FAST_SIZE.setVolatile(this, INVALID_SIZE);
        }

        /**
         * Adds a counter value to the snapshot if not already set.
         * 
         * @param tid thread ID
         * @param opKind operation kind
         * @param counter counter value
         */
        public void add(int tid, int opKind, long counter) {
            if (getThreadSnapshotUpdateCounter(tid, opKind) == INVALID_COUNTER) {
                SNAPSHOT.compareAndSet(snapshot[tid], opKind, INVALID_COUNTER, counter);
            }
        }

        /**
         * Updates a counter value in the snapshot to a newer value.
         * 
         * @param tid thread ID
         * @param opKind operation kind
         * @param counter new counter value
         */
        public void forward(int tid, int opKind, long counter) {
            long snapshotCounter = getThreadSnapshotUpdateCounter(tid, opKind);
            while (snapshotCounter == INVALID_COUNTER || counter > snapshotCounter) { 
                // Should not execute more than 2 iterations
                long witnessedSnapshotCounter = (long) SNAPSHOT.compareAndExchange(snapshot[tid], opKind, snapshotCounter, counter);
                if (witnessedSnapshotCounter == snapshotCounter) {
                    break;
                }
                snapshotCounter = witnessedSnapshotCounter;
            }
        }

        /**
         * Checks if this snapshot is in collecting state.
         * 
         * @return true if collecting
         */
        public boolean isCollecting() {
            return (boolean) COLLECTING.getVolatile(this);
        }

        /**
         * Marks this snapshot as no longer collecting.
         * This is the linearization point for size operations.
         * 
         * @return this snapshot
         */
        public CountersSnapshot deactivate() {
            COLLECTING.setVolatile(this, false);
            return this;
        }

        /**
         * Gets the current size value if set.
         * 
         * @return current size or INVALID_SIZE
         */
        public long retrieveSize() {
            return (long) SIZE.getOpaque(this);
        }

        /**
         * Gets the fast size value.
         * 
         * @return fast size or INVALID_SIZE
         */
        public long retrieveFastSize() {
            return (long) FAST_SIZE.getOpaque(this);
        }

        /**
         * Sets the fast path size calculation.
         * 
         * @param value size value to set
         */
        public void setFastSize(long value) {
            FAST_SIZE.setVolatile(this, value);
        }

        /**
         * Computes the final size by combining slow path and fast path results.
         * 
         * @return computed size
         */
        public long computeSize() {
            long computedSize = 0;
            int nextId = ThreadID.nextId.get();
            
            // Sum insert-remove counts from all threads
            for (int tid = 0; tid < nextId; ++tid) {
                computedSize += getThreadSnapshotUpdateCounter(tid, UpdateOperations.OpKind.Separated.INSERT) -
                        getThreadSnapshotUpdateCounter(tid, UpdateOperations.OpKind.Separated.REMOVE);
            }
            
            // Add fast path contributions
            computedSize += (long) FAST_SIZE.getOpaque(this);
            SIZE.setVolatile(this, computedSize);

            return computedSize;
        }

        /**
         * Gets the counter value for a thread and operation in this snapshot.
         * 
         * @param tid thread ID
         * @param opKind operation kind
         * @return counter value
         */
        private long getThreadSnapshotUpdateCounter(int tid, int opKind) {
            return (long) SNAPSHOT.getVolatile(snapshot[tid], opKind);
        }
        
        // VarHandles for the CountersSnapshot class
        private static final VarHandle SNAPSHOT = MethodHandles.arrayElementVarHandle(long[].class);
        private static final VarHandle COLLECTING;
        private static final VarHandle SIZE;
        private static final VarHandle FAST_SIZE;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                COLLECTING = l.findVarHandle(CountersSnapshot.class, "collecting", boolean.class);
                SIZE = l.findVarHandle(CountersSnapshot.class, "size", long.class);
                FAST_SIZE = l.findVarHandle(CountersSnapshot.class, "fast_size", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    
    // VarHandles for accessing arrays with volatile semantics
    private static final VarHandle FAST_METADATA_COUNTERS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle METADATA_COUNTERS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle OP_PHASE = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle SIZE_PHASE = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle COUNTERS_SNAPSHOT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            COUNTERS_SNAPSHOT = l.findVarHandle(
                    BarrierSizeCalculator.class, "countersSnapshot", CountersSnapshot.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
