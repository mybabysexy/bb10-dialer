package com.duc1607.bb10dialer.data

/** What was typed as a list entry: digits, with the "+" of an international prefix. */
fun blacklistEntry(typed: String): String {
    val digits = typed.filter { it.isDigit() }
    return if (digits.isNotEmpty() && typed.trim().startsWith("+")) "+$digits" else digits
}

/**
 * The ways a caller's number can be read. [written] is its digits as they came. [national] has no
 * trunk zero or country code and [international] has the country code; null when not known.
 */
class NumberForms(val written: String, val national: String?, val international: String?)

/**
 * Every entry is a prefix, and a whole number is a prefix of itself. "024" finds "0241234567" and
 * "+84241234567" alike. An entry of zeros alone says nothing about a national number.
 */
fun isBlacklisted(number: NumberForms, entries: List<String>): Boolean = entries.any { entry ->
    val digits = entry.filter { it.isDigit() }
    when {
        digits.isEmpty() -> false
        entry.startsWith("+") -> number.international?.startsWith(digits) == true
        number.written.startsWith(digits) -> true
        else -> {
            val national = digits.trimStart('0')
            national.isNotEmpty() && number.national?.startsWith(national) == true
        }
    }
}
