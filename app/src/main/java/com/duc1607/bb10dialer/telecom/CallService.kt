package com.duc1607.bb10dialer.telecom

import android.app.Notification
import android.content.pm.ServiceInfo
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.widget.Toast
import com.duc1607.bb10dialer.CallActivity
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val LOOKUP_TIMEOUT_MILLIS = 400L
private const val AUTO_ANSWER_MILLIS = 5000L

/**
 * What Telecom binds to while there are calls, once this is the default phone app. It reports the
 * calls to [CallCenter] and then follows its state: ringing, the notifications, and opening the
 * call screen.
 */
class CallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var ringer: CallRinger
    private lateinit var notifications: CallNotifications
    private lateinit var recorder: CallRecorder
    private var ringingId: Int? = null
    private var ringingName: String? = null
    private var quietFor: Int? = null
    private val opened = HashSet<Int>()
    private var foregroundId: Int? = null
    private var foregroundKey: Any? = null
    private var inForeground = false
    private var recording = false
    private val autoRecorded = HashSet<Int>()

    override fun onCreate() {
        super.onCreate()
        ringer = CallRinger(this)
        notifications = CallNotifications(this)
        recorder = CallRecorder(this)
        CallCenter.attach(this)
        scope.launch { CallCenter.state.collect(::follow) }
    }

    override fun onDestroy() {
        ringer.stop()
        stopRecording()
        notifications.cancelIncoming()
        CallCenter.detach(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        // Going on on another of the user's devices: nothing to do with this screen.
        if (call.details.hasProperty(Call.Details.PROPERTY_IS_EXTERNAL_CALL)) return
        CallCenter.added(this, call)
    }

    override fun onCallRemoved(call: Call) {
        CallCenter.removed(call)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        startActivity(CallActivity.intent(this))
    }

    override fun onSilenceRinger() = silence()

    internal fun silence() {
        ringer.stop()
        CallCenter.ringerSilenced()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        CallCenter.audioChanged { it.copy(muted = isMuted) }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        CallCenter.audioChanged { it.copy(current = callEndpoint) }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        CallCenter.audioChanged { it.copy(available = availableEndpoints.toList()) }
    }

    internal fun toggleRecording() {
        if (recording) {
            stopRecording()
            return
        }
        val call = CallCenter.state.value.foreground ?: return
        startRecording(call)
    }

    private fun startRecording(call: LiveCall) {
        if (call.emergency || !PhoneSettingsStore.get(this).canRecordCalls) return
        if (recorder.start(call.number)) {
            recording = true
            CallCenter.recordingChanged(true)
            foregroundKey = null
            follow(CallCenter.state.value)
        } else {
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        if (!recording) return
        recorder.stop()
        recording = false
        CallCenter.recordingChanged(false)
        foregroundKey = null
    }

    private fun follow(state: CallCenterState) {
        followRinging(state)
        val foreground = state.foreground
        // A call the user is part of opens the screen once; a ringing one is for the notification.
        if (foreground != null && opened.add(foreground.id)) {
            startActivity(CallActivity.intent(this))
        }
        if (foreground == null && recording) stopRecording()
        if (foreground != null && foreground.emergency && recording) stopRecording()
        autoRecord(foreground)
        followForeground(foreground)
    }

    /** Once for each call: one the user stopped recording stays stopped. */
    private fun autoRecord(call: LiveCall?) {
        if (call == null || call.phase != CallPhase.Active || recording) return
        val settings = PhoneSettingsStore.get(this)
        if (settings.autoRecord && settings.canRecordCalls && autoRecorded.add(call.id)) startRecording(call)
    }

    private fun followRinging(state: CallCenterState) {
        val ringing = state.ringing
        if (ringing == null) {
            if (ringingId != null) {
                ringer.stop()
                notifications.cancelIncoming()
                ringingId = null
                ringingName = null
                quietFor = null
            }
            return
        }
        if (ringingId == ringing.id) {
            // Up and in front now: the notification must not drop down over it. Stays so if the
            // user then leaves the screen, whom the ringing call should not chase.
            val name = ringingName
            if (state.screenShown && quietFor != ringing.id && name != null) {
                quietFor = ringing.id
                notifications.incoming(ringing, name, quiet = true)
            }
            return
        }
        ringingId = ringing.id
        val busy = state.foreground != null
        // A call always takes the whole screen, whatever the user is doing.
        runCatching { startActivity(CallActivity.intent(this)) }
        // The name goes in the notification. The ring cannot wait long for it.
        scope.launch {
            val caller = withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) { lookUpCaller(this@CallService, ringing.number) }
            }
            if (ringingId != ringing.id) return@launch
            val name = caller?.name ?: ringing.number
            ringingName = name
            val shown = CallCenter.state.value.screenShown
            if (shown) quietFor = ringing.id
            notifications.incoming(ringing, name, quiet = shown)
            if (busy) ringer.beepForWaiting() else ringer.ring(ringing.number, caller?.ringtone, simSlot(this@CallService, ringing.account))
            autoAnswerOnHeadset(ringing.id, busy)
        }
    }

    /** The Bluetooth setting: never for a second call, which would put the first on hold unasked. */
    private suspend fun autoAnswerOnHeadset(id: Int, busy: Boolean) {
        if (busy || !PhoneSettingsStore.get(this).autoAnswerBluetooth) return
        delay(AUTO_ANSWER_MILLIS)
        val state = CallCenter.state.value
        val onHeadset = state.audio.available.any { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        if (ringingId == id && onHeadset) CallCenter.answer(id)
    }

    private fun followForeground(call: LiveCall?) {
        if (call == null) {
            if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
            foregroundId = null
            foregroundKey = null
            return
        }
        val key = listOf(call.id, call.connectedAtMillis, recording)
        if (key == foregroundKey) return
        foregroundKey = key
        foregroundId = call.id
        scope.launch {
            val caller = withContext(Dispatchers.IO) { lookUpCaller(this@CallService, call.number) }
            if (foregroundId == call.id) post(notifications.ongoing(call, caller?.name ?: call.number))
        }
    }

    private fun post(notification: Notification) {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (recording) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        // The notification is a convenience: a refusal must not bring a call down with it.
        runCatching { startForeground(ONGOING_NOTIFICATION, notification, types) }
            .onSuccess { inForeground = true }
    }
}
