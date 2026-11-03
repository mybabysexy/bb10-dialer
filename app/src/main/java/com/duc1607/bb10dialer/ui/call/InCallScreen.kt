package com.duc1607.bb10dialer.ui.call

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.ui.common.dialSymbol
import com.duc1607.bb10dialer.ui.dialpad.PadKeys
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.bb10VerticalScroll
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class CallStatus { Dialing, Active, OnHold, Busy, Unreachable }

private val CallStatus.failed get() = this == CallStatus.Busy || this == CallStatus.Unreachable

private const val DISABLED_ALPHA = 0.35f
private const val SECOND_MILLIS = 1000L
private const val MINUTE_SECONDS = 60L
private const val HOUR_SECONDS = 3600L
private const val TONES = "0123456789*#"
private const val MAX_TYPED = 40
private const val NOTE_LINES = 3
private const val PAD_ROWS = 4
private const val DIGIT_TO_ROW = 0.58f
private const val MIN_DIGIT_SP = 22f
private const val MAX_DIGIT_SP = 36f
private const val NOTE_SLIDE_MILLIS = 180
private const val BREATH_MILLIS = 900
private const val BREATH_MIN_ALPHA = 0.25f
private const val BREATH_MIN_SCALE = 0.9f
private val NameLineHeight = 30.sp
private val NoteLineHeight = 20.sp
private val NoteFieldMaxHeight = 90.dp

/** "0:13", or "1:02:13" past an hour. */
fun callTimer(seconds: Long): String {
    val minutes = (seconds % HOUR_SECONDS / MINUTE_SECONDS).toString()
    val rest = (seconds % MINUTE_SECONDS).toString().padStart(2, '0')
    val hours = seconds / HOUR_SECONDS
    return if (hours > 0) "$hours:${minutes.padStart(2, '0')}:$rest" else "$minutes:$rest"
}

/**
 * A call going out or under way. One that could not connect says why under the name, its actions
 * go dim and half of the red bar becomes a green one to try again.
 *
 * The Dial Pad action swaps the other actions for the keys, with the name moved to the top to make
 * room. What was typed shows under the name; Back or the button by the bar puts the actions back.
 */
@Composable
fun InCallScreen(
    party: CallParty,
    myNumber: String?,
    status: CallStatus,
    /** When it connected, by the wall clock. Null until then: no timer. */
    connectedAtMillis: Long?,
    speakerOn: Boolean,
    muted: Boolean,
    recording: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onSpeaker: () -> Unit,
    onMute: () -> Unit,
    onDigit: (Char) -> Unit,
    onRecord: () -> Unit,
    onAddCall: () -> Unit,
    onEnd: () -> Unit,
    onCallAgain: () -> Unit,
    modifier: Modifier = Modifier,
    canRecord: Boolean = true,
    /** False when recording is not allowed: the other actions keep their places. */
    showRecord: Boolean = true,
    /** Who is on hold behind this call. Tapping the strip with their name swaps the two. */
    heldTitle: String? = null,
    onSwap: () -> Unit = {},
    /** Set when the held call can join this one: takes the place of Add a Call. */
    onMerge: (() -> Unit)? = null,
    onResume: () -> Unit = {},
    /** The people in a conference, with a strip that lists them. */
    memberCount: Int = 0,
    onMembers: () -> Unit = {},
    /** What is being typed in the note dialog; null when it was left without saving. */
    onNoteDraft: (String?) -> Unit = {},
    /** Off in Phone Settings, for a phone with a key for it. */
    showEndButton: Boolean = true,
) {
    val d = Bb10.dimens
    val enabled = !status.failed
    var padOpen by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val showPad = padOpen && enabled
    BackHandler(enabled = showPad) { padOpen = false }

    fun press(symbol: Char) {
        typed = (typed + symbol).takeLast(MAX_TYPED)
        onDigit(symbol)
    }

    CallLayout(
        photo = party.photo,
        myNumber = myNumber,
        modifier = modifier,
        panelWeight = if (showPad) 1f else null,
        nameOnTop = showPad,
        name = { NameRow(party, status, connectedAtMillis, typed.takeIf { showPad && it.isNotEmpty() }) },
        panel = {
            Column(
                (if (showPad) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .background(Bb10.colors.surface)
                    .navigationBarsPadding()
                    .padding(d.spaceM),
            ) {
                if (showPad) {
                    CallPad(Modifier.weight(1f).fillMaxWidth(), ::press)
                } else {
                    if (heldTitle != null) {
                        CallStrip(stringResource(R.string.call_held, heldTitle), stringResource(R.string.swap_calls), onSwap)
                    }
                    if (memberCount > 0) {
                        CallStrip(stringResource(R.string.conference_members), memberCount.toString(), onMembers)
                    }
                    Row(Modifier.height(d.actionBarHeight).fillMaxWidth()) {
                        CallAction(Bb10Icons.VolumeHigh, stringResource(R.string.call_speaker), enabled, speakerOn, onClick = onSpeaker)
                        CallAction(DialerIcons.Mute, stringResource(R.string.call_mute), enabled, muted, onClick = onMute)
                        CallAction(DialerIcons.DialPad, stringResource(R.string.tab_dial_pad), enabled) { padOpen = true }
                    }
                    Row(Modifier.height(d.actionBarHeight).fillMaxWidth()) {
                        if (showRecord) {
                            CallAction(
                                DialerIcons.Record,
                                stringResource(R.string.call_record),
                                enabled && canRecord,
                                recording,
                                activeColor = DialerColors.DeclineRed,
                                breathes = recording,
                                onClick = onRecord,
                            )
                        }
                        CallAction(Bb10Icons.Edit, stringResource(R.string.call_note), enabled, note.isNotEmpty()) { noteOpen = true }
                        when {
                            status == CallStatus.OnHold -> CallAction(DialerIcons.Phone, stringResource(R.string.resume_call), enabled, onClick = onResume)
                            onMerge != null -> CallAction(DialerIcons.AddCall, stringResource(R.string.merge_calls), enabled, onClick = onMerge)
                            else -> CallAction(DialerIcons.AddCall, stringResource(R.string.call_add), enabled, onClick = onAddCall)
                        }
                        if (!showRecord) Spacer(Modifier.weight(1f))
                    }
                }
                if (status.failed || showEndButton || showPad) Spacer(Modifier.height(d.spaceS))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceS)) {
                    when {
                        status.failed -> {
                            CallBar(DialerIcons.Phone, stringResource(R.string.call_again), DialerColors.CallAgainBar, onCallAgain)
                            CallBar(DialerIcons.HangUp, stringResource(R.string.end_call), DialerColors.EndCallBar, onEnd)
                        }
                        showEndButton -> CallBar(DialerIcons.HangUp, stringResource(R.string.end_call), DialerColors.DeclineRed, onEnd)
                        else -> Spacer(Modifier.weight(1f))
                    }
                    if (showPad) {
                        SquareBarButton(DialerIcons.DialPad, stringResource(R.string.hide_dial_pad)) { padOpen = false }
                    }
                }
            }
        },
    )
    if (noteOpen) {
        NoteDialog(
            note,
            onDraft = onNoteDraft,
            onSave = { onNoteChange(it); noteOpen = false },
            onDismiss = { onNoteDraft(null); noteOpen = false },
        )
    }
}

/** The keys, which also take digits, `*` and `#` from a hardware keyboard. */
@Composable
private fun CallPad(modifier: Modifier, onSymbol: (Char) -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    BoxWithConstraints(
        modifier
            .focusRequester(focus)
            .onKeyEvent { e ->
                val symbol = e.dialSymbol(context)
                if (e.type == KeyEventType.KeyDown && symbol in TONES) {
                    onSymbol(symbol)
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        val digitSize = (maxHeight.value / PAD_ROWS * DIGIT_TO_ROW).coerceIn(MIN_DIGIT_SP, MAX_DIGIT_SP).sp
        Column(Modifier.fillMaxSize()) {
            val voicemail = remember(context) { PhoneSettingsStore.get(context).voicemailNumber.isNotEmpty() }
            PadKeys(digitSize, holdZeroForPlus = false, voicemail = voicemail, onSymbol = onSymbol)
        }
    }
}

/** A slim row over the actions, about the other side of the call. */
@Composable
private fun CallStrip(text: String, action: String, onClick: () -> Unit) {
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    Row(
        Modifier
            .fillMaxWidth()
            .height(d.minTouchTarget)
            .background(Bb10.colors.surfaceVariant)
            .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onClick = onClick)
            .padding(horizontal = d.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10OnHighlight(highlight) {
            Bb10Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(d.spaceS))
            Bb10Text(action, style = Bb10.typography.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

@Composable
private fun SquareBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val highlight = rememberBb10Highlight()
    Box(
        Modifier
            .size(Bb10.dimens.actionBarHeight)
            .background(Bb10.colors.surfaceVariant)
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) { Bb10Icon(icon, size = 28.dp) }
    }
}

/**
 * For a dialog with a field. The kit centres it on the whole screen, which puts its buttons under an
 * on-screen keyboard. Padding only lifts it by part of the keyboard, by an amount that changes with
 * the strip of suggestions, so it goes to the top and stays put while typing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Modifier.clearOfKeyboard(): Modifier =
    if (WindowInsets.isImeVisible) {
        fillMaxHeight().statusBarsPadding().wrapContentHeight(Alignment.Top)
    } else {
        this
    }

/**
 * Goes in such a dialog. Left alone, Android pans its window to show the cursor and lets it drop back
 * at the next touch, which takes the buttons out from under the finger.
 */
@Composable
internal fun DialogWindowStaysPut() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {}
    }
}

/** Starts from the saved note; only Save keeps what was typed. */
@Composable
private fun NoteDialog(note: String, onDraft: (String) -> Unit, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(note) }
    Bb10Dialog(
        title = stringResource(R.string.call_note),
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        focusContent = true,
        onConfirm = { onSave(draft.trim()) },
        onDismiss = onDismiss,
    ) {
        DialogWindowStaysPut()
        // A long note scrolls here, so that it does not push the buttons under the keyboard.
        Box(Modifier.heightIn(max = NoteFieldMaxHeight).bb10VerticalScroll(rememberScrollState())) {
            Bb10TextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onDraft(it)
                },
                placeholder = stringResource(R.string.call_note_hint),
                singleLine = false,
                minLines = NOTE_LINES,
                startEditing = true,
            )
        }
    }
}

@Composable
private fun NameRow(party: CallParty, status: CallStatus, connectedAtMillis: Long?, typed: String?) {
    val c = Bb10.colors
    val note = typed ?: when (status) {
        CallStatus.Dialing -> stringResource(R.string.call_status_dialing)
        CallStatus.Active -> null
        CallStatus.OnHold -> stringResource(R.string.call_status_on_hold)
        CallStatus.Busy -> stringResource(R.string.call_status_busy)
        CallStatus.Unreachable -> stringResource(R.string.call_status_unreachable)
    }
    // Kept while it fades out, after the note itself is gone.
    var lastNote by remember { mutableStateOf("") }
    if (note != null) lastNote = note
    val reveal by animateFloatAsState(if (note != null) 1f else 0f, tween(NOTE_SLIDE_MILLIS), label = "note")
    val density = LocalDensity.current
    val nameHeight = with(density) { NameLineHeight.toDp() }
    val noteHeight = with(density) { NoteLineHeight.toDp() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        // Always tall enough for both lines, so that a note only moves the name up in its place.
        Box(Modifier.weight(1f).height(nameHeight + noteHeight), contentAlignment = Alignment.BottomStart) {
            Bb10Text(
                lastNote,
                Modifier.graphicsLayer { alpha = reveal },
                style = Bb10.typography.secondary.copy(lineHeight = NoteLineHeight),
                color = c.onAccent,
                maxLines = 1,
            )
            Bb10Text(
                party.title,
                Modifier
                    .offset { IntOffset(0, -(noteHeight.toPx() * reveal).roundToInt()) }
                    .semantics { heading() },
                style = Bb10.typography.title.copy(fontSize = 24.sp, lineHeight = NameLineHeight, fontWeight = FontWeight.SemiBold),
                color = c.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connectedAtMillis != null && (status == CallStatus.Active || status == CallStatus.OnHold)) {
            Spacer(Modifier.width(Bb10.dimens.spaceS))
            ElapsedTime(connectedAtMillis)
        }
    }
}

@Composable
private fun ElapsedTime(connectedAtMillis: Long) {
    val seconds by produceState(0L, connectedAtMillis) {
        while (true) {
            val elapsed = (System.currentTimeMillis() - connectedAtMillis).coerceAtLeast(0L)
            value = elapsed / SECOND_MILLIS
            delay(SECOND_MILLIS - elapsed % SECOND_MILLIS)
        }
    }
    Bb10Text(
        callTimer(seconds),
        style = Bb10.typography.title.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
        color = Bb10.colors.onAccent,
        maxLines = 1,
    )
}

@Composable
private fun RowScope.CallAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean = false,
    activeColor: Color = Bb10.colors.accent,
    /** A colour alone is easy to miss, for what must not go unnoticed. */
    breathes: Boolean = false,
    onClick: () -> Unit,
) {
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    val color = if (active) activeColor else Bb10.colors.textPrimary
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { selected = active }
            .bb10Clickable(enabled = enabled, role = Role.Button, highlight = highlight, flat = true, onClick = onClick)
            .padding(horizontal = d.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10OnHighlight(highlight) {
            Bb10Icon(
                icon,
                if (breathes) Modifier.breathing() else Modifier,
                tint = if (highlight.active) Bb10.colors.onAccent else color,
                size = 28.dp,
            )
            Spacer(Modifier.width(d.spaceS))
            Bb10Text(
                label,
                Modifier.weight(1f, fill = false),
                style = Bb10.typography.body,
                color = if (highlight.active) Color.Unspecified else color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Slowly fades and shrinks, and back. Read while drawing, so that the screen is not composed again. */
@Composable
private fun Modifier.breathing(): Modifier {
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    return graphicsLayer {
        alpha = BREATH_MIN_ALPHA + (1f - BREATH_MIN_ALPHA) * breath
        val scale = BREATH_MIN_SCALE + (1f - BREATH_MIN_SCALE) * breath
        scaleX = scale
        scaleY = scale
    }
}

/** The wide bar at the bottom. */
@Composable
private fun RowScope.CallBar(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val c = Bb10.colors
    val d = Bb10.dimens
    Row(
        Modifier
            .weight(1f)
            .height(d.actionBarHeight)
            .background(color)
            .bb10Clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = d.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10Icon(icon, tint = c.onAccent, size = 28.dp)
        Spacer(Modifier.width(d.spaceS))
        Bb10Text(label, style = Bb10.typography.title, color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
