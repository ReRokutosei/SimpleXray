/*
 * xray_exec.c – spawn the Xray binary with the Android VPN fd properly inherited.
 */

#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

#define LOG_TAG    "XrayExec"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* fd number reserved for the VPN fd inside the Xray process */
#define CHILD_TUN_FD 4

static void close_extra_fds(int keep_fd)
{
    long max_fd = sysconf(_SC_OPEN_MAX);
    if (max_fd <= 0 || max_fd > 65536) max_fd = 1024;
    for (int fd = 3; fd < (int)max_fd; fd++) {
        if (fd != keep_fd) close(fd);
    }
}

JNIEXPORT jintArray JNICALL
Java_com_simplexray_an_service_TProxyService_nativeSpawnXray(
        JNIEnv *env, jclass clazz,
        jstring xray_path_j,
        jstring asset_dir_j,
        jint    vpn_fd,
        jstring format_j)
{
    const char *xray_path = (*env)->GetStringUTFChars(env, xray_path_j, NULL);
    const char *asset_dir = (*env)->GetStringUTFChars(env, asset_dir_j,  NULL);
    const char *format    = (*env)->GetStringUTFChars(env, format_j,    NULL);

    int stdin_pipe[2]  = {-1, -1};
    int stdout_pipe[2] = {-1, -1};

    if (pipe(stdin_pipe) < 0 || pipe(stdout_pipe) < 0) {
        LOGE("pipe() failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, xray_path_j, xray_path);
        (*env)->ReleaseStringUTFChars(env, asset_dir_j,  asset_dir);
        (*env)->ReleaseStringUTFChars(env, format_j,    format);
        return NULL;
    }

    char asset_env[4096];
    char tun_fd_env[64];
    snprintf(asset_env,   sizeof(asset_env),   "XRAY_LOCATION_ASSET=%s", asset_dir);
    snprintf(tun_fd_env,  sizeof(tun_fd_env),  "XRAY_TUN_FD=%d",         CHILD_TUN_FD);

    extern char **environ;
    int parent_ec = 0;
    while (environ[parent_ec]) parent_ec++;

    char **new_env = (char **)malloc((size_t)(parent_ec + 3) * sizeof(char *));
    if (!new_env) {
        LOGE("malloc failed for new_env");
        close(stdin_pipe[0]);  close(stdin_pipe[1]);
        close(stdout_pipe[0]); close(stdout_pipe[1]);
        (*env)->ReleaseStringUTFChars(env, xray_path_j, xray_path);
        (*env)->ReleaseStringUTFChars(env, asset_dir_j,  asset_dir);
        (*env)->ReleaseStringUTFChars(env, format_j,    format);
        return NULL;
    }
    int ni = 0;
    for (int i = 0; i < parent_ec; i++) {
        if (strncmp(environ[i], "XRAY_LOCATION_ASSET=", 20) == 0) continue;
        if (strncmp(environ[i], "XRAY_TUN_FD=",         12) == 0) continue;
        new_env[ni++] = environ[i];
    }
    new_env[ni++] = asset_env;
    new_env[ni++] = tun_fd_env;
    new_env[ni]   = NULL;

    char *argv[] = { (char *)xray_path, "run", "-format", (char *)format, "-config", "stdin:", NULL };

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        free(new_env);
        close(stdin_pipe[0]);  close(stdin_pipe[1]);
        close(stdout_pipe[0]); close(stdout_pipe[1]);
        (*env)->ReleaseStringUTFChars(env, xray_path_j, xray_path);
        (*env)->ReleaseStringUTFChars(env, asset_dir_j,  asset_dir);
        return NULL;
    }

    if (pid == 0) {
        /* child */
        prctl(PR_SET_PDEATHSIG, SIGKILL);  /* die with parent */

        dup2(stdin_pipe[0],  STDIN_FILENO);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stdout_pipe[1], STDERR_FILENO);

        close(stdin_pipe[0]);  close(stdin_pipe[1]);
        close(stdout_pipe[0]); close(stdout_pipe[1]);

        /* dup2 does not copy FD_CLOEXEC, so CHILD_TUN_FD survives exec */
        if ((int)vpn_fd >= 0 && (int)vpn_fd != CHILD_TUN_FD) {
            if (dup2((int)vpn_fd, CHILD_TUN_FD) < 0) {
                _exit(1);
            }
        }

        close_extra_fds(CHILD_TUN_FD);

        execve(xray_path, argv, new_env);
        _exit(1);
    }

    /* parent */
    free(new_env);

    close(stdin_pipe[0]);
    close(stdout_pipe[1]);

    (*env)->ReleaseStringUTFChars(env, xray_path_j, xray_path);
    (*env)->ReleaseStringUTFChars(env, asset_dir_j,  asset_dir);
    (*env)->ReleaseStringUTFChars(env, format_j,    format);

    LOGI("Spawned xray pid=%d stdout_read_fd=%d stdin_write_fd=%d",
         pid, stdout_pipe[0], stdin_pipe[1]);

    jintArray result = (*env)->NewIntArray(env, 3);
    if (!result) {
        close(stdout_pipe[0]); close(stdin_pipe[1]);
        return NULL;
    }
    jint arr[3] = { (jint)pid, (jint)stdout_pipe[0], (jint)stdin_pipe[1] };
    (*env)->SetIntArrayRegion(env, result, 0, 3, arr);
    return result;
}
