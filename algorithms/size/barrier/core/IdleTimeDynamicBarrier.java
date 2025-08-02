package algorithms.size.barrier.core;

public interface IdleTimeDynamicBarrier {
    public void register();
    public void register(int tid);
    public void leave();
    public void leave(int tid);
    public void await();
    public void trigger();
    public long getPhase();
    public long getThreadPhase();
}
