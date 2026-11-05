package com.duc1607.bb10dialer.ui.common

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.isCallKey
import com.duc1607.bb10dialer.data.pastableNumber
import com.duc1607.bb10dialer.telecom.CallCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What a key types on a dial pad: the digit the phone's keyboard prints on it, or else the character
 * it gives. Held down, a letter key types one digit, not a row of them.
 */
internal fun KeyEvent.dialSymbol(context: Context): Char {
    val mapped = if (isAltPressed) null else PhoneSettingsStore.get(context).phoneModel.symbolOf(key.nativeKeyCode)
    return when {
        mapped == null -> utf16CodePoint.toChar()
        nativeKeyEvent.repeatCount == 0 -> mapped
        else -> 0.toChar()
    }
}

/** The digit this key types on a dial pad, also while it is held: 1 to 9, or null for any other. */
internal fun KeyEvent.heldDigit(context: Context): Char? {
    val mapped = if (isAltPressed) null else PhoneSettingsStore.get(context).phoneModel.symbolOf(key.nativeKeyCode)
    return (mapped ?: utf16CodePoint.toChar()).takeIf { it in '1'..'9' }
}

/**
 * The number on the clipboard, when it holds one ([pastableNumber]). Reading the clip shows the
 * system's "pasted" notice, so this is only for when the user asks: a hold, a menu opened.
 */
internal fun clipboardNumber(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val description = clipboard.primaryClipDescription ?: return null
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null
    return runCatching { pastableNumber(clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)) }.getOrNull()
}

/** The Call key was pressed, on a phone that has one. */
internal fun KeyEvent.isCallKey(context: Context): Boolean =
    type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount == 0 &&
        isCallKey(nativeKeyEvent.keyCode, nativeKeyEvent.scanCode) &&
        PhoneSettingsStore.get(context).phoneModel.hasCallKey

/**
 * Runs [onCall] on the Call key while this or something in it has the focus, however the key gets
 * here. Without a keyboard app it is a key event. A keyboard app keeps the key and asks the field in
 * focus to do its action instead, and there is no field here, so a phone with a Call key gets one
 * nobody sees while this has the focus. Other keys that app turns into text still arrive as keys.
 */
internal fun Modifier.onCallKey(context: Context, onCall: () -> Unit): Modifier = this then CallKeyElement(context, onCall)

private data class CallKeyElement(val context: Context, val onCall: () -> Unit) : ModifierNodeElement<CallKeyNode>() {
    override fun create() = CallKeyNode(context, onCall)

    override fun update(node: CallKeyNode) {
        node.context = context
        node.onCall = onCall
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onCallKey"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class CallKeyNode(var context: Context, var onCall: () -> Unit) :
    Modifier.Node(),
    KeyInputModifierNode,
    FocusEventModifierNode,
    PlatformTextInputModifierNode {
    private var session: Job? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!event.isCallKey(context)) return false
        onCall()
        return true
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onFocusEvent(focusState: FocusState) {
        if (!focusState.hasFocus) {
            session?.cancel()
            session = null
        } else if (session == null && PhoneSettingsStore.get(context).phoneModel.hasCallKey) {
            session = coroutineScope.launch {
                establishTextInputSession { startInputMethod(CallKeyField(requireView()) { onCall() }) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class CallKeyField(private val view: View, private val onCall: () -> Unit) : PlatformTextInputMethodRequest {
    override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
        outAttributes.inputType = InputType.TYPE_CLASS_PHONE
        outAttributes.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        // Not a full editor: what the keyboard app commits is passed on as key events.
        return object : BaseInputConnection(view, false) {
            override fun performEditorAction(actionCode: Int): Boolean {
                onCall()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                sendKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DEL))
                sendKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DEL))
                return true
            }
        }
    }
}

/**
 * For the call screen's `dispatchKeyEvent`: the speaker key of the phone ([PhoneModel.speakerKey])
 * turns the speaker on or off while there is a call to talk on, whatever has the focus. True when
 * the key was taken, its release included.
 */
internal fun dispatchSpeakerKey(context: Context, event: AndroidKeyEvent): Boolean {
    if (event.keyCode != PhoneSettingsStore.get(context).phoneModel.speakerKey) return false
    if (CallCenter.state.value.foreground == null) return false
    if (event.action == AndroidKeyEvent.ACTION_DOWN && event.repeatCount == 0) CallCenter.toggleSpeaker()
    return true
}

/**
 * For an activity's `dispatchKeyEvent`. A call that is ringing goes first, whatever is on screen.
 * The key goes no further than the app: left alone, the system opens the phone's own dialer with it.
 */
internal fun dispatchCallKey(context: Context, event: AndroidKeyEvent, dispatch: (AndroidKeyEvent) -> Boolean): Boolean {
    if (!isCallKey(event.keyCode, event.scanCode) || !PhoneSettingsStore.get(context).phoneModel.hasCallKey) {
        return dispatch(event)
    }
    val ringing = CallCenter.state.value.ringing
    if (ringing == null) {
        dispatch(event)
    } else if (event.action == AndroidKeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        CallCenter.answer(ringing.id)
    }
    return true
}
