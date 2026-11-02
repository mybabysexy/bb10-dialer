package com.duc1607.bb10dialer.ui.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duc1607.bb10dialer.DialerViewModel
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.Contact
import com.duc1607.bb10dialer.data.ContactAccount
import com.duc1607.bb10dialer.data.ContactDraft
import com.duc1607.bb10dialer.data.ContactField
import com.duc1607.bb10dialer.data.ContactPhoto
import com.duc1607.bb10dialer.data.FieldKind
import com.duc1607.bb10dialer.ui.common.Avatar
import com.duc1607.bb10dialer.ui.theme.DialerIcons
import duc1607.bb10.designsystem.components.Bb10Action
import duc1607.bb10.designsystem.components.Bb10DatePickerField
import duc1607.bb10.designsystem.components.Bb10Dialog
import duc1607.bb10.designsystem.components.Bb10Divider
import duc1607.bb10.designsystem.components.Bb10Dropdown
import duc1607.bb10.designsystem.components.Bb10DropdownPanel
import duc1607.bb10.designsystem.components.Bb10Icon
import duc1607.bb10.designsystem.components.Bb10OnHighlight
import duc1607.bb10.designsystem.components.Bb10Scaffold
import duc1607.bb10.designsystem.components.Bb10Text
import duc1607.bb10.designsystem.components.Bb10TextField
import duc1607.bb10.designsystem.components.Bb10TitleAction
import duc1607.bb10.designsystem.components.bb10Clickable
import duc1607.bb10.designsystem.components.bb10VerticalScroll
import duc1607.bb10.designsystem.components.rememberBb10Highlight
import duc1607.bb10.designsystem.icons.Bb10Icons
import duc1607.bb10.designsystem.theme.Bb10
import java.time.LocalDate

private val PictureHeight = 120.dp
private val MinHeightForScrollbar = 32.dp
private val AddFieldListHeight = 320.dp
private val TypeWeight = 0.42f
private const val FIRST_BIRTH_YEAR = 1900

/** What Add Field offers. */
private class FieldOption(@StringRes val label: Int, val kind: FieldKind, val type: Int = 0)

private val FieldOptions = listOf(
    FieldOption(R.string.field_mobile, FieldKind.Phone, Phone.TYPE_MOBILE),
    FieldOption(R.string.field_work_phone, FieldKind.Phone, Phone.TYPE_WORK),
    FieldOption(R.string.field_home_phone, FieldKind.Phone, Phone.TYPE_HOME),
    FieldOption(R.string.field_other_phone, FieldKind.Phone, Phone.TYPE_OTHER),
    FieldOption(R.string.field_email, FieldKind.Email, Email.TYPE_HOME),
    FieldOption(R.string.field_website, FieldKind.Website),
    FieldOption(R.string.field_address, FieldKind.Address, StructuredPostal.TYPE_HOME),
    FieldOption(R.string.field_company, FieldKind.Organization),
    FieldOption(R.string.field_note, FieldKind.Note),
    FieldOption(R.string.field_relation, FieldKind.Relation, Relation.TYPE_FRIEND),
    FieldOption(R.string.field_label, FieldKind.Label),
    FieldOption(R.string.field_nickname, FieldKind.Nickname),
    FieldOption(R.string.field_birthday, FieldKind.Birthday),
)

/** A contact holds these once; the others can repeat. */
private val SingleKinds = setOf(FieldKind.Organization, FieldKind.Note, FieldKind.Nickname, FieldKind.Birthday)

private class FormRow(val id: Int, val field: ContactField)

/** What is typed so far. Survives the activity being recreated. */
private class ContactForm {
    var loaded by mutableStateOf(false)
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var favorite by mutableStateOf(false)
    var accountIndex by mutableIntStateOf(0)
    /** A newly chosen picture, not saved yet. */
    var photoUri by mutableStateOf<String?>(null)
    val rows = mutableStateListOf<FormRow>()
    private var nextId = 0

    fun add(field: ContactField) {
        rows += FormRow(nextId++, field)
    }

    fun change(id: Int, edit: (ContactField) -> ContactField) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = FormRow(id, edit(rows[index].field))
    }

    fun remove(id: Int) {
        rows.removeAll { it.id == id }
    }

    val canSave: Boolean
        get() = firstName.isNotBlank() || lastName.isNotBlank() || rows.any { it.field.hasContent }

    companion object {
        private const val COLUMNS = 7

        val Saver = listSaver<ContactForm, Any?>(
            save = { form ->
                buildList {
                    add(form.loaded)
                    add(form.firstName)
                    add(form.lastName)
                    add(form.favorite)
                    add(form.accountIndex)
                    add(form.photoUri)
                    form.rows.forEach { row ->
                        val f = row.field
                        add(f.kind.name)
                        add(f.value)
                        add(f.type)
                        add(f.label)
                        add(f.title)
                        add(f.dataId)
                        add(f.rawContactId)
                    }
                }
            },
            restore = { saved ->
                ContactForm().apply {
                    loaded = saved[0] as Boolean
                    firstName = saved[1] as String
                    lastName = saved[2] as String
                    favorite = saved[3] as Boolean
                    accountIndex = saved[4] as Int
                    photoUri = saved[5] as String?
                    saved.drop(6).chunked(COLUMNS).forEach { c ->
                        add(
                            ContactField(
                                kind = FieldKind.valueOf(c[0] as String),
                                value = c[1] as String,
                                type = c[2] as Int,
                                label = c[3] as String?,
                                title = c[4] as String,
                                dataId = c[5] as Long?,
                                rawContactId = c[6] as Long?,
                            )
                        )
                    }
                }
            },
        )
    }
}

/**
 * New Contact and Edit Contact: the picture, first and last name and the numbers; everything else
 * comes through Add Field and lands below. A new contact also chooses where it is saved. Nothing
 * is written before Save, favorite included; Delete asks first.
 */
@Composable
fun ContactEditorScreen(
    contactId: Long?,
    /** Filled in as the first number of a new contact. */
    number: String?,
    contact: Contact?,
    viewModel: DialerViewModel,
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val form = rememberSaveable(contactId, saver = ContactForm.Saver) { ContactForm() }
    val isNew = contactId == null
    var busy by remember { mutableStateOf(false) }
    var addingField by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        if (form.loaded) return@LaunchedEffect
        if (contactId == null) {
            form.add(ContactField(FieldKind.Phone, number.orEmpty(), Phone.TYPE_MOBILE))
        } else {
            val data = viewModel.contactData(contactId) ?: return@LaunchedEffect onCancel()
            form.firstName = data.firstName
            form.lastName = data.lastName
            form.favorite = contact?.favorite == true
            data.editableFields.sortedBy { it.kind.ordinal }.forEach(form::add)
        }
        form.loaded = true
    }

    val localName = stringResource(R.string.save_to_local)
    val accounts by produceState(listOf(ContactAccount(null, null))) {
        if (isNew) value = viewModel.contactAccounts()
    }
    val pickPhoto = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) form.photoUri = uri.toString()
    }
    val failed = stringResource(R.string.save_failed)
    val failedBecause = stringResource(R.string.save_failed_because)

    fun save() {
        busy = true
        val draft = ContactDraft(
            contactId = contactId,
            account = accounts.getOrElse(form.accountIndex) { ContactAccount(null, null) },
            firstName = form.firstName,
            lastName = form.lastName,
            fields = form.rows.map { it.field },
            favorite = form.favorite,
            photoUri = form.photoUri,
        )
        viewModel.saveContact(draft) { result ->
            busy = false
            result
                .onSuccess(onSaved)
                // What went wrong, for the user to pass on: phones differ in how they refuse.
                .onFailure { Toast.makeText(context, failedBecause.format(it.javaClass.simpleName), Toast.LENGTH_LONG).show() }
        }
    }

    BackHandler(onBack = onCancel)
    Bb10Scaffold(
        title = stringResource(if (isNew) R.string.new_contact else R.string.edit_contact),
        modifier = modifier,
        dismissAction = Bb10TitleAction(stringResource(R.string.cancel), onClick = onCancel),
        acceptAction = Bb10TitleAction(
            stringResource(R.string.save),
            enabled = form.loaded && form.canSave && !busy,
            onClick = ::save,
        ),
        // Two slots: Add Field, and the menu button that holds the rest.
        maxVisibleActions = 2,
        actions = listOf(
            Bb10Action("addField", stringResource(R.string.add_field), Bb10Icons.Add) { addingField = true },
            Bb10Action(
                "favorite",
                stringResource(if (form.favorite) R.string.remove_favorite else R.string.mark_favorite),
                DialerIcons.Star,
            ) { form.favorite = !form.favorite },
            Bb10Action("delete", stringResource(R.string.delete), Bb10Icons.Delete, enabled = !isNew) {
                confirmDelete = true
            },
        ),
    ) {
        FormColumn(form.rows.size) {
            if (isNew) {
                Bb10Dropdown(
                    label = stringResource(R.string.save_to),
                    options = accounts.map { it.name ?: localName },
                    selectedIndex = form.accountIndex,
                    onSelect = { form.accountIndex = it },
                )
            }
            Picture(
                photo = form.photoUri?.let { ContactPhoto(it, 0) } ?: contact?.largePhoto ?: contact?.photo,
                favorite = form.favorite,
                onClick = { pickPhoto.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            )
            ValueField(form.firstName, stringResource(R.string.first_name)) { form.firstName = it }
            ValueField(form.lastName, stringResource(R.string.last_name)) { form.lastName = it }
            form.rows.groupBy { it.field.kind }.forEach { (kind, rows) ->
                Bb10Divider()
                Bb10Text(
                    stringResource(kindLabel(kind)),
                    modifier = Modifier.padding(start = Bb10.dimens.spaceL, top = Bb10.dimens.spaceS),
                )
                rows.forEach { row ->
                    key(row.id) {
                        FieldEditor(row.field, { form.change(row.id, it) }, { form.remove(row.id) })
                    }
                }
            }
            Spacer(Modifier.height(Bb10.dimens.spaceL))
        }
    }

    if (addingField) {
        AddFieldDialog(
            options = FieldOptions.filter { option ->
                option.kind !in SingleKinds || form.rows.none { it.field.kind == option.kind }
            },
            onPick = { option ->
                addingField = false
                val value = if (option.kind == FieldKind.Birthday) LocalDate.now().toString() else ""
                form.add(ContactField(option.kind, value, option.type))
            },
            onDismiss = { addingField = false },
        )
    }
    if (confirmDelete && contactId != null) {
        Bb10Dialog(
            title = stringResource(R.string.delete_contact_title),
            message = stringResource(R.string.delete_contact_message),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteContact(contactId) { deleted ->
                    if (deleted) onDeleted() else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The scrolling page. The kit's scroll bar throws when the viewport is shorter than its smallest
 * thumb, which the on-screen keyboard causes on a small screen: such a page goes without the bar.
 */
@Composable
private fun FormColumn(rowCount: Int, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    // What Add Field added lands at the end: bring it into view.
    var lastCount by remember { mutableIntStateOf(rowCount) }
    LaunchedEffect(rowCount) {
        if (rowCount > lastCount && lastCount > 0) {
            withFrameNanos { }
            scroll.animateScrollTo(scroll.maxValue)
        }
        lastCount = rowCount
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val page = if (maxHeight < MinHeightForScrollbar) {
            Modifier.verticalScroll(scroll)
        } else {
            Modifier.bb10VerticalScroll(scroll)
        }
        Column(Modifier.fillMaxSize().then(page), content = content)
    }
}

@Composable
private fun Picture(photo: ContactPhoto?, favorite: Boolean, onClick: () -> Unit) {
    val c = Bb10.colors
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    Box(
        Modifier
            .padding(top = d.spaceS)
            .fillMaxWidth()
            .height(PictureHeight)
            .background(c.surfaceVariant)
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
    ) {
        if (photo != null) {
            Avatar(Modifier.fillMaxSize(), photo)
        } else {
            Bb10OnHighlight(highlight) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Bb10Icon(Bb10Icons.Add, size = 40.dp, tint = c.textSecondary)
                    Bb10Text(stringResource(R.string.add_picture))
                }
            }
        }
        if (favorite) {
            Image(
                imageVector = DialerIcons.Star,
                contentDescription = stringResource(R.string.favorite),
                modifier = Modifier.align(Alignment.TopEnd).padding(d.spaceS).size(24.dp),
                colorFilter = ColorFilter.tint(c.accent),
            )
        }
    }
}

@Composable
private fun FieldEditor(field: ContactField, onChange: ((ContactField) -> ContactField) -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (typesOf(field.kind).isNotEmpty()) {
            TypeDropdown(field, onChange, Modifier.weight(TypeWeight))
        }
        Column(Modifier.weight(1f)) {
            when (field.kind) {
                FieldKind.Organization -> {
                    ValueField(field.value, stringResource(R.string.field_company)) { v -> onChange { it.copy(value = v) } }
                    ValueField(field.title, stringResource(R.string.field_job_title)) { v -> onChange { it.copy(title = v) } }
                }
                FieldKind.Birthday -> BirthdayField(field, onChange)
                else -> ValueField(
                    field.value,
                    stringResource(kindLabel(field.kind)),
                    keyboardType = when (field.kind) {
                        FieldKind.Phone -> KeyboardType.Phone
                        FieldKind.Email -> KeyboardType.Email
                        FieldKind.Website -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                    singleLine = field.kind != FieldKind.Note && field.kind != FieldKind.Address,
                ) { v -> onChange { it.copy(value = v) } }
            }
        }
        RemoveButton(onRemove)
    }
}

@Composable
private fun ValueField(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Bb10TextField(
        value = value,
        onValueChange = onValueChange,
        // These fields have no label over them, which is what the kit names a field after.
        modifier = Modifier.semantics { contentDescription = placeholder },
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/** A date that came from elsewhere may have no year: that one stays plain text. */
@Composable
private fun BirthdayField(field: ContactField, onChange: ((ContactField) -> ContactField) -> Unit) {
    val date = remember(field.value) { runCatching { LocalDate.parse(field.value) }.getOrNull() }
    if (date == null) {
        ValueField(field.value, stringResource(R.string.field_birthday)) { v -> onChange { it.copy(value = v) } }
    } else {
        Bb10DatePickerField(
            label = stringResource(R.string.field_birthday),
            date = date,
            onDateChange = { d -> onChange { it.copy(value = d.toString()) } },
            yearRange = FIRST_BIRTH_YEAR..LocalDate.now().year,
        )
    }
}

/** The kit's drop-down shows a label and a value; here the value is all there is to say. */
@Composable
private fun TypeDropdown(field: ContactField, onChange: ((ContactField) -> ContactField) -> Unit, modifier: Modifier) {
    val resources = LocalContext.current.resources
    val types = typesOf(field.kind)
    Bb10DropdownPanel(
        label = typeLabel(resources, field.kind, field.type, field.label),
        value = "",
        modifier = modifier,
    ) { close ->
        Column {
            types.forEach { type ->
                OptionRow(typeLabel(resources, field.kind, type, null)) {
                    onChange { it.copy(type = type, label = null) }
                    close()
                }
            }
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val highlight = rememberBb10Highlight()
    val label = stringResource(R.string.remove_field)
    Box(
        Modifier
            .size(Bb10.dimens.minTouchTarget)
            .semantics { contentDescription = label }
            .bb10Clickable(role = Role.Button, highlight = highlight, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Bb10OnHighlight(highlight) { Bb10Icon(Bb10Icons.Close) }
    }
}

@Composable
private fun AddFieldDialog(options: List<FieldOption>, onPick: (FieldOption) -> Unit, onDismiss: () -> Unit) {
    Bb10Dialog(
        title = stringResource(R.string.add_field),
        confirmText = stringResource(R.string.cancel),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = AddFieldListHeight).bb10VerticalScroll(rememberScrollState())) {
            options.forEach { option ->
                OptionRow(stringResource(option.label)) { onPick(option) }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, onClick: () -> Unit) {
    val d = Bb10.dimens
    val highlight = rememberBb10Highlight()
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = d.minTouchTarget)
                .bb10Clickable(role = Role.Button, highlight = highlight, flat = true, onClick = onClick)
                .padding(horizontal = d.spaceL),
            contentAlignment = Alignment.CenterStart,
        ) {
            Bb10OnHighlight(highlight) {
                Bb10Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Bb10Divider()
    }
}
