package algorithms.size.barrier.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

import measurements.support.ThreadID;

public class IdleTimeDynamicBarrierImpl implements IdleTimeDynamicBarrier{

    // Implementation variables
    private long sensePhase = 0;
    private long parityWaiting = 0;
    private long [] activeThread = new long[activeArraySize];
    private ThreadLocal<Long> threadPhase = new ThreadLocal<Long>();
    
    // Helper fields
    private static final int activeArraySize = ThreadID.MAX_THREADS;
    private static final int sizeShift = 31;
    private static final int parityShift = 63;
    private static final int senseShift = 63;
    private static final long sizeIncrementValue = 1L << sizeShift;

    private static final long senseMask = 1L << senseShift;
    private static final long phaseMask = senseMask - 1;
    private static final long waitingMask = (1L << sizeShift) - 1;

    public IdleTimeDynamicBarrierImpl() {
    }


    @Override
    public long getPhase() {
        return extractPhase((long) SENSE_PHASE.getAcquire(this));
    }

    @Override
    public long getThreadPhase() {
        return threadPhase.get();
    }

    @Override
    public void register() {
        incrementSize();
        threadPhase.set(getPhase());
        if (isBarrierActive()) {
            if (isIncrementForIncorrectPhase(extractParity(incrementWaiting()))) {
                incrementThreadPhase();
            }
            waitingLoop();
        }
    }

    @Override
    public void await() {
        if (threadInSamePhase()) {
            return;
        }
        incrementThreadPhase();
        incrementWaiting();
        waitingLoop();
    }

    @Override
    public void leave() {
        decrementSize();
    }

    @Override
    public void trigger() {
        setWaitingToZero();
        // Not needed in intel
        //VarHandle.acquireFence();
        incrementBarrierPhase();
        if (noActiveThreads()) {
            deactivateBarrierFromTrigger();
        }
    }

    private void prepareNextPhase() {
        long expectedValue = (long) PARITY_SIZE_WAITING.get(this);
        long newValue = ((1L - (extractParity(expectedValue))) << parityShift) + (extractSize(expectedValue) << sizeShift);
        while (!PARITY_SIZE_WAITING.compareAndSet(this, expectedValue, newValue)) {
            expectedValue = (long) PARITY_SIZE_WAITING.get(this);
            newValue = ((1L - (extractParity(expectedValue))) << parityShift) + (extractSize(expectedValue) << sizeShift);
        }
    }


    private void waitingLoop() {
        while (isBarrierActive()) {
            if (allActiveThreadsBlocked()) {
                deactivateBarrier();
            }
        }
    }

    private boolean noActiveThreads() {
        return numberOfActiveThreads() == 0;
    }

    private long getPhaseLSB() {
        return extractPhase((long) SENSE_PHASE.get(this)) & 0x1;
    }

    private long getThreadPhaseLSB() {
        return threadPhase.get() & 0x1;
    }

    private boolean threadInSamePhase() {
        return threadPhase.get() == getPhase();
    }

    private void incrementThreadPhase() {
        threadPhase.set(getThreadPhase() + 1);
    }

    private long numberOfActiveThreads() {
        return sumActiveArray();
        //return extractSize((long) PARITY_SIZE_WAITING.get(this));
    }

    private long sumActiveArray() {
        long count = 0;
        for (int i = 0; i < activeArraySize; i++)
            count += (long) ACTIVE_THREAD.getAcquire(activeThread, i);
        return count;
    }

    private void deactivateBarrier() {
        long expectedValue = threadPhase.get() + ((1L - getThreadPhaseLSB()) << senseShift);
        long newValue = threadPhase.get() + (getThreadPhaseLSB() << senseShift);
        SENSE_PHASE.compareAndSet(this, expectedValue, newValue);
    }

    private boolean allActiveThreadsBlocked() {
        long localValue = (long) PARITY_SIZE_WAITING.getVolatile(this);
        return numberOfActiveThreads() == extractWaiting(localValue);
        //return extractSize(localValue) == extractWaiting(localValue);
    }

    private boolean isIncrementForIncorrectPhase(long localParity) {
        return localParity != getThreadPhaseLSB();
    }

    private boolean isBarrierActive() {
        return extractSense((long) SENSE_PHASE.get(this)) != getThreadPhaseLSB();
    }

    private long extractParity(long field) {
        return (field >>> parityShift) & 0x1;
    }

    private long extractSense(long field) {
        return (field >>> parityShift) & 0x1;
    }

    private long extractWaiting(long field) {
        return field & waitingMask;
    }

    private long extractSize(long field) {
        return (field >>> sizeShift) & waitingMask;
    }

    private long extractPhase(long field) {
        return field & phaseMask;
    }

    private void incrementSize() {
        ACTIVE_THREAD.getAndAddRelease(activeThread, getThreadIndex(), 1);
    }

    private long incrementWaiting() {
        return (long) PARITY_SIZE_WAITING.getAndAddAcquire(this, 1);
    }

    private void decrementSize() {
        ACTIVE_THREAD.getAndAddAcquire(activeThread, getThreadIndex(), -1);
    }

    private void incrementBarrierPhase() {
        SENSE_PHASE.getAndAdd(this, 1);
    }

    private void deactivateBarrierFromTrigger() {
        long newSensePhase = ((getPhaseLSB()) << senseShift) + extractPhase((long) SENSE_PHASE.get(this));
        SENSE_PHASE.set(this, newSensePhase);
    }

    private int getThreadIndex() {
        return (int) (ThreadID.threadID.get() % activeArraySize);
    }

    private void setWaitingToZero() {
        long newValue = ((1L - (getPhaseLSB())) << parityShift);
        PARITY_SIZE_WAITING.set(this, newValue);
    }

    // VarHandle mechanics
    private static final VarHandle SENSE_PHASE;
    private static final VarHandle PARITY_SIZE_WAITING;
    private static final VarHandle ACTIVE_THREAD = MethodHandles.arrayElementVarHandle(long[].class);;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SENSE_PHASE = l.findVarHandle(IdleTimeDynamicBarrierImpl.class, "sensePhase", long.class);
            PARITY_SIZE_WAITING = l.findVarHandle(IdleTimeDynamicBarrierImpl.class, "parityWaiting", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
