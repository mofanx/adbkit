package com.adbkit.app.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbServiceTest {

    @Test
    fun `shellQuote escapes single quotes and wraps in single quotes`() {
        assertEquals("'hello'", AdbService.shellQuote("hello"))
        assertEquals("'it'\\''s'", AdbService.shellQuote("it's"))
        assertEquals("''", AdbService.shellQuote(""))
        assertEquals("'path with spaces'", AdbService.shellQuote("path with spaces"))
    }

    @Test
    fun `CommandResult success reflects non-zero exit code`() {
        val success = CommandResult(true, "output", "", 0)
        val failure = CommandResult(false, "", "error", 1)
        assertTrue(success.success)
        assertFalse(failure.success)
        assertEquals("output", success.output)
        assertEquals("error", failure.error)
    }

    @Test
    fun `verifyImageMd5 returns empty for missing file`() = runTest {
        val result = AdbService.verifyImageMd5("/non/existent/image.img")
        assertFalse(result.success)
        assertTrue(result.error.isNotEmpty())
    }

    @Test
    fun `classifyConnectionError identifies common adb connect failures`() {
        assertEquals("connected", AdbService.classifyConnectionError(CommandResult(true, "connected to 192.168.1.2:5555", "", 0)))
        assertEquals("refused", AdbService.classifyConnectionError(CommandResult(false, "", "Connection refused", 1)))
        assertEquals("unreachable", AdbService.classifyConnectionError(CommandResult(false, "", "No route to host", 1)))
        assertEquals("offline", AdbService.classifyConnectionError(CommandResult(false, "", "device offline", 1)))
        assertEquals("auth", AdbService.classifyConnectionError(CommandResult(false, "", "unauthorized", 1)))
        assertEquals("invalid", AdbService.classifyConnectionError(CommandResult(false, "", "cannot resolve host", 1)))
        assertEquals("failed", AdbService.classifyConnectionError(CommandResult(false, "", "some random error", 1)))
    }
}
