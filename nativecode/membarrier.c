#include <jni.h>
#include <linux/membarrier.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
#include "nativecode_MemBarrier.h"

JNIEXPORT jint JNICALL Java_nativecode_MemBarrier_flushAllThreads(JNIEnv *env, jclass clazz) {
    int ret = syscall(SYS_membarrier, MEMBARRIER_CMD_SHARED, 0);
    if (ret == -1) {
        return -errno;
    }
    return ret;
}
