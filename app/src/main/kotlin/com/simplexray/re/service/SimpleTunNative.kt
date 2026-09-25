package com.simplexray.re.service

/** JNI entry points for the in-process SimpleTUN backend. */
object SimpleTunNative {
    init {
        System.loadLibrary("simpletun-jni")
    }

    external fun nativeStart(fd: Int, socksHost: String?, socksPort: Int): Int
    external fun nativeStop(): Int
    external fun nativeVersion(): String
}
