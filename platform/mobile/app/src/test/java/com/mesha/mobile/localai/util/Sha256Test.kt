package com.mesha.mobile.localai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Sha256Test {

    @Test
    fun ofFile_matchesKnownDigest() {
        val file = File.createTempFile("sha", ".bin").apply { writeText("hello") }
        // Known SHA-256 of the ASCII string "hello".
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Sha256.ofFile(file),
        )
        file.delete()
    }

    @Test
    fun ofFile_handlesLargeMultiBufferInput() {
        val file = Files.createTempFile("big", ".bin").toFile()
        val bytes = ByteArray(1 shl 20) { (it % 256).toByte() } // 1 MB
        file.writeBytes(bytes)

        // Digest is stable and equals a fresh computation.
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, Sha256.ofFile(file))
        file.delete()
    }

    @Test
    fun matches_isCaseInsensitive() {
        assertTrue(Sha256.matches("ABCDEF", "abcdef"))
        assertFalse(Sha256.matches("abcdef", "123456"))
    }

    @Test
    fun matches_blankExpectedSkipsVerification() {
        assertTrue(Sha256.matches("", "anything"))
    }
}
