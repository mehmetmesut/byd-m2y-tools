// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailDomainCompletionsTest {
    @Test fun allDomainsAfterAt() = assertEquals(
        listOf("ali@gmail.com", "ali@hotmail.com", "ali@outlook.com", "ali@icloud.com", "ali@yahoo.com", "ali@yandex.com"),
        emailDomainCompletions("ali@"))

    @Test fun filtersByDomainPrefix() = assertEquals(listOf("ali@yahoo.com", "ali@yandex.com"), emailDomainCompletions("ali@ya"))

    @Test fun prefixIsCaseInsensitive() = assertEquals(listOf("Ali@hotmail.com"), emailDomainCompletions("Ali@HO"))

    @Test fun completeDomainIsNotRepeated() = assertEquals(emptyList<String>(), emailDomainCompletions("ali@gmail.com"))

    @Test fun noMailNoCompletions() {
        assertEquals(emptyList<String>(), emailDomainCompletions("ali"))
        assertEquals(emptyList<String>(), emailDomainCompletions("@gm"))
        assertEquals(emptyList<String>(), emailDomainCompletions("a@b@g"))
    }
}
