#include <jni.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "zeptun.h"

#define ZEPTUN_JNI_CLASS "com/simplexray/re/service/ZeptunNative"

typedef struct {
    Zeptun *tun;
    JavaVM *vm;
    jobject service;
    jmethodID protect;
    pthread_t thread;
    int running;
} ZeptunContext;

static ZeptunContext context;
static pthread_mutex_t context_mutex = PTHREAD_MUTEX_INITIALIZER;

static JNIEnv *attach_current_thread(int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    if (context.vm == NULL) return NULL;
    if ((*context.vm)->GetEnv(context.vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    if ((*context.vm)->AttachCurrentThread(context.vm, &env, NULL) != JNI_OK) {
        return NULL;
    }
    *attached = 1;
    return env;
}

static bool protect_socket(void *ctx, int fd) {
    (void)ctx;
    if (context.vm == NULL || context.service == NULL || context.protect == NULL) {
        return true;
    }

    int attached = 0;
    JNIEnv *env = attach_current_thread(&attached);
    if (env == NULL) return false;

    jboolean ok = (*env)->CallBooleanMethod(env, context.service, context.protect, (jint)fd);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        ok = JNI_FALSE;
    }
    if (attached) (*context.vm)->DetachCurrentThread(context.vm);
    return ok == JNI_TRUE;
}

static void *run_engine(void *arg) {
    (void)arg;
    zeptun_run(context.tun);
    return NULL;
}

static jint native_start(JNIEnv *env, jclass klass, jobject service, jint fd, jstring json) {
    (void)klass;
    if (fd < 0) return ZEPTUN_ERR_INVALID_ARGUMENT;

    pthread_mutex_lock(&context_mutex);
    if (context.tun != NULL) {
        pthread_mutex_unlock(&context_mutex);
        return ZEPTUN_ERR_ALREADY_RUNNING;
    }
    memset(&context, 0, sizeof(context));
    (*env)->GetJavaVM(env, &context.vm);

    if (service != NULL) {
        context.service = (*env)->NewGlobalRef(env, service);
        if (context.service == NULL) {
            pthread_mutex_unlock(&context_mutex);
            return ZEPTUN_ERR_OUT_OF_MEMORY;
        }
        jclass service_class = (*env)->GetObjectClass(env, service);
        context.protect = (*env)->GetMethodID(env, service_class, "protect", "(I)Z");
        (*env)->DeleteLocalRef(env, service_class);
        if (context.protect == NULL || (*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            (*env)->DeleteGlobalRef(env, context.service);
            memset(&context, 0, sizeof(context));
            pthread_mutex_unlock(&context_mutex);
            return ZEPTUN_ERR_INVALID_ARGUMENT;
        }
    }

    int rc;
    if (json != NULL) {
        const char *text = (*env)->GetStringUTFChars(env, json, NULL);
        if (text == NULL) {
            if (context.service != NULL) (*env)->DeleteGlobalRef(env, context.service);
            memset(&context, 0, sizeof(context));
            pthread_mutex_unlock(&context_mutex);
            return ZEPTUN_ERR_INVALID_ARGUMENT;
        }
        rc = zeptun_create_from_json(text, strlen(text), &context.tun);
        (*env)->ReleaseStringUTFChars(env, json, text);
    } else {
        ZeptunConfig config;
        rc = zeptun_config_init(&config, ZEPTUN_PRESET_MOBILE);
        if (rc == ZEPTUN_OK) {
            config.device_kind = ZEPTUN_DEVICE_FD;
            config.tun_fd = fd;
            rc = zeptun_create(&config, &context.tun);
        }
    }
    if (rc != ZEPTUN_OK || context.tun == NULL) {
        if (context.service != NULL) (*env)->DeleteGlobalRef(env, context.service);
        memset(&context, 0, sizeof(context));
        pthread_mutex_unlock(&context_mutex);
        return rc;
    }

    rc = zeptun_set_device_fd(context.tun, fd);
    if (rc == ZEPTUN_OK) {
        rc = zeptun_set_protect_callback(context.tun, protect_socket, NULL);
    }
    if (rc != ZEPTUN_OK || pthread_create(&context.thread, NULL, run_engine, NULL) != 0) {
        zeptun_destroy(context.tun);
        if (context.service != NULL) (*env)->DeleteGlobalRef(env, context.service);
        memset(&context, 0, sizeof(context));
        pthread_mutex_unlock(&context_mutex);
        return rc == ZEPTUN_OK ? ZEPTUN_ERR_OUT_OF_MEMORY : rc;
    }
    context.running = 1;
    pthread_mutex_unlock(&context_mutex);
    return ZEPTUN_OK;
}

static jint native_stop(JNIEnv *env, jclass klass) {
    (void)env;
    (void)klass;

    pthread_mutex_lock(&context_mutex);
    Zeptun *tun = context.tun;
    pthread_t thread = context.thread;
    int running = context.running;
    pthread_mutex_unlock(&context_mutex);

    if (tun == NULL) return ZEPTUN_OK;
    int rc = zeptun_stop(tun);
    if (running) pthread_join(thread, NULL);

    pthread_mutex_lock(&context_mutex);
    zeptun_destroy(context.tun);
    if (context.service != NULL) {
        (*env)->DeleteGlobalRef(env, context.service);
    }
    memset(&context, 0, sizeof(context));
    pthread_mutex_unlock(&context_mutex);
    return rc;
}

static jstring native_version(JNIEnv *env, jclass klass) {
    (void)klass;
    return (*env)->NewStringUTF(env, zeptun_version_string());
}

static jlong native_counter(JNIEnv *env, jclass klass, jint index) {
    (void)env;
    (void)klass;
    if (index < 0) return -1;

    pthread_mutex_lock(&context_mutex);
    Zeptun *tun = context.tun;
    pthread_mutex_unlock(&context_mutex);
    if (tun == NULL) return -1;

    ZeptunStats stats;
    if (zeptun_stats(tun, &stats) != ZEPTUN_OK) return -1;
    const uint64_t *fields = &stats.rx_packets;
    const size_t count = (sizeof(ZeptunStats) - offsetof(ZeptunStats, rx_packets)) / sizeof(uint64_t);
    if ((size_t)index >= count) return -1;
    return (jlong)fields[index];
}

static const JNINativeMethod methods[] = {
    { "nativeStart", "(Ljava/lang/Object;ILjava/lang/String;)I", (void *)native_start },
    { "nativeStop", "()I", (void *)native_stop },
    { "nativeVersion", "()Ljava/lang/String;", (void *)native_version },
    { "nativeCounter", "(I)J", (void *)native_counter },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = (*env)->FindClass(env, ZEPTUN_JNI_CLASS);
    if (cls == NULL) return JNI_ERR;
    if ((*env)->RegisterNatives(env, cls, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        (*env)->DeleteLocalRef(env, cls);
        return JNI_ERR;
    }
    (*env)->DeleteLocalRef(env, cls);
    return JNI_VERSION_1_6;
}
