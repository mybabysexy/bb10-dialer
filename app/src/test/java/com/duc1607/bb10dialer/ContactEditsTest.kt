package com.duc1607.bb10dialer

import com.duc1607.bb10dialer.data.ContactAccount
import com.duc1607.bb10dialer.data.ContactData
import com.duc1607.bb10dialer.data.ContactField
import com.duc1607.bb10dialer.data.FieldKind
import com.duc1607.bb10dialer.data.RawContactRef
import com.duc1607.bb10dialer.data.planFieldChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEditsTest {

    private val phone = ContactField(FieldKind.Phone, "0123", type = 2, dataId = 1, rawContactId = 10)
    private val note = ContactField(FieldKind.Note, "Hi", dataId = 2, rawContactId = 10)

    @Test
    fun `nothing changed writes nothing`() {
        val changes = planFieldChanges(listOf(phone, note), listOf(note, phone))
        assertTrue(changes.inserts.isEmpty() && changes.updates.isEmpty() && changes.deletes.isEmpty())
    }

    @Test
    fun `removed and emptied rows are deleted`() {
        val changes = planFieldChanges(listOf(phone, note), listOf(phone.copy(value = "  ")))
        assertEquals(listOf(1L, 2L), changes.deletes)
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun `new rows insert and blank new rows are dropped`() {
        val fresh = ContactField(FieldKind.Email, " a@b.c ")
        val blank = ContactField(FieldKind.Phone, "")
        val changes = planFieldChanges(listOf(phone), listOf(phone, fresh, blank))
        assertEquals(listOf(fresh.copy(value = "a@b.c")), changes.inserts)
    }

    @Test
    fun `changing a type or a value updates the row`() {
        val changes = planFieldChanges(listOf(phone), listOf(phone.copy(type = 3)))
        assertEquals(listOf(phone.copy(type = 3)), changes.updates)
    }

    @Test
    fun `a company without a name but with a job is kept`() {
        val job = ContactField(FieldKind.Organization, "", title = "Dev")
        assertEquals(listOf(job), planFieldChanges(emptyList(), listOf(job)).inserts)
    }

    @Test
    fun `rows of a read-only source are left out of editing and repeats out of the card`() {
        val local = RawContactRef(10, ContactAccount(null, null), readOnly = false)
        val app = RawContactRef(11, ContactAccount("app", "me"), readOnly = true)
        val copy = ContactField(FieldKind.Phone, "01 23", dataId = 3, rawContactId = 11)
        val data = ContactData(1, "A", "B", null, listOf(phone, copy), listOf(local, app))
        assertEquals(local, data.editableRaw)
        assertEquals(listOf(phone), data.editableFields)
        assertEquals(listOf(phone), data.uniqueFields)
    }
}
