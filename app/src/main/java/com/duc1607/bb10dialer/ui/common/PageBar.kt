package com.duc1607.bb10dialer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10

/** Same measures as the kit's action bar, whose Back button and primary circle are internal. */
private val BackSlashLean = 10.dp
private val ActionIconSize = 28.dp
private val ActionWidth = 72.dp
private val IconLabelGap = 4.dp

class PageBarAction(val icon: ImageVector, val label: String, val tint: Color? = null, val onClick: () -> Unit)

/**
 * The bottom bar of a page without a header, which the kit's scaffold does not offer: Back, the
 * large circle in the middle, and one more action on either side of it.
 */
@Composable
fun PageBar(
    onBack: () -> Unit,
    primary: PageBarAction?,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    action: PageBarAction? = null,
    /** To the left of the circle. */
    startAction: PageBarAction? = null,
    /** The menu of the page, at the right end. */
    onMore: (() -> Unit)? = null,
) {
    val d = Bb10.dimens
    // The slash leans out of a square, so the bottom edge is as long as the bar is tall.
    val backWidth = d.actionBarHeight + BackSlashLean
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(d.actionBarHeight)
                .background(Bb10.colors.surfaceVariant)
        )
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BackButton(backWidth, onBack)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (startAction != null) BarAction(startAction)
            }
            if (primary != null) PrimaryButton(primary, primaryColor)
            Row(Modifier.weight(1f)) {
                if (action != null) BarAction(action)
            }
            if (onMore != null) MoreButton(backWidth, onMore) else Spacer(Modifier.width(backWidth))
        }
    }
}

@Composable
private fun MoreButton(width: Dp, onClick: () -> Unit) {
    val highlight = rememberBb10Highlight()
    val label = Bb10.strings.moreActions
    Box(
        Modifier
            .width(width)
            .height(Bb10.dimens.actionBarHeight)
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) { Bb10Icon(Bb10Icons.More, size = ActionIconSize) }
    }
}

/** Gap under the labels, so that icon + label are centred in the bar. */
@Composable
private fun labelBottomPadding(): Dp =
    ((Bb10.dimens.actionBarHeight - ActionIconSize - IconLabelGap - 14.dp) / 2).coerceAtLeast(4.dp)

@Composable
private fun BackButton(width: Dp, onClick: () -> Unit) {
    val c = Bb10.colors
    val highlight = rememberBb10Highlight()
    val label = Bb10.strings.back
    Box(
        Modifier
            .width(width)
            .height(Bb10.dimens.actionBarHeight)
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, slant = BackSlashLean, onClick = onClick)
            .drawBehind {
                drawLine(
                    color = c.accent,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width - BackSlashLean.toPx(), size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) {
            Bb10Icon(Bb10Icons.ChevronLeft, Modifier.padding(end = BackSlashLean), size = ActionIconSize)
        }
    }
}

/** The kit's primary circle, in a colour of the caller's choice. */
@Composable
private fun PrimaryButton(action: PageBarAction, color: Color) {
    val c = Bb10.colors
    val d = Bb10.dimens
    Column(
        Modifier.width(d.primaryActionSize + 32.dp).padding(bottom = labelBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(d.primaryActionSize)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = action.label }
                .bb10Clickable(role = Role.Button, circle = true, onClick = action.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Bb10Icon(action.icon, tint = c.onAccent, size = ActionIconSize)
        }
        Spacer(Modifier.height(IconLabelGap))
        Bb10Text(action.label, style = Bb10.typography.action, maxLines = 1)
    }
}

@Composable
private fun BarAction(action: PageBarAction) {
    val highlight = rememberBb10Highlight()
    Box(
        Modifier
            .width(ActionWidth)
            .height(Bb10.dimens.actionBarHeight)
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = action.onClick),
    ) {
        Bb10OnHighlight(highlight) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = labelBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (action.tint != null && !highlight.active) {
                    Bb10Icon(action.icon, tint = action.tint, size = ActionIconSize)
                } else {
                    Bb10Icon(action.icon, size = ActionIconSize)
                }
                Spacer(Modifier.height(IconLabelGap))
                Bb10Text(
                    action.label,
                    style = Bb10.typography.action.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
