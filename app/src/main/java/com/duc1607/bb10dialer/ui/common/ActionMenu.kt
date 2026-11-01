package com.duc1607.bb10dialer.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.duc1607.bb10dialer.ui.theme.DialerMenuTheme
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10LinkItem
import duc1607.bb10.designsystem.theme.Bb10

private val ShadowWidth = 6.dp
private val MinMenuWidth = 240.dp
private const val MENU_WIDTH_FRACTION = 0.555f

@Immutable
class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The right-hand menu of the phone app: a black panel that slides over the app, entries at the
 * bottom. Unlike the kit's menu (internal to `Bb10Scaffold`, which this app does not use) the app
 * behind is not dimmed, only shaded along the panel's edge, as in the original.
 *
 * Back, tapping outside and swiping it to the right close it. The panel itself takes the focus when
 * it opens, so nothing is highlighted after a touch while the d-pad still starts inside it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionMenu(
    open: Boolean,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    BackHandler(enabled = open, onBack = onDismiss)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = max(maxWidth * MENU_WIDTH_FRACTION, MinMenuWidth)
        val shown by animateDpAsState(
            targetValue = if (open) panelWidth else 0.dp,
            animationSpec = tween(if (open) 180 else 120),
            label = "menuShown",
        )
        if (open) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { focus.requestFocus() }
            }
        }
        // Composed from the moment it opens, off screen: the focus request above needs it there.
        if (open || shown > 0.dp) {
            val colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f))
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -shown)
                    .fillMaxHeight()
                    .width(ShadowWidth)
                    .background(Brush.horizontalGradient(colors))
            )
            DialerMenuTheme {
                val title = Bb10.strings.moreActions
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = panelWidth - shown)
                        .fillMaxHeight()
                        .width(panelWidth)
                        .background(Bb10.colors.surface)
                        .semantics { paneTitle = title }
                        .pointerInput(Unit) {
                            val threshold = 48.dp.toPx()
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = { if (drag > threshold) onDismiss() },
                                onHorizontalDrag = { _, dx -> drag += dx },
                            )
                        }
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                        // Must sit on the focusable, not the group: the group would hand the request
                        // to its entries, which refuse focus after a touch, and a search field being
                        // edited would keep the focus (and answer Back itself).
                        .focusRequester(focus)
                        .focusable(),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Bb10Divider()
                        entries.forEach { entry ->
                            Bb10LinkItem(
                                title = entry.label,
                                icon = entry.icon,
                                enabled = entry.enabled,
                                onClick = {
                                    onDismiss()
                                    entry.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
