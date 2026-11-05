package com.duc1607.bb10dialer.data

import android.view.KeyEvent

/** How a Q25 reports its Call key when the key has no key code of its own. */
private const val CALL_KEY_SCAN_CODE = 231

fun isCallKey(keyCode: Int, scanCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_CALL || scanCode == CALL_KEY_SCAN_CODE

/** The phone this runs on, for what its keyboard does. */
enum class PhoneModel {
    Generic, Q25, P26;

    /** The Call key answers and calls. */
    val hasCallKey: Boolean get() = this == Q25

    /** The key that turns the speaker on and off during a call, if the keyboard has one. */
    val speakerKey: Int? get() = if (this == Q25) KeyEvent.KEYCODE_GRAVE else null

    /** What a letter key types on a dial pad, as the digits printed on the key. */
    fun symbolOf(keyCode: Int): Char? = when (this) {
        Generic -> null
        Q25 -> Q25Keys[keyCode]
        P26 -> P26Keys[keyCode]
    }
}

private val SharedKeys = mapOf(
    KeyEvent.KEYCODE_W to '1',
    KeyEvent.KEYCODE_E to '2',
    KeyEvent.KEYCODE_R to '3',
    KeyEvent.KEYCODE_S to '4',
    KeyEvent.KEYCODE_D to '5',
    KeyEvent.KEYCODE_F to '6',
)

private val Q25Keys = SharedKeys + mapOf(
    KeyEvent.KEYCODE_Z to '7',
    KeyEvent.KEYCODE_X to '8',
    KeyEvent.KEYCODE_C to '9',
    KeyEvent.KEYCODE_Q to '#',
    KeyEvent.KEYCODE_A to '*',
)

private val P26Keys = SharedKeys + mapOf(
    KeyEvent.KEYCODE_X to '7',
    KeyEvent.KEYCODE_C to '8',
    KeyEvent.KEYCODE_V to '9',
    KeyEvent.KEYCODE_Q to '0',
    KeyEvent.KEYCODE_A to '#',
    KeyEvent.KEYCODE_Z to '*',
)
