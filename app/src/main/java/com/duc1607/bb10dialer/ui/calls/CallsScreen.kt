package com.duc1607.bb10dialer.ui.calls

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallBook
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallGroup
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.data.ContactIndex
import com.duc1607.bb10dialer.data.ContactPhoto
import com.duc1607.bb10dialer.data.PhoneSettingsStore
import com.duc1607.bb10dialer.data.QUICK_CONTACT_SLOTS
import com.duc1607.bb10dialer.data.newMissedCount
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.CellDivider
import com.duc1607.bb10dialer.ui.common.EmptyMessage
import com.duc1607.bb10dialer.ui.common.LocalTabs
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.common.isDelete
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
import java.util.Date

private val CallIconSize = 18.dp
private const val VOICEMAIL_RATIO = 2.25f

/** Clear on the upper half of a quick contact, then down to black behind its text. */
private val QuickContactShade = Brush.verticalGradient(
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f),
)

/** What a call row shows: a known name over "Mobile: number", or the number over "Unknown". */
private class CallRowText(val title: String, val subtitle: String, val photo: ContactPhoto?)

@Composable
private fun rememberCallRowText(call: CallEntry, index: ContactIndex, showNumbers: Boolean): CallRowText {
    val unknown = stringResource(R.string.unknown_number)
    val hidden = stringResource(R.string.private_number)
    val labeled = stringResource(R.string.labeled_number)
    return remember(call, index, showNumbers, unknown, hidden, labeled) {
        val known = index.find(call.number)
        val name = known?.first?.name ?: call.cachedName
        val label = known?.second?.label ?: call.cachedLabel
        val photo = known?.first?.photo
        when {
            call.number.isEmpty() -> CallRowText(hidden, unknown, null)
            name == null -> CallRowText(call.number, unknown, photo)
            label == null -> CallRowText(name, if (showNumbers) call.number else "", photo)
            !showNumbers -> CallRowText(name, label, photo)
            else -> CallRowText(name, labeled.format(label, call.number), photo)
        }
    }
}

/**
 * The Calls tab: quick contacts on top, after voicemail if it has a number, then the call log by day, all or only the
 * missed ones. Tapping a row calls back; its chevron folds out details, message and call.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    book: CallBook,
    /** The calls that have a note. */
    noted: Set<Long>,
    /** The calls that have a recording. */
    recorded: Set<Long>,
    /** Off: no numbers for saved contacts. */
    showNumbers: Boolean,
    /** The user set a voicemail number, which gets a tile. */
    showVoicemail: Boolean,
    /** Missed calls newer than this are new to the user. */
    missedSeenMillis: Long,
    canReadCalls: Boolean,
    index: ContactIndex,
    quick: List<String?>,
    listState: LazyListState,
    actions: DialerActions,
    onPickQuick: (Int) -> Unit,
    /** Holding a speed dial, or Menu on it: picks it for the menu ([LocalBb10Menu]). */
    onQuickMenu: (Int) -> Unit,
    onDetails: (String) -> Unit,
    /** Holding a row, or Menu on it: picks it for the menu ([LocalBb10Menu]). */
    onCallMenu: (CallGroup) -> Unit,
    /** Del on a row. */
    onDeleteCall: (CallGroup) -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val missedOnly = filter == 1
    val sections = if (missedOnly) book.missed else book.all
    val newMissed = remember(book, missedSeenMillis) { newMissedCount(book.calls, missedSeenMillis) }
    // One for the whole list: making one for each row that scrolls in is what costs.
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    // Looking at them is what makes them no longer new.
    LaunchedEffect(missedOnly, book) {
        val latest = book.calls.firstOrNull { it.kind == CallKind.Missed }?.dateMillis
        if (missedOnly && latest != null && latest > missedSeenMillis) {
            PhoneSettingsStore.update(context) { it.copy(missedSeenMillis = latest) }
        }
    }
    val icons = rememberListIcons()
    Bb10LazyColumn(modifier.fillMaxSize().background(Bb10.colors.background), listState) {
        item(key = "quick") { QuickActions(quick, index, showVoicemail, showNumbers, actions, onPickQuick, onQuickMenu) }
        item(key = "filter") {
            LocalTabs(
                tabs = listOf(
                    stringResource(R.string.filter_all),
                    if (newMissed > 0) stringResource(R.string.filter_missed_count, newMissed) else stringResource(R.string.filter_missed),
                ),
                selected = filter,
                onSelect = { filter = it },
                emphasized = if (newMissed > 0) setOf(1) else emptySet(),
            )
        }
        for (section in sections) {
            stickyHeader(key = "day-${section.title}", contentType = "day") { SectionHeader(section.title) }
            items(section.items, key = { it.key }, contentType = { "call" }) { group ->
                CallRow(
                    group = group,
                    hasNote = group.entries.any { it.id in noted },
                    hasRecording = group.entries.any { it.id in recorded },
                    showNumbers = showNumbers,
                    index = index,
                    timeFormat = timeFormat,
                    icons = icons,
                    expanded = expanded == group.key,
                    onToggle = { expanded = if (expanded == group.key) null else group.key },
                    onCall = { actions.call(group.latest.number) },
                    onInfo = { onDetails(group.latest.number) },
                    onSms = { actions.sms(group.latest.number) },
                    onMenu = { onCallMenu(group) },
                    onDelete = { onDeleteCall(group) },
                )
            }
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                when {
                    !canReadCalls -> EmptyMessage(
                        stringResource(R.string.calls_permission),
                        actionText = stringResource(R.string.allow_access),
                        onAction = onAllowAccess,
                    )
                    missedOnly -> EmptyMessage(stringResource(R.string.no_missed_calls))
                    else -> EmptyMessage(stringResource(R.string.no_calls))
                }
            }
        }
    }
}

/** Voicemail, when it has a number, and the quick contacts: square tiles across the width. */
@Composable
private fun QuickActions(
    quick: List<String?>,
    index: ContactIndex,
    showVoicemail: Boolean,
    showNumbers: Boolean,
    actions: DialerActions,
    onPick: (Int) -> Unit,
    onMenu: (Int) -> Unit,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
        if (showVoicemail) {
            val voicemail = rememberBb10Highlight()
            Column(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .bb10Clickable(role = Role.Button, highlight = voicemail, onClick = actions::voicemail),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Wider than tall: this keeps it from being squeezed into a square icon.
                Bb10Icon(DialerIcons.Voicemail, Modifier.fillMaxWidth(0.66f).aspectRatio(VOICEMAIL_RATIO))
                Spacer(Modifier.height(d.spaceS))
                Bb10Text(
                    stringResource(R.string.voicemail),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = d.spaceXs),
                )
            }
        }
        repeat(if (showVoicemail) QUICK_CONTACT_SLOTS - 1 else QUICK_CONTACT_SLOTS) { slot ->
            val number = quick.getOrNull(slot)
            QuickContactTile(
                number = number,
                index = index,
                showNumbers = showNumbers,
                modifier = Modifier.weight(1f),
                onClick = { if (number != null) actions.call(number) else onPick(slot) },
                onMenu = if (number != null) ({ onMenu(slot) }) else null,
            )
        }
    }
}

/** Empty, it asks for a number. Set, it shows who it calls: holding it opens the menu. */
@Composable
private fun QuickContactTile(
    number: String?,
    index: ContactIndex,
    showNumbers: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val known = remember(number, index) { number?.let { index.find(it) } }
    val contact = known?.first
    val photo = contact?.largePhoto ?: contact?.photo
    val side = LocalContext.current.resources.displayMetrics.widthPixels / QUICK_CONTACT_SLOTS
    Avatar(
        modifier
            .aspectRatio(1f)
            .bb10Clickable(role = Role.Button, onMenu = onMenu, onClick = onClick),
        photo,
        photoSide = side,
    ) {
        if (number == null) {
            Bb10Text(
                stringResource(R.string.add_number),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = d.spaceM),
            )
        } else {
            Box(Modifier.fillMaxSize().background(QuickContactShade))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = d.spaceS, vertical = d.spaceXs)) {
                Bb10Text(
                    contact?.name ?: number,
                    style = Bb10.typography.title.copy(fontWeight = FontWeight.Bold),
                    color = c.onAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (known != null) {
                    Bb10Text(
                        if (showNumbers) stringResource(R.string.quick_contact_number, number, known.second.label) else known.second.label,
                        style = Bb10.typography.secondary,
                        color = c.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallRow(
    group: CallGroup,
    hasNote: Boolean,
    hasRecording: Boolean,
    showNumbers: Boolean,
    index: ContactIndex,
    timeFormat: java.text.DateFormat,
    icons: ListIcons,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCall: () -> Unit,
    onInfo: () -> Unit,
    onSms: () -> Unit,
    onMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val call = group.latest
    val text = rememberCallRowText(call, index, showNumbers)
    val rowHeight = d.listItemMinHeight
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .onCallKey(context) { if (call.number.isNotEmpty()) onCall() }
            .onKeyEvent { e ->
                if (e.isDelete()) {
                    onDelete()
                    true
                } else false
            },
    ) {
        Row(
            // The list's own background shows through: painting it again for each row costs a lot.
            (if (expanded) Modifier.background(DialerColors.RowExpanded) else Modifier)
                .fillMaxWidth()
                .height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val highlight = rememberBb10Highlight()
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onMenu = onMenu, onClick = onCall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(Modifier.size(rowHeight), text.photo)
                Spacer(Modifier.width(d.spaceS))
                Bb10OnHighlight(highlight) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f)) {
                                Bb10Text(
                                    text.title,
                                    style = t.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (group.count > 1) {
                                    Bb10Text(
                                        stringResource(R.string.call_count, group.count),
                                        style = t.body,
                                        color = c.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Bb10Text(
                                remember(call.dateMillis, timeFormat) { timeFormat.format(Date(call.dateMillis)) },
                                style = t.secondary,
                                color = c.textSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(start = d.spaceS, end = d.spaceS),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ListIcon(
                                icons.call(call.kind),
                                if (highlight.active) c.onAccent else call.kind.tint(),
                                CallIconSize,
                            )
                            if (hasNote) {
                                Spacer(Modifier.width(d.spaceXs))
                                ListIcon(
                                    icons.note,
                                    if (highlight.active) c.onAccent else c.textSecondary,
                                    CallIconSize,
                                )
                            }
                            if (hasRecording) {
                                Spacer(Modifier.width(d.spaceXs))
                                ListIcon(
                                    icons.recording,
                                    if (highlight.active) c.onAccent else c.textSecondary,
                                    CallIconSize,
                                )
                            }
                            Spacer(Modifier.width(d.spaceXs))
                            Bb10Text(
                                text.subtitle,
                                style = t.secondary,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            CellDivider()
            ExpandChevron(expanded, icons, onToggle)
        }
        Bb10Divider()
        FoldOut(expanded) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(DialerColors.RowStrip),
                ) {
                    StripAction(Bb10Icons.Info, stringResource(R.string.action_info), onInfo)
                    CellDivider()
                    StripAction(DialerIcons.Message, stringResource(R.string.action_sms), onSms)
                    CellDivider()
                    StripAction(DialerIcons.Phone, stringResource(R.string.action_call), onCall)
                }
                Bb10Divider()
            }
        }
    }
}

@Composable
private fun RowScope.StripAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val highlight = rememberBb10Highlight()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) { Bb10Icon(icon, size = 26.dp) }
    }
}
