package com.duc1607.bb10dialer.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.data.ContactPhoto
import com.duc1607.bb10dialer.data.ContactPhotos
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Button
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10TabBar
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIGURE_GRID = 24f

/** A path, not a vector image: lists draw this on most rows, and an image costs a composition. */
private val FigurePath: Path = PathParser().parsePathString(DialerIcons.FIGURE).toPath()

/**
 * A contact's photo, or the grey tile with a figure when there is none (yet). It keeps its colours
 * while its row is highlighted (a plain [Image], not a `Bb10Icon`).
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    photo: ContactPhoto? = null,
    /** The most the photo is shown at, in pixels, for one much larger than its place. */
    photoSide: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val resolver = LocalContext.current.contentResolver
    val figure = DialerColors.AvatarFigure
    // Most rows have no photo or one already in memory: those need no coroutine as they scroll in.
    val cached = remember(photo, photoSide) { photo?.let { ContactPhotos.cached(it, photoSide) } }
    Box(modifier.background(DialerColors.AvatarFill)) {
        val loaded = if (cached != null || photo == null) {
            cached
        } else {
            val image by produceState<ImageBitmap?>(null, photo, photoSide) {
                value = withContext(Dispatchers.IO) { ContactPhotos.load(resolver, photo, photoSide) }
            }
            image
        }
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Canvas(Modifier.fillMaxSize().padding(top = 2.dp)) {
                val side = minOf(size.width, size.height)
                translate((size.width - side) / 2, (size.height - side) / 2) {
                    scale(side / FIGURE_GRID, Offset.Zero) { drawPath(FigurePath, figure) }
                }
            }
        }
        content()
    }
}

/**
 * A day or a letter over a list, with the grey line of the phone app. Not `Bb10ListHeader`, which
 * is an upper-case label over an accent line.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val d = Bb10.dimens
    Column(modifier.fillMaxWidth().background(Bb10.colors.background)) {
        Bb10Text(
            text,
            style = Bb10.typography.secondary.copy(fontWeight = FontWeight.SemiBold),
            color = DialerColors.SectionText,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = d.spaceS, vertical = d.spaceXs)
                .semantics { heading() },
        )
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(DialerColors.SectionLine))
    }
}

/** Tabs of a screen, in their grey band. */
@Composable
fun LocalTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The tabs with something new, for tabs that can have that. The others are then not bold. */
    emphasized: Set<Int>? = null,
) {
    val d = Bb10.dimens
    Box(modifier.fillMaxWidth().padding(start = d.spaceS, end = d.spaceS, top = d.spaceM, bottom = d.spaceXs)) {
        Box(Modifier.fillMaxWidth().background(Bb10.colors.surfaceVariant).padding(2.dp)) {
            if (emphasized == null) {
                Bb10TabBar(tabs = tabs, selectedIndex = selected, onSelect = onSelect)
            } else {
                EmphasisTabs(tabs, selected, onSelect, emphasized)
            }
        }
    }
}

private val LocalTabHeight = 40.dp

/** The kit's tab bar, whose labels are always bold. */
@Composable
private fun EmphasisTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, emphasized: Set<Int>) {
    val c = Bb10.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(LocalTabHeight)) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selected
                val highlight = rememberBb10Highlight()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) c.background else c.surfaceVariant)
                        .semantics { this.selected = isSelected }
                        .bb10Clickable(role = Role.Tab, highlight = highlight, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Bb10OnHighlight(highlight) {
                        Bb10Text(
                            label,
                            style = Bb10.typography.button.copy(
                                fontWeight = if (isSelected || index in emphasized) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) c.accent else c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Bb10Divider()
    }
}

/** The short line between neighbouring cells of a row. */
@Composable
fun CellDivider(modifier: Modifier = Modifier, color: Color = Bb10.colors.divider) {
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = Bb10.dimens.spaceS)
            .width(1.dp)
            .background(color)
    )
}

/** Why a list is empty, with a way out when there is one. */
@Composable
fun EmptyMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    val d = Bb10.dimens
    Column(
        modifier.fillMaxWidth().padding(vertical = d.spaceL * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Bb10Text(
            text,
            color = Bb10.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = d.spaceL, vertical = d.spaceS),
        )
        if (actionText != null) Bb10Button(actionText, onClick = onAction)
    }
}
