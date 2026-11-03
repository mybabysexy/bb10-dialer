package com.duc1607.bb10dialer.data

import android.content.Context

/** The fourth shows when no voicemail number is set, in the place of the voicemail tile. */
const val QUICK_CONTACT_SLOTS = 4

/** The keys of the dial pad that call a number when held; 1 is voicemail's. */
val SPEED_DIAL_KEYS = 2..9

/**
 * Where [number] goes among the [shown] quick contacts: the first empty one. Null when it already is
 * one, -1 when none is empty.
 */
fun freeQuickSlot(quick: List<String?>, shown: Int, number: String): Int? {
    val tiles = quick.take(shown)
    if (tiles.any { it != null && sameNumber(it, number) }) return null
    return tiles.indexOfFirst { it == null }.let { if (it < 0 && tiles.size < shown) tiles.size else it }
}

/**
 * Speed dial, kept on this device: the numbers on the tiles of the Calls tab, and those that the
 * keys 2 to 9 of the dial pad call when held.
 */
class QuickContacts(context: Context) {
    private val prefs = context.getSharedPreferences("quick_contacts", Context.MODE_PRIVATE)

    fun load(): List<String?> = List(QUICK_CONTACT_SLOTS) { prefs.getString(key(it), null) }

    fun set(slot: Int, number: String?) {
        prefs.edit().putString(key(slot), number).apply()
    }

    /** The number of each dial pad key that has one. */
    fun loadKeys(): Map<Int, String> =
        SPEED_DIAL_KEYS.mapNotNull { digit -> prefs.getString(padKey(digit), null)?.let { digit to it } }.toMap()

    fun setKey(digit: Int, number: String?) {
        prefs.edit().putString(padKey(digit), number).apply()
    }

    private fun key(slot: Int) = "slot_$slot"

    private fun padKey(digit: Int) = "key_$digit"
}
