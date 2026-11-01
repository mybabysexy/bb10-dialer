package com.duc1607.bb10dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.inputmethod.EditorInfo

/**
 * A keyboard app driven by broadcasts, to test what a real one does with the Call key: the action
 * of the field in focus, not a key event. Debug builds only. Set up with
 * `adb shell ime enable <pkg>/.FakeKeyboard` and `adb shell ime set <pkg>/.FakeKeyboard`, then
 * `adb shell am broadcast -a fake.IME --es go 1`, `--es text w` or `--es del 1`.
 */
class FakeKeyboard : InputMethodService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connection = currentInputConnection
            val info = currentInputEditorInfo
            Log.i("FakeKeyboard", "connection=${connection != null} inputType=${info?.inputType} imeOptions=${info?.imeOptions}")
            intent.getStringExtra("text")?.let { connection?.commitText(it, 1) }
            if (intent.hasExtra("del")) connection?.deleteSurroundingText(1, 0)
            if (intent.hasExtra("go")) connection?.performEditorAction((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter("fake.IME"), RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
