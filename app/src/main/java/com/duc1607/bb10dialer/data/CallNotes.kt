package com.duc1607.bb10dialer.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user wrote about a call. [savedAtMillis] is during or at the end of that call. */
@Immutable
data class CallNote(val number: String, val createdAtMillis: Long, val savedAtMillis: Long, val text: String)

/** The notes of one number, sorted out by the call they were written in. */
data class MatchedNotes(val byCall: Map<Long, List<CallNote>>, val unmatched: List<CallNote>)

/**
 * Hands each note to its call: the newest that began before it was saved. [calls] are one number's,
 * newest first. A note older than them all lost its call, but is still the user's.
 */
fun matchNotes(calls: List<CallEntry>, notes: List<CallNote>): MatchedNotes {
    val (matched, unmatched) = notes
        .map { note -> note to calls.firstOrNull { it.dateMillis <= note.savedAtMillis } }
        .partition { it.second != null }
    return MatchedNotes(matched.groupBy({ it.second!!.id }, { it.first }), unmatched.map { it.first })
}

/**
 * The calls of the whole log, newest first, that have one of [items]: what was made during a call,
 * from [numberOf], at the time [atOf], belongs to the newest call that began before it.
 */
fun <T> callsWith(calls: List<CallEntry>, items: List<T>, numberOf: (T) -> String, atOf: (T) -> Long): Set<Long> {
    if (items.isEmpty()) return emptySet()
    val byNumber = calls.groupBy { numberKey(it.number) }
    return items.mapNotNullTo(HashSet()) { item ->
        byNumber[numberKey(numberOf(item))]?.firstOrNull { it.dateMillis <= atOf(item) }?.id
    }
}

fun notedCalls(calls: List<CallEntry>, notes: List<CallNote>): Set<Long> =
    callsWith(calls, notes, { it.number }, { it.savedAtMillis })

/**
 * Kept in device protected storage: a call before the first unlock after a restart can have a note
 * too. One note for each call, known by its number and when it was created.
 */
object CallNotes {
    private const val FILE = "call_notes"
    private const val SEPARATOR = '|'
    private var flow: MutableStateFlow<List<CallNote>>? = null

    fun state(context: Context): StateFlow<List<CallNote>> {
        val current = flow ?: MutableStateFlow(read(context)).also { flow = it }
        return current.asStateFlow()
    }

    /** Blank text takes the note away. */
    fun save(context: Context, number: String, createdAtMillis: Long, text: String) {
        if (number.isEmpty()) return
        val key = key(number, createdAtMillis)
        prefs(context).edit().apply {
            if (text.isBlank()) remove(key) else putString(key, "${System.currentTimeMillis()}$SEPARATOR$text")
        }.apply()
        reload(context)
    }

    fun delete(context: Context, note: CallNote) {
        prefs(context).edit().remove(key(note.number, note.createdAtMillis)).apply()
        reload(context)
    }

    private fun reload(context: Context) {
        state(context)
        flow!!.value = read(context)
    }

    private fun key(number: String, createdAtMillis: Long) = "$createdAtMillis$SEPARATOR$number"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(context: Context): List<CallNote> = prefs(context).all.mapNotNull { (key, value) ->
        val created = key.substringBefore(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
        val body = value as? String ?: return@mapNotNull null
        val saved = body.substringBefore(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
        CallNote(key.substringAfter(SEPARATOR), created, saved, body.substringAfter(SEPARATOR))
    }
}
