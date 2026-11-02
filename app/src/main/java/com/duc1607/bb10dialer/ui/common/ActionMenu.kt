package com.duc1607.bb10dialer.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import android.content.res.Configuration
import com.duc1607.bb10dialer.ui.theme.DialerMenuTheme
import duc1607.bb10.designsystem.components.Bb10Action
import duc1607.bb10.designsystem.components.Bb10MenuEntries
import duc1607.bb10.designsystem.components.Bb10MenuHold
import duc1607.bb10.designsystem.components.menuPeekWidth
import duc1607.bb10.designsystem.theme.Bb10

private val ShadowWidth = 6.dp
private val MinMenuWidth = 240.dp
private const val MENU_WIDTH_FRACTION = 0.555f

@Immutable
class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    /** The key that picks it while the menu is open, shown on it when there is a keyboard. */
    val shortcut: Key? = null,
    /** Delete, Remove: at the bottom of the menu, apart from the others. */
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** The Del key of a hardware keyboard: Backspace on most, Delete on some. */
val DeleteKeys = listOf(Key.Backspace, Key.Delete)

/** True for the first press of the Del key, which an item takes to delete itself. */
fun androidx.compose.ui.input.key.KeyEvent.isDelete(): Boolean =
    type == KeyEventType.KeyDown && key in DeleteKeys && nativeKeyEvent.repeatCount == 0

/** Whether [pressed] is [shortcut]; the two delete keys count as one. */
private fun shortcutMatches(shortcut: Key?, pressed: Key): Boolean =
    shortcut != null && (shortcut == pressed || (shortcut in DeleteKeys && pressed in DeleteKeys))

/**
 * The right-hand menu of the phone app: a black panel that slides over the app, its entries laid
 * out by the kit (in the middle, Delete at the bottom). Unlike the kit's menu (internal to
 * `Bb10Scaffold`, which this app does not use) the app behind is not dimmed, only shaded along the
 * panel's edge, as in the original. A menu about one held item ([dim]) does dim it, and [peek]s in
 * as far as its icons while the finger is still down: the finger ([hold]) can then pick one.
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
    peek: Boolean = false,
    dim: Boolean = false,
    hold: Bb10MenuHold? = null,
) {
    val showHints = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS
    val focus = remember { FocusRequester() }
    val showing = open || peek
    BackHandler(enabled = showing, onBack = onDismiss)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = max(maxWidth * MENU_WIDTH_FRACTION, MinMenuWidth)
        val shown by animateDpAsState(
            targetValue = when {
                open -> panelWidth
                peek -> Bb10.dimens.menuPeekWidth
                else -> 0.dp
            },
            animationSpec = tween(if (open) 180 else 120),
            label = "menuShown",
        )
        // The app is dimmed the moment the menu shows up, peeking included.
        val shade by animateFloatAsState(
            targetValue = if (showing && dim) 1f else 0f,
            animationSpec = tween(120),
            label = "menuDim",
        )
        if (showing) {
            val scrim = Bb10.colors.scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim.copy(alpha = scrim.alpha * shade))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        if (open) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { focus.requestFocus() }
            }
        }
        // Composed from the moment it opens, off screen: the focus request above needs it there.
        if (showing || shown > 0.dp) {
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
                        .onKeyEvent { e ->
                            val entry = entries.firstOrNull { it.enabled && shortcutMatches(it.shortcut, e.key) }
                            if (entry == null || e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount != 0) {
                                return@onKeyEvent false
                            }
                            onDismiss()
                            entry.onClick()
                            true
                        }
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                        // Must sit on the focusable, not the group: the group would hand the request
                        // to its entries, which refuse focus after a touch, and a search field being
                        // edited would keep the focus (and answer Back itself).
                        .focusRequester(focus)
                        .focusable(),
                ) {
                    Bb10MenuEntries(
                        actions = entries.map { it.toAction() },
                        hold = hold,
                        showHints = showHints,
                        onPick = { action ->
                            onDismiss()
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** As the kit's menu takes it: the label, unique in a menu, is its id. */
fun MenuEntry.toAction() = Bb10Action(
    id = label,
    label = label,
    icon = icon,
    shortcut = shortcut,
    enabled = enabled,
    destructive = destructive,
    onClick = onClick,
)
