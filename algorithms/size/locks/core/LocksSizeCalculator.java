package algorithms.size.locks.core;

/**
 * This is an implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 * 
 * The current file implements the LocksSizeCalculator, which uses a Reader-Writer lock based
 * methodology to compute the size of a concurrent data structure.
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
import java.util.concurrent.locks.StampedLock;
import jdk.internal.vm.annotation.Contended;

public class LocksSizeCalculator {
    // Each long is 8 bytes; PADDING longs provide 8 * PADDING bytes of padding
    // to prevent false sharing
    private static final int PADDING = Padding.PADDING;
    
    // Thread-local operation counters, padded to prevent false sharing
    // The '+1' provides padding before the array to prevent false sharing with thread 0
    private final long[][] metadataCounters = new long[ThreadID.MAX_THREADS + 1][PADDING]; 
    
    // StampedLock used to synchronize size computation
    @Contended
    public final StampedLock sl = new StampedLock();
    
    // Current size calculation state
    @Contended
    private volatile SizeInfo sizeInfo = new SizeInfo();

    /**
     * Initializes a new LocksSizeCalculator.
     */
    public LocksSizeCalculator() {
        sizeInfo.setSize(-1);
    }

    /**
     * Computes the current size by acquiring a write lock and summing thread counters.
     * 
     * @return the computed size
     */
    public long compute() {
        long size, stamp;
        SizeInfo witnessedSizeInfo, currentSizeInfo = (SizeInfo) SIZE_INFO.getVolatile(this);
        
        // If the current size info doesn't have a valid size yet, try to set up a new computation
        if (currentSizeInfo.getSize() != SizeInfo.INVALID_SIZE) {
            SizeInfo newSizeInfo = new SizeInfo();
            witnessedSizeInfo = (SizeInfo) SIZE_INFO.compareAndExchange(this, currentSizeInfo, newSizeInfo);
            
            if (witnessedSizeInfo == currentSizeInfo) {
                // We got the responsibility to compute the size
                currentSizeInfo = newSizeInfo;
                stamp = sl.writeLock();
                try {
                    size = computeSize();
                    currentSizeInfo.setSize(size);
                    return size;
                } finally {
                    sl.unlockWrite(stamp);
                }
            } else {
                // Another thread is computing the size, use its result
                currentSizeInfo = witnessedSizeInfo;
            }
        }
        
        // Wait for the size computation to complete
        while (true) {
            if ((size = currentSizeInfo.getSize()) != SizeInfo.INVALID_SIZE) {
                return size;
            }
        }
    }

    /**
     * Computes size by summing all thread counters.
     * Must be called with the write lock held.
     * 
     * @return the computed size
     */
    long computeSize() {
        long sum = 0;
        int prevId, nextId = ThreadID.nextId.get();
        int i = 1;
        
        while (true) {
            for (; i <= nextId; i++) {
                sum += (long) METADATA_COUNTERS.getVolatile(metadataCounters[i], 0);
            }
            
            // Check if new threads registered during collection
            prevId = nextId;
            if (prevId != (nextId = ThreadID.nextId.get())) {
                i = prevId + 1;
                continue;
            } else {
                break;
            }
        }
        
        return sum;
    }

    /**
     * Updates the calling thread's counter when an operation completes.
     * 
     * @param opKind the operation kind (positive for insert, negative for remove)
     */
    public void updateMetadata(int opKind) {
        int tid = ThreadID.threadID.get();
        METADATA_COUNTERS.setVolatile(metadataCounters[tid + 1], 0, 
                opKind + (long) METADATA_COUNTERS.getVolatile(metadataCounters[tid + 1], 0));
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
    
    // VarHandle for accessing arrays with volatile semantics
    private static final VarHandle METADATA_COUNTERS = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle SIZE_INFO;
    
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZE_INFO = l.findVarHandle(
                    LocksSizeCalculator.class, "sizeInfo", SizeInfo.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
