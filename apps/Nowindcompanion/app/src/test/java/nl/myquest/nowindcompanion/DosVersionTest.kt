package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class DosVersionTest {
    @Test
    fun dos1_when_dos2_disabled() {
        for (msx in MsxVersion.values()) {
            assertEquals(1, dosVersionFor(msx.value, enableDos2 = false))
        }
    }

    @Test
    fun dos2_only_on_msx2_and_newer() {
        assertEquals(1, dosVersionFor(MsxVersion.One.value, enableDos2 = true))
        assertEquals(2, dosVersionFor(MsxVersion.Two.value, enableDos2 = true))
        assertEquals(2, dosVersionFor(MsxVersion.TwoPlus.value, enableDos2 = true))
        assertEquals(2, dosVersionFor(MsxVersion.TurboR.value, enableDos2 = true))
    }
}
