package com.duc1607.bb10dialer.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10

private val MoreWidth = 56.dp
private val IndicatorHeight = 2.dp
private val TabIconSize = 28.dp
private val TabIconLabelGap = 2.dp

/**
 * The bottom bar: the app's three tabs and the "⋮" that opens the tab's menu. The selected tab is
 * accent, with a line along its top edge that slides from tab to tab.
 */
@Composable
fun DialerTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bb10.colors
    val tabs = listOf(
        stringResource(R.string.tab_calls) to DialerIcons.CallsTab,
        stringResource(R.string.tab_contacts) to DialerIcons.Person,
        stringResource(R.string.tab_dial_pad) to DialerIcons.DialPad,
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(Bb10.dimens.actionBarHeight)
            .background(c.surfaceVariant)
    ) {
        val tabWidth = (maxWidth - MoreWidth) / tabs.size
        val indicator by animateDpAsState(tabWidth * selected, tween(180), label = "tabIndicator")
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { index, (label, icon) ->
                val isSelected = index == selected
                val highlight = rememberBb10Highlight()
                Column(
                    Modifier
                        .width(tabWidth)
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .bb10Clickable(role = Role.Tab, highlight = highlight, onClick = { onSelect(index) }),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Bb10OnHighlight(highlight) {
                        Bb10Icon(icon, tint = if (isSelected) c.accent else c.textPrimary, size = TabIconSize)
                        Spacer(Modifier.height(TabIconLabelGap))
                        Bb10Text(
                            label,
                            style = Bb10.typography.action,
                            color = if (isSelected) DialerColors.SelectedTabLabel else c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val moreHighlight = rememberBb10Highlight()
            val moreLabel = Bb10.strings.moreActions
            Box(
                Modifier
                    .width(MoreWidth)
                    .fillMaxHeight()
                    .semantics { contentDescription = moreLabel }
                    .bb10Clickable(role = Role.Button, highlight = moreHighlight, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Bb10OnHighlight(moreHighlight) { Bb10Icon(Bb10Icons.More, size = TabIconSize) }
            }
        }
        Box(
            Modifier
                .offset(x = indicator)
                .width(tabWidth)
                .height(IndicatorHeight)
                .background(c.accent)
        )
    }
}
