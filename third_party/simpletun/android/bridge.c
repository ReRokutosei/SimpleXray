#include <jni.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <signal.h>
#include <arpa/inet.h>
#include <android/log.h>

#include "simpletun.h"

#define TAG "SimpleTUN-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define SIMPLETUN_JNI_CLASS "com/simplexray/re/service/SimpleTunNative"

typedef struct {
    pthread_t thread;
    int thread_created;
    int tun_fd;
    uint32_t socks_ip;
    uint16_t socks_port;
    int running;
} SimpleTunContext;

static SimpleTunContext context;
static pthread_mutex_t context_mutex = PTHREAD_MUTEX_INITIALIZER;

static void *run_engine(void *arg) {
    (void)arg;
    LOGI("SimpleTUN worker thread started");
    int rc = simpletun_run();
    LOGI("SimpleTUN worker thread finished with rc=%d", rc);

    pthread_mutex_lock(&context_mutex);
    context.running = 0;
    pthread_mutex_unlock(&context_mutex);

    return NULL;
}

static jint native_start(JNIEnv *env, jclass klass, jint fd, jstring socks_host, jint socks_port) {
    (void)klass;
    if (fd < 0 || socks_port <= 0 || socks_port > 65535) {
        return -1;
    }

    signal(SIGPIPE, SIG_IGN);

    pthread_mutex_lock(&context_mutex);
    if (context.running) {
        pthread_mutex_unlock(&context_mutex);
        return 0; // Already running
    }
    if (context.thread_created) {
        pthread_join(context.thread, NULL);
        context.thread_created = 0;
    }

    uint32_t ip = 0x7f000001; // default 127.0.0.1
    if (socks_host != NULL) {
        const char *host_str = (*env)->GetStringUTFChars(env, socks_host, NULL);
        if (host_str != NULL) {
            struct in_addr addr;
            if (inet_aton(host_str, &addr) != 0) {
                ip = ntohl(addr.s_addr);
            }
            (*env)->ReleaseStringUTFChars(env, socks_host, host_str);
        }
    }

    // Synchronously initialize the engine on the calling thread so that
    // any initialization failure is immediately propagated to Kotlin.
    int init_rc = simpletun_init(fd, ip, (uint16_t)socks_port);
    if (init_rc != 0) {
        pthread_mutex_unlock(&context_mutex);
        LOGE("Failed to initialize SimpleTUN engine: rc=%d", init_rc);
        return init_rc;
    }

    memset(&context, 0, sizeof(context));
    context.tun_fd = fd;
    context.socks_ip = ip;
    context.socks_port = (uint16_t)socks_port;
    context.running = 1;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    // Explicitly configure 4MB stack to accommodate Engine struct without stack overflow on Bionic
    pthread_attr_setstacksize(&attr, 4 * 1024 * 1024);

    int create_rc = pthread_create(&context.thread, &attr, run_engine, &context);
    pthread_attr_destroy(&attr);

    if (create_rc != 0) {
        simpletun_stop();
        simpletun_run();
        context.running = 0;
        context.thread_created = 0;
        pthread_mutex_unlock(&context_mutex);
        LOGE("Failed to create worker thread for SimpleTUN: rc=%d", create_rc);
        return -2;
    }

    context.thread_created = 1;
    pthread_mutex_unlock(&context_mutex);
    LOGI("SimpleTUN nativeStart succeeded");
    return 0;
}

static jint native_stop(JNIEnv *env, jclass klass) {
    (void)env;
    (void)klass;

    pthread_mutex_lock(&context_mutex);
    if (!context.thread_created) {
        pthread_mutex_unlock(&context_mutex);
        return 0;
    }

    LOGI("Stopping SimpleTUN engine...");
    simpletun_stop();

    pthread_t thread_to_join = context.thread;
    context.running = 0;
    context.thread_created = 0;
    pthread_mutex_unlock(&context_mutex);

    pthread_join(thread_to_join, NULL);

    LOGI("SimpleTUN engine stopped cleanly");
    return 0;
}

static jstring native_version(JNIEnv *env, jclass klass) {
    (void)klass;
    return (*env)->NewStringUTF(env, simpletun_version());
}

static const JNINativeMethod methods[] = {
    { "nativeStart", "(ILjava/lang/String;I)I", (void *)native_start },
    { "nativeStop", "()I", (void *)native_stop },
    { "nativeVersion", "()Ljava/lang/String;", (void *)native_version },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = (*env)->FindClass(env, SIMPLETUN_JNI_CLASS);
    if (cls == NULL) {
        LOGE("Could not find class: %s", SIMPLETUN_JNI_CLASS);
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, cls, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        (*env)->DeleteLocalRef(env, cls);
        LOGE("Failed to register native methods for SimpleTUN");
        return JNI_ERR;
    }
    (*env)->DeleteLocalRef(env, cls);
    return JNI_VERSION_1_6;
}
