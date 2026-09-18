#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

// Functions exported from Go (main.go)
extern int mipsTunStart(int tunFd, const char* socksHost, int socksPort, int mtu, const char* username, const char* password);
extern int mipsTunStop(void);
extern int mipsTunIsRunning(void);
extern void mipsTunGetStats(uint64_t *tx_pkts, uint64_t *tx_bytes, uint64_t *rx_pkts, uint64_t *rx_bytes);

JNIEXPORT jboolean JNICALL
Java_com_simplexray_re_service_TProxyService_MipsTunStartService(
    JNIEnv *env,
    jobject thiz,
    jint tun_fd,
    jstring socks_host,
    jint socks_port,
    jint mtu,
    jstring username,
    jstring password
) {
    const char *c_host = NULL;
    const char *c_user = NULL;
    const char *c_pass = NULL;

    if (socks_host != NULL) {
        c_host = (*env)->GetStringUTFChars(env, socks_host, NULL);
    }
    if (username != NULL) {
        c_user = (*env)->GetStringUTFChars(env, username, NULL);
    }
    if (password != NULL) {
        c_pass = (*env)->GetStringUTFChars(env, password, NULL);
    }

    int ret = mipsTunStart(
        tun_fd,
        c_host ? c_host : "127.0.0.1",
        socks_port,
        mtu,
        c_user ? c_user : "",
        c_pass ? c_pass : ""
    );

    if (socks_host != NULL && c_host != NULL) {
        (*env)->ReleaseStringUTFChars(env, socks_host, c_host);
    }
    if (username != NULL && c_user != NULL) {
        (*env)->ReleaseStringUTFChars(env, username, c_user);
    }
    if (password != NULL && c_pass != NULL) {
        (*env)->ReleaseStringUTFChars(env, password, c_pass);
    }

    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_simplexray_re_service_TProxyService_MipsTunStopService(
    JNIEnv *env,
    jobject thiz
) {
    int ret = mipsTunStop();
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_simplexray_re_service_TProxyService_MipsTunIsRunning(
    JNIEnv *env,
    jobject thiz
) {
    return mipsTunIsRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_com_simplexray_re_service_TProxyService_MipsTunGetStats(
    JNIEnv *env,
    jobject thiz
) {
    uint64_t tx_pkts = 0, tx_bytes = 0, rx_pkts = 0, rx_bytes = 0;
    mipsTunGetStats(&tx_pkts, &tx_bytes, &rx_pkts, &rx_bytes);

    jlong array[4];
    array[0] = (jlong)tx_pkts;
    array[1] = (jlong)tx_bytes;
    array[2] = (jlong)rx_pkts;
    array[3] = (jlong)rx_bytes;

    jlongArray res = (*env)->NewLongArray(env, 4);
    if (res != NULL) {
        (*env)->SetLongArrayRegion(env, res, 0, 4, array);
    }
    return res;
}
