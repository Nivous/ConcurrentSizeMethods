package algorithms.size.barrier.core;

import measurements.support.Padding;
import measurements.support.ThreadID;

public class WeakIdleTimeDynamicBarrierImpl implements IdleTimeDynamicBarrier{

    // Implementation variables
    private long [][] activeArray = new long[ThreadID.MAX_THREADS][Padding.PADDING];
    
    
    public WeakIdleTimeDynamicBarrierImpl() {
    }


    @Override
    public long getPhase() {
        return 0;
    }

    @Override
    public long getThreadPhase() {
        return 0;
    }

    @Override
    public void register() {
        activeArray[ThreadID.threadID.get()][0] = 1;
    }

    @Override
    public void await() {
    }

    @Override
    public void leave() {
        activeArray[ThreadID.threadID.get()][0] = 0;
    }

    @Override
    public void trigger() {
    }

    public void blockUntilAllInactive() {
        for (int i = 0; i < ThreadID.MAX_THREADS; i++) {
            while (activeArray[i][0] == 1);
        }
    }
}
