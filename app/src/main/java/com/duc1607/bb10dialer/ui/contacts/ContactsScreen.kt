package com.duc1607.bb10dialer.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.Contact
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.FAVORITES_SECTION
import com.duc1607.bb10dialer.data.contactSections
import com.duc1607.bb10dialer.data.searchContacts
import com.duc1607.bb10dialer.ui.DialerActions
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.common.EmptyMessage
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.common.isDelete
import com.duc1607.bb10dialer.ui.common.onCallKey
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import com.duc1607.bb10dialer.data.SimLine
import com.duc1607.bb10dialer.ui.common.PullDownHeader
import com.duc1607.bb10dialer.ui.common.SimHeader
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.theme.Bb10

/**
 * The Contacts tab: the search field (focused on arrival), then contacts by first letter. Tapping a
 * contact folds out View Contact Details and their numbers; tapping a number calls it.
 *
 * The search field sits in the content, not in a header as the kit asks: this tab has no header.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    book: ContactBook,
    simLines: List<SimLine>,
    canReadContacts: Boolean,
    refocus: Int,
    actions: DialerActions,
    onOpenCard: (Long) -> Unit,
    /** Holding a contact, or Menu on it: picks it for the menu ([LocalBb10Menu]). */
    onContactMenu: (Contact) -> Unit,
    /** Del on a contact. */
    onDeleteContact: (Contact) -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Bb10.colors
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val searchFocus = remember { FocusRequester() }
    val sections = remember(book, query) {
        if (query.isBlank()) book.sections else contactSections(searchContacts(book.contacts, query))
    }

    // Coming back from the menu with the keys; the first arrival is `startEditing` below. After a
    // touch, focusing the field would bring the on-screen keyboard back up under the finger.
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(refocus) {
        if (refocus > 0 && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.fillMaxWidth().background(DialerColors.SearchBand)) {
            Bb10TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_contacts),
                clearable = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                focusRequester = searchFocus,
                startEditing = true,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(DialerColors.SearchLineStart, c.accent)))
        )
        // Under the search field, which stays put, and over the first group of contacts.
        PullDownHeader(header = { SimHeader(simLines) }, modifier = Modifier.weight(1f)) {
            Bb10LazyColumn(Modifier.fillMaxSize()) {
                for (section in sections) {
                    stickyHeader(key = "letter-${section.title}") {
                        SectionHeader(
                            if (section.title == FAVORITES_SECTION) stringResource(R.string.favorites) else section.title
                        )
                    }
                    items(section.items, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            expanded = expanded == contact.id,
                            onToggle = { expanded = if (expanded == contact.id) null else contact.id },
                            onDetails = { onOpenCard(contact.id) },
                            onNumber = actions::call,
                            onCallKey = actions::call,
                            onMenu = { onContactMenu(contact) },
                            onDelete = { onDeleteContact(contact) },
                        )
                    }
                }
                if (sections.isEmpty()) {
                    item(key = "empty") {
                        if (canReadContacts) {
                            EmptyMessage(stringResource(R.string.no_contacts))
                        } else {
                            EmptyMessage(
                                stringResource(R.string.contacts_permission),
                                actionText = stringResource(R.string.allow_access),
                                onAction = onAllowAccess,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A contact that folds out their numbers, and View Contact Details when [onDetails] is given. */
@Composable
fun ContactRow(
    contact: Contact,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDetails: (() -> Unit)?,
    onNumber: (String) -> Unit,
    /** The Call key on this contact, or on one of their numbers. */
    onCallKey: ((String) -> Unit)? = null,
    /** Holding the contact, or Menu on it (null: no menu): picks it for the menu ([LocalBb10Menu]). */
    onMenu: (() -> Unit)? = null,
    /** Del on the contact. */
    onDelete: (() -> Unit)? = null,
) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            // On the row alone: the numbers under it have their own.
            (if (onCallKey == null) Modifier else Modifier.onCallKey(context) { contact.numbers.firstOrNull()?.let { onCallKey(it.number) } })
                .fillMaxWidth()
                .height(d.listItemMinHeight)
                .then(if (expanded) Modifier.background(DialerColors.RowExpanded) else Modifier)
                .then(
                    if (onDelete == null) Modifier else Modifier.onKeyEvent { e ->
                        if (e.isDelete()) {
                            onDelete()
                            true
                        } else false
                    }
                )
                .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onMenu = onMenu, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Modifier.size(d.listItemMinHeight), contact.photo)
            Spacer(Modifier.width(d.spaceS))
            Bb10OnHighlight(highlight) {
                Bb10Text(
                    contact.name,
                    style = Bb10.typography.title.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (contact.favorite) {
                    val label = stringResource(R.string.favorite)
                    Bb10Icon(
                        DialerIcons.Star,
                        Modifier
                            .padding(horizontal = d.spaceS)
                            .semantics { contentDescription = label },
                        tint = if (highlight.active) c.onAccent else c.accent,
                        size = 22.dp,
                    )
                }
            }
        }
        Bb10Divider()
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180)),
            exit = shrinkVertically(tween(150)),
        ) {
            Column {
                if (onDetails != null) {
                    ChildRow(DialerIcons.ContactCard, stringResource(R.string.view_contact_details), null, onClick = onDetails)
                }
                contact.numbers.forEach { phone ->
                    ChildRow(
                        DialerIcons.Device,
                        phone.number,
                        phone.label,
                        if (onCallKey == null) Modifier else Modifier.onCallKey(context) { onCallKey(phone.number) },
                    ) { onNumber(phone.number) }
                }
            }
        }
    }
}

@Composable
private fun ChildRow(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val t = Bb10.typography
    val highlight = rememberBb10Highlight()
    Column(modifier.fillMaxWidth().background(DialerColors.RowChild)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = d.listItemMinHeight)
                .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onClick = onClick)
                .padding(vertical = d.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bb10OnHighlight(highlight) {
                Box(Modifier.width(d.listItemMinHeight), contentAlignment = Alignment.Center) {
                    Bb10Icon(icon, size = 28.dp)
                }
                Spacer(Modifier.width(d.spaceS))
                Column(Modifier.weight(1f)) {
                    Bb10Text(
                        title,
                        style = t.title.copy(fontWeight = FontWeight.Normal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Bb10Text(subtitle, style = t.secondary, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
        Bb10Divider(color = DialerColors.RowChildDivider)
    }
}
