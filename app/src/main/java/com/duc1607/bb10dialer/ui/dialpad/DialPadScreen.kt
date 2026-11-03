package com.duc1607.bb10dialer.ui.dialpad

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.NumberMatch
import com.duc1607.bb10dialer.data.latestNumber
import com.duc1607.bb10dialer.data.matchNumbers
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.CellDivider
import com.duc1607.bb10dialer.ui.common.dialSymbol
import com.duc1607.bb10dialer.ui.common.heldDigit
import com.duc1607.bb10dialer.ui.common.onCallKey
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10

private const val MAX_NUMBER_LENGTH = 32
private const val DIALABLE = "0123456789*#+"

/** Below this much room for the pad (a 720x720 screen) the header and the keys tighten up. */
private val TightPadHeight = 300.dp
private val HeaderHeight = 76.dp
private val TightHeaderHeight = 64.dp
private val SimButtonGap = 8.dp

/**
 * The Dial Pad tab. While typing, the header counts the contact numbers that contain what was
 * typed; its chevron shows them over the keys. A number no contact owns can be added as a contact.
 * Digits, `*`, `#` and `+` also work from a hardware keyboard, Del deletes, Enter and the Call key
 * call. Calling with nothing typed fills in the number of the latest call, ready to go. With nothing
 * else typed, holding 1 to 9 (on the screen or the keyboard) is [onSpeedDial]: voicemail for 1.
 */
@Composable
fun DialPadScreen(
    number: String,
    /** Edits the number. Keys can come faster than recomposition, so an edit gets the latest one. */
    onNumberEdit: ((String) -> String) -> Unit,
    book: ContactBook,
    /** The call log, newest first. */
    calls: List<CallEntry>,
    /** The SIM slots in use. With two, the call button splits into one for each. */
    sims: List<Int>,
    refocus: Int,
    actions: DialerActions,
    onAddContact: (String) -> Unit,
    /** A voicemail number is set: 1 shows its mark. */
    hasVoicemail: Boolean,
    onSpeedDial: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayOpen by rememberSaveable { mutableStateOf(false) }
    val matches = remember(book, number) { matchNumbers(book.contacts, number) }
    val showOverlay = overlayOpen && matches.isNotEmpty()
    val isKnown = remember(book, number) { book.index.find(number) != null }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current

    fun type(symbol: Char) = onNumberEdit { if (it.length < MAX_NUMBER_LENGTH) it + symbol else it }
    fun delete() = onNumberEdit { it.dropLast(1) }

    // A key held for speed dial: its repeats type nothing until it is let go.
    val heldKey = remember { arrayOfNulls<Key>(1) }

    fun hold(digit: Char) {
        if (number.isEmpty()) onSpeedDial(digit - '0') else type(digit)
    }

    fun call(sim: Int?) {
        if (number.isNotEmpty()) {
            actions.call(number, sim)
        } else {
            latestNumber(calls, sim)?.let { latest -> onNumberEdit { latest } }
        }
    }

    // The screen itself takes the focus, so a keyboard types here from the start.
    LaunchedEffect(refocus) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Bb10.colors.background)
            .focusRequester(focus)
            .onCallKey(context) { call(null) }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == heldKey[0]) {
                    heldKey[0] = null
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (e.key == heldKey[0]) return@onKeyEvent true
                // The first press typed the digit; held past the repeat delay, it is speed dial.
                val held = e.heldDigit(context)
                if (e.nativeKeyEvent.repeatCount == 1 && held != null && number == held.toString()) {
                    heldKey[0] = e.key
                    onNumberEdit { "" }
                    onSpeedDial(held - '0')
                    return@onKeyEvent true
                }
                val symbol = e.dialSymbol(context)
                when {
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                        if (e.nativeKeyEvent.repeatCount == 0) call(null)
                        true
                    }
                    e.key == Key.Backspace || e.key == Key.Delete -> { delete(); true }
                    symbol in DIALABLE -> { type(symbol); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val tight = maxHeight < TightPadHeight + HeaderHeight
        Column(Modifier.fillMaxSize()) {
            NumberHeader(
                number = number,
                matchCount = matches.size,
                overlayOpen = showOverlay,
                canAddContact = number.isNotEmpty(),
                showAddContact = !isKnown,
                height = if (tight) TightHeaderHeight else HeaderHeight,
                onToggleOverlay = { overlayOpen = !overlayOpen },
                onAddContact = { onAddContact(number) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Pad(
                    tight = tight,
                    sims = sims,
                    canDelete = number.isNotEmpty(),
                    hasVoicemail = hasVoicemail,
                    onHold = { digit -> hold(digit) },
                    onSymbol = ::type,
                    onDelete = ::delete,
                    onClear = { onNumberEdit { "" } },
                    // Not `::call`: Compose keeps a reference from before the number changed.
                    onCall = { sim -> call(sim) },
                )
                if (showOverlay) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(DialerColors.PadScrim)
                            .pointerInput(Unit) { detectTapGestures { overlayOpen = false } }
                    )
                    Bb10LazyColumn(Modifier.fillMaxWidth()) {
                        items(matches, key = { "${it.contact.id}/${it.phone.number}" }) { match ->
                            MatchRow(match) {
                                // Fills the number in, ready to call or to edit.
                                onNumberEdit { match.phone.number.substringBefore('@').filter { it in DIALABLE } }
                                overlayOpen = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberHeader(
    number: String,
    matchCount: Int,
    overlayOpen: Boolean,
    canAddContact: Boolean,
    showAddContact: Boolean,
    height: Dp,
    onToggleOverlay: () -> Unit,
    onAddContact: () -> Unit,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val big = t.title.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light)
    val scroll = rememberScrollState()
    // The end of a long number is what was just typed.
    LaunchedEffect(number) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(DialerColors.DialHeader),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = d.spaceM, bottom = d.spaceS),
        ) {
            if (matchCount > 0) {
                Bb10Text(
                    pluralStringResource(R.plurals.matching_numbers, matchCount, matchCount),
                    style = t.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (number.isEmpty()) {
                Bb10Text(
                    stringResource(R.string.enter_number),
                    style = big.copy(fontStyle = FontStyle.Italic),
                    color = DialerColors.DialHint,
                    maxLines = 1,
                )
            } else {
                Bb10Text(number, Modifier.horizontalScroll(scroll), style = big, maxLines = 1)
            }
        }
        if (matchCount > 0) {
            val highlight = rememberBb10Highlight()
            val label = stringResource(if (overlayOpen) R.string.hide_matches else R.string.show_matches)
            Row(Modifier.height(d.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
                CellDivider()
                Box(
                    Modifier
                        .size(d.minTouchTarget)
                        .semantics { contentDescription = label }
                        .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onToggleOverlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Bb10OnHighlight(highlight) {
                        Bb10Icon(if (overlayOpen) Bb10Icons.ChevronUp else Bb10Icons.ChevronDown)
                    }
                }
                if (showAddContact) CellDivider()
            }
        }
        if (showAddContact) {
            val highlight = rememberBb10Highlight()
            val label = stringResource(R.string.add_contact)
            Box(
                Modifier
                    .size(d.minTouchTarget)
                    .semantics { contentDescription = label }
                    .bb10Clickable(
                        enabled = canAddContact,
                        role = Role.Button,
                        highlight = highlight,
                        onClick = onAddContact,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Bb10OnHighlight(highlight) { Bb10Icon(DialerIcons.AddContact, size = 28.dp) }
            }
        }
    }
}

@Composable
private fun Pad(
    tight: Boolean,
    sims: List<Int>,
    canDelete: Boolean,
    hasVoicemail: Boolean,
    onHold: (Char) -> Unit,
    onSymbol: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCall: (sim: Int?) -> Unit,
) {
    val d = Bb10.dimens
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Four rows of keys, the slightly taller call row, and air under it when there is room.
        val rows = if (tight) 5.1f else 5.6f
        val digitSize = (maxHeight.value / rows * 0.58f).coerceIn(22f, 36f).sp
        Column(Modifier.fillMaxSize().padding(top = if (tight) 0.dp else d.spaceM)) {
            PadKeys(digitSize, voicemail = hasVoicemail, onHold = onHold, onSymbol = onSymbol)
            Row(Modifier.weight(1.1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (sims.size >= 2) {
                    // SIM 1 ends in the middle of the 0 key, SIM 2 in the middle of the # key.
                    CallButton(
                        stringResource(R.string.call_sim, sims[0]),
                        Modifier.weight(1.5f).padding(start = d.spaceM, end = SimButtonGap / 2),
                        centered = true,
                    ) { onCall(sims[0]) }
                    CallButton(
                        stringResource(R.string.call_sim, sims[1]),
                        Modifier.weight(1f).padding(horizontal = SimButtonGap / 2),
                        color = DialerColors.CallSecondSim,
                        centered = true,
                    ) { onCall(sims[1]) }
                    DeleteButton(Modifier.weight(0.5f).padding(start = SimButtonGap / 2), canDelete, onDelete, onClear)
                } else {
                    CallButton(
                        stringResource(R.string.action_call),
                        Modifier.weight(2f).padding(start = d.spaceM),
                    ) { onCall(null) }
                    DeleteButton(Modifier.weight(1f), canDelete, onDelete, onClear)
                }
            }
            if (!tight) Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun CallButton(
    label: String,
    modifier: Modifier,
    color: Color = DialerColors.CallGreen,
    centered: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    Row(
        modifier
            .fillMaxHeight()
            .background(color)
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick)
            .padding(start = if (centered) d.spaceXs else d.spaceL * 2, end = if (centered) d.spaceXs else 0.dp),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10Icon(DialerIcons.Phone, tint = c.onAccent, size = if (centered) 22.dp else 28.dp)
        Spacer(Modifier.width(if (centered) d.spaceXs else d.spaceS))
        Bb10Text(
            label,
            style = if (centered) Bb10.typography.body else Bb10.typography.title,
            color = c.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeleteButton(modifier: Modifier, enabled: Boolean, onDelete: () -> Unit, onClear: () -> Unit) {
    val highlight = rememberBb10Highlight()
    val label = stringResource(R.string.delete_digit)
    Box(
        modifier
            .fillMaxHeight()
            .semantics { contentDescription = label }
            .bb10Clickable(
                enabled = enabled,
                role = Role.Button,
                highlight = highlight,
                onLongClick = onClear,
                onClick = onDelete,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) { Bb10Icon(DialerIcons.Backspace, size = 26.dp) }
    }
}

@Composable
private fun MatchRow(match: NumberMatch, onClick: () -> Unit) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val highlight = rememberBb10Highlight()
    val labeled = stringResource(R.string.labeled_number, match.phone.label, match.phone.number)
    // The matched digits are accent; a highlighted row is all white.
    val base = if (highlight.active) c.onAccent else c.textSecondary
    val matched = if (highlight.active) c.onAccent else c.accent
    val text = remember(labeled, match, base, matched) {
        val offset = labeled.length - match.phone.number.length
        buildAnnotatedString {
            append(labeled.substring(0, offset + match.range.first))
            withStyle(SpanStyle(color = matched)) {
                append(labeled.substring(offset + match.range.first, offset + match.range.last + 1))
            }
            append(labeled.substring(offset + match.range.last + 1))
        }
    }
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.listItemMinHeight)
                .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Modifier.size(d.listItemMinHeight), match.contact.photo)
            Spacer(Modifier.width(d.spaceS))
            Bb10OnHighlight(highlight) {
                Column {
                    Bb10Text(match.contact.name, style = t.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(
                        text,
                        style = t.secondary.copy(color = base),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Bb10Divider()
    }
}
