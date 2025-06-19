package algorithms.size.optimistic.core;

/**
 * This is an implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 * 
 * The current file implements the OptimisticSizeCalculator class,
 * which is the core of the optimistic size algorithm.
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

import measurements.support.ThreadID;
import measurements.support.Padding;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import jdk.internal.vm.annotation.Contended;

public class OptimisticSizeCalculator {
    // Each long is 8 bytes; PADDING longs provide 8 * PADDING bytes of padding
    // to prevent false sharing
    private static final int PADDING = Padding.PADDING;
    
    // Maximum number of attempts to compute size before seeking help
    private final int maxTries;
    
    @Contended
    private final long[][] metadataCounters = new long[ThreadID.MAX_THREADS + 1][PADDING];
    @Contended
    private final long[][] activityCounters = new long[ThreadID.MAX_THREADS + 1][PADDING];
    @Contended
    private final long[] awaitingSizes = new long[PADDING];
    @Contended
    private volatile SizeInfo sizeInfo = new SizeInfo();

    /**
     * Initializes a new OptimisticSizeCalculator.
     * 
     * @param maxTries maximum number of size computation attempts before seeking help
     */
    public OptimisticSizeCalculator(int maxTries) {
        this.maxTries = maxTries;
        // Initialize the awaiting sizes counter to 0
        AWAITING_SIZES.setVolatile(awaitingSizes, 1, 0);
        // Initialize all thread metadataCounters to 0
        for (int i = 0; i <= ThreadID.MAX_THREADS; i++) {
            METADATA_COUNTERS.setVolatile(metadataCounters[i], 0, 0);
        }
    }

    /**
     * Computes the current size by reading thread metadataCounters optimistically.
     * Will retry up to maxTries times.
     * 
     * @return the computed size
     */
    public int compute() {
        long sum = 0;
        int count = 0;
        SizeInfo currentSizeInfo = (SizeInfo) SIZE_INFO.getVolatile(this);
        SizeInfo activeSizeInfo;
        boolean validSizeInfo = false;
        SizeInfo newSizeInfo = new SizeInfo();

        // Determine which SizeInfo to use (existing or new)
        if (currentSizeInfo.getSize() == SizeInfo.INVALID_SIZE) {
            activeSizeInfo = currentSizeInfo;
        } else {
            validSizeInfo = true;
            SizeInfo witnessedSizeInfo = (SizeInfo) SIZE_INFO.compareAndExchange(
                    this, currentSizeInfo, newSizeInfo);
            activeSizeInfo = (witnessedSizeInfo == currentSizeInfo) ? newSizeInfo : witnessedSizeInfo;
        }

        // Main computation loop
        for (;;) {
            sum = activeSizeInfo.getSize();
            
            // If we have a valid size, use it
            if (sum != SizeInfo.INVALID_SIZE) {
                if (validSizeInfo) {
                    break;
                } else {
                    // Try to update to a new SizeInfo
                    SizeInfo witnessedSizeInfo = (SizeInfo) SIZE_INFO.compareAndExchange(
                            this, currentSizeInfo, newSizeInfo);
                    validSizeInfo = true;
                    
                    if (witnessedSizeInfo == currentSizeInfo) {
                        activeSizeInfo = newSizeInfo;
                    } else {
                        activeSizeInfo = witnessedSizeInfo;
                        sum = activeSizeInfo.getSize();
                        if (sum != SizeInfo.INVALID_SIZE) {
                            break;
                        }
                    }
                }
            }

            // Register for help if we've exceeded maxTries
            if (count == maxTries) {
                AWAITING_SIZES.getAndAdd(awaitingSizes, 1, 1);
            }
            if (count <= maxTries) {
                count++;
            }
            
            // Try to compute size with optimistic concurrency control
            final Long[] status = tryReadLock();
            sum = 0;
            int nextId = ThreadID.nextId.get();
            for (int i = 1; i <= nextId; i++) {
                sum += (long) METADATA_COUNTERS.getVolatile(metadataCounters[i], 0);
            }
            
            // If no concurrent modifications detected, set the size and finish
            if (retryReadLock(status)) {
                activeSizeInfo.compareAndSetSize(SizeInfo.INVALID_SIZE, sum);
                break;
            }
        }
        
        // Deregister from help if needed
        if (count == maxTries + 1) {
            AWAITING_SIZES.getAndAdd(awaitingSizes, 1, -1);
        }

        return (int) sum;
    }

    /**
     * Helps compute size for size threads that have exceeded maxTries.
     * Only does work if there are size threads awaiting help.
     */
    public void helpSize() {
        // Only help if there are threads waiting
        if ((long) AWAITING_SIZES.getVolatile(awaitingSizes, 1) > 0) {
            SizeInfo activeSizeInfo = (SizeInfo) SIZE_INFO.getVolatile(this);
            
            // Only help if size computation is in progress
            if (activeSizeInfo.getSize() == SizeInfo.INVALID_SIZE) {
                for (;;) {
                    if (activeSizeInfo.getSize() != SizeInfo.INVALID_SIZE) {
                        break;
                    }
                    
                    // Try to compute size with optimistic concurrency control
                    long sum = 0;
                    final Long[] status = tryReadLock();
                    int nextId = ThreadID.nextId.get();
                    for (int i = 1; i <= nextId; i++) {
                        sum += (long) METADATA_COUNTERS.getVolatile(metadataCounters[i], 0);
                    }
                    
                    // If no concurrent modifications detected, set the size and finish
                    if (retryReadLock(status)) {
                        activeSizeInfo.compareAndSetSize(SizeInfo.INVALID_SIZE, sum);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Updates the calling thread's counter when an operation completes.
     * @param opKind the operation kind to update the counter for
     */
    public void updateMetadata(int opKind) {
        int tid = ThreadID.threadID.get();
        METADATA_COUNTERS.setVolatile(metadataCounters[tid + 1], 0, 
                opKind + (long) METADATA_COUNTERS.getVolatile(metadataCounters[tid + 1], 0));
    }

    /**
     * Blocks size operations by incrementing the thread's activity counter to an odd value.
     * Size operations will detect this and retry rather than using a potentially inconsistent value.
     */
    public void blockSize() {
        int tid = ThreadID.threadID.get();
        ACTIVITY_COUNTERS.setVolatile(activityCounters[tid + 1], 0, 
                1 + (long) ACTIVITY_COUNTERS.getVolatile(activityCounters[tid + 1], 0));
    }

    /**
     * Unblocks size operations by incrementing the thread's activity counter to an even value.
     */
    public void unblockSize() {
        int tid = ThreadID.threadID.get();
        ACTIVITY_COUNTERS.setVolatile(activityCounters[tid + 1], 0, 
                1 + (long) ACTIVITY_COUNTERS.getVolatile(activityCounters[tid + 1], 0));
    }

    /**
     * Takes a non-snapshot copy of activity counters to detect concurrent modifications.
     * 
     * @return array of activity counter values at the time of the call
     */
    public Long[] tryReadLock() {
        Long[] status = new Long[ThreadID.MAX_THREADS];
        int nextId = ThreadID.nextId.get();
        
        for (int i = 0; i < nextId; ) {
            status[i] = (long) ACTIVITY_COUNTERS.getVolatile(activityCounters[i + 1], 0);
            // Only proceed past threads with even activity counter values (not in critical section)
            if (status[i] % 2 == 0) {
                i++;
            }
        }
        
        return status;
    }

    /**
     * Verifies that the activity counters have not changed since tryReadLock was called.
     * 
     * @param value the activity counter status from tryReadLock
     * @return true if no concurrent modifications detected, false otherwise
     */
    public boolean retryReadLock(Long[] value) {
        int prevId, nextId = ThreadID.nextId.get();
        int i = 0;
        
        while (true) {
            // Verify each thread's activity counter hasn't changed
            for (; i < nextId; i++) {
                if ((long) ACTIVITY_COUNTERS.getVolatile(activityCounters[i + 1], 0) > value[i]) {
                    return false;
                }
            }
            
            // Check if new threads were created during verification
            prevId = nextId;
            if (prevId != (nextId = ThreadID.nextId.get())) {
                i = prevId;
                continue;
            } else {
                break;
            }
        }
        
        return true;
    }

    /**
     * Holder for size computation state.
     */
    private static class SizeInfo {
        public static final long INVALID_SIZE = Long.MAX_VALUE;
        private volatile long size;

        SizeInfo() {
            SIZE.setVolatile(this, INVALID_SIZE);
        }

        public void compareAndSetSize(long oldSize, long size) {
            SIZE.compareAndSet(this, oldSize, size);
        }

        public long getSize() {
            return (long) SIZE.getVolatile(this);
        }

        public void setSize(long size) {
            SIZE.setVolatile(this, size);
        }
        
        private static final VarHandle SIZE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                SIZE = l.findVarHandle(SizeInfo.class, "size", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    
    // VarHandles for accessing arrays with volatile semantics
    private static final VarHandle METADATA_COUNTERS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle AWAITING_SIZES = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle ACTIVITY_COUNTERS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle SIZE_INFO;
    
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZE_INFO = l.findVarHandle(
                    OptimisticSizeCalculator.class, "sizeInfo", SizeInfo.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
