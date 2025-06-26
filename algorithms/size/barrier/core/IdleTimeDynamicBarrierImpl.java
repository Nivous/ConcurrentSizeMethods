package algorithms.size.barrier.core;

import java.util.concurrent.atomic.AtomicLong;

public class IdleTimeDynamicBarrierImpl implements IdleTimeDynamicBarrier{
    // Implementation variables
    private AtomicLong sensePhase;
    private AtomicLong paritySizeWaiting;
    private ThreadLocal<Long> threadPhase = new ThreadLocal<>();

    // Helper fields
    private static final long senseMask = 1L << 63;
    private static final long phaseMask = ~senseMask;
    private static final long parityMask = 1L << 63;
    private static final long waitingMask = (1L << 31) - 1;
    private static final long sizeMask = (1L << 62) - 1 - waitingMask;

    private static final int sizeShift = 31;
    private static final int parityShift = 63;
    private static final int senseShift = 63;
    private static final long sizeIncrementValue = 1L << 31;

    public IdleTimeDynamicBarrierImpl() {
        sensePhase = new AtomicLong(0L);
        paritySizeWaiting = new AtomicLong(0L);
    }

    @Override
    public long getPhase() {
        return sensePhase.get() & phaseMask;
    }

    @Override
    public long getThreadPhase() {
        return threadPhase.get();
    }

    @Override
    public void register() {
        paritySizeWaiting.getAndAdd(sizeIncrementValue);
        threadPhase.set(getPhase());
        if (isBarrierActive()) {
            long localParity = paritySizeWaiting.getAndIncrement() >>> parityShift;
            if (isIncrementForIncorrectPhase(localParity))
                threadPhase.set(threadPhase.get() + 1);
            while (isBarrierActive()) {
                if (allActiveThreadsBlocked())
                    deactivateBarrier();
            }
        }
    }

    @Override
    public void await() {
        if (threadInSamePhase())
            return;
        incrementThreadPhase();
        paritySizeWaiting.getAndIncrement();
        while (isBarrierActive()) {
            if (allActiveThreadsBlocked())
                deactivateBarrier();
        }
    }

    @Override
    public void leave() {
        paritySizeWaiting.getAndAdd(-sizeIncrementValue);
    }

    @Override
    public void trigger() {
        prepareNextPhase();
        sensePhase.getAndIncrement();
        if (noActiveThreads())
            sensePhase.set((sensePhase.get() & phaseMask) + (getPhaseLSB() << senseShift));
    }

    private void prepareNextPhase() {
        long expectedValue = paritySizeWaiting.get();
        long newValue = ((1L - (expectedValue >>> parityShift)) << parityShift) + (expectedValue & sizeMask);
        while(!paritySizeWaiting.compareAndSet(expectedValue, newValue)) {
            expectedValue = paritySizeWaiting.get();
            newValue = ((1L - (expectedValue >>> parityShift)) << parityShift) + (expectedValue & sizeMask);
        }
    }

    private boolean noActiveThreads() {
        return numberOfActiveThreads() == 0;
    }

    private long getPhaseLSB() {
        return sensePhase.get() & 0x1;
    }

    private long getThreadPhaseLSB() {
        return threadPhase.get() & 0x1;
    }

    private boolean threadInSamePhase() {
        return threadPhase.get() == (sensePhase.get() & phaseMask);
    }

    private void incrementThreadPhase() {
        threadPhase.set(getThreadPhase() + 1);
    }

    private long numberOfActiveThreads() {
        return (paritySizeWaiting.get() & sizeMask) >>> sizeShift;
    }

    private long numberOfWaitingThreads() {
        return paritySizeWaiting.get() & waitingMask;
    }

    private void deactivateBarrier() {
        long expectedValue = threadPhase.get() + ((1L - getThreadPhaseLSB()) << senseShift);
        long newValue = threadPhase.get() + (getThreadPhaseLSB() << senseShift);
        sensePhase.compareAndSet(expectedValue, newValue);
    }

    private boolean allActiveThreadsBlocked() {
        long localValue = paritySizeWaiting.get();
        return ((localValue & sizeMask) >>> sizeShift) == (localValue & waitingMask);
    }

    private boolean isIncrementForIncorrectPhase(long localParity) {
        return localParity != getThreadPhaseLSB();
    }

    private boolean isBarrierActive() {
        return ((sensePhase.get() & senseMask) >>> senseShift) != getThreadPhaseLSB();
    }
}
