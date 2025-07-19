package algorithms.size.barrier.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IdleTimeDynamicBarrierWithCallBackImpl implements IdleTimeDynamicBarrierWithCallBack{

    // Implementation variables
    private AtomicLong sensePhase = new AtomicLong(0);
    private AtomicLong paritySizeWaiting = new AtomicLong(0);
    private ThreadLocal<Long> threadPhase = new ThreadLocal<Long>();
    private Supplier<Boolean> finalCallBackFunction = null;
    private AtomicLong callBackFunctionSwitch = new AtomicLong(0);
    private boolean activateCB = false;

    // Helper fields
    private static final int sizeShift = 31;
    private static final int parityShift = 63;
    private static final int senseShift = 63;
    private static final long sizeIncrementValue = 1L << sizeShift;

    private static final long senseMask = 1L << senseShift;
    private static final long phaseMask = senseMask - 1;
    private static final long waitingMask = (1L << sizeShift) - 1;
    private static final long sizeMask = (1L << 62) - 1 - waitingMask;

    public IdleTimeDynamicBarrierWithCallBackImpl() {
    }

    public IdleTimeDynamicBarrierWithCallBackImpl(Supplier<Boolean> cb_func) {
        finalCallBackFunction = cb_func;
    }

    @Override
    public long getPhase() {
        return extractPhase(sensePhase.get());
    }

    @Override
    public long getThreadPhase() {
        return threadPhase.get();
    }

    @Override
    public void setCBFunc(Supplier<Boolean> cb_func) {
        finalCallBackFunction = cb_func;
    }

    @Override
    public void register() {
        incrementSize();
        threadPhase.set(getPhase());
        if (isBarrierActive()) {
            long result = incrementWaiting();
            long localParity = extractParity(result);
            if (isIncrementForIncorrectPhase(localParity)) {
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
    public void trigger(boolean activate_cb) {
        prepareNextPhase();
        activateCB = activate_cb;
        incrementBarrierPhase();
        if (noActiveThreads()) {
            if (callBackFunctionSwitch.compareAndSet(getPhase() - 1, getPhase())) {
                if (finalCallBackFunction != null && activateCB)
                    finalCallBackFunction.get();
                deactivateBarrierFromTrigger();
            }
        }
    }

    private void prepareNextPhase() {
        long expectedValue = paritySizeWaiting.get();
        long newValue = ((1L - (extractParity(expectedValue))) << parityShift) + (extractSize(expectedValue) << sizeShift);
        while (!paritySizeWaiting.compareAndSet(expectedValue, newValue)) {
            expectedValue = paritySizeWaiting.get();
            newValue = ((1L - (extractParity(expectedValue))) << parityShift) + (extractSize(expectedValue) << sizeShift);
        }
    }


    private void waitingLoop() {
        while (isBarrierActive()) {
            if (allActiveThreadsBlocked()) {
                if (callBackFunctionSwitch.compareAndSet(getThreadPhase() - 1, getThreadPhase())) {
                    if (finalCallBackFunction != null && activateCB)
                        finalCallBackFunction.get();
                    deactivateBarrier();
                }
            }
        }
    }

    private boolean noActiveThreads() {
        return numberOfActiveThreads() == 0;
    }

    private long getPhaseLSB() {
        return extractPhase(sensePhase.get()) & 0x1;
    }

    private long getThreadPhaseLSB() {
        return threadPhase.get() & 0x1;
    }

    private boolean threadInSamePhase() {
        return threadPhase.get() == extractPhase(sensePhase.get());
    }

    private void incrementThreadPhase() {
        threadPhase.set(getThreadPhase() + 1);
    }

    private long numberOfActiveThreads() {
        long res = extractSize(paritySizeWaiting.get());
        return res;
    }

    private long numberOfWaitingThreads() {
        long res = extractWaiting(paritySizeWaiting.get());
        return res;
    }

    private void deactivateBarrier() {
        long expectedValue = threadPhase.get() + ((1L - getThreadPhaseLSB()) << senseShift);
        long newValue = threadPhase.get() + (getThreadPhaseLSB() << senseShift);
        sensePhase.compareAndSet(expectedValue, newValue);
    }

    private boolean allActiveThreadsBlocked() {
        long localValue = paritySizeWaiting.get();
        long size = extractSize(localValue);
        long waiting = extractWaiting(localValue);
        
        return size == waiting;
    }

    private boolean isIncrementForIncorrectPhase(long localParity) {
        return localParity != getThreadPhaseLSB();
    }

    private boolean isBarrierActive() {
        return extractSense(sensePhase.get()) != getThreadPhaseLSB();
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
        paritySizeWaiting.getAndAdd(sizeIncrementValue);
    }

    private long incrementWaiting() {
        long result = paritySizeWaiting.incrementAndGet();
        return result;
    }

    private void decrementSize() {
        paritySizeWaiting.getAndAdd(-sizeIncrementValue);
    }

    private void incrementBarrierPhase() {
        sensePhase.getAndAdd(1);
    }

    private void deactivateBarrierFromTrigger() {
        long newSensePhase = ((getPhaseLSB()) << senseShift) + extractPhase(sensePhase.get());
        sensePhase.set(newSensePhase);
    }
}
