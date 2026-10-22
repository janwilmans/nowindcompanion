package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class TransferStatsTest {
    private val second = 1_000_000_000L

    // a transfer of 'kb' kilobytes that took one second
    private fun TransferStats.add(kb: Int) = add(kb * 1024L, second)

    @Test
    fun average_maximum_and_sample_standard_deviation() {
        val stats = TransferStats().add(2).add(4).add(4).add(4).add(5).add(5).add(7).add(9)
        assertEquals(8, stats.count)
        assertEquals(40 * 1024L, stats.totalBytes)
        assertEquals(5.0, stats.mean, 1e-9)
        assertEquals(9.0, stats.maximum, 1e-9)
        // squared differences from the mean sum to 32, the sample variance is 32 / 7
        assertEquals(sqrt(32.0 / 7.0), stats.standardDeviation!!, 1e-9)
    }

    @Test
    fun speed_is_kilobytes_per_second() {
        val stats = TransferStats().add(36 * 1024L, second / 2)
        assertEquals(72.0, stats.mean, 1e-9)
    }

    @Test
    fun no_deviation_for_a_single_transfer() {
        assertNull(TransferStats().add(10).standardDeviation)
        assertNull(TransferStats().standardDeviation)
    }

    @Test
    fun empty_transfers_are_ignored() {
        assertEquals(TransferStats(), TransferStats().add(0, second).add(1024, 0))
    }
}
