package com.duc1607.bb10dialer.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.ContactIndex
import com.duc1607.bb10dialer.data.ContactPhoto
import com.duc1607.bb10dialer.data.localDate
import com.duc1607.bb10dialer.data.sameNumber
import com.duc1607.bb10dialer.data.sectionsByDay
import com.duc1607.bb10dialer.telecom.BlockedNumberList
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.PageBar
import com.duc1607.bb10dialer.ui.common.PageBarAction
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.theme.Bb10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The call history of one number: who it is, a way to message them, the calls by day, and at the
 * bottom Back, Callback and the contact card, or Add Contact for a stranger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallDetailsScreen(
    number: String,
    calls: List<CallEntry>,
    index: ContactIndex,
    actions: DialerActions,
    onOpenCard: (Long) -> Unit,
    onAddContact: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val c = Bb10.colors
    val d = Bb10.dimens
    val known = index.find(number)
    val history = remember(calls, number) { calls.filter { it.number == number || sameNumber(it.number, number) } }
    val sections = remember(history) {
        val zone = ZoneId.systemDefault()
        val days = dayFormatter()
        sectionsByDay(history, { it.localDate(zone) }, { days.title(it) })
    }
    val name = known?.first?.name ?: history.firstOrNull()?.cachedName
    val label = known?.second?.label ?: history.firstOrNull()?.cachedLabel

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canBlock = remember { BlockedNumberList.canBlock(context) }
    var blocked by remember(number) { mutableStateOf(canBlock && BlockedNumberList.isBlocked(context, number)) }
    val numbers = remember(number) { listOf(number) }
    val extras = rememberCallExtras(numbers, history)

    Column(modifier.fillMaxSize().background(c.background)) {
        Header(
            title = name ?: number,
            number = number,
            photo = known?.first?.photo,
            subtitle = when {
                name == null -> stringResource(R.string.unknown_number)
                label == null -> number
                else -> stringResource(R.string.details_subtitle, number, label)
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Bb10LazyColumn(Modifier.fillMaxSize().padding(bottom = d.actionBarHeight)) {
                item(key = "message") { DetailActions(number, actions) }
                callHistory(sections, extras)
            }
            PageBar(
                onBack = onBack,
                primary = PageBarAction(DialerIcons.Phone, stringResource(R.string.callback)) { actions.call(number) },
                primaryColor = DialerColors.CallGreen,
                action = if (known != null) {
                    PageBarAction(DialerIcons.Person, stringResource(R.string.contact_card)) { onOpenCard(known.first.id) }
                } else {
                    PageBarAction(DialerIcons.AddContact, stringResource(R.string.add_contact), onClick = onAddContact)
                },
                startAction = if (canBlock) {
                    PageBarAction(
                        DialerIcons.Block,
                        stringResource(if (blocked) R.string.unblock else R.string.block),
                        tint = if (blocked) DialerColors.MissedRed else null,
                    ) {
                        val wanted = !blocked
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { BlockedNumberList.setBlocked(context, number, wanted) }
                            if (done) blocked = wanted
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Holding the title copies it, holding the line under it copies the number. */
@Composable
private fun Header(title: String, number: String, photo: ContactPhoto?, subtitle: String) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val clipboard = LocalClipboardManager.current
    fun copyOnHold(text: String) = Modifier.pointerInput(text) {
        detectTapGestures(onLongPress = { if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text)) })
    }
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().height(d.listItemMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Modifier.size(d.listItemMinHeight), photo)
            Spacer(Modifier.width(d.spaceM))
            Column(Modifier.weight(1f)) {
                Bb10Text(
                    title,
                    style = t.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.then(copyOnHold(title)),
                )
                Bb10Text(
                    subtitle,
                    style = t.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = copyOnHold(number),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent))
    }
}

@Composable
private fun DetailActions(number: String, actions: DialerActions) {
    Row(
        Modifier.fillMaxWidth().height(Bb10.dimens.minTouchTarget),
        horizontalArrangement = Arrangement.Center,
    ) {
        IconAction(DialerIcons.Message, stringResource(R.string.action_sms)) { actions.sms(number) }
    }
}
