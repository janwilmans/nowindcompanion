package nl.myquest.nowindcompanion

import kotlin.math.sqrt

// Speed statistics over DSKIO transfers, one sample per transfer in KB/s.
// Uses Welford's running algorithm, so mean and variance are updated without keeping the samples.
data class TransferStats(
    val count: Int = 0,
    val totalBytes: Long = 0,
    val mean: Double = 0.0,
    private val m2: Double = 0.0,     // sum of squared differences from the mean
    val maximum: Double = 0.0,
) {
    fun add(bytes: Long, nanos: Long): TransferStats {
        if (bytes <= 0 || nanos <= 0) return this
        val speed = bytes / 1024.0 / (nanos / 1e9)
        val n = count + 1
        val delta = speed - mean
        val newMean = mean + delta / n
        return TransferStats(
            count = n,
            totalBytes = totalBytes + bytes,
            mean = newMean,
            m2 = m2 + delta * (speed - newMean),
            maximum = maxOf(maximum, speed),
        )
    }

    // sample standard deviation (n - 1), undefined for less than two transfers
    val standardDeviation: Double?
        get() = if (count >= 2) sqrt(m2 / (count - 1)) else null
}

fun formatSpeed(kbPerSecond: Double?): String =
    kbPerSecond?.let { "%.1f KB/s".format(it) } ?: "–"

// The transfers of the last seconds, for a live speed figure. Only the time spent transferring counts, so the
// speed does not drop while the MSX is busy with other things between reads. Thread-safe: filled by the
// hosting thread, read by the UI.
class RecentTransfers(private val windowNanos: Long = 20_000_000_000L) {
    private class Sample(val end: Long, val bytes: Long, val nanos: Long)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(bytes: Long, nanos: Long, now: Long = System.nanoTime()) {
        if (bytes <= 0 || nanos <= 0) return
        samples.addLast(Sample(now, bytes, nanos))
        prune(now)
    }

    // KB/s over the transfers that ended within the window, null when there were none
    @Synchronized
    fun activeSpeed(now: Long = System.nanoTime()): Double? {
        prune(now)
        val nanos = samples.sumOf { it.nanos }
        if (nanos <= 0) return null
        return samples.sumOf { it.bytes } / 1024.0 / (nanos / 1e9)
    }

    @Synchronized
    fun clear() = samples.clear()

    private fun prune(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().end > windowNanos) samples.removeFirst()
    }
}
