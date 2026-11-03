package com.duc1607.bb10dialer.ui.dialpad

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.theme.Bb10

private class PadKey(val symbol: Char, @StringRes val letters: Int? = null, val icon: ImageVector? = null)

private val PadRows = listOf(
    listOf(
        PadKey('1', icon = DialerIcons.Voicemail),
        PadKey('2', R.string.pad_letters_2),
        PadKey('3', R.string.pad_letters_3),
    ),
    listOf(PadKey('4', R.string.pad_letters_4), PadKey('5', R.string.pad_letters_5), PadKey('6', R.string.pad_letters_6)),
    listOf(PadKey('7', R.string.pad_letters_7), PadKey('8', R.string.pad_letters_8), PadKey('9', R.string.pad_letters_9)),
    listOf(PadKey('*'), PadKey('0', R.string.pad_letters_0), PadKey('#')),
)

/**
 * The four rows of keys, sharing the height of the column they are in. Holding 0 types the "+"
 * written next to it, unless [holdZeroForPlus] is off: a call in progress has no use for it.
 * Holding 1 to 9 is [onHold] (speed dial), when given. The voicemail mark on 1 shows only when
 * there is a [voicemail] number.
 */
@Composable
internal fun ColumnScope.PadKeys(
    digitSize: TextUnit,
    holdZeroForPlus: Boolean = true,
    voicemail: Boolean = false,
    onHold: ((Char) -> Unit)? = null,
    onSymbol: (Char) -> Unit,
) {
    PadRows.forEach { row ->
        Row(Modifier.weight(1f).fillMaxWidth()) {
            row.forEach { key ->
                val hold = when {
                    key.symbol == '0' && holdZeroForPlus -> ({ onSymbol('+') })
                    onHold != null && key.symbol in '1'..'9' -> ({ onHold(key.symbol) })
                    else -> null
                }
                PadButton(key, digitSize, voicemail, hold, onSymbol)
            }
        }
    }
}

@Composable
private fun RowScope.PadButton(key: PadKey, digitSize: TextUnit, voicemail: Boolean, onHold: (() -> Unit)?, onSymbol: (Char) -> Unit) {
    val t = Bb10.typography
    val highlight = rememberBb10Highlight()
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .bb10Clickable(
                role = Role.Button,
                highlight = highlight,
                onLongClick = onHold,
                onClick = { onSymbol(key.symbol) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bb10OnHighlight(highlight) {
            Spacer(Modifier.weight(0.3f))
            Bb10Text(
                key.symbol.toString(),
                style = t.title.copy(fontSize = digitSize, lineHeight = digitSize, fontWeight = FontWeight.Light),
                maxLines = 1,
            )
            Spacer(Modifier.width(Bb10.dimens.spaceM))
            if (key.icon != null && voicemail) {
                Bb10Icon(key.icon, Modifier.width(22.dp).height(10.dp))
            } else if (key.letters != null) {
                Bb10Text(stringResource(key.letters), style = t.body, maxLines = 1)
            }
            Spacer(Modifier.weight(0.7f))
        }
    }
}
