package nativeCode;

public class MemBarrier {
    static {
        System.loadLibrary("membarrier");
    }

    public static native int flushAllThreads();
}
