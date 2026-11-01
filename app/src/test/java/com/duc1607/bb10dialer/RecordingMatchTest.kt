package com.duc1607.bb10dialer

import com.duc1607.bb10dialer.data.CallBook
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.telecom.Recording
import com.duc1607.bb10dialer.telecom.matchRecordings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingMatchTest {
    private fun call(id: Long, at: Long, kind: CallKind = CallKind.Incoming) =
        CallEntry(id = id, number = "0912345678", dateMillis = at, durationSeconds = 60, kind = kind)

    private fun recording(at: Long) = Recording(File("r$at.ogg"), "0912345678", at)

    @Test
    fun `a recording belongs to the newest call that began before it`() {
        val calls = listOf(call(3, 3000), call(2, 2000), call(1, 1000))
        val result = matchRecordings(calls, listOf(recording(2500), recording(1500), recording(3001)))
        assertEquals(listOf(2500L), result.byCall[2]?.map { it.startedAtMillis })
        assertEquals(listOf(1500L), result.byCall[1]?.map { it.startedAtMillis })
        assertEquals(listOf(3001L), result.byCall[3]?.map { it.startedAtMillis })
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun `one call can hold several recordings, newest first as given`() {
        val result = matchRecordings(listOf(call(1, 1000)), listOf(recording(1900), recording(1100)))
        assertEquals(listOf(1900L, 1100L), result.byCall[1]?.map { it.startedAtMillis })
    }

    @Test
    fun `a recording older than the whole history is kept apart, not lost`() {
        val result = matchRecordings(listOf(call(1, 1000)), listOf(recording(500)))
        assertTrue(result.byCall.isEmpty())
        assertEquals(listOf(500L), result.unmatched.map { it.startedAtMillis })
    }

    @Test
    fun `a cleared log leaves every recording unmatched`() {
        val result = matchRecordings(emptyList(), listOf(recording(500)))
        assertEquals(1, result.unmatched.size)
    }

    @Test
    fun `the book lists missed calls apart and groups neighbours`() {
        val book = CallBook(
            listOf(
                call(3, 3000, CallKind.Missed),
                call(2, 2000, CallKind.Missed),
                call(1, 1000, CallKind.Incoming),
            ),
        )
        assertEquals(listOf(2, 1), book.all.flatMap { it.items }.map { it.count })
        assertEquals(listOf(2), book.missed.flatMap { it.items }.map { it.count })
    }
}
