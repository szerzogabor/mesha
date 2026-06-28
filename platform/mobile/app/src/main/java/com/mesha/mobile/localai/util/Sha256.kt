package com.mesha.mobile.localai.util

import java.io.File
import java.security.MessageDigest

/** SHA-256 helpers for verifying downloaded model integrity. */
object Sha256 {

    /** Streams [file] through SHA-256 and returns the lowercase hex digest. */
    fun ofFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /**
     * True when [actual] matches [expected], case-insensitively. A blank [expected] means the
     * catalog did not pin a checksum, so verification is skipped (and treated as a match).
     */
    fun matches(expected: String, actual: String): Boolean =
        expected.isBlank() || expected.equals(actual, ignoreCase = true)

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            out.append(HEX[v ushr 4])
            out.append(HEX[v and 0x0f])
        }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
