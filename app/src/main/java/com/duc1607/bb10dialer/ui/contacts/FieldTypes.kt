package com.duc1607.bb10dialer.ui.contacts

import android.content.res.Resources
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.annotation.StringRes
import com.duc1607.bb10dialer.R
import com.duc1607.bb10dialer.data.FieldKind

/** The types a kind offers in its drop-down. Empty for a kind that has none to choose. */
fun typesOf(kind: FieldKind): List<Int> = when (kind) {
    FieldKind.Phone -> listOf(
        Phone.TYPE_MOBILE, Phone.TYPE_WORK, Phone.TYPE_HOME, Phone.TYPE_MAIN,
        Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME, Phone.TYPE_PAGER, Phone.TYPE_OTHER,
    )
    FieldKind.Email -> listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_MOBILE, Email.TYPE_OTHER)
    FieldKind.Address -> listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER)
    FieldKind.Relation -> listOf(
        Relation.TYPE_SPOUSE, Relation.TYPE_PARTNER, Relation.TYPE_PARENT, Relation.TYPE_MOTHER,
        Relation.TYPE_FATHER, Relation.TYPE_CHILD, Relation.TYPE_BROTHER,
        Relation.TYPE_SISTER, Relation.TYPE_RELATIVE, Relation.TYPE_FRIEND, Relation.TYPE_ASSISTANT,
        Relation.TYPE_MANAGER,
    )
    else -> emptyList()
}

/** "Mobile", "Work", or the user's own label, in the device's language. */
fun typeLabel(resources: Resources, kind: FieldKind, type: Int, label: String?): String = when (kind) {
    FieldKind.Phone -> Phone.getTypeLabel(resources, type, label.orEmpty())
    FieldKind.Email -> Email.getTypeLabel(resources, type, label.orEmpty())
    FieldKind.Address -> StructuredPostal.getTypeLabel(resources, type, label.orEmpty())
    FieldKind.Relation -> Relation.getTypeLabel(resources, type, label.orEmpty())
    else -> ""
}.toString()

@StringRes
fun kindLabel(kind: FieldKind): Int = when (kind) {
    FieldKind.Phone -> R.string.field_phone
    FieldKind.Email -> R.string.field_email
    FieldKind.Website -> R.string.field_website
    FieldKind.Address -> R.string.field_address
    FieldKind.Organization -> R.string.field_company
    FieldKind.Birthday -> R.string.field_birthday
    FieldKind.Relation -> R.string.field_relation
    FieldKind.Nickname -> R.string.field_nickname
    FieldKind.Label -> R.string.field_label
    FieldKind.Note -> R.string.field_note
}
