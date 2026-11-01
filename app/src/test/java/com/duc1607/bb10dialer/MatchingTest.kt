package com.duc1607.bb10dialer

import com.duc1607.bb10dialer.data.CallEntry
import com.duc1607.bb10dialer.data.CallKind
import com.duc1607.bb10dialer.data.Contact
import com.duc1607.bb10dialer.data.FAVORITES_SECTION
import com.duc1607.bb10dialer.data.PhoneNumber
import com.duc1607.bb10dialer.data.contactSections
import com.duc1607.bb10dialer.data.findTyped
import com.duc1607.bb10dialer.data.groupCalls
import com.duc1607.bb10dialer.data.indexLetter
import com.duc1607.bb10dialer.data.matchNumbers
import com.duc1607.bb10dialer.data.sameNumber
import com.duc1607.bb10dialer.data.searchContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class MatchingTest {

    private val duc = Contact(
        1, "Đức",
        listOf(
            PhoneNumber("0346697174", "Mobile"),
            PhoneNumber("0346697174@imsmnc004mcc4523gppnetworkorg", "Mobile"),
        ),
    )
    private val an = Contact(2, "An", listOf(PhoneNumber("+84 975 928 214", "Work")))
    private val service = Contact(3, "123 Service", listOf(PhoneNumber("1900", "Other")))
    private val contacts = listOf(duc, an, service)

    @Test
    fun `typed digits match inside any number`() {
        val matches = matchNumbers(contacts, "034")
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.contact == duc })
        assertEquals(0..2, matches[0].range)
    }

    @Test
    fun `match range skips separators`() {
        assertEquals(4..6, findTyped("+84 975 928", "975"))
        assertEquals(1..4, findTyped("+84 975 928", "849"))
        assertNull(findTyped("0346", "9"))
        assertNull(findTyped("0346", ""))
    }

    @Test
    fun `typed text is not a pattern`() {
        assertNull(findTyped("0346", "*"))
        assertEquals(1..1, findTyped("1*2", "*"))
    }

    @Test
    fun `same number ignores how the country code is written`() {
        assertTrue(sameNumber("0975928214", "+84 975 928 214"))
        assertFalse(sameNumber("0975928214", "0975928215"))
    }

    @Test
    fun `short codes only equal themselves`() {
        assertTrue(sameNumber("1900", "1900"))
        assertFalse(sameNumber("1900", "0341900"))
        assertFalse(sameNumber("", ""))
    }

    @Test
    fun `sections are by unaccented first letter with hash last`() {
        assertEquals("D", indexLetter("Đức"))
        assertEquals("#", indexLetter("123"))
        assertEquals(listOf("A", "D", "#"), contactSections(contacts).map { it.title })
    }

    @Test
    fun `favorites come first and only there`() {
        val zed = Contact(4, "Zed", emptyList(), favorite = true)
        val bao = Contact(5, "Bảo", emptyList(), favorite = true)
        val sections = contactSections(contacts + zed + bao)
        assertEquals(listOf(FAVORITES_SECTION, "A", "D", "#"), sections.map { it.title })
        assertEquals(listOf(bao, zed), sections[0].items)
        assertEquals(contacts.size + 2, sections.sumOf { it.items.size })
    }

    @Test
    fun `no favorites means no favorites section`() {
        assertFalse(contactSections(contacts).any { it.title == FAVORITES_SECTION })
    }

    @Test
    fun `search ignores accents and finds numbers`() {
        assertEquals(listOf(duc), searchContacts(contacts, "duc"))
        assertEquals(listOf(an), searchContacts(contacts, "975"))
        assertEquals(contacts, searchContacts(contacts, "  "))
    }

    @Test
    fun `only neighbouring calls of one number kind and day group`() {
        val day = 86_400_000L
        fun call(id: Long, number: String, date: Long, kind: CallKind = CallKind.Outgoing) =
            CallEntry(id, number, date, 0, kind)

        val noon = 5 * day + day / 2
        val groups = groupCalls(
            listOf(
                call(1, "A", noon),
                call(2, "A", noon - 1000),
                call(3, "A", noon - 2000, CallKind.Missed),
                call(4, "B", noon - 3000),
                call(5, "A", noon - 4000),
                call(6, "A", 4 * day),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(2, 1, 1, 1, 1), groups.map { it.count })
        assertEquals(1L, groups[0].latest.id)
    }
}
