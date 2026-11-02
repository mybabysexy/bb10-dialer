package com.duc1607.bb10dialer.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import duc1607.bb10.designsystem.components.Bb10Dialog
import com.duc1607.bb10dialer.ui.common.MenuEntry
import com.duc1607.bb10dialer.ui.common.ActionMenu
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.Contact
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.ContactData
import com.duc1607.bb10dialer.data.ContactField
import com.duc1607.bb10dialer.data.FieldKind
import com.duc1607.bb10dialer.data.localDate
import com.duc1607.bb10dialer.data.sameNumber
import com.duc1607.bb10dialer.data.sectionsByDay
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.calls.callHistory
import com.duc1607.bb10dialer.ui.calls.rememberCallExtras
import com.duc1607.bb10dialer.ui.calls.dayFormatter
import com.duc1607.bb10dialer.ui.calls.title
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.EmptyMessage
import com.duc1607.bb10dialer.ui.common.LocalTabs
import com.duc1607.bb10dialer.ui.common.PageBar
import com.duc1607.bb10dialer.ui.common.PageBarAction
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import java.time.ZoneId

private const val HEADER_HEIGHT_FRACTION = 0.27f
private const val TAB_DETAILS = 0

/**
 * The contact card: the photo with the name over it, Details (numbers to call or text, then what
 * else is known) and Activity (their recent calls). A big header instead of the kit's title bar,
 * as in the original.
 */
@Composable
fun ContactCardScreen(
    contactId: Long,
    book: ContactBook,
    calls: List<CallEntry>,
    revision: Int,
    loadData: suspend (Long) -> ContactData?,
    actions: DialerActions,
    onEdit: () -> Unit,
    /** After the user said yes. */
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val contact = remember(book, contactId) { book.contacts.firstOrNull { it.id == contactId } }
    // Deleted or merged away while this was open.
    LaunchedEffect(contact) { if (contact == null) onBack() }
    if (contact == null) return

    val data by produceState<ContactData?>(null, contactId, revision) { value = loadData(contactId) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_DETAILS) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // The card takes the focus, so that Del and Menu reach it from a hardware keyboard.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount != 0) return@onKeyEvent false
                when (e.key) {
                    Key.Backspace, Key.Delete -> {
                        confirmDelete = true
                        true
                    }
                    Key.Menu -> {
                        menuOpen = !menuOpen
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
    Column(Modifier.fillMaxSize().background(Bb10.colors.background)) {
        CardHeader(contact)
        LocalTabs(
            tabs = listOf(stringResource(R.string.tab_details), stringResource(R.string.tab_activity)),
            selected = tab,
            onSelect = { tab = it },
        )
        Bb10Divider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val listModifier = Modifier.fillMaxSize().padding(bottom = Bb10.dimens.actionBarHeight)
            if (tab == TAB_DETAILS) {
                DetailsTab(contact, data, actions, listModifier)
            } else {
                ActivityTab(contact, calls, listModifier)
            }
            PageBar(
                onBack = onBack,
                primary = PageBarAction(Bb10Icons.Edit, stringResource(R.string.action_edit), onClick = onEdit),
                primaryColor = Bb10.colors.accent,
                modifier = Modifier.align(Alignment.BottomCenter),
                onMore = { menuOpen = true },
            )
        }
    }
    ActionMenu(
        open = menuOpen,
        entries = listOf(
            MenuEntry(stringResource(R.string.delete), Bb10Icons.Delete, shortcut = Key.Delete, destructive = true) {
                confirmDelete = true
            },
        ),
        onDismiss = { menuOpen = false },
    )
    if (confirmDelete) {
        Bb10Dialog(
            title = stringResource(R.string.delete_contact_title),
            message = stringResource(R.string.delete_contact_message),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    }
}

@Composable
private fun CardHeader(contact: Contact) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val shade = Brush.verticalGradient(listOf(Color.Transparent, DialerColors.PhotoScrim))
    val photo = contact.largePhoto ?: contact.photo
    Box(Modifier.fillMaxWidth().fillMaxHeight(HEADER_HEIGHT_FRACTION)) {
        if (photo != null) {
            Avatar(Modifier.fillMaxSize(), photo)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(DialerColors.CardHeaderTop, DialerColors.CardHeaderBottom)))
            )
            Image(
                imageVector = DialerIcons.Person,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(DialerColors.PhotoScrim),
            )
        }
        Box(Modifier.fillMaxSize().background(shade))
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = d.spaceM, vertical = d.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10Text(
                contact.name,
                Modifier.weight(1f).semantics { heading() },
                style = Bb10.typography.title.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light),
                color = c.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.favorite) {
                Bb10Icon(DialerIcons.Star, tint = c.onAccent, size = 24.dp, contentDescription = stringResource(R.string.favorite))
            }
        }
    }
}

@Composable
private fun DetailsTab(contact: Contact, data: ContactData?, actions: DialerActions, modifier: Modifier) {
    val resources = LocalContext.current.resources
    val others = remember(data) {
        data?.uniqueFields.orEmpty().filter { it.kind != FieldKind.Phone }.sortedBy { it.kind.ordinal }
    }
    val hasNumbers = contact.numbers.isNotEmpty()
    Bb10LazyColumn(modifier) {
        items(contact.numbers, key = { "call-${it.number}" }) { phone ->
            CardRow(DialerIcons.Device, null, phone.number, stringResource(R.string.call_type, phone.label), phone.number) {
                actions.call(phone.number)
            }
        }
        if (hasNumbers) {
            item(key = "gap-numbers") { GroupGap() }
            items(contact.numbers, key = { "text-${it.number}" }) { phone ->
                CardRow(
                    DialerIcons.Message,
                    DialerColors.MessageYellow,
                    phone.number,
                    stringResource(R.string.text_type, phone.label),
                    phone.number,
                ) { actions.sms(phone.number) }
            }
        }
        if (others.isNotEmpty()) {
            if (hasNumbers) item(key = "gap-others") { GroupGap() }
            items(others, key = { "field-${it.dataId}" }) { field ->
                CardRow(field.icon(), null, field.mainText(), field.caption(resources), field.mainText(), null)
            }
        }
    }
}

private fun ContactField.icon(): ImageVector = when (kind) {
    FieldKind.Email -> Bb10Icons.Mail
    FieldKind.Birthday -> DialerIcons.Star
    else -> Bb10Icons.Info
}

private fun ContactField.mainText(): String = when {
    kind == FieldKind.Organization && value.isNotEmpty() && title.isNotEmpty() -> "$value, $title"
    kind == FieldKind.Organization && value.isEmpty() -> title
    else -> value
}

@Composable
private fun ContactField.caption(resources: android.content.res.Resources): String {
    val name = stringResource(kindLabel(kind))
    val type = typeLabel(resources, kind, type, label)
    return if (type.isEmpty()) name else stringResource(R.string.labeled_number, name, type)
}

@Composable
private fun GroupGap() {
    Box(Modifier.fillMaxWidth().height(Bb10.dimens.spaceM).background(DialerColors.CardGroupGap))
}

/** A value over what it is. Rows without [onClick] only inform. Holding one copies [copy]. */
@Composable
private fun CardRow(icon: ImageVector, tint: Color?, title: String, caption: String, copy: String, onClick: (() -> Unit)?) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val highlight = rememberBb10Highlight()
    val clipboard = LocalClipboardManager.current
    val copyIt = { clipboard.setText(AnnotatedString(copy)) }
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = d.listItemMinHeight)
                .then(
                    if (onClick != null) {
                        Modifier.bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onLongClick = copyIt, onClick = onClick)
                    } else {
                        Modifier.pointerInput(copy) { detectTapGestures(onLongPress = { copyIt() }) }
                    }
                )
                .padding(vertical = d.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10OnHighlight(highlight) {
                Box(Modifier.width(d.listItemMinHeight), contentAlignment = Alignment.Center) {
                    if (tint != null) {
                        Bb10Icon(icon, tint = tint, size = 28.dp)
                    } else {
                        Bb10Icon(icon, size = 28.dp)
                    }
                }
                Spacer(Modifier.width(d.spaceS))
                Column(Modifier.weight(1f).padding(end = d.spaceS)) {
                    Bb10Text(title, style = t.title.copy(fontWeight = FontWeight.Normal), overflow = TextOverflow.Ellipsis, maxLines = 3)
                    Bb10Text(caption, style = t.secondary, color = c.textSecondary, maxLines = 1)
                }
            }
        }
        Bb10Divider()
    }
}

/** The same as the call details of a number, for all of the contact's numbers. */
@Composable
private fun ActivityTab(contact: Contact, calls: List<CallEntry>, modifier: Modifier) {
    val numbers = remember(contact) { contact.numbers.map { it.number } }
    val theirs = remember(numbers, calls) { calls.filter { call -> numbers.any { sameNumber(it, call.number) } } }
    val sections = remember(theirs) {
        val zone = ZoneId.systemDefault()
        val days = dayFormatter()
        sectionsByDay(theirs, { it.localDate(zone) }, { days.title(it) })
    }
    val extras = rememberCallExtras(numbers, theirs)
    Bb10LazyColumn(modifier) {
        callHistory(sections, extras)
        if (sections.isEmpty() && extras.notes.unmatched.isEmpty() && extras.recordings.unmatched.isEmpty()) {
            item(key = "empty") { EmptyMessage(stringResource(R.string.no_calls)) }
        }
    }
}
