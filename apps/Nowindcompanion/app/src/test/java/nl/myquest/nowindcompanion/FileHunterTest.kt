package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileHunterTest {
    private val baseUrl = "https://download.file-hunter.com/Games/MSX2/DSK/"

    // a trimmed copy of a real directory index page
    private fun listing() = parseListing(
        javaClass.getResource("/filehunter-listing.html")!!.readText(),
        baseUrl
    )

    @Test
    fun parent_link_and_non_disk_files_are_skipped() {
        val names = listing().map { it.name }
        assertEquals(
            listOf(
                "1 Level Only Game (1993)(Mint Soft)(ja).zip",
                "1148PM Version 4 (1992)(Fantasy Creators)(jp).zip",
                "Archive",
            ),
            names
        )
    }

    @Test
    fun urls_are_absolute_and_sizes_parsed() {
        val first = listing().first()
        assertEquals("$baseUrl" + "1%20Level%20Only%20Game%20(1993)(Mint%20Soft)(ja).zip", first.url)
        assertEquals("273.61 KB", first.size)
        assertFalse(first.isDirectory)
        assertTrue(first.isDiskImage)
    }

    @Test
    fun directories_are_recognized() {
        val archive = listing().last()
        assertTrue(archive.isDirectory)
        assertEquals(baseUrl + "Archive/", archive.url)
        assertEquals("", archive.size)
    }

    @Test
    fun file_names_are_made_safe() {
        assertEquals("a_b_c.dsk", safeFileName("a/b:c.dsk"))
    }
}
