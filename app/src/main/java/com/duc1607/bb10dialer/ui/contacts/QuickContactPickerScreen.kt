package com.duc1607.bb10dialer.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.ContactBook
import com.duc1607.bb10dialer.data.FAVORITES_SECTION
import com.duc1607.bb10dialer.data.contactSections
import com.duc1607.bb10dialer.data.searchContacts
import com.duc1607.bb10dialer.ui.common.SectionHeader
import com.duc1607.bb10dialer.ui.theme.DialerColors
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Action
import duc1607.bb10.designsystem.components.Bb10LazyColumn
import duc1607.bb10.designsystem.components.Bb10Scaffold
import duc1607.bb10.designsystem.components.Bb10Search
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TitleAction
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10

/**
 * Chooses the number of a quick contact: the contacts that have one, folding out their numbers.
 * The header says Select Contact, and Select Number once a contact is open. Search takes over the
 * whole header.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickContactPickerScreen(
    book: ContactBook,
    onCancel: () -> Unit,
    onPick: (String) -> Unit,
    onNewContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val callable = remember(book) { book.contacts.filter { it.numbers.isNotEmpty() } }
    val sections = remember(callable, query) { contactSections(searchContacts(callable, query)) }
    val hasContacts = callable.isNotEmpty()

    val searchAction = Bb10Action("search", stringResource(R.string.action_search), Bb10Icons.Search, enabled = hasContacts) {
        searching = true
    }
    val newAction = Bb10Action("new", stringResource(R.string.action_new), DialerIcons.AddContact, onClick = onNewContact)

    Bb10Scaffold(
        title = stringResource(if (expanded != null) R.string.select_number else R.string.select_contact),
        modifier = modifier,
        dismissAction = Bb10TitleAction(stringResource(R.string.cancel), onClick = onCancel),
        search = if (searching) {
            Bb10Search(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    searching = false
                    query = ""
                },
                placeholder = stringResource(R.string.search_contacts),
                cancelText = stringResource(R.string.cancel),
            )
        } else {
            null
        },
        actions = if (hasContacts) listOf(searchAction) else listOf(searchAction, newAction),
    ) {
        if (sections.isEmpty()) {
            NoContacts()
        } else {
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
                            onDetails = null,
                            onNumber = onPick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoContacts() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = DialerIcons.Person,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            colorFilter = ColorFilter.tint(DialerColors.AvatarFill),
        )
        Spacer(Modifier.height(Bb10.dimens.spaceM))
        Bb10Text(stringResource(R.string.no_contacts), style = Bb10.typography.title)
    }
}
