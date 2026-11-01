package com.duc1607.bb10dialer.ui.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10

private val ChevronCellWidth = 48.dp

/**
 * The icons of a long list, each made once for the whole list. Every `Bb10Icon` runs a small
 * composition of its own to draw its vector, which is what makes rows scrolling in expensive.
 */
@Stable
internal class ListIcons(
    private val down: Painter,
    private val up: Painter,
    private val outgoing: Painter,
    private val incoming: Painter,
    private val missed: Painter,
    /** Next to the call's own icon, when the user wrote something about it. */
    val note: Painter,
    /** Likewise, when it was recorded. */
    val recording: Painter,
) {
    fun chevron(expanded: Boolean): Painter = if (expanded) up else down

    fun call(kind: CallKind): Painter = when (kind.icon()) {
        DialerIcons.Outgoing -> outgoing
        DialerIcons.Missed -> missed
        else -> incoming
    }
}

@Composable
internal fun rememberListIcons(): ListIcons {
    val down = rememberVectorPainter(Bb10Icons.ChevronDown)
    val up = rememberVectorPainter(Bb10Icons.ChevronUp)
    val outgoing = rememberVectorPainter(DialerIcons.Outgoing)
    val incoming = rememberVectorPainter(DialerIcons.Incoming)
    val missed = rememberVectorPainter(DialerIcons.Missed)
    val note = rememberVectorPainter(Bb10Icons.Edit)
    val recording = rememberVectorPainter(DialerIcons.Record)
    return remember(down, up, outgoing, incoming, missed, note, recording) {
        ListIcons(down, up, outgoing, incoming, missed, note, recording)
    }
}

/** One of [ListIcons], in one colour. */
@Composable
internal fun ListIcon(painter: Painter, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(painter, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** The cell at the end of a row that folds something out. */
@Composable
internal fun ExpandChevron(expanded: Boolean, icons: ListIcons, onToggle: () -> Unit) {
    val highlight = rememberBb10Highlight()
    val label = stringResource(if (expanded) R.string.collapse else R.string.expand)
    Box(
        Modifier
            .width(ChevronCellWidth)
            .fillMaxHeight()
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        ListIcon(
            icons.chevron(expanded),
            if (highlight.active) Bb10.colors.onAccent else Bb10.colors.textPrimary,
            Bb10.dimens.iconSize,
        )
    }
}

/**
 * What a row folds out. Nothing at all is composed while it is folded: a list scrolls past many
 * rows, nearly all of them closed.
 */
@Composable
internal fun FoldOut(expanded: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (expanded || state.currentState || !state.isIdle) {
        AnimatedVisibility(
            visibleState = state,
            enter = expandVertically(tween(180)),
            exit = shrinkVertically(tween(150)),
        ) { content() }
    }
}
