package com.duc1607.bb10dialer.data

import androidx.compose.runtime.Immutable

/** What a contact can hold, in the order the card shows them. */
enum class FieldKind { Phone, Email, Website, Address, Organization, Birthday, Relation, Nickname, Label, Note }

/**
 * One piece of a contact. [value] is the number, address, note...; an organization's job goes in
 * [title]. [type] and [label] are the system's "Mobile" / "Work" / custom label. [dataId] and
 * [rawContactId] say where the row lives, and are null for one that was not saved yet.
 */
@Immutable
data class ContactField(
    val kind: FieldKind,
    val value: String,
    val type: Int = 0,
    val label: String? = null,
    val title: String = "",
    val dataId: Long? = null,
    val rawContactId: Long? = null,
) {
    val hasContent: Boolean get() = value.isNotBlank() || title.isNotBlank()
}

/** Where a contact is saved. Null [type] and [name] mean only on this device. */
@Immutable
data class ContactAccount(val type: String?, val name: String?)

/** One of the raw contacts that the system links into a contact. */
@Immutable
data class RawContactRef(val id: Long, val account: ContactAccount, val readOnly: Boolean)

/** Everything about one saved contact. */
@Immutable
data class ContactData(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val nameDataId: Long?,
    val fields: List<ContactField>,
    val rawContacts: List<RawContactRef>,
) {
    /** New rows go here. Null when every source of this contact is read-only. */
    val editableRaw: RawContactRef? get() = rawContacts.firstOrNull { !it.readOnly }

    /** The rows that can be changed, leaving those of a read-only source out. */
    val editableFields: List<ContactField>
        get() = fields.filter { field -> rawContacts.any { it.id == field.rawContactId && !it.readOnly } }

    /** Linked sources often repeat a row: the card shows it once. */
    val uniqueFields: List<ContactField>
        get() = fields.distinctBy { field ->
            val plain = if (field.kind == FieldKind.Phone) field.value.filterNot { it in " -()." } else field.value.trim()
            Triple(field.kind, plain, field.title)
        }
}

/** A filled-in form. [contactId] is null for a new contact, which goes to [account]. */
class ContactDraft(
    val contactId: Long?,
    val account: ContactAccount,
    val firstName: String,
    val lastName: String,
    val fields: List<ContactField>,
    val favorite: Boolean,
    val photoUri: String?,
)

class FieldChanges(
    val inserts: List<ContactField>,
    val updates: List<ContactField>,
    val deletes: List<Long>,
)

/** What to write so that [original] becomes [edited]. A row emptied by the user is a deleted row. */
fun planFieldChanges(original: List<ContactField>, edited: List<ContactField>): FieldChanges {
    val kept = edited
        .map { it.copy(value = it.value.trim(), title = it.title.trim()) }
        .filter { it.hasContent }
    val byId = original.associateBy { it.dataId }
    val keptIds = kept.mapNotNull { it.dataId }.toSet()
    return FieldChanges(
        inserts = kept.filter { it.dataId == null },
        updates = kept.filter { it.dataId != null && byId[it.dataId] != it },
        deletes = original.mapNotNull { it.dataId }.filter { it !in keptIds },
    )
}
