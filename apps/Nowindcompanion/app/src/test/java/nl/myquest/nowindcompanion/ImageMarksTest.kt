package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMarksTest {
    @Test
    fun the_image_setting_overrides_the_default() {
        val settings = Settings(enableDos2 = true, imageDos2 = mapOf("Puyo Puyo (1991)(Compile).dsk" to false))
        assertFalse(settings.dos2For("Puyo Puyo (1991)(Compile).dsk"))
        assertTrue(settings.dos2For("Aleste 2 (1988)(Compile)(en)(Disk1of3)(Woomb).dsk"))
        assertTrue(settings.dos2For(null))
        assertFalse(Settings(enableDos2 = false).dos2For("Aleste 2.dsk"))
        assertTrue(Settings(enableDos2 = false, imageDos2 = mapOf("Aleste 2.dsk" to true)).dos2For("Aleste 2.dsk"))
    }

    @Test
    fun marks_survive_encoding() {
        val names = setOf("Knightmare - Majyo Densetsu (1986) Konami [9832].rom", "Puyo Puyo (1991)(Compile).dsk")
        assertEquals(names, decodeNames(encodeNames(names)))
        assertEquals(emptySet<String>(), decodeNames(null))

        val dos2 = mapOf("Puyo Puyo (1991)(Compile).dsk" to false, "Aleste 2.dsk" to true)
        assertEquals(dos2, decodeImageDos2(encodeImageDos2(dos2)))
        assertEquals(emptyMap<String, Boolean>(), decodeImageDos2("garbage\n2\tx\n"))
    }
}
