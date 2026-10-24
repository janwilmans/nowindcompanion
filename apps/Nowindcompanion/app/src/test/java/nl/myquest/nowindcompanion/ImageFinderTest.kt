package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageFinderTest {
    private fun disk(name: String) = RemoteEntry(name, "https://x/$name", isDirectory = false, fromNowindApi = true, bytes = 737280)
    private fun rom(name: String, bytes: Long) = RemoteEntry(name, "https://x/$name", isDirectory = false, fromNowindApi = true, rom = true, bytes = bytes)

    private val archive = listOf(
        rom("Knightmare - Gradius Edition - Kona..(1986)[Enhanced v261010 by Toughkid]", 131072),
        rom("Knightmare - Majyo Densetsu (1986) Konami [9832]", 32768),
        disk("Knightmare. Majyo Densetsu (1986)(Konami)"),
        disk("AutoDemo & Cheat Twinbee & Knightmare"),
        RemoteEntry("Knightmare", "https://x/Knightmare/", isDirectory = true),
    )

    @Test
    fun the_msx_name_loses_its_extension() {
        assertEquals("KNIGHT", imageKeyword(" KNIGHT.DSK "))
        assertEquals("GAME", imageKeyword("GAME.rom"))
        assertEquals("NEMESIS 2", imageKeyword("NEMESIS 2"))
    }

    @Test
    fun names_that_start_with_the_keyword_and_are_shortest_come_first() {
        // the original release; 'AutoDemo & Cheat Twinbee & Knightmare' has the keyword too, but not at the start
        assertEquals("Knightmare. Majyo Densetsu (1986)(Konami)", findInArchive(archive, "KNIGHTMARE")?.name)
        assertEquals("Knightmare - Majyo Densetsu (1986) Konami [9832]", findInArchive(archive, "KNIGHTMARE KONAMI [9832]")?.name)
    }

    @Test
    fun megaroms_are_skipped() {
        // the shortest name, but at 128 KB the loader can not start it
        val withMegaRom = archive + rom("Knightmare", 131072)
        assertEquals("Knightmare. Majyo Densetsu (1986)(Konami)", findInArchive(withMegaRom, "KNIGHTMARE")?.name)
    }

    @Test
    fun all_keywords_rank_first() {
        assertEquals("AutoDemo & Cheat Twinbee & Knightmare", findInArchive(archive, "TWINBEE KNIGHTMARE")?.name)
    }

    @Test
    fun folders_and_unknown_names_find_nothing() {
        assertNull(findInArchive(archive, "GRADIUS 2"))
        assertNull(findInArchive(archive, "   "))
    }
}
