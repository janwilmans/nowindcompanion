package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class DiskSourcesTest {
    private fun resource(name: String) = javaClass.getResource(name)!!.readText()

    @Test
    fun detects_the_nowind_api() {
        val body = "0 Ogro (1985)(unknown)\t368640\n1 Level Only Game (1993)(Mint Soft)(ja)\t737280\n"
        assertEquals(SourceKind.NowindApi, classifySource(body, defaultSource("example").url).kind)
    }

    @Test
    fun detects_a_directory_listing() {
        val url = "https://download.file-hunter.com/Games/MSX2/DSK/"
        assertEquals(SourceKind.DirectoryListing, classifySource(resource("/filehunter-listing.html"), url).kind)
    }

    @Test
    fun detects_a_web_page_with_links() {
        val url = "http://floppy.disk.free.fr/Sources/games_msx.htm"
        assertEquals(SourceKind.WebPage, classifySource(resource("/linkpage-sample.html"), url).kind)
    }

    @Test(expected = java.io.IOException::class)
    fun rejects_other_content() {
        classifySource("just some text", "https://example.com/readme.txt")
    }

    @Test
    fun sources_survive_storage() {
        val sources = listOf(
            DiskSource("https://download.file-hunter.com/Games/MSX2/DSK/", SourceKind.DirectoryListing, enabled = true),
            DiskSource("http://floppy.disk.free.fr/Sources/games_msx.htm", SourceKind.WebPage, enabled = false),
        )
        assertEquals(sources, decodeSources(encodeSources(sources)))
        assertEquals(emptyList<DiskSource>(), decodeSources(null))
    }

    @Test
    fun urls_without_scheme_get_https() {
        assertEquals("https://example.com/disks/", normalizeSourceUrl(" example.com/disks/ "))
        assertEquals("http://example.com/", normalizeSourceUrl("http://example.com/"))
    }

    @Test
    fun a_name_without_dots_is_a_nowind_api() {
        assertEquals("https://api.example-site.com/nowind.php", expandSourceName(" example-site "))
        assertEquals("https://api.example.com/nowind.php", defaultSource("example").url)
        assertEquals("https://example.com/", expandSourceName("example.com/"))
        assertEquals("https://my-site.com/disks", expandSourceName("my-site.com/disks"))
        assertEquals("http://localhost/disks", expandSourceName("http://localhost/disks"))
    }

    @Test
    fun the_default_source_is_off_until_a_name_is_entered() {
        assertEquals(false, defaultSource("  ").enabled)
        assertEquals(true, defaultSource("example").enabled)
    }

    @Test
    fun labels() {
        assertEquals("default", defaultSource("example").label)
        assertEquals("download.file-hunter.com/Games/MSX2+/DSK", DiskSource("https://download.file-hunter.com/Games/MSX2+/DSK/", SourceKind.DirectoryListing).label)
        assertEquals("msxarchive.nl/pub/msx/mep-mirror/Games", DiskSource("https://www.msxarchive.nl/pub/msx/mep-mirror/Games/", SourceKind.DirectoryListing).label)
    }
}
