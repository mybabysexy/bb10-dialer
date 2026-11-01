package com.duc1607.bb10dialer

import android.telecom.DisconnectCause
import com.duc1607.bb10dialer.telecom.CallCenterState
import com.duc1607.bb10dialer.telecom.CallFailure
import com.duc1607.bb10dialer.telecom.CallPhase
import com.duc1607.bb10dialer.telecom.LiveCall
import com.duc1607.bb10dialer.telecom.dialerSpecialCode
import com.duc1607.bb10dialer.telecom.failureOf
import com.duc1607.bb10dialer.telecom.isMmi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogicTest {
    private fun call(id: Int, phase: CallPhase, parent: Int? = null) =
        LiveCall(id = id, number = "$id", phase = phase, parentId = parent)

    @Test
    fun `the call being talked on comes before one on hold or being set up`() {
        val state = CallCenterState(calls = listOf(call(1, CallPhase.Holding), call(2, CallPhase.Dialing), call(3, CallPhase.Active)))
        assertEquals(3, state.foreground?.id)
        assertEquals(1, state.background?.id)
    }

    @Test
    fun `a second call being placed shows over the one it put on hold`() {
        val state = CallCenterState(calls = listOf(call(1, CallPhase.Holding), call(2, CallPhase.Dialing)))
        assertEquals(2, state.foreground?.id)
        assertEquals(1, state.background?.id)
    }

    @Test
    fun `a call left on hold alone is the foreground, with no background`() {
        val state = CallCenterState(calls = listOf(call(1, CallPhase.Holding)))
        assertEquals(1, state.foreground?.id)
        assertNull(state.background)
    }

    @Test
    fun `a ringing call is not the foreground`() {
        val state = CallCenterState(calls = listOf(call(1, CallPhase.Ringing)))
        assertEquals(1, state.ringing?.id)
        assertNull(state.foreground)
    }

    @Test
    fun `members of a conference do not count as calls of their own`() {
        val state = CallCenterState(
            calls = listOf(
                call(1, CallPhase.Active).copy(childIds = listOf(2, 3)),
                call(2, CallPhase.Active, parent = 1),
                call(3, CallPhase.Active, parent = 1),
            ),
        )
        assertEquals(1, state.foreground?.id)
        assertNull(state.background)
        assertEquals(listOf(2, 3), state.members(state.foreground!!).map { it.id })
    }

    @Test
    fun `only an outgoing call that never connected can fail`() {
        assertEquals(CallFailure.Busy, failureOf(DisconnectCause.BUSY, outgoing = true, everConnected = false))
        assertEquals(CallFailure.Unreachable, failureOf(DisconnectCause.ERROR, outgoing = true, everConnected = false))
        assertEquals(CallFailure.Unreachable, failureOf(DisconnectCause.REMOTE, outgoing = true, everConnected = false))
        assertNull(failureOf(DisconnectCause.BUSY, outgoing = false, everConnected = false))
        assertNull(failureOf(DisconnectCause.ERROR, outgoing = true, everConnected = true))
    }

    @Test
    fun `hanging up is not a failure`() {
        assertNull(failureOf(DisconnectCause.LOCAL, outgoing = true, everConnected = false))
        assertNull(failureOf(DisconnectCause.CANCELED, outgoing = true, everConnected = false))
    }

    @Test
    fun `secret codes and network codes are told from numbers`() {
        assertEquals("4636", dialerSpecialCode("*#*#4636#*#*"))
        assertNull(dialerSpecialCode("*#*##*#*"))
        assertNull(dialerSpecialCode("0912345678"))
        assertTrue(isMmi("*100#"))
        assertTrue(isMmi("#31#"))
        assertFalse(isMmi("0912345678"))
        assertFalse(isMmi("112"))
    }
}
