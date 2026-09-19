package main

/*
#cgo android LDFLAGS: -llog
#if defined(__ANDROID__)
#include <android/log.h>
#include <stdlib.h>

static inline void mips_log_d(const char* msg) {
    __android_log_write(ANDROID_LOG_DEBUG, "MipsTun", msg);
}

static inline void mips_log_i(const char* msg) {
    __android_log_write(ANDROID_LOG_INFO, "MipsTun", msg);
}

static inline void mips_log_w(const char* msg) {
    __android_log_write(ANDROID_LOG_WARN, "MipsTun", msg);
}

static inline void mips_log_e(const char* msg) {
    __android_log_write(ANDROID_LOG_ERROR, "MipsTun", msg);
}
#else
#include <stdio.h>
#include <stdlib.h>

static inline void mips_log_d(const char* msg) {
    fprintf(stderr, "[DEBUG] MipsTun: %s\n", msg);
}

static inline void mips_log_i(const char* msg) {
    fprintf(stderr, "[INFO] MipsTun: %s\n", msg);
}

static inline void mips_log_w(const char* msg) {
    fprintf(stderr, "[WARN] MipsTun: %s\n", msg);
}

static inline void mips_log_e(const char* msg) {
    fprintf(stderr, "[ERROR] MipsTun: %s\n", msg);
}
#endif
*/
import "C"
import "unsafe"

func logDebug(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.mips_log_d(cmsg)
}

func logInfo(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.mips_log_i(cmsg)
}

func logWarn(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.mips_log_w(cmsg)
}

func logError(msg string) {
	cmsg := C.CString(msg)
	defer C.free(unsafe.Pointer(cmsg))
	C.mips_log_e(cmsg)
}
