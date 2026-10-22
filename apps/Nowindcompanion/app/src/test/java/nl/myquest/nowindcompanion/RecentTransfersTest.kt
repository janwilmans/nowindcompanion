package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentTransfersTest {
    private val second = 1_000_000_000L

    @Test
    fun counts_only_the_time_spent_transferring() {
        val recent = RecentTransfers(windowNanos = 20 * second)
        recent.add(100 * 1024L, second / 2, now = 1 * second)
        // ten seconds of nothing in between does not lower the speed
        recent.add(300 * 1024L, second * 3 / 2, now = 11 * second)
        assertEquals(200.0, recent.activeSpeed(now = 12 * second)!!, 1e-9)
    }

    @Test
    fun transfers_older_than_the_window_are_forgotten() {
        val recent = RecentTransfers(windowNanos = 20 * second)
        recent.add(100 * 1024L, second, now = 1 * second)
        recent.add(50 * 1024L, second, now = 15 * second)
        assertEquals(75.0, recent.activeSpeed(now = 20 * second)!!, 1e-9)
        assertEquals(50.0, recent.activeSpeed(now = 22 * second)!!, 1e-9)
        assertNull(recent.activeSpeed(now = 36 * second))
    }
}
