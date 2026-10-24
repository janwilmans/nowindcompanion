package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHelpersTest {

    @Test
    fun strip_quotes() {
        assertEquals("foo", stripQuotes("pre(\"foo\")bar"))
        assertEquals("GAME.DSK", stripQuotes("GAME.DSK"))
        assertEquals("", stripQuotes("\"half"))
    }

    @Test
    fun now_map() {
        val listing = nowMap("/L", 1, "game.dsk")
        assertTrue(listing.contains("Drive A: DiskRom"))
        assertTrue(listing.contains("Drive B, driveId 0 (game.dsk)"))
        assertTrue(nowMap("0 1", 0, null).startsWith("error: drive is not a hdd!"))
        assertTrue(nowMap("5 1", 0, null).startsWith("error: drive_id invalid!"))
        assertTrue(nowMap("0", 0, null).startsWith("error: no 2 arguments found"))
    }
}
