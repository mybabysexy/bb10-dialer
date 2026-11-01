package com.duc1607.bb10dialer

import android.view.KeyEvent
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.data.CallNote
import com.duc1607.bb10dialer.data.NumberForms
import com.duc1607.bb10dialer.data.PhoneModel
import com.duc1607.bb10dialer.data.PhoneSettings
import com.duc1607.bb10dialer.data.blacklistEntry
import com.duc1607.bb10dialer.data.isBlacklisted
import com.duc1607.bb10dialer.data.isCallKey
import com.duc1607.bb10dialer.data.latestNumber
import com.duc1607.bb10dialer.data.matchNotes
import com.duc1607.bb10dialer.data.moduleInstallScript
import com.duc1607.bb10dialer.data.newMissedCount
import com.duc1607.bb10dialer.data.notedCalls
import com.duc1607.bb10dialer.data.ringtoneFor
import com.duc1607.bb10dialer.data.sampleSizeFor
import com.duc1607.bb10dialer.ui.calls.playbackTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSettingsTest {
    @Test
    fun `the keys of a Q25 and a P26`() {
        assertEquals('1', PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_W))
        assertEquals('9', PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_C))
        assertEquals('#', PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_Q))
        assertEquals('*', PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_A))
        assertEquals('7', PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_Z))
        assertEquals('1', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_W))
        assertEquals('7', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_X))
        assertEquals('8', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_C))
        assertEquals('9', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_V))
        assertEquals('0', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_Q))
        assertEquals('#', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_A))
        assertEquals('*', PhoneModel.P26.symbolOf(KeyEvent.KEYCODE_Z))
    }

    @Test
    fun `a generic phone leaves its keys alone`() {
        assertNull(PhoneModel.Generic.symbolOf(KeyEvent.KEYCODE_W))
        assertNull(PhoneModel.Q25.symbolOf(KeyEvent.KEYCODE_P))
    }

    @Test
    fun `only a Q25 has a call key`() {
        assertTrue(PhoneModel.Q25.hasCallKey)
        assertFalse(PhoneModel.P26.hasCallKey)
        assertFalse(PhoneModel.Generic.hasCallKey)
    }

    @Test
    fun `the second SIM rings its own tone, or the first one's`() {
        val both = PhoneSettings(ringtone = "one", ringtoneSim2 = "two")
        assertEquals("one", ringtoneFor(both, 1))
        assertEquals("two", ringtoneFor(both, 2))
        assertEquals("one", ringtoneFor(both, null))
        assertEquals("one", ringtoneFor(PhoneSettings(ringtone = "one"), 2))
    }

    private val hanoi = NumberForms("0241234567", "241234567", "84241234567")
    private val hanoiFromAbroad = NumberForms("84241234567", "241234567", "84241234567")

    @Test
    fun `an entry finds a number however it was written`() {
        assertTrue(isBlacklisted(hanoi, listOf("024")))
        assertTrue(isBlacklisted(hanoiFromAbroad, listOf("024")))
        assertTrue(isBlacklisted(hanoi, listOf("+8424")))
        assertTrue(isBlacklisted(hanoiFromAbroad, listOf("+8424")))
        assertTrue(isBlacklisted(hanoi, listOf("0241234567")))
    }

    @Test
    fun `other numbers get through`() {
        assertFalse(isBlacklisted(hanoi, emptyList()))
        assertFalse(isBlacklisted(hanoi, listOf("028", "+8428", "1900")))
        assertFalse(isBlacklisted(hanoi, listOf("02412345678")))
    }

    @Test
    fun `zeros alone do not block every national number`() {
        assertFalse(isBlacklisted(NumberForms("841234", "1234", "841234"), listOf("0", "00")))
        assertTrue(isBlacklisted(hanoi, listOf("0")))
    }

    @Test
    fun `a typed entry keeps digits and a leading plus`() {
        assertEquals("+8424", blacklistEntry(" +84 24"))
        assertEquals("1900", blacklistEntry("19-00"))
        assertEquals("", blacklistEntry("+"))
    }

    @Test
    fun `recording takes the setting, and root or the phone line`() {
        assertFalse(PhoneSettings().canRecordCalls(hasLine = false))
        assertFalse(PhoneSettings().canRecordCalls(hasLine = true))
        assertFalse(PhoneSettings(callRecording = true).canRecordCalls(hasLine = false))
        assertFalse(PhoneSettings(rootGranted = true).canRecordCalls(hasLine = true))
        assertTrue(PhoneSettings(callRecording = true, rootGranted = true).canRecordCalls(hasLine = false))
        assertTrue(PhoneSettings(callRecording = true).canRecordCalls(hasLine = true))
    }

    @Test
    fun `the module holds the app and its allowed permission`() {
        val script = moduleInstallScript("/data/app/x==/base.apk", "com.example")
        assertTrue(script.contains("cp '/data/app/x==/base.apk'"))
        assertTrue(script.contains("privapp-permissions package=\"com.example\""))
        assertTrue(script.contains("android.permission.CAPTURE_AUDIO_OUTPUT"))
        assertTrue(script.contains("system_file"))
    }

    @Test
    fun `playback time has two digits for minutes and seconds`() {
        assertEquals("00:00", playbackTime(0))
        assertEquals("00:05", playbackTime(5_400))
        assertEquals("01:30", playbackTime(90_000))
        assertEquals("1:02:05", playbackTime(3_725_000))
    }

    @Test
    fun `a photo is halved only while it still fills its place`() {
        assertEquals(1, sampleSizeFor(720, 0))
        assertEquals(2, sampleSizeFor(720, 360))
        assertEquals(1, sampleSizeFor(720, 361))
        assertEquals(1, sampleSizeFor(96, 360))
        assertEquals(4, sampleSizeFor(1600, 360))
    }

    @Test
    fun `the Call key is its key code or its scan code`() {
        assertTrue(isCallKey(KeyEvent.KEYCODE_CALL, 0))
        assertTrue(isCallKey(KeyEvent.KEYCODE_UNKNOWN, 231))
        assertFalse(isCallKey(KeyEvent.KEYCODE_W, 17))
    }

    @Test
    fun `only missed calls after the last look are new`() {
        val calls = listOf(
            CallEntry(3, "1", 300, 0, CallKind.Missed),
            CallEntry(2, "2", 200, 0, CallKind.Incoming),
            CallEntry(1, "3", 100, 0, CallKind.Missed),
        )
        assertEquals(2, newMissedCount(calls, 0))
        assertEquals(1, newMissedCount(calls, 100))
        assertEquals(0, newMissedCount(calls, 300))
    }

    @Test
    fun `an empty dial pad fills in the latest number of the chosen SIM`() {
        val calls = listOf(
            CallEntry(4, "0900000004", 400, 0, CallKind.Blocked, slot = 1),
            CallEntry(3, "", 300, 0, CallKind.Missed, slot = 1),
            CallEntry(2, "0900000002", 200, 0, CallKind.Missed, slot = 2),
            CallEntry(1, "+84900000001@ims", 100, 0, CallKind.Outgoing, slot = 1),
        )
        assertEquals("0900000002", latestNumber(calls, null))
        assertEquals("0900000002", latestNumber(calls, 2))
        assertEquals("+84900000001", latestNumber(calls, 1))
        assertNull(latestNumber(calls, 3))
        assertNull(latestNumber(emptyList(), null))
    }

    private fun call(id: Long, number: String, at: Long) = CallEntry(id, number, at, 0, CallKind.Incoming)
    private fun note(number: String, at: Long) = CallNote(number, at, at, "text")

    @Test
    fun `a note belongs to the newest call before it was saved`() {
        val calls = listOf(call(3, "0346697174", 300), call(2, "0346697174", 200), call(1, "0346697174", 100))
        val matched = matchNotes(calls, listOf(note("0346697174", 250), note("0346697174", 50)))
        assertEquals(setOf(2L), matched.byCall.keys)
        assertEquals(1, matched.unmatched.size)
    }

    @Test
    fun `the whole log knows which calls have a note`() {
        val calls = listOf(call(3, "0346697174", 300), call(2, "0900000000", 200), call(1, "0346697174", 100))
        assertEquals(setOf(1L, 2L), notedCalls(calls, listOf(note("+84346697174", 150), note("0900000000", 400))))
        assertEquals(emptySet<Long>(), notedCalls(calls, emptyList()))
    }
}
