package algorithms.size.barrier.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CyclicDynamicBarrierImpl {
    private AtomicLong counter;
    private long size;
    IdleTimeDynamicBarrier internalBarrier;
    private AtomicBoolean flag;
    private volatile boolean finishRegistration;
    private volatile long phase;

    public CyclicDynamicBarrierImpl(long size) {
        counter = new AtomicLong(0);
        this.size = size;
        flag = new AtomicBoolean(false);
        internalBarrier = new IdleTimeDynamicBarrierAltImpl();
        finishRegistration = false;
        phase = 0;
    }

    public void await() {
        if (phase == 0) {
            internalBarrier.register();
            long pos = counter.getAndAdd(1);
            if (pos == size - 1)
                finishRegistration = true;
            while (!finishRegistration);
        }
        long localPhase = internalBarrier.getThreadPhase();
        boolean res = false;
        if (localPhase == phase) {
            res = flag.compareAndSet(false, true);
        }
        if (res) {
            if (localPhase == phase) {
                internalBarrier.trigger();
                phase++;
            }
            flag.set(false);
        }
        while (localPhase == phase);
        internalBarrier.await();
    }

    
/*
   public CyclicDynamicBarrierImpl(long size) {
        counter = new AtomicLong(0);
        this.size = size;
        flag = new AtomicBoolean(false);
        internalBarrier = new IdleTimeDynamicBarrierAltImpl(size);
        finishRegistration = false;
        phase = 0;
    }

    public void await() {
        long localPhase = internalBarrier.getThreadPhase();
        boolean res = false;
        if (localPhase == phase) {
            res = flag.compareAndSet(false, true);
        }
        if (res) {
            if (localPhase == phase) {
                internalBarrier.trigger();
                phase++;
            }
            flag.set(false);
        }
        while (localPhase == phase);
        internalBarrier.await();
    }

*/

    private boolean threadHasSamePhase() {
        return internalBarrier.getThreadPhase() == internalBarrier.getPhase();
    }

    private boolean isFirstPhase() {
        return internalBarrier.getPhase() == 0;
    }

    public long getPhase() {
        return internalBarrier.getPhase();
    }

    public long getThreadPhase() {
        return internalBarrier.getThreadPhase();
    }
}
