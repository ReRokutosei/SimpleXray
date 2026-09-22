package com.simplexray.re.service

/** JNI entry points for the in-process Zeptun backend. */
object ZeptunNative {
    init {
        System.loadLibrary("zeptun-jni")
    }

    external fun nativeStart(service: Any?, fd: Int, json: String?): Int
    external fun nativeStop(): Int
    external fun nativeVersion(): String
    external fun nativeCounter(index: Int): Long
}
