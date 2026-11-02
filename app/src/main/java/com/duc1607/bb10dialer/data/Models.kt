package com.duc1607.bb10dialer.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class CallKind { Incoming, Outgoing, Missed, Rejected, Blocked, Voicemail }

/** One row of the system call log. [cachedName] / [cachedLabel] are what the log stored itself. */
@Immutable
data class CallEntry(
    val id: Long,
    val number: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val kind: CallKind,
    val cachedName: String? = null,
    val cachedLabel: String? = null,
    /** The SIM slot, counted from 1; null when not known. */
    val slot: Int? = null,
)

/** Consecutive calls from one number, of one kind, on one day: a single row, "Duc (3)". */
@Immutable
data class CallGroup(val entries: List<CallEntry>) {
    /** The newest call: entries are in call log order. */
    val latest: CallEntry get() = entries.first()
    val key: Long get() = latest.id
    val count: Int get() = entries.size
}

@Immutable
data class PhoneNumber(val number: String, val label: String)

/** A contact's thumbnail. Replacing a photo keeps its [uri] and changes its [id]. */
@Immutable
data class ContactPhoto(val uri: String, val id: Long)

@Immutable
data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<PhoneNumber>,
    val photo: ContactPhoto? = null,
    val favorite: Boolean = false,
    /** The full size photo, for the places that show it large. */
    val largePhoto: ContactPhoto? = null,
    /** For a Chinese name: how it is spelled in Latin letters. */
    val phonetic: Phonetic? = null,
) {
    /** Worked out once, on the loading thread: sorting and searching keep asking for it. */
    val foldedName: String = name.folded()
    /** What the contact is listed and sorted by: a Chinese name goes under the letter it starts with. */
    val indexKey: String = phonetic?.full ?: foldedName
}

/** The contacts and what the screens derive from them, built once away from the main thread. */
@Immutable
class ContactBook(val contacts: List<Contact> = emptyList()) {
    val sections: List<Section<Contact>> = contactSections(contacts)
    val index = ContactIndex(contacts)
}

/** The call log as the Calls tab lists it, built once away from the main thread. */
@Immutable
class CallBook(
    val calls: List<CallEntry> = emptyList(),
    val locale: Locale = Locale.getDefault(),
    dayTitle: (LocalDate) -> String = { it.toString() },
) {
    private val zone = ZoneId.systemDefault()
    val all: List<Section<CallGroup>> = sectioned(calls, dayTitle)
    val missed: List<Section<CallGroup>> = sectioned(calls.filter { it.kind == CallKind.Missed }, dayTitle)

    private fun sectioned(entries: List<CallEntry>, dayTitle: (LocalDate) -> String) =
        sectionsByDay(groupCalls(entries, zone), { it.latest.localDate(zone) }, dayTitle)
}

/** Time on the phone from [sinceMillis] on; [bySlot] by SIM slot, null for calls on no SIM known. */
@Immutable
data class CallSummary(val sinceMillis: Long?, val totalSeconds: Long, val bySlot: List<Pair<Int?, Long>>)

/** A SIM in use. [slot] counts from 1. */
@Immutable
data class SimLine(val slot: Int, val number: String? = null, val carrier: String? = null)

/** One number of a contact that contains what was typed on the dial pad. */
@Immutable
data class NumberMatch(val contact: Contact, val phone: PhoneNumber, val range: IntRange)

@Immutable
data class Section<T>(val title: String, val items: List<T>)
