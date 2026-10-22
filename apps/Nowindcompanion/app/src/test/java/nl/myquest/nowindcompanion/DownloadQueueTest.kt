package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueTest {
    private fun entry(name: String) = RemoteEntry(name, "https://example/$name", isDirectory = false)

    @Test
    fun downloads_in_queue_order_without_duplicates() {
        val queue = DownloadQueue()
        assertTrue(queue.enqueue(entry("a.zip")))
        assertTrue(queue.enqueue(entry("b.zip")))
        assertFalse(queue.enqueue(entry("a.zip")))
        assertEquals("a.zip", queue.next()!!.entry.name)
        assertEquals("b.zip", queue.next()!!.entry.name)
        assertNull(queue.next())
    }

    @Test
    fun download_and_host_jumps_the_queue() {
        val queue = DownloadQueue()
        queue.enqueue(entry("a.zip"))
        queue.enqueue(entry("b.zip"))
        queue.enqueueFirst(entry("b.zip"))
        val first = queue.next()!!
        assertEquals("b.zip", first.entry.name)
        assertTrue(first.hostWhenDone)
        assertEquals(listOf("a.zip"), queue.pending.map { it.entry.name })
    }

    @Test
    fun removed_entries_are_not_downloaded() {
        val queue = DownloadQueue()
        queue.enqueue(entry("a.zip"))
        queue.remove("https://example/a.zip")
        assertNull(queue.next())
    }
}
