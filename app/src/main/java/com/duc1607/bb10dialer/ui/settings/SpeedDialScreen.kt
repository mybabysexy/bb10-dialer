package com.duc1607.bb10dialer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.ContactIndex
import com.duc1607.bb10dialer.data.QUICK_CONTACT_SLOTS
import com.duc1607.bb10dialer.data.SPEED_DIAL_KEYS
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.common.isDelete
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.theme.Bb10

/** A speed dial that is about to be removed: what it calls, and how to take it away. */
private class Removal(val name: String, val remove: () -> Unit)

/**
 * The speed dial: the tiles at the top of the Calls tab (three when voicemail takes the first place,
 * four otherwise) and the keys 2 to 9 of the dial pad, which call when held. Tapping one chooses its
 * number; holding it, or Del on it, asks before removing it.
 */
@Composable
fun SpeedDialScreen(
    quick: List<String?>,
    keys: Map<Int, String>,
    index: ContactIndex,
    hasVoicemail: Boolean,
    showNumbers: Boolean,
    onPickTile: (Int) -> Unit,
    onRemoveTile: (Int) -> Unit,
    onPickKey: (Int) -> Unit,
    onRemoveKey: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var removal by remember { mutableStateOf<Removal?>(null) }
    val tiles = if (hasVoicemail) QUICK_CONTACT_SLOTS - 1 else QUICK_CONTACT_SLOTS

    SettingsFrame(stringResource(R.string.setting_speed_dial), onBack) {
        Bb10LazyColumn(Modifier.fillMaxSize()) {
            item(key = "calls") {
                SectionHeader(stringResource(R.string.tab_calls))
                SettingNote(stringResource(R.string.speed_dial_tiles_note))
            }
            items(tiles, key = { "tile-$it" }) { slot ->
                val number = quick.getOrNull(slot)
                val photo = remember(number, index) { number?.let { index.find(it) }?.first?.photo }
                SpeedDialRow(
                    number = number,
                    index = index,
                    showNumbers = showNumbers,
                    lead = { Avatar(Modifier.size(Bb10.dimens.listItemMinHeight), photo) },
                    onPick = { onPickTile(slot) },
                    onRemove = { name -> removal = Removal(name) { onRemoveTile(slot) } },
                )
            }
            item(key = "pad") {
                SectionHeader(stringResource(R.string.tab_dial_pad))
                SettingNote(stringResource(R.string.speed_dial_keys_note))
            }
            items(SPEED_DIAL_KEYS.toList(), key = { "key-$it" }) { digit ->
                SpeedDialRow(
                    number = keys[digit],
                    index = index,
                    showNumbers = showNumbers,
                    lead = { DigitBox(digit) },
                    onPick = { onPickKey(digit) },
                    onRemove = { name -> removal = Removal(name) { onRemoveKey(digit) } },
                )
            }
        }
    }

    removal?.let { r ->
        Bb10Dialog(
            title = stringResource(R.string.speed_dial_remove_title),
            message = stringResource(R.string.speed_dial_remove_message, r.name),
            confirmText = stringResource(R.string.remove),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                removal = null
                r.remove()
            },
            onDismiss = { removal = null },
        )
    }
}

/** The key, where the other section has the contact's picture. */
@Composable
private fun DigitBox(digit: Int) {
    val size = Bb10.dimens.listItemMinHeight
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Bb10Text(
            digit.toString(),
            style = Bb10.typography.title.copy(fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light),
            maxLines = 1,
        )
    }
}

/** One speed dial: who it calls, or Add Number. [onRemove] gets the name shown for it. */
@Composable
private fun SpeedDialRow(
    number: String?,
    index: ContactIndex,
    showNumbers: Boolean,
    lead: @Composable () -> Unit,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val highlight = rememberBb10Highlight()
    val known = remember(number, index) { number?.let { index.find(it) } }
    val name = known?.first?.name ?: number
    val subtitle = when {
        known == null -> null
        showNumbers -> stringResource(R.string.quick_contact_number, number.orEmpty(), known.second.label)
        else -> known.second.label
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.listItemMinHeight)
                .onKeyEvent { e ->
                    if (name != null && e.isDelete()) {
                        onRemove(name)
                        true
                    } else false
                }
                .bb10Clickable(
                    role = Role.Button,
                    highlight = highlight,
                    flat = true,
                    onLongClick = name?.let { { onRemove(it) } },
                    onClick = onPick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10OnHighlight(highlight) {
                lead()
                Spacer(Modifier.width(d.spaceS))
                Column(Modifier.weight(1f)) {
                    if (name == null) {
                        Bb10Text(
                            stringResource(R.string.add_number),
                            style = t.body.copy(fontStyle = FontStyle.Italic),
                            color = if (highlight.active) c.onAccent else c.textSecondary,
                            maxLines = 1,
                        )
                    } else {
                        Bb10Text(name, style = t.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Bb10Text(
                                subtitle,
                                style = t.secondary,
                                color = if (highlight.active) c.onAccent else c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Bb10Divider()
    }
}
