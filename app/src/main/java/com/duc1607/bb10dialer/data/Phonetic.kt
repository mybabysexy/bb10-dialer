package com.duc1607.bb10dialer.data

import android.icu.text.Transliterator
import androidx.compose.runtime.Immutable

/**
 * A Chinese name in Latin letters, so that it can be found by typing: [full] is every syllable run
 * together, "zhangsan" for 张三, and [initials] their first letters, "zs".
 */
@Immutable
data class Phonetic(val full: String, val initials: String) {
    /** The query is folded already. Spaces between syllables do not matter. */
    fun matches(query: String): Boolean {
        val q = query.filterNot { it.isWhitespace() }
        return q.isNotEmpty() && (full.contains(q) || initials.startsWith(q))
    }
}

private fun Char.isHan(): Boolean = Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN

/**
 * Null for a name with no Chinese in it, which is found by its own letters. Each Chinese character is
 * one syllable, from [romanize]; a run of other letters or digits is a word of its own.
 */
fun phoneticOf(name: String, romanize: (String) -> String): Phonetic? {
    if (name.none { it.isHan() }) return null
    val syllables = mutableListOf<String>()
    val word = StringBuilder()
    fun endWord() {
        if (word.isNotEmpty()) syllables += word.toString().folded()
        word.clear()
    }
    for (char in name) {
        when {
            char.isHan() -> {
                endWord()
                val syllable = romanize(char.toString()).folded().filter { it.isLetter() }
                if (syllable.isNotEmpty()) syllables += syllable
            }
            char.isLetterOrDigit() -> word.append(char)
            else -> endWord()
        }
    }
    endWord()
    if (syllables.isEmpty()) return null
    return Phonetic(syllables.joinToString(""), syllables.joinToString("") { it.take(1) })
}

/**
 * Android's own pinyin, the one its contacts use: surnames are read the way names are. Off the main
 * thread, as the contacts are loaded.
 */
object HanRomanizer {
    private val transliterator by lazy { runCatching { Transliterator.getInstance("Han-Latin/Names; Latin-ASCII") }.getOrNull() }

    @Synchronized
    fun romanize(text: String): String = transliterator?.let { runCatching { it.transliterate(text) }.getOrNull() }.orEmpty()
}
