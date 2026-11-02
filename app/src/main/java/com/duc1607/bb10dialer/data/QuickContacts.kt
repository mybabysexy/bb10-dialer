package com.duc1607.bb10dialer.data

import android.content.Context

/** The fourth shows when no voicemail number is set, in the place of the voicemail tile. */
const val QUICK_CONTACT_SLOTS = 4

/**
 * Where [number] goes among the [shown] quick contacts: the first empty one. Null when it already is
 * one, -1 when none is empty.
 */
fun freeQuickSlot(quick: List<String?>, shown: Int, number: String): Int? {
    val tiles = quick.take(shown)
    if (tiles.any { it != null && sameNumber(it, number) }) return null
    return tiles.indexOfFirst { it == null }.let { if (it < 0 && tiles.size < shown) tiles.size else it }
}

/** The numbers on the quick contact tiles of the Calls tab, kept on this device. */
class QuickContacts(context: Context) {
    private val prefs = context.getSharedPreferences("quick_contacts", Context.MODE_PRIVATE)

    fun load(): List<String?> = List(QUICK_CONTACT_SLOTS) { prefs.getString(key(it), null) }

    fun set(slot: Int, number: String?) {
        prefs.edit().putString(key(slot), number).apply()
    }

    private fun key(slot: Int) = "slot_$slot"
}
