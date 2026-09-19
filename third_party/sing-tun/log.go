package main

/*
#cgo android LDFLAGS: -llog
#if defined(__ANDROID__)
#include <android/log.h>
#include <stdlib.h>

static inline void sing_log_d(const char* msg) {
    __android_log_write(ANDROID_LOG_DEBUG, "SingTun", msg);
}

static inline void sing_log_i(const char* msg) {
    __android_log_write(ANDROID_LOG_INFO, "SingTun", msg);
}

static inline void sing_log_w(const char* msg) {
    __android_log_write(ANDROID_LOG_WARN, "SingTun", msg);
}

static inline void sing_log_e(const char* msg) {
    __android_log_write(ANDROID_LOG_ERROR, "SingTun", msg);
}
#else
#include <stdio.h>
#include <stdlib.h>

static inline void sing_log_d(const char* msg) {
    fprintf(stderr, "[DEBUG] SingTun: %s\n", msg);
}

static inline void sing_log_i(const char* msg) {
    fprintf(stderr, "[INFO] SingTun: %s\n", msg);
}

static inline void sing_log_w(const char* msg) {
    fprintf(stderr, "[WARN] SingTun: %s\n", msg);
}

static inline void sing_log_e(const char* msg) {
    fprintf(stderr, "[ERROR] SingTun: %s\n", msg);
}
#endif
*/
import "C"
import (
	"fmt"
	"unsafe"
)

func logDebug(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.sing_log_d(cmsg)
}

func logInfo(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.sing_log_i(cmsg)
}

func logWarn(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.sing_log_w(cmsg)
}

func logError(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.sing_log_e(cmsg)
}

type singTunLogger struct{}

func (l *singTunLogger) Trace(args ...any) { logDebug(fmt.Sprint(args...)) }
func (l *singTunLogger) Debug(args ...any) { logDebug(fmt.Sprint(args...)) }
func (l *singTunLogger) Info(args ...any)  { logInfo(fmt.Sprint(args...)) }
func (l *singTunLogger) Warn(args ...any)  { logWarn(fmt.Sprint(args...)) }
func (l *singTunLogger) Error(args ...any) { logError(fmt.Sprint(args...)) }
func (l *singTunLogger) Fatal(args ...any) { logError(fmt.Sprint(args...)) }
func (l *singTunLogger) Panic(args ...any) { logError(fmt.Sprint(args...)) }
