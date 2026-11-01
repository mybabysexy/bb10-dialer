package com.duc1607.bb10dialer.ui.call

import android.telecom.CallEndpoint
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.canRecordCalls
import com.duc1607.bb10dialer.telecom.CallCenter
import com.duc1607.bb10dialer.telecom.CallCenterState
import com.duc1607.bb10dialer.telecom.Caller
import com.duc1607.bb10dialer.telecom.CallFailure
import com.duc1607.bb10dialer.telecom.CallPhase
import com.duc1607.bb10dialer.telecom.FailedCall
import com.duc1607.bb10dialer.telecom.LiveCall
import com.duc1607.bb10dialer.telecom.lookUpCaller
import com.duc1607.bb10dialer.telecom.ownNumber
import com.duc1607.bb10dialer.telecom.simSlot
import com.duc1607.bb10dialer.ui.common.onCallKey
import com.duc1607.bb10dialer.ui.theme.DialerColors
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long a call that could not connect stays on screen, with the way to try again. */
private const val FAILED_SHOWN_MILLIS = 3000L

/** Picks the screen for the calls of the moment, and hands what the user does to [CallCenter]. */
@Composable
fun CallHost(
    state: CallCenterState,
    onCallAgain: (FailedCall) -> Unit,
    onAddCall: () -> Unit,
    onClose: () -> Unit,
) {
    val ringing = state.ringing
    val call = state.foreground
    val failed = state.failed
    when {
        ringing != null -> key(ringing.id) { Incoming(ringing, state) }
        call != null -> key(call.id) { Ongoing(call, state, onAddCall) }
        failed != null -> Failed(failed, onCallAgain, onClose)
        // A moment with nothing to show, while the screen closes: dark, as the screens are.
        else -> Column(Modifier.fillMaxSize().background(DialerColors.CallPanel)) {}
    }
}

@Composable
private fun Incoming(ringing: LiveCall, state: CallCenterState) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    // For the Call key, which only reaches what has the focus when a keyboard app has it first.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    val party = rememberParty(ringing.number, ringing.presentation, detailed = true)
    val other = state.foreground
    val otherParty = other?.let { rememberParty(it.number, it.presentation, detailed = false) }
    var replying by rememberSaveable { mutableStateOf(false) }
    val settings = PhoneSettingsStore.get(LocalContext.current)
    IncomingCallScreen(
        party = party,
        myNumber = rememberLine(ringing.account),
        ringerSilenced = state.ringerSilenced,
        waitingOn = otherParty?.title,
        modifier = (
            if (settings.silenceByTap) {
                Modifier.pointerInput(Unit) { detectTapGestures { CallCenter.silence() } }
            } else {
                Modifier
            }
            )
            .onCallKey(context) { CallCenter.answer(ringing.id) }
            .focusRequester(focus)
            .focusable(),
        onSilence = CallCenter::silence,
        onSms = if (settings.replyNow && ringing.canReplySms) ({ replying = true }) else null,
        onAnswer = { CallCenter.answer(ringing.id) },
        onEndAndAnswer = if (other != null) ({ CallCenter.endAndAnswer(ringing.id) }) else null,
        onDecline = { CallCenter.decline(ringing.id) },
        showSlider = settings.showAnswerSlider,
    )
    if (replying) {
        ReplyDialog(
            saved = settings.replies,
            onSend = { text ->
                replying = false
                CallCenter.declineWithMessage(ringing.id, text)
            },
            onDismiss = { replying = false },
        )
    }
}

@Composable
private fun Ongoing(call: LiveCall, state: CallCenterState, onAddCall: () -> Unit) {
    val members = if (call.conference) state.members(call) else emptyList()
    val single = rememberParty(call.number, call.presentation, detailed = false)
    val party = if (call.conference) {
        CallParty(stringResource(R.string.conference_call, members.size))
    } else {
        single
    }
    val held = state.background
    val heldParty = held?.let { rememberParty(it.number, it.presentation, detailed = false) }
    var routing by rememberSaveable { mutableStateOf(false) }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    val audio = state.audio
    InCallScreen(
        party = party,
        myNumber = rememberLine(call.account),
        status = when (call.phase) {
            CallPhase.Active -> CallStatus.Active
            CallPhase.Holding -> CallStatus.OnHold
            else -> CallStatus.Dialing
        },
        connectedAtMillis = call.connectedAtMillis,
        speakerOn = audio.onSpeaker || (audio.needsPicker && !audio.onEarpiece),
        muted = audio.muted,
        recording = state.recording,
        note = state.notes[call.id].orEmpty(),
        canRecord = !call.emergency,
        showRecord = PhoneSettingsStore.get(LocalContext.current).canRecordCalls(LocalContext.current),
        onNoteChange = { CallCenter.setNote(call.id, it) },
        onNoteDraft = { CallCenter.draftNote(call.id, it) },
        onSpeaker = { if (audio.needsPicker) routing = true else CallCenter.toggleSpeaker() },
        onMute = { CallCenter.setMuted(!audio.muted) },
        onDigit = { CallCenter.sendTone(call.id, it) },
        onRecord = CallCenter::toggleRecording,
        onAddCall = onAddCall,
        onEnd = { CallCenter.hangUp(call.id) },
        onCallAgain = {},
        heldTitle = heldParty?.title,
        onSwap = CallCenter::swap,
        onMerge = if (call.canMerge && held != null) ({ CallCenter.merge(call.id) }) else null,
        onResume = { CallCenter.resume(call.id) },
        memberCount = members.size,
        onMembers = { showMembers = true },
        showEndButton = PhoneSettingsStore.get(LocalContext.current).showEndButton,
    )
    if (call.phase == CallPhase.ChoosingSim) {
        SimDialog(
            choices = call.simChoices,
            onPick = { CallCenter.pickSim(call.id, it) },
            onDismiss = { CallCenter.hangUp(call.id) },
        )
    }
    call.postDial?.let { digits ->
        Bb10Dialog(
            title = stringResource(R.string.post_dial_title),
            message = digits,
            confirmText = stringResource(R.string.post_dial_send),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { CallCenter.answerPostDial(call.id, true) },
            onDismiss = { CallCenter.answerPostDial(call.id, false) },
        )
    }
    if (routing) {
        RouteDialog(
            available = audio.available,
            onPick = {
                routing = false
                CallCenter.route(it)
            },
            onDismiss = { routing = false },
        )
    }
    if (showMembers && members.isNotEmpty()) {
        MembersDialog(members, onDismiss = { showMembers = false })
    }
}

@Composable
private fun Failed(failed: FailedCall, onCallAgain: (FailedCall) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    LaunchedEffect(failed) {
        delay(FAILED_SHOWN_MILLIS)
        onClose()
    }
    InCallScreen(
        party = rememberParty(failed.number, TelecomManager.PRESENTATION_ALLOWED, detailed = false),
        myNumber = rememberLine(failed.account),
        status = if (failed.failure == CallFailure.Busy) CallStatus.Busy else CallStatus.Unreachable,
        connectedAtMillis = null,
        speakerOn = false,
        muted = false,
        recording = false,
        note = "",
        onNoteChange = {},
        onSpeaker = {},
        onMute = {},
        onDigit = {},
        onRecord = {},
        onAddCall = {},
        onEnd = onClose,
        onCallAgain = { onCallAgain(failed) },
    )
}

/** Who a number is: found in contacts, or the number itself, or that it is hidden. */
@Composable
private fun rememberParty(number: String, presentation: Int, detailed: Boolean): CallParty {
    val context = LocalContext.current
    val hidden = presentation != TelecomManager.PRESENTATION_ALLOWED || number.isEmpty()
    val hiddenTitle = stringResource(if (presentation == TelecomManager.PRESENTATION_RESTRICTED) R.string.private_number else R.string.unknown_number)
    val unknown = stringResource(R.string.unknown_caller)
    val showNumbers = PhoneSettingsStore.get(context).showNumbers
    val caller by produceState<Caller?>(null, number) {
        value = if (hidden) null else withContext(Dispatchers.IO) { lookUpCaller(context, number) }
    }
    val known = caller
    return when {
        hidden -> CallParty(hiddenTitle)
        known == null -> CallParty(number, if (detailed) unknown else null)
        else -> CallParty(
            known.name,
            if (!detailed) null else if (!showNumbers) known.label else if (known.label != null) stringResource(R.string.quick_contact_number, number, known.label) else number,
            known.photo,
        )
    }
}

/** The user's line a call is on, for the top of the screen: the number when the SIM knows it. */
@Composable
private fun rememberLine(account: PhoneAccountHandle?): String? {
    val context = LocalContext.current
    val slot = remember(account) { simSlot(context, account) }
    val own = remember(account) { ownNumber(context, account) }
    val label = slot?.let { stringResource(R.string.sim_label, it) }
    return own ?: label
}

@Composable
private fun SimDialog(choices: List<PhoneAccountHandle>, onPick: (PhoneAccountHandle) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = choices.mapIndexed { i, account ->
        val slot = simSlot(context, account) ?: (i + 1)
        stringResource(R.string.sim_label, slot) to { onPick(account) }
    }
    ChoiceDialog(stringResource(R.string.choose_sim), entries, onDismiss)
}

@Composable
private fun RouteDialog(available: List<CallEndpoint>, onPick: (CallEndpoint) -> Unit, onDismiss: () -> Unit) {
    val entries = available.map { endpoint ->
        val name = when (endpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> stringResource(R.string.route_earpiece)
            CallEndpoint.TYPE_SPEAKER -> stringResource(R.string.call_speaker)
            CallEndpoint.TYPE_WIRED_HEADSET -> stringResource(R.string.route_headset)
            else -> endpoint.endpointName.toString()
        }
        name to { onPick(endpoint) }
    }
    ChoiceDialog(stringResource(R.string.route_title), entries, onDismiss)
}

@Composable
private fun ReplyDialog(saved: List<String>, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var writing by rememberSaveable { mutableStateOf(false) }
    if (writing) {
        var text by rememberSaveable { mutableStateOf("") }
        Bb10Dialog(
            title = stringResource(R.string.action_sms),
            confirmText = stringResource(R.string.post_dial_send),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = text.isNotBlank(),
            focusContent = true,
            modifier = Modifier.clearOfKeyboard(),
            onConfirm = { onSend(text.trim()) },
            onDismiss = { writing = false },
        ) {
            DialogWindowStaysPut()
            Bb10TextField(value = text, onValueChange = { text = it }, singleLine = false, minLines = 2, startEditing = true)
        }
        return
    }
    val builtIn = listOf(stringResource(R.string.reply_later), stringResource(R.string.reply_busy), stringResource(R.string.reply_call_me))
    val messages = builtIn.mapIndexed { i, text -> saved.getOrNull(i)?.takeIf { it.isNotBlank() } ?: text }
    val entries = messages.map { it to { onSend(it) } } +
        (stringResource(R.string.reply_write) to { writing = true })
    ChoiceDialog(stringResource(R.string.action_sms), entries, onDismiss)
}

@Composable
private fun MembersDialog(members: List<LiveCall>, onDismiss: () -> Unit) {
    val entries = members.flatMap { member ->
        val name = rememberParty(member.number, member.presentation, detailed = false).title
        buildList {
            if (member.canSeparate) add(stringResource(R.string.conference_separate, name) to { CallCenter.separate(member.id); onDismiss() })
            add(stringResource(R.string.conference_hang_up, name) to { CallCenter.hangUp(member.id); onDismiss() })
        }
    }
    ChoiceDialog(stringResource(R.string.conference_members), entries, onDismiss)
}

/** A short list to pick one from; the only other way out is the button. */
@Composable
private fun ChoiceDialog(title: String, entries: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val d = Bb10.dimens
    Bb10Dialog(
        title = title,
        confirmText = stringResource(R.string.cancel),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            for ((label, pick) in entries) {
                Bb10Divider()
                val highlight = rememberBb10Highlight()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = d.minTouchTarget)
                        .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onClick = pick)
                        .padding(horizontal = d.spaceL, vertical = d.spaceM),
                ) {
                    Bb10OnHighlight(highlight) { Bb10Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
