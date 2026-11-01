package com.duc1607.bb10dialer

import com.duc1607.bb10dialer.ui.call.callTimer
import org.junit.Assert.assertEquals
import org.junit.Test

class CallTimerTest {
    @Test
    fun `seconds are padded and minutes are not`() {
        assertEquals("0:00", callTimer(0))
        assertEquals("0:13", callTimer(13))
        assertEquals("12:05", callTimer(725))
    }

    @Test
    fun `hours appear only past an hour, with padded minutes`() {
        assertEquals("59:59", callTimer(3599))
        assertEquals("1:00:00", callTimer(3600))
        assertEquals("1:02:13", callTimer(3733))
    }
}
