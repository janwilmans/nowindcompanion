package nl.myquest.nowindcompanion

import nl.myquest.nowindcompanion.ui.formatD2xxVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class D2xxVersionTest {
    @Test
    fun formats_hex_digits() {
        assertEquals("2.10.00", formatD2xxVersion(0x21000000))
        assertEquals("2.13.03", formatD2xxVersion(0x21303000))
    }
}
