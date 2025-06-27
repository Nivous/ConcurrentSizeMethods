package algorithms.size.barrier.core;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class IdleTimeDynamicBarrierImpl implements IdleTimeDynamicBarrier{

    // Implementation variables
    private volatile long sensePhase = 0;
    private volatile long paritySizeWaiting = 0;
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
    }

    @Override
    public long getPhase() {
        return ((long) SENSE_PHASE.getVolatile(this)) & phaseMask;
    }

    @Override
    public long getThreadPhase() {
        return threadPhase.get();
    }

    @Override
    public void register() {
        PARITY_SIZE_WAITING.getAndAdd(this, sizeIncrementValue);
        threadPhase.set(getPhase());
        if (isBarrierActive()) {
            long localParity = ((long) PARITY_SIZE_WAITING.getAndAdd(this, 1)) >>> parityShift;
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
        PARITY_SIZE_WAITING.getAndAdd(this, 1);
        while (isBarrierActive()) {
            if (allActiveThreadsBlocked())
                deactivateBarrier();
        }
    }

    @Override
    public void leave() {
        PARITY_SIZE_WAITING.getAndAdd(-sizeIncrementValue);
    }

    @Override
    public void trigger() {
        prepareNextPhase();
        SENSE_PHASE.getAndAdd(this, 1);
        if (noActiveThreads())
            SENSE_PHASE.setOpaque(this, ((((long) SENSE_PHASE.getOpaque(this)) & phaseMask) + (getPhaseLSB() << senseShift)));
    }

    private void prepareNextPhase() {
        long expectedValue = (long) PARITY_SIZE_WAITING.getOpaque(this);
        long newValue = ((1L - (expectedValue >>> parityShift)) << parityShift) + (expectedValue & sizeMask);
        while(!PARITY_SIZE_WAITING.compareAndSet(this, expectedValue, newValue)) {
            expectedValue = (long) PARITY_SIZE_WAITING.getOpaque(this);
            newValue = ((1L - (expectedValue >>> parityShift)) << parityShift) + (expectedValue & sizeMask);
        }
    }

    private boolean noActiveThreads() {
        return numberOfActiveThreads() == 0;
    }

    private long getPhaseLSB() {
        return ((long) SENSE_PHASE.getVolatile(this)) & 0x1;
    }

    private long getThreadPhaseLSB() {
        return threadPhase.get() & 0x1;
    }

    private boolean threadInSamePhase() {
        return threadPhase.get() == (((long) SENSE_PHASE.getVolatile(this)) & phaseMask);
    }

    private void incrementThreadPhase() {
        threadPhase.set(getThreadPhase() + 1);
    }

    private long numberOfActiveThreads() {
        return (((long) PARITY_SIZE_WAITING.getVolatile(this)) & sizeMask) >>> sizeShift;
    }

    private long numberOfWaitingThreads() {
        return ((long) PARITY_SIZE_WAITING.getVolatile(this)) & waitingMask;
    }

    private void deactivateBarrier() {
        long expectedValue = threadPhase.get() + ((1L - getThreadPhaseLSB()) << senseShift);
        long newValue = threadPhase.get() + (getThreadPhaseLSB() << senseShift);
        SENSE_PHASE.compareAndSet(this, expectedValue, newValue);
    }

    private boolean allActiveThreadsBlocked() {
        long localValue = (long) PARITY_SIZE_WAITING.getVolatile(this);
        return ((localValue & sizeMask) >>> sizeShift) == (localValue & waitingMask);
    }

    private boolean isIncrementForIncorrectPhase(long localParity) {
        return localParity != getThreadPhaseLSB();
    }

    private boolean isBarrierActive() {
        return ((((long) SENSE_PHASE.getVolatile(this) & senseMask)) >>> senseShift) != getThreadPhaseLSB();
    }

    // VarHandle mechanics
    private static final VarHandle SENSE_PHASE;
    private static final VarHandle PARITY_SIZE_WAITING;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SENSE_PHASE = l.findVarHandle(IdleTimeDynamicBarrierImpl.class, "sensePhase", long.class);
            PARITY_SIZE_WAITING = l.findVarHandle(IdleTimeDynamicBarrierImpl.class, "paritySizeWaiting", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
