package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteListingTest {
    private val baseUrl = "https://download.file-hunter.com/Games/MSX2/DSK/"

    // a trimmed copy of a real directory index page
    private fun listing() = parseHtmlListing(
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

    @Test
    fun link_page_names_come_from_the_url() {
        val base = "http://floppy.disk.free.fr/Sources/games_msx.htm"
        val entries = parseHtmlListing(javaClass.getResource("/linkpage-sample.html")!!.readText(), base)
        assertEquals(listOf("1942-msx.zip", "antartic adventure-msx.zip"), entries.map { it.name })
        assertEquals("http://floppy.disk.free.fr/Sources/Roms/msx/1942-msx.zip", entries[0].url)
        assertTrue(entries.none { it.isDirectory })
        // the year in the same table row is not a size
        assertEquals("", entries[0].size)
    }

    @Test
    fun nowind_api_listing_downloads_by_index() {
        val url = "https://api.file-hunter.com/nowind.php?type=DSK&msx=2"
        val entries = parseNowindApiListing("Gorby vs Puyo (19xx)(Compile)\t368640\nPuyo Puyo (1991)(Compile)\t737280\n", url)
        assertEquals(listOf("Gorby vs Puyo (19xx)(Compile)", "Puyo Puyo (1991)(Compile)"), entries.map { it.name })
        assertEquals("$url&download=1", entries[1].url)
        assertEquals("Puyo Puyo (1991)(Compile).dsk", entries[1].fileName)
        assertTrue(entries[1].fromNowindApi)
        assertEquals(emptyList<RemoteEntry>(), parseNowindApiListing("No files found\t0\n", url))
    }

    @Test
    fun nowind_api_msx2plus_uses_a_literal_plus() {
        assertEquals("https://x/nowind.php?type=DSK&msx=2+", nowindApiListUrl("https://x/nowind.php", MsxFilter.Msx2Plus))
        assertEquals("https://x/nowind.php?type=DSK", nowindApiListUrl("https://x/nowind.php", MsxFilter.All))
        assertEquals("https://x/nowind.php?type=DSK&msx=turbo-r", nowindApiListUrl("https://x/nowind.php", MsxFilter.TurboR))
    }

    @Test
    fun nowind_api_download_header() {
        assertEquals(NowindApiHeader(1474560, 2), parseNowindApiHeader("size:1474560,disks:2,name:3 Wishes, the game (1988).dsk"))
        assertEquals(NowindApiHeader(null, 1), parseNowindApiHeader("garbage"))
    }

    @Test
    fun disk_sets_are_named_like_the_listing() {
        assertEquals(
            listOf("3 Wishes (1988)(jp)(Disk 1 of 2).dsk", "3 Wishes (1988)(jp)(Disk 2 of 2).dsk"),
            diskSetFileNames("3 Wishes (1988)(jp)(Disk 2 of 2)", 2)
        )
        assertEquals(listOf("Game (Disk 1 of 2).dsk", "Game (Disk 2 of 2).dsk"), diskSetFileNames("Game", 2))
    }
}
