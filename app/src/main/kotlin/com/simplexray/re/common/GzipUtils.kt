package com.simplexray.re.common

import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

object GzipUtils {
    /**
     * Inspects the leading bytes of [input] and wraps it with a [GZIPInputStream]
     * if the stream starts with the Gzip magic header (0x1f, 0x8b).
     * Otherwise, returns an [InputStream] delivering the unmodified stream.
     */
    fun wrapIfNeeded(input: InputStream): InputStream {
        val pushback = PushbackInputStream(input, 2)
        val header = ByteArray(2)
        val read = pushback.read(header)
        if (read > 0) {
            pushback.unread(header, 0, read)
        }
        return if (read == 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
            GZIPInputStream(pushback)
        } else {
            pushback
        }
    }
}
