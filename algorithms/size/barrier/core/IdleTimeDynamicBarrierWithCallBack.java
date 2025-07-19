package algorithms.size.barrier.core;

import java.util.function.Supplier;

public interface IdleTimeDynamicBarrierWithCallBack {
    public void register();
    public void leave();
    public void await();
    public void trigger(boolean activate_cb);
    public long getPhase();
    public long getThreadPhase();
    public void setCBFunc(Supplier<Boolean> cb_func);
}
