package algorithms.size.barrier.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import measurements.support.ThreadID;

public class IdleTimeDynamicBarrierAltImpl implements IdleTimeDynamicBarrier{

    // Implementation variables
    private long sensePhase = 0;
    private long phMod4Size = 0;
    private long sizeAtTrigger = 0;
    private ThreadLocal<Long> threadPhase = new ThreadLocal<Long>();
    // Helper fields
    private static final int phMod4Shift = 62;
    private static final int senseShift = 63;

    private static final long senseMask = 1L << senseShift;
    private static final long phaseMask = senseMask - 1;
    private static final long sizeMask = (1L << phMod4Shift) - 1;

    public IdleTimeDynamicBarrierAltImpl() {
    }


    @Override
    public long getPhase() {
        return extractPhase((long) SENSE_PHASE.getVolatile(this));
    }

    @Override
    public long getThreadPhase() {
        return threadPhase.get();
    }

    @Override
    public void register() {
        long localMod4 = extractphMod4(incrementSize());
        long localPhase = getPhase();
        if (localMod4 == ((localPhase - 1) % 4))
            decrementSizeAtTrigger();
        else if (localMod4 == ((localPhase + 1) % 4))
            localPhase += 1;
        threadPhase.set(localPhase);
        blockUntilBarrierDeactivates();
    }

    @Override
    public void await() {
        if (!isBarrierActive()) {
            return;
        }
        incrementThreadPhase();
        decrementSizeAtTrigger();
        blockUntilBarrierDeactivates();
    }

    @Override
    public void leave() {
        long localMod4 = extractphMod4(decrementSize());
        if (localMod4 != (getThreadPhase() % 4))
            decrementSizeAtTrigger();
    }

    @Override
    public void trigger() {
        prepareNextPhase();
        incrementBarrierPhase();
        if (allActiveThreadsBlocked()) {
            deactivateBarrierFromTrigger();
        }
    }

    private void prepareNextPhase() {
        decrementSizeAtTrigger();
        long expectedValue = (long) PHMOD4_SIZE.getVolatile(this);
        long newValue = (newPhMod4(extractphMod4(expectedValue)) << phMod4Shift) + extractSize(expectedValue);
        while (!PHMOD4_SIZE.compareAndSet(this, expectedValue, newValue)) {
            expectedValue = (long) PHMOD4_SIZE.getVolatile(this);
            newValue = (newPhMod4(extractphMod4(expectedValue)) << phMod4Shift) + extractSize(expectedValue);
        }
        SIZE_AT_TRIGGER.getAndAdd(this, extractSize(newValue) + 1);
    }


    private void blockUntilBarrierDeactivates() {
        while (shouldThreadBeBlocked()) {
            if (allActiveThreadsBlocked()) {
                deactivateBarrier();
            }
        }
    }

    private long getPhaseLSB() {
        return extractPhase((long) SENSE_PHASE.getVolatile(this)) & 0x1;
    }

    private long getThreadPhaseLSB() {
        return threadPhase.get() & 0x1;
    }

    private void incrementThreadPhase() {
        threadPhase.set(getThreadPhase() + 1);
    }

    private void deactivateBarrier() {
        long expectedValue = threadPhase.get() + ((1L - getThreadPhaseLSB()) << senseShift);
        long newValue = threadPhase.get() + (getThreadPhaseLSB() << senseShift);
        SENSE_PHASE.compareAndSet(this, expectedValue, newValue);
    }

    private boolean allActiveThreadsBlocked() {
        return (long) SIZE_AT_TRIGGER.getVolatile(this) == 0;
    }

    private boolean isBarrierActive() {
        return extractSense((long) SENSE_PHASE.getVolatile(this)) != getPhaseLSB();
    }

    private boolean shouldThreadBeBlocked() {
        return extractSense((long) SENSE_PHASE.getVolatile(this)) != getThreadPhaseLSB();
    }

    private long extractphMod4(long field) {
        return (field >>> phMod4Shift) & 0x3;
    }

    private long extractSense(long field) {
        return (field >>> senseShift) & 0x1;
    }

    private long extractSize(long field) {
        return field & sizeMask;
    }

    private long extractPhase(long field) {
        return field & phaseMask;
    }

    private long incrementSize() {
        return (long)PHMOD4_SIZE.getAndAdd(this, 1);
    }

    private long decrementSizeAtTrigger() {
        return (long) SIZE_AT_TRIGGER.getAndAdd(this, -1);
    }

    private long decrementSize() {
        return (long) PHMOD4_SIZE.getAndAdd(this, -1);
    }

    private void incrementBarrierPhase() {
        SENSE_PHASE.getAndAdd(this, 1);
    }

    private long newPhMod4(long field) {
        return (field + 1) % 4;
    }

    private void deactivateBarrierFromTrigger() {
        long newSensePhase = ((getPhaseLSB()) << senseShift) + extractPhase((long) SENSE_PHASE.getOpaque(this));
        SENSE_PHASE.setOpaque(this, newSensePhase);
    }

    // VarHandle mechanics
    private static final VarHandle SENSE_PHASE;
    private static final VarHandle PHMOD4_SIZE;
    private static final VarHandle SIZE_AT_TRIGGER;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SENSE_PHASE = l.findVarHandle(IdleTimeDynamicBarrierAltImpl.class, "sensePhase", long.class);
            PHMOD4_SIZE = l.findVarHandle(IdleTimeDynamicBarrierAltImpl.class, "phMod4Size", long.class);
            SIZE_AT_TRIGGER = l.findVarHandle(IdleTimeDynamicBarrierAltImpl.class, "sizeAtTrigger", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
