package com.duc1607.bb10dialer.data

import android.content.Context

/** The fourth shows when no voicemail number is set, in the place of the voicemail tile. */
const val QUICK_CONTACT_SLOTS = 4

/** The numbers on the quick contact tiles of the Calls tab, kept on this device. */
class QuickContacts(context: Context) {
    private val prefs = context.getSharedPreferences("quick_contacts", Context.MODE_PRIVATE)

    fun load(): List<String?> = List(QUICK_CONTACT_SLOTS) { prefs.getString(key(it), null) }

    fun set(slot: Int, number: String?) {
        prefs.edit().putString(key(slot), number).apply()
    }

    private fun key(slot: Int) = "slot_$slot"
}
