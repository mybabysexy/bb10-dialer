package com.duc1607.bb10dialer.telecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import com.duc1607.bb10dialer.data.CallNotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.IdentityHashMap

private const val TONE_MILLIS = 150L

/**
 * The calls of the moment. [CallService] feeds it from Telecom, the screens read [state] and ask for
 * changes here. Everything runs on the main thread, which is where Telecom calls back.
 */
object CallCenter {
    private val _state = MutableStateFlow(CallCenterState())
    val state: StateFlow<CallCenterState> = _state.asStateFlow()

    private val calls = LinkedHashMap<Int, Call>()
    private val ids = IdentityHashMap<Call, Int>()
    private var nextId = 1
    private var service: CallService? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
        override fun onConferenceableCallsChanged(call: Call, calls: MutableList<Call>) = publish()
        override fun onCannedTextResponsesLoaded(call: Call, responses: MutableList<String>) = publish()
        override fun onPostDialWait(call: Call, remaining: String) {
            postDial[call] = remaining
            publish()
        }
    }
    private val postDial = IdentityHashMap<Call, String>()
    /** What is being typed in the note dialog, kept if the call ends before Save. */
    private val noteDrafts = HashMap<Int, String>()

    internal fun attach(service: CallService) {
        this.service = service
    }

    internal fun detach(service: CallService) {
        if (this.service === service) this.service = null
    }

    internal fun added(context: Context, call: Call) {
        val id = nextId++
        ids[call] = id
        calls[id] = call
        call.registerCallback(callback, main)
        appContext = context.applicationContext
        _state.update { it.copy(failed = null, ringerSilenced = it.ringerSilenced && calls.size > 1) }
        publish()
    }

    internal fun removed(call: Call) {
        call.unregisterCallback(callback)
        val details = call.details
        val failure = failureOf(
            details.disconnectCause.code,
            outgoing = details.callDirection == Call.Details.DIRECTION_OUTGOING,
            everConnected = details.connectTimeMillis > 0,
        )
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val id = ids.remove(call)
        calls.remove(id)
        postDial.remove(call)
        val note = noteDrafts.remove(id) ?: state.value.notes[id]
        if (note != null) saveNote(call, note)
        _state.update { state ->
            state.copy(
                failed = if (failure != null) FailedCall(number, details.accountHandle, failure) else state.failed,
                notes = state.notes.filterKeys { it != id },
                ringerSilenced = false,
            )
        }
        publish()
    }

    internal fun audioChanged(transform: (CallAudio) -> CallAudio) {
        _state.update { it.copy(audio = transform(it.audio)) }
    }

    internal fun ringerSilenced() {
        _state.update { it.copy(ringerSilenced = true) }
    }

    fun screenShown(shown: Boolean) {
        _state.update { it.copy(screenShown = shown) }
    }

    internal fun recordingChanged(recording: Boolean) {
        _state.update { it.copy(recording = recording) }
    }

    private var appContext: Context? = null

    private fun publish() {
        val live = calls.map { (id, call) -> liveCall(id, call) }
        _state.update { it.copy(calls = live) }
    }

    private fun liveCall(id: Int, call: Call): LiveCall {
        val d = call.details
        val number = d.handle?.schemeSpecificPart.orEmpty()
        val telephony = appContext?.getSystemService(TelephonyManager::class.java)
        val choices = d.intentExtras?.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)
        return LiveCall(
            id = id,
            number = number,
            presentation = d.handlePresentation,
            phase = phaseOf(d.state),
            connectedAtMillis = d.connectTimeMillis.takeIf { it > 0 },
            account = d.accountHandle,
            simChoices = choices.orEmpty(),
            emergency = number.isNotEmpty() && runCatching { telephony?.isEmergencyNumber(number) }.getOrNull() == true,
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canSwap = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canMerge = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            canMute = d.can(Call.Details.CAPABILITY_MUTE),
            canReplySms = d.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT),
            parentId = call.parent?.let { ids[it] },
            childIds = call.children.mapNotNull { ids[it] },
            canSeparate = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canHangUpSingly = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
            postDial = postDial[call],
            replies = call.cannedTextResponses.orEmpty(),
        )
    }

    private fun call(id: Int): Call? = calls[id]

    fun answer(id: Int) {
        call(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Ends the call under way first, for a network that cannot hold it. */
    fun endAndAnswer(id: Int) {
        state.value.foreground?.let { hangUp(it.id) }
        answer(id)
    }

    fun decline(id: Int) {
        call(id)?.reject(false, null)
    }

    fun declineWithMessage(id: Int, text: String) {
        call(id)?.reject(true, text)
    }

    fun hangUp(id: Int) {
        call(id)?.disconnect()
    }

    fun hold(id: Int) {
        call(id)?.hold()
    }

    fun resume(id: Int) {
        call(id)?.unhold()
    }

    /** Puts [foreground] on hold and takes [background] up again. */
    fun swap() {
        val state = state.value
        val current = state.foreground ?: return
        if (current.conference && current.canSwap) {
            call(current.id)?.swapConference()
            return
        }
        val other = state.background ?: return
        call(other.id)?.unhold()
    }

    fun merge(id: Int) {
        val call = call(id) ?: return
        val target = call.conferenceableCalls.firstOrNull()
        if (target != null) call.conference(target) else call.mergeConference()
    }

    fun separate(id: Int) {
        call(id)?.splitFromConference()
    }

    fun pickSim(id: Int, account: PhoneAccountHandle) {
        call(id)?.phoneAccountSelected(account, false)
    }

    fun sendTone(id: Int, symbol: Char) {
        val call = call(id) ?: return
        call.playDtmfTone(symbol)
        main.postDelayed({ call.stopDtmfTone() }, TONE_MILLIS)
    }

    fun answerPostDial(id: Int, proceed: Boolean) {
        val call = call(id) ?: return
        postDial.remove(call)
        call.postDialContinue(proceed)
        publish()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun route(endpoint: CallEndpoint) {
        val service = service ?: return
        service.requestCallEndpointChange(
            endpoint,
            service.mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) = Unit
            },
        )
    }

    /** Speaker on or off, for a phone with nothing else to choose from. */
    fun toggleSpeaker() {
        val audio = state.value.audio
        val wanted = if (audio.onSpeaker) CallEndpoint.TYPE_EARPIECE else CallEndpoint.TYPE_SPEAKER
        audio.available.firstOrNull { it.endpointType == wanted }?.let(::route)
    }

    fun silence() {
        service?.silence()
    }

    /** A conference's note is also about each of the people in it. */
    private fun noteTargets(id: Int): List<Int> = listOf(id) + calls[id]?.children?.mapNotNull { ids[it] }.orEmpty()

    /** Kept right away, so a crash does not lose it. */
    fun setNote(id: Int, note: String) {
        val targets = noteTargets(id)
        targets.forEach { target ->
            noteDrafts.remove(target)
            calls[target]?.let { saveNote(it, note) }
        }
        _state.update { state -> state.copy(notes = state.notes + targets.associateWith { note }) }
    }

    /** Null when the dialog was left without saving. */
    fun draftNote(id: Int, draft: String?) {
        noteTargets(id).forEach {
            if (draft != null) noteDrafts[it] = draft.trim() else noteDrafts.remove(it)
        }
    }

    private fun saveNote(call: Call, note: String) {
        val context = appContext ?: return
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        CallNotes.save(context, number, call.details.creationTimeMillis, note)
    }

    fun toggleRecording() {
        service?.toggleRecording()
    }

    fun dismissFailed() {
        _state.update { it.copy(failed = null) }
    }

}
