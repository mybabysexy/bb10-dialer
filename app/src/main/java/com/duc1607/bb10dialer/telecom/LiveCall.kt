package com.duc1607.bb10dialer.telecom

import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.runtime.Immutable

enum class CallPhase { Connecting, ChoosingSim, Dialing, Ringing, Active, Holding, Ending, Ended }

enum class CallFailure { Busy, Unreachable }

/** What the screens need to know about a call, without touching [android.telecom.Call]. */
@Immutable
data class LiveCall(
    val id: Int,
    val number: String,
    val presentation: Int = TelecomManager.PRESENTATION_ALLOWED,
    val phase: CallPhase,
    val connectedAtMillis: Long? = null,
    val account: PhoneAccountHandle? = null,
    val simChoices: List<PhoneAccountHandle> = emptyList(),
    val emergency: Boolean = false,
    val canHold: Boolean = false,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val canMute: Boolean = true,
    val canReplySms: Boolean = false,
    val parentId: Int? = null,
    val childIds: List<Int> = emptyList(),
    val canSeparate: Boolean = false,
    val canHangUpSingly: Boolean = false,
    /** Digits waiting for the user's go-ahead, after a ";" in the number. */
    val postDial: String? = null,
    val replies: List<String> = emptyList(),
) {
    val conference: Boolean get() = childIds.isNotEmpty()
}

/** Where the sound goes. */
@Immutable
data class CallAudio(
    val muted: Boolean = false,
    val current: CallEndpoint? = null,
    val available: List<CallEndpoint> = emptyList(),
) {
    val onSpeaker: Boolean get() = current?.endpointType == CallEndpoint.TYPE_SPEAKER
    val onEarpiece: Boolean get() = current?.endpointType == CallEndpoint.TYPE_EARPIECE
    /** More than the phone's own two: choosing takes a list, not a toggle. */
    val needsPicker: Boolean get() = available.any {
        it.endpointType != CallEndpoint.TYPE_SPEAKER && it.endpointType != CallEndpoint.TYPE_EARPIECE
    }
}

/** A call that went out and got nowhere. Stays on screen until dismissed or tried again. */
@Immutable
data class FailedCall(val number: String, val account: PhoneAccountHandle?, val failure: CallFailure)

@Immutable
data class CallCenterState(
    val calls: List<LiveCall> = emptyList(),
    val audio: CallAudio = CallAudio(),
    val failed: FailedCall? = null,
    val ringerSilenced: Boolean = false,
    /** What the user wrote about each call, by [LiveCall.id]. */
    val notes: Map<Int, String> = emptyMap(),
    val recording: Boolean = false,
    /** The call screen is what the user is looking at. */
    val screenShown: Boolean = false,
) {
    /** Top level calls: a conference's members are listed under it. */
    private val tops: List<LiveCall> get() = calls.filter { it.parentId == null }
    val ringing: LiveCall? get() = tops.firstOrNull { it.phase == CallPhase.Ringing }
    /** The call the in-call screen is about: the one being talked on, else the one being set up. */
    val foreground: LiveCall? get() = foregroundOf(tops)
    /** The other call, on hold while [foreground] is under way. */
    val background: LiveCall? get() = foreground?.let { main ->
        tops.firstOrNull { it.id != main.id && it.phase == CallPhase.Holding }
    }
    fun members(conference: LiveCall): List<LiveCall> = calls.filter { it.parentId == conference.id }
}

internal fun foregroundOf(calls: List<LiveCall>): LiveCall? {
    val order = listOf(
        CallPhase.Active, CallPhase.ChoosingSim, CallPhase.Dialing, CallPhase.Connecting,
        CallPhase.Holding, CallPhase.Ending,
    )
    return order.firstNotNullOfOrNull { phase -> calls.firstOrNull { it.phase == phase } }
}

internal fun phaseOf(state: Int): CallPhase = when (state) {
    Call.STATE_NEW, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL, Call.STATE_AUDIO_PROCESSING -> CallPhase.Connecting
    Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.ChoosingSim
    Call.STATE_DIALING -> CallPhase.Dialing
    Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.Ringing
    Call.STATE_ACTIVE -> CallPhase.Active
    Call.STATE_HOLDING -> CallPhase.Holding
    Call.STATE_DISCONNECTING -> CallPhase.Ending
    else -> CallPhase.Ended
}

/**
 * Why an outgoing call that never connected ended, or null when there is nothing to tell: the user
 * hung up, or it did connect. Hanging up is [DisconnectCause.LOCAL] or [DisconnectCause.CANCELED].
 */
internal fun failureOf(code: Int, outgoing: Boolean, everConnected: Boolean): CallFailure? {
    if (!outgoing || everConnected) return null
    return when (code) {
        DisconnectCause.BUSY, DisconnectCause.REJECTED -> CallFailure.Busy
        DisconnectCause.LOCAL, DisconnectCause.CANCELED, DisconnectCause.ANSWERED_ELSEWHERE,
        DisconnectCause.CALL_PULLED, DisconnectCause.OTHER,
        -> null
        else -> CallFailure.Unreachable
    }
}

/** A secret code a dialer hands to the phone: *#*#4636#*#* gives 4636. */
internal fun dialerSpecialCode(number: String): String? {
    if (!number.startsWith("*#*#") || !number.endsWith("#*#*")) return null
    return number.substring(4, number.length - 4).takeIf { it.isNotEmpty() }
}

/** A code the network or the phone answers, not a number to call: *100#, *#06#. */
internal fun isMmi(number: String): Boolean =
    number.length >= 3 && number.endsWith("#") && (number.startsWith("*") || number.startsWith("#"))
