package algorithms.size.barrier.core;

public interface IdleTimeDynamicBarrier {
    public void register();
    public void leave();
    public void await();
    public void trigger();
    public long getPhase();
    public long getThreadPhase();
}
