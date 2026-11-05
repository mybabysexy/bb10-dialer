package com.duc1607.bb10dialer.data

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DIALABLE = "0123456789*#+"

/** Below this many digits a number is a service code: it only equals itself. */
private const val MIN_LOOSE_DIGITS = 9

/**
 * What identifies a line: the last nine digits, which do not depend on how the country code was
 * written, so "0346697174", "+84 346 697 174" and "0346697174@ims..." are one number.
 */
internal fun numberKey(number: String): String {
    val digits = number.substringBefore('@').filter { it.isDigit() }
    return if (digits.length < MIN_LOOSE_DIGITS) digits else digits.takeLast(MIN_LOOSE_DIGITS)
}

/** Whether two numbers reach the same line (see [numberKey]). */
fun sameNumber(a: String, b: String): Boolean {
    val key = numberKey(a)
    return key.isNotEmpty() && key == numberKey(b)
}

/**
 * What an empty dial pad fills in: the number of the newest call, on SIM [slot] when one is chosen.
 * A blocked call was never a conversation, so it does not count.
 */
fun latestNumber(calls: List<CallEntry>, slot: Int?): String? = calls.firstNotNullOfOrNull { call ->
    val dialable = call.number.substringBefore('@').filter { it in DIALABLE }
    dialable.takeIf { it.isNotEmpty() && call.kind != CallKind.Blocked && (slot == null || call.slot == slot) }
}

/** The missed calls that came in after [seenMillis]. */
fun newMissedCount(calls: List<CallEntry>, seenMillis: Long): Int =
    calls.count { it.kind == CallKind.Missed && it.dateMillis > seenMillis }

/** Finds the contact that owns a number. */
/** A pasted [text] that is a phone number: 3 to 13 digits and nothing else, spaces around aside. */
fun pastableNumber(text: CharSequence?): String? =
    text?.toString()?.trim()?.takeIf { it.length in 3..13 && it.all { c -> c in '0'..'9' } }

class ContactIndex(contacts: List<Contact>) {
    private val byKey = HashMap<String, Pair<Contact, PhoneNumber>>()

    init {
        for (contact in contacts) {
            for (phone in contact.numbers) {
                val key = numberKey(phone.number)
                if (key.isNotEmpty()) byKey.putIfAbsent(key, contact to phone)
            }
        }
    }

    fun find(number: String): Pair<Contact, PhoneNumber>? = byKey[numberKey(number)]
}

/**
 * Where [typed] sits inside [number], as characters of [number], skipping separators: "34"
 * matches "0 3-4..." at 2..4. Null when it is not there. Never treats [typed] as a pattern.
 */
fun findTyped(number: String, typed: String): IntRange? {
    if (typed.isEmpty()) return null
    val positions = number.indices.filter { number[it] in DIALABLE }
    val dialable = positions.joinToString("") { number[it].toString() }
    val start = dialable.indexOf(typed)
    if (start < 0) return null
    return positions[start]..positions[start + typed.length - 1]
}

/** Every contact number containing [typed], for the dial pad. */
fun matchNumbers(contacts: List<Contact>, typed: String): List<NumberMatch> =
    contacts.flatMap { contact ->
        contact.numbers.mapNotNull { phone ->
            findTyped(phone.number, typed)?.let { NumberMatch(contact, phone, it) }
        }
    }

private val CombiningMarks = Regex("\\p{M}+")

/** Lower case, without accents, so "duc" finds "Đức". */
fun String.folded(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(CombiningMarks, "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .lowercase()

/** Contacts whose name contains [query], or whose number does when it is typed as digits. */
fun searchContacts(contacts: List<Contact>, query: String): List<Contact> {
    val q = query.trim().folded()
    if (q.isEmpty()) return contacts
    return contacts.filter { contact ->
        contact.foldedName.contains(q) || contact.phonetic?.matches(q) == true ||
            contact.numbers.any { findTyped(it.number, q) != null }
    }
}

/** The letter a contact is listed under: "Đức" under D; anything but a letter under #. */
fun indexLetter(name: String): String = letterOfFolded(name.folded())

private fun letterOfFolded(folded: String): String {
    val first = folded.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercase() else "#"
}

/** The title of the favorites section. The screen shows it in the device's language. */
const val FAVORITES_SECTION = "★"

/** Favorites first, each contact only there; then the others by first letter, A to Z, with # last. */
fun contactSections(contacts: List<Contact>): List<Section<Contact>> {
    val (favorites, others) = contacts.partition { it.favorite }
    val letters = others
        .groupBy { letterOfFolded(it.indexKey) }
        .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
        .map { (letter, items) -> Section(letter, items.sortedBy { it.indexKey }) }
    return if (favorites.isEmpty()) {
        letters
    } else {
        listOf(Section(FAVORITES_SECTION, favorites.sortedBy { it.indexKey })) + letters
    }
}

fun CallEntry.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()

/** Merges neighbouring calls of the same number, kind and day; keeps the order of [entries]. */
fun groupCalls(entries: List<CallEntry>, zone: ZoneId = ZoneId.systemDefault()): List<CallGroup> {
    val groups = mutableListOf<MutableList<CallEntry>>()
    for (entry in entries) {
        val last = groups.lastOrNull()?.last()
        val joins = last != null &&
            last.number == entry.number &&
            last.kind == entry.kind &&
            last.localDate(zone) == entry.localDate(zone)
        if (joins) groups.last().add(entry) else groups.add(mutableListOf(entry))
    }
    return groups.map { CallGroup(it) }
}

/** Splits an already sorted list into days; [title] names each day. */
fun <T> sectionsByDay(
    items: List<T>,
    dateOf: (T) -> LocalDate,
    title: (LocalDate) -> String,
): List<Section<T>> {
    val sections = mutableListOf<Pair<LocalDate, MutableList<T>>>()
    for (item in items) {
        val date = dateOf(item)
        if (sections.lastOrNull()?.first == date) {
            sections.last().second.add(item)
        } else {
            sections.add(date to mutableListOf(item))
        }
    }
    return sections.map { (date, list) -> Section(title(date), list) }
}
