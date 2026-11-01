package com.duc1607.bb10dialer

import com.duc1607.bb10dialer.data.PhoneSettings
import com.duc1607.bb10dialer.data.smartDialed
import com.duc1607.bb10dialer.telecom.enabledIn
import com.duc1607.bb10dialer.telecom.forwardedTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSettingsTest {
    @Test
    fun `nothing set up leaves a number alone`() {
        assertEquals("0912345678", smartDialed("0912345678", PhoneSettings()))
        assertEquals("1234", smartDialed("1234", PhoneSettings()))
    }

    @Test
    fun `a short number gets the area code when that makes a whole one`() {
        val settings = PhoneSettings(areaCode = "28", nationalLength = 10)
        assertEquals("2838123456", smartDialed("38123456", settings))
        assertEquals("0912345678", smartDialed("0912345678", settings))
    }

    @Test
    fun `an extension goes through the access number, after the pause`() {
        val settings = PhoneSettings(extensionAccess = "0281234", extensionLength = 4, pauseSeconds = 3)
        assertEquals("0281234,,5678", smartDialed("5678", settings))
    }

    @Test
    fun `codes and written out numbers are never touched`() {
        val settings = PhoneSettings(areaCode = "28", nationalLength = 6, extensionAccess = "1", extensionLength = 4)
        assertEquals("*100#", smartDialed("*100#", settings))
        assertEquals("+123", smartDialed("+123", settings))
    }

    @Test
    fun `a service state is read from the wording of its reply`() {
        val on = listOf("Service was enabled.", "Service was enabled for:")
        val off = listOf("Service has been disabled.")
        assertEquals(true, enabledIn("Service was enabled for:\nVoice", on, off))
        assertEquals(false, enabledIn("Service has been disabled.", on, off))
        assertNull(enabledIn("Network error", on, off))
        assertNull(enabledIn(null, on, off))
        assertNull(enabledIn("Service", listOf(""), listOf("")))
    }

    @Test
    fun `a forwarding reply gives the number, nothing, or no answer`() {
        assertEquals("+84912345678", forwardedTo("Voice: +84912345678\nData: Not forwarded"))
        assertEquals("0912345678", forwardedTo("Thoại: 091 234 5678"))
        assertEquals("", forwardedTo("Voice: Not forwarded"))
        assertNull(forwardedTo(null))
        assertNull(forwardedTo("  "))
    }
}
