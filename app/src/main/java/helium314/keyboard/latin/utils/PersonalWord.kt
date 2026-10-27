// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.provider.UserDictionary
import com.android.inputmethod.latin.BinaryDictionary

/** M2Y Board: the toolbar key that adds a word (name, e-mail…) to the personal dictionary in one tap. */
object PersonalWord {
    private const val WEIGHT = 250 // same weight as words added in the personal dictionary editor
    private const val EDGE_PUNCTUATION = ".,;:!?()\"'"
    private val WHITESPACE = Regex("\\s+")

    /** The selected text if there is one, otherwise the last whitespace-separated word before the cursor. */
    @JvmStatic
    fun wordToAdd(selected: CharSequence?, beforeCursor: CharSequence?): String? {
        val word = (if (!selected.isNullOrBlank()) selected.toString()
            else beforeCursor?.toString()?.split(WHITESPACE)?.lastOrNull { it.isNotEmpty() })
            ?.trim { it.isWhitespace() || it in EDGE_PUNCTUATION }
        return word?.takeIf { it.isNotEmpty() && it.length <= BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH && '\n' !in it }
    }

    /** Adds [word] for all languages; false if it is already there (in any language). Queries a content provider:
     *  call off the UI thread. Synchronized so two quick taps cannot add the same word twice. */
    @JvmStatic
    @Synchronized
    fun add(context: Context, word: String): Boolean {
        context.contentResolver.query(
            UserDictionary.Words.CONTENT_URI, arrayOf(UserDictionary.Words.WORD),
            "${UserDictionary.Words.WORD}=?", arrayOf(word), null
        )?.use { if (it.count > 0) return false }
        UserDictionary.Words.addWord(context, word, WEIGHT, null, null)
        return true
    }
}
