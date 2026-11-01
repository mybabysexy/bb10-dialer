package com.duc1607.bb10dialer.data

import android.accounts.AccountManager
import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import com.duc1607.bb10dialer.telecom.simSlot
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Reads the system call log and contacts. Call these off the main thread. */
class DialerRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun loadCalls(): List<CallEntry> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val result = mutableListOf<CallEntry>()
        // Asking the phone takes a moment, and a log has few accounts in it.
        val slots = HashMap<Pair<String?, String?>, Int?>()
        resolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && result.size < MAX_CALLS) {
                val cachedType = c.getInt(6)
                val cachedLabel = c.getString(7)
                val account = c.getString(8) to c.getString(9)
                val slot = slots.getOrPut(account) { slotOf(account.first, account.second) }
                result += CallEntry(
                    id = c.getLong(0),
                    number = c.getString(1).orEmpty(),
                    dateMillis = c.getLong(2),
                    durationSeconds = c.getLong(3),
                    kind = callKind(c.getInt(4)),
                    cachedName = c.getString(5)?.takeIf { it.isNotBlank() },
                    cachedLabel = if (c.isNull(6)) null else phoneLabel(cachedType, cachedLabel),
                    slot = slot,
                )
            }
        }
        return result
    }

    fun loadContacts(): List<Contact> {
        val numbers = mutableMapOf<Long, MutableList<PhoneNumber>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val list = numbers.getOrPut(c.getLong(0)) { mutableListOf() }
                // Linked raw contacts often repeat a number, sometimes spaced differently.
                val plain = number.filterNot { it in NUMBER_SEPARATORS }
                if (list.none { it.number.filterNot { ch -> ch in NUMBER_SEPARATORS } == plain }) {
                    list += PhoneNumber(number, phoneLabel(c.getInt(2), c.getString(3)))
                }
            }
        }
        val contacts = mutableListOf<Contact>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME_PRIMARY,
                Contacts.PHOTO_THUMBNAIL_URI,
                Contacts.PHOTO_ID,
                Contacts.STARRED,
                Contacts.PHOTO_URI,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val phones = numbers[id].orEmpty()
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: phones.firstOrNull()?.number ?: continue
                val photo = c.getString(2)?.let { ContactPhoto(it, c.getLong(3)) }
                val largePhoto = c.getString(5)?.let { ContactPhoto(it, c.getLong(3)) }
                contacts += Contact(id, name, phones, photo, favorite = c.getInt(4) != 0, largePhoto = largePhoto)
            }
        }
        return contacts
    }

    /** All rows of one contact, or null when it is gone. */
    fun loadContactData(contactId: Long): ContactData? {
        val writable = writableAccountTypes()
        val accounts = mutableListOf<Pair<Long, ContactAccount>>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) accounts += c.getLong(0) to ContactAccount(c.getString(1), c.getString(2))
        }
        // A maker's own on-device account can look read-only: rather try than refuse the whole contact.
        val anyWritable = accounts.any { (_, account) -> account.type == null || account.type in writable }
        val rawContacts = accounts.map { (id, account) ->
            RawContactRef(id, account, readOnly = anyWritable && account.type != null && account.type !in writable)
        }
        if (rawContacts.isEmpty()) return null

        val groups = loadGroupTitles()
        val fields = mutableListOf<ContactField>()
        var firstName = ""
        var lastName = ""
        var nameDataId: Long? = null
        var nameRaw: RawContactRef? = null
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4),
            "${Data.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID}",
        )?.use { c ->
            while (c.moveToNext()) {
                val dataId = c.getLong(0)
                val rawId = c.getLong(1)
                val mimeType = c.getString(2)
                if (mimeType == StructuredName.CONTENT_ITEM_TYPE) {
                    // Linked sources each have a name: prefer one that can be edited.
                    val raw = rawContacts.firstOrNull { it.id == rawId }
                    if (nameDataId == null || (nameRaw?.readOnly == true && raw?.readOnly == false)) {
                        firstName = c.getString(4).orEmpty()
                        lastName = c.getString(5).orEmpty()
                        nameDataId = dataId
                        nameRaw = raw
                    }
                    continue
                }
                val kind = MIME_TO_KIND[mimeType] ?: continue
                val value = if (kind == FieldKind.Label) {
                    // System groups such as "My Contacts" are not labels the user gave.
                    groups[c.getLong(3)] ?: continue
                } else {
                    c.getString(3).orEmpty()
                }
                if (kind == FieldKind.Birthday && c.getInt(4) != Event.TYPE_BIRTHDAY) continue
                val field = ContactField(
                    kind = kind,
                    value = value,
                    type = if (kind == FieldKind.Label) 0 else c.getInt(4),
                    label = if (kind == FieldKind.Label) null else c.getString(5),
                    title = if (kind == FieldKind.Organization) c.getString(6).orEmpty() else "",
                    dataId = dataId,
                    rawContactId = rawId,
                )
                // Synced contacts can hold empty rows: nothing to show, and not ours to delete.
                if (field.hasContent) fields += field
            }
        }
        return ContactData(contactId, firstName, lastName, nameDataId, fields, rawContacts)
    }

    /** Where a new contact can go: this device, then the accounts that sync contacts back. */
    fun loadAccounts(): List<ContactAccount> {
        val writable = writableAccountTypes()
        val accounts = AccountManager.get(context).accounts
            .filter { it.type in writable }
            .map { ContactAccount(it.type, it.name) }
        return listOf(ContactAccount(null, null)) + accounts
    }

    /** The account types whose contacts sync back, so changes to them stick. */
    private fun writableAccountTypes(): Set<String> =
        ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }
            .toSet()

    /** Writes [draft] and returns the id of the contact. Throws when the system refuses. */
    fun saveContact(draft: ContactDraft): Long {
        val contactId = draft.contactId
        return if (contactId == null) insertContact(draft) else updateContact(contactId, draft)
    }

    private fun insertContact(draft: ContactDraft): Long {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, draft.account.type)
                .withValue(RawContacts.ACCOUNT_NAME, draft.account.name)
                .withValue(RawContacts.STARRED, if (draft.favorite) 1 else 0)
                .build()
        )
        if (draft.firstName.isNotBlank() || draft.lastName.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValues(nameValues(draft.firstName, draft.lastName))
                .build()
        }
        planFieldChanges(emptyList(), draft.fields).inserts.forEach { field ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValues(fieldValues(field, draft.account))
                .build()
        }
        val rawId = ContentUris.parseId(resolver.applyBatch(ContactsContract.AUTHORITY, ops)[0].uri!!)
        draft.photoUri?.let { writePhoto(rawId, it) }
        return contactIdOf(rawId)
    }

    private fun updateContact(contactId: Long, draft: ContactDraft): Long {
        val current = checkNotNull(loadContactData(contactId)) { "Contact $contactId is gone" }
        val target = checkNotNull(current.editableRaw) { "Contact $contactId is read-only" }
        val changes = planFieldChanges(current.editableFields, draft.fields)
        val ops = arrayListOf<ContentProviderOperation>()

        if (draft.firstName.trim() != current.firstName || draft.lastName.trim() != current.lastName) {
            val nameId = current.nameDataId
            ops += if (nameId != null) {
                ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, nameId))
                    .withValues(nameValues(draft.firstName, draft.lastName).apply { remove(Data.MIMETYPE) })
                    .build()
            } else {
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, target.id)
                    .withValues(nameValues(draft.firstName, draft.lastName))
                    .build()
            }
        }
        changes.deletes.forEach { id ->
            ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, id)).build()
        }
        changes.updates.forEach { field ->
            val account = current.rawContacts.firstOrNull { it.id == field.rawContactId }?.account ?: target.account
            ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, field.dataId!!))
                .withValues(fieldValues(field, account).apply { remove(Data.MIMETYPE) })
                .build()
        }
        changes.inserts.forEach { field ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, target.id)
                .withValues(fieldValues(field, target.account))
                .build()
        }
        ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId))
            .withValue(Contacts.STARRED, if (draft.favorite) 1 else 0)
            .build()
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        draft.photoUri?.let { writePhoto(target.id, it) }
        return contactIdOf(target.id)
    }

    fun deleteContact(contactId: Long) {
        resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), null, null)
    }

    private fun contactIdOf(rawId: Long): Long {
        val uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId)
        return resolver.query(uri, arrayOf(RawContacts.CONTACT_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: error("Raw contact $rawId is gone")
    }

    private fun nameValues(firstName: String, lastName: String) = ContentValues().apply {
        put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        put(StructuredName.GIVEN_NAME, firstName.trim().ifEmpty { null })
        put(StructuredName.FAMILY_NAME, lastName.trim().ifEmpty { null })
    }

    /** The columns of a row. Every kind keeps its text in DATA1, and most their type in DATA2. */
    private fun fieldValues(field: ContactField, account: ContactAccount) = ContentValues().apply {
        put(Data.MIMETYPE, KIND_TO_MIME.getValue(field.kind))
        when (field.kind) {
            FieldKind.Label -> put(GroupMembership.GROUP_ROW_ID, groupIdFor(field.value, account))
            FieldKind.Note, FieldKind.Nickname -> put(Data.DATA1, field.value)
            FieldKind.Website -> {
                put(Website.URL, field.value)
                put(Website.TYPE, field.type.takeIf { it != 0 || field.label != null } ?: Website.TYPE_HOMEPAGE)
                put(Website.LABEL, field.label)
            }
            FieldKind.Birthday -> {
                put(Event.START_DATE, field.value)
                put(Event.TYPE, Event.TYPE_BIRTHDAY)
            }
            FieldKind.Organization -> {
                put(Organization.COMPANY, field.value.ifEmpty { null })
                put(Organization.TITLE, field.title.ifEmpty { null })
                put(Organization.TYPE, field.type.takeIf { it != 0 || field.label != null } ?: Organization.TYPE_WORK)
                put(Organization.LABEL, field.label)
            }
            FieldKind.Phone, FieldKind.Email, FieldKind.Address, FieldKind.Relation -> {
                put(Data.DATA1, field.value)
                put(Data.DATA2, field.type)
                put(Data.DATA3, field.label)
            }
        }
    }

    /** Titles of the groups the user made, by id: not "My Contacts" or "Starred in Android". */
    private fun loadGroupTitles(): Map<Long, String> {
        val titles = mutableMapOf<Long, String>()
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE),
            "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.FAVORITES} = 0 AND ${Groups.AUTO_ADD} = 0",
            null, null,
        )?.use { c ->
            while (c.moveToNext()) c.getString(1)?.let { titles[c.getLong(0)] = it }
        }
        return titles
    }

    /** The account's group called [title], made if it does not exist yet. */
    private fun groupIdFor(title: String, account: ContactAccount): Long {
        val where = buildString {
            append("${Groups.TITLE} = ? AND ${Groups.DELETED} = 0 AND ")
            append(if (account.name == null) "${Groups.ACCOUNT_NAME} IS NULL" else "${Groups.ACCOUNT_NAME} = ?")
        }
        val args = listOfNotNull(title, account.name).toTypedArray()
        resolver.query(Groups.CONTENT_URI, arrayOf(Groups._ID), where, args, null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val values = ContentValues().apply {
            put(Groups.TITLE, title)
            put(Groups.ACCOUNT_TYPE, account.type)
            put(Groups.ACCOUNT_NAME, account.name)
        }
        return ContentUris.parseId(resolver.insert(Groups.CONTENT_URI, values)!!)
    }

    /** Scales the picture down to what a contact holds and stores it. */
    private fun writePhoto(rawId: Long, source: String) {
        val decoder = ImageDecoder.createSource(resolver, Uri.parse(source))
        val bitmap = ImageDecoder.decodeBitmap(decoder) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_PHOTO_SIZE) d.setTargetSampleSize(longest / MAX_PHOTO_SIZE)
        }
        val target = Uri.withAppendedPath(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
        )
        resolver.openAssetFileDescriptor(target, "rw")?.use { file ->
            file.createOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        }
    }

    /** The slots holding a usable SIM, counted from 1. Reading this takes no permission. */
    fun loadSims(): List<Int> {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        return (0 until telephony.activeModemCount)
            .filter { telephony.getSimState(it) == TelephonyManager.SIM_STATE_READY }
            .map { it + 1 }
    }

    /**
     * The SIMs in use, by slot: their numbers and carriers, as far as they are known. Many SIMs do
     * not know their own number, and without the phone permissions only the slots are known.
     */
    fun loadSimLines(): List<SimLine> {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
        val active = runCatching { subscriptions.activeSubscriptionInfoList }.getOrNull().orEmpty()
        return loadSims().map { slot ->
            val info = active.firstOrNull { it.simSlotIndex == slot - 1 }
            SimLine(
                slot = slot,
                number = info?.let { runCatching { subscriptions.getPhoneNumber(it.subscriptionId) }.getOrNull() }
                    ?.takeIf { it.isNotBlank() },
                carrier = info?.carrierName?.toString()?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** Counted from the call log, which only goes back so far: the first call found says how far. */
    fun loadCallSummary(fromMillis: Long): CallSummary {
        val seconds = LinkedHashMap<Int?, Long>()
        var oldest: Long? = null
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.DATE, CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, CallLog.Calls.PHONE_ACCOUNT_ID,
            ),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(fromMillis.toString()),
            "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                oldest = c.getLong(0)
                val slot = slotOf(c.getString(2), c.getString(3))
                seconds[slot] = (seconds[slot] ?: 0L) + c.getLong(1)
            }
        }
        return CallSummary(oldest ?: fromMillis.takeIf { it > 0 }, seconds.values.sum(), seconds.toList())
    }

    private fun slotOf(component: String?, id: String?): Int? {
        val name = component?.let(ComponentName::unflattenFromString)
        return if (name != null && id != null) simSlot(context, PhoneAccountHandle(name, id)) else null
    }

    fun clearCalls() {
        resolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }

    private fun phoneLabel(type: Int, label: String?): String =
        Phone.getTypeLabel(context.resources, type, label.orEmpty()).toString()

    private fun callKind(type: Int): CallKind = when (type) {
        CallLog.Calls.OUTGOING_TYPE -> CallKind.Outgoing
        CallLog.Calls.MISSED_TYPE -> CallKind.Missed
        CallLog.Calls.REJECTED_TYPE -> CallKind.Rejected
        CallLog.Calls.BLOCKED_TYPE -> CallKind.Blocked
        CallLog.Calls.VOICEMAIL_TYPE -> CallKind.Voicemail
        else -> CallKind.Incoming
    }

    private companion object {
        const val MAX_CALLS = 500
        const val MAX_PHOTO_SIZE = 1024
        const val PHOTO_QUALITY = 90

        val KIND_TO_MIME = mapOf(
            FieldKind.Phone to Phone.CONTENT_ITEM_TYPE,
            FieldKind.Email to Email.CONTENT_ITEM_TYPE,
            FieldKind.Website to Website.CONTENT_ITEM_TYPE,
            FieldKind.Address to StructuredPostal.CONTENT_ITEM_TYPE,
            FieldKind.Organization to Organization.CONTENT_ITEM_TYPE,
            FieldKind.Birthday to Event.CONTENT_ITEM_TYPE,
            FieldKind.Relation to Relation.CONTENT_ITEM_TYPE,
            FieldKind.Nickname to Nickname.CONTENT_ITEM_TYPE,
            FieldKind.Label to GroupMembership.CONTENT_ITEM_TYPE,
            FieldKind.Note to Note.CONTENT_ITEM_TYPE,
        )
        val MIME_TO_KIND = KIND_TO_MIME.entries.associate { (kind, mime) -> mime to kind }
        const val NUMBER_SEPARATORS = " -(). "
    }
}
