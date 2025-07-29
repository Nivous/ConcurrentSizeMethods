package algorithms.size.barrier.core;

import java.util.function.*;

import measurements.support.ThreadID;

public class OperationSelector<K, V> {
    private IdleTimeDynamicBarrier [] usedBarrier = new IdleTimeDynamicBarrier[ThreadID.MAX_THREADS];
    private BarrierSizeCalculator sizeCalculator;

    public OperationSelector(BarrierSizeCalculator calculator) {
        sizeCalculator = calculator;
    }

    public V selectRemoveGet(Function<K, V> firstRemoveGet,
                          Function<K, V> secondRemoveGet,
                          K key) {
        V res;
        if (preOp())
            res = firstRemoveGet.apply(key);
        else
            res = secondRemoveGet.apply(key);
        postOp();
        return res;
    }

    public V selectPut(BiFunction<K, V, V> firstPut,
                       BiFunction<K, V, V> secondPut,
                       K key,
                       V value) {
        V res;
        if (preOp())
            res = firstPut.apply(key, value);
        else
            res = secondPut.apply(key, value);
        postOp();
        return res;
    }

    public V selectObjectRemoveGet(Function<Object, V> firstRemoveGet,
                          Function<Object, V> secondRemoveGet,
                          Object key) {
        V res;
        if (preOp())
            res = firstRemoveGet.apply(key);
        else
            res = secondRemoveGet.apply(key);
        postOp();
        return res;
    }

    public V selectObjectRemove(BiFunction<Object, Object, V> firstPut,
                       BiFunction<Object, Object, V> secondPut,
                       Object key,
                       Object value) {
        V res;
        if (preOp())
            res = firstPut.apply(key, value);
        else
            res = secondPut.apply(key, value);
        postOp();
        return res;
    }

    // Return true if should use fast path
    private boolean preOp() {
        int tid = ThreadID.threadID.get();
        //System.out.println("Thread: " + tid + ", Before preOp");
        IdleTimeDynamicBarrier secondBarrier = null;
        IdleTimeDynamicBarrier firstBarrier = null;
        firstBarrier = sizeCalculator.getBarrierUsed();
        firstBarrier.register();
        secondBarrier = firstBarrier;

        if (firstBarrier instanceof WeakIdleTimeDynamicBarrierImpl)
            secondBarrier = sizeCalculator.getBarrierUsed();

        if (secondBarrier != firstBarrier) {
            firstBarrier.leave();
            secondBarrier.register();
        }

        //System.out.println("Thread: " + tid + ", After preOp");
        
        usedBarrier[tid] = secondBarrier;

        return useFastPath(usedBarrier[tid].getThreadPhase());
    }

    private void postOp() {
        usedBarrier[ThreadID.threadID.get()].leave();
    }

    /**
     * Checks If a thread should operate in the slow path or fast path by parity of the size phase.
     */
    private boolean useFastPath(long sizePhase) {
        return (sizePhase & 1) == 0;
    }
}