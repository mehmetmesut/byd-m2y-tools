// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.utils.PersonalWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalWordTest {
    @Test fun `selection wins over the word before the cursor`() {
        assertEquals("Mehmet Mesut", PersonalWord.wordToAdd("  Mehmet Mesut ", "ali@hotmail.com"))
    }

    @Test fun `last word before the cursor without trailing punctuation`() {
        assertEquals("ornek@ornek.com", PersonalWord.wordToAdd(null, "e-postam ornek@ornek.com."))
        assertEquals("Ankara", PersonalWord.wordToAdd("", "Ankara, "))
        assertEquals("ali@ornek.com", PersonalWord.wordToAdd("(ali@ornek.com),", null))
    }

    @Test fun `nothing to add`() {
        assertNull(PersonalWord.wordToAdd(null, null))
        assertNull(PersonalWord.wordToAdd("  ", "   "))
        assertNull(PersonalWord.wordToAdd(null, "..."))
    }

    @Test fun `too long or multi-line text is not added`() {
        assertNull(PersonalWord.wordToAdd("a".repeat(49), null))
        assertNull(PersonalWord.wordToAdd("satır\nsatır", null))
    }
}
