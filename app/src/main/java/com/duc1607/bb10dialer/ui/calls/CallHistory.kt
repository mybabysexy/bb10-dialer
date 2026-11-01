package com.duc1607.bb10dialer.ui.calls

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallNote
import com.duc1607.bb10dialer.data.CallNotes
import com.duc1607.bb10dialer.data.MatchedNotes
import com.duc1607.bb10dialer.data.Section
import com.duc1607.bb10dialer.data.matchNotes
import com.duc1607.bb10dialer.data.numberKey
import com.duc1607.bb10dialer.data.sameNumber
import com.duc1607.bb10dialer.telecom.CallRecorder
import com.duc1607.bb10dialer.telecom.CallRecordings
import com.duc1607.bb10dialer.telecom.Recording
import com.duc1607.bb10dialer.telecom.matchRecordings
import com.duc1607.bb10dialer.ui.common.CellDivider
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Slider
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val POSITION_EVERY_MILLIS = 100L

/** The one recording that plays at a time, in a list of them. */
@Stable
internal class RecordingPlayback {
    var player by mutableStateOf<MediaPlayer?>(null)
        private set
    var playing by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set

    fun stop() {
        player?.release()
        player = null
        playing = null
        paused = false
    }

    private fun play(recording: Recording, fraction: Float) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(recording.file.path)
                setOnCompletionListener { stop() }
                prepare()
                seekTo((duration * fraction).toInt())
                start()
            }
        }.getOrNull()
        if (player != null) playing = recording.file.name
    }

    fun toggle(recording: Recording) {
        val current = player
        when {
            current == null || playing != recording.file.name -> play(recording, 0f)
            paused -> {
                current.start()
                paused = false
            }
            else -> {
                current.pause()
                paused = true
            }
        }
    }

    fun seek(recording: Recording, fraction: Float) {
        val current = player
        if (current != null && playing == recording.file.name) {
            current.seekTo((current.duration * fraction).toInt())
        } else {
            play(recording, fraction)
        }
    }

    fun delete(context: Context, recording: Recording) {
        if (playing == recording.file.name) stop()
        CallRecorder.delete(context, recording)
    }
}

/** What the calls of a history have with them, and which of them is folded out. */
@Stable
internal class CallExtras(
    val recordings: CallRecordings,
    val notes: MatchedNotes,
    val playback: RecordingPlayback,
    private val expandedState: MutableState<Long?>,
) {
    var expanded: Long?
        get() = expandedState.value
        set(value) {
            expandedState.value = value
        }
}

/**
 * [numbers] are the numbers of the person whose [history] this is, newest first. Each number's notes
 * and recordings go with that number's calls.
 */
@Composable
internal fun rememberCallExtras(numbers: List<String>, history: List<CallEntry>): CallExtras {
    val context = LocalContext.current
    val allRecordings by CallRecorder.all(context).collectAsState()
    val allNotes by CallNotes.state(context).collectAsState()
    val expanded = rememberSaveable { mutableStateOf<Long?>(null) }
    val playback = remember { RecordingPlayback() }
    DisposableEffect(playback) { onDispose { playback.stop() } }
    return remember(numbers, history, allRecordings, allNotes) {
        val lines = numbers.distinctBy { numberKey(it) }
        val recordings = lines.map { line ->
            matchRecordings(
                history.filter { it.number == line || sameNumber(it.number, line) },
                allRecordings.filter { sameNumber(it.number, line) }.sortedByDescending { it.startedAtMillis },
            )
        }
        val notes = lines.map { line ->
            matchNotes(
                history.filter { it.number == line || sameNumber(it.number, line) },
                allNotes.filter { sameNumber(it.number, line) }.sortedByDescending { it.savedAtMillis },
            )
        }
        CallExtras(
            CallRecordings(recordings.flatMap { it.byCall.entries }.associate { it.key to it.value }, recordings.flatMap { it.unmatched }),
            MatchedNotes(notes.flatMap { it.byCall.entries }.associate { it.key to it.value }, notes.flatMap { it.unmatched }),
            playback,
            expanded,
        )
    }
}

/**
 * The calls by day. A call that has a note or was recorded has a chevron, as in the call list, which
 * folds them out. What has lost its call, which the log dropped or the user cleared, comes last.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.callHistory(sections: List<Section<CallEntry>>, extras: CallExtras) {
    for (section in sections) {
        stickyHeader(key = "day-${section.title}", contentType = "day") { SectionHeader(section.title) }
        items(section.items, key = { it.id }, contentType = { "call" }) { call ->
            val recordings = extras.recordings.byCall[call.id].orEmpty()
            val notes = extras.notes.byCall[call.id].orEmpty()
            HistoryRow(
                call = call,
                expanded = extras.expanded == call.id,
                onToggle = if (recordings.isEmpty() && notes.isEmpty()) {
                    null
                } else {
                    { extras.expanded = if (extras.expanded == call.id) null else call.id }
                },
            ) {
                notes.forEach { NoteRow(it) }
                recordings.forEach { RecordingRow(it, extras.playback) }
            }
        }
    }
    if (extras.notes.unmatched.isNotEmpty()) {
        stickyHeader(key = "notes", contentType = "day") { SectionHeader(stringResource(R.string.call_note)) }
        items(extras.notes.unmatched, key = { "note-${it.createdAtMillis}" }) { NoteRow(it) }
    }
    if (extras.recordings.unmatched.isNotEmpty()) {
        stickyHeader(key = "recordings", contentType = "day") { SectionHeader(stringResource(R.string.recordings)) }
        items(extras.recordings.unmatched, key = { it.file.name }) { RecordingRow(it, extras.playback) }
    }
}

@Composable
internal fun IconAction(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val highlight = rememberBb10Highlight()
    Box(
        Modifier
            .size(Bb10.dimens.minTouchTarget)
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) {
            if (tint != null && !highlight.active) Bb10Icon(icon, tint = tint, size = 28.dp) else Bb10Icon(icon, size = 28.dp)
        }
    }
}

@Composable
private fun NoteRow(note: CallNote) {
    val d = Bb10.dimens
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().background(DialerColors.RowChild)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = d.listItemMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(d.minTouchTarget), contentAlignment = Alignment.Center) {
                Bb10Icon(Bb10Icons.Edit, size = 28.dp)
            }
            Bb10Text(note.text, Modifier.weight(1f).padding(vertical = d.spaceXs))
            IconAction(Bb10Icons.Delete, stringResource(R.string.delete)) { CallNotes.delete(context, note) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DialerColors.RowChildDivider))
    }
}

@Composable
private fun RecordingRow(recording: Recording, playback: RecordingPlayback) {
    val d = Bb10.dimens
    val context = LocalContext.current
    // Set while this is the recording being played, or paused.
    val player = playback.player.takeIf { playback.playing == recording.file.name }
    val playing = player != null && !playback.paused
    var position by remember(player) { mutableFloatStateOf(0f) }
    var total by remember(recording) { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { total = withContext(Dispatchers.IO) { CallRecorder.durationOf(recording) } }
    LaunchedEffect(player) {
        while (player != null) {
            // A player can be let go of between two looks.
            runCatching {
                total = player.duration.toLong()
                position = player.currentPosition.toFloat() / total
            }
            delay(POSITION_EVERY_MILLIS)
        }
    }
    Column(Modifier.fillMaxWidth().background(DialerColors.RowChild)) {
        Row(
            Modifier.fillMaxWidth().height(d.listItemMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                if (playing) DialerIcons.Pause else DialerIcons.Play,
                stringResource(if (playing) R.string.pause_recording else R.string.play_recording),
                onClick = { playback.toggle(recording) },
            )
            Bb10Slider(
                value = position,
                onValueChange = {
                    // At once, not at the next look: the time is how the user finds the place.
                    position = it
                    playback.seek(recording, it)
                },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.recording_position),
            )
            Bb10Text(
                "${playbackTime((total * position).toLong())}/${playbackTime(total)}",
                style = Bb10.typography.secondary.copy(fontFeatureSettings = "tnum"),
                color = Bb10.colors.textSecondary,
                maxLines = 1,
            )
            IconAction(Bb10Icons.Delete, stringResource(R.string.delete)) { playback.delete(context, recording) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DialerColors.RowChildDivider))
    }
}

/** One call of a history. With [onToggle] it has a chevron, which folds out [detail]. */
@Composable
fun HistoryRow(
    call: CallEntry,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    detail: @Composable () -> Unit = {},
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().background(DialerColors.RowStrip)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.listItemMinHeight)
                .background(if (expanded) DialerColors.RowExpanded else DialerColors.RowStrip),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).padding(horizontal = d.spaceS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.spaceS),
            ) {
                Bb10Icon(call.kind.icon(), tint = call.kind.tint(), size = 18.dp)
                Bb10Text(
                    context.callOutcome(call),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Bb10Text(
                    context.callTime(call.dateMillis),
                    style = Bb10.typography.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            if (onToggle != null) {
                CellDivider()
                ExpandChevron(expanded, rememberListIcons(), onToggle)
            }
        }
        Bb10Divider()
        if (onToggle != null) {
            FoldOut(expanded) { Column { detail() } }
        }
    }
}
