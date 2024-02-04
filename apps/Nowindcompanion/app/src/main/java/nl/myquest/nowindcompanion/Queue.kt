package nl.myquest.nowindcompanion

import kotlinx.coroutines.yield
import java.io.IOException
import java.util.LinkedList
import java.util.concurrent.TimeoutException

class Queue(
    private val queue: LinkedList<Int> = LinkedList<Int>()
) {

    private var startOfTimeout = System.currentTimeMillis()
    public fun restartTimeout() {
        startOfTimeout = System.currentTimeMillis()
    }

    public fun timeoutExipired(): Boolean {
        val duration = System.currentTimeMillis() - startOfTimeout
        return duration > 200
    }

    private fun checkTimeout() {
        if (timeoutExipired()) {
            throw TimeoutException("Nowind protocol timeout after 200ms")
        }
    }

    public suspend fun add(data: ByteArray) {
        for (value in data) {
            queue.add(value.toInt() and 0xff)
        }
        yield()
    }

    public fun size(): Int {
        return queue.size
    }

    public fun readByte(): Int {
        return queue.pop()
    }

    public fun readWord(): Int {
        val low = queue.pop()
        return (queue.pop() * 256) + low
    }

    public suspend fun waitFor(values: List<Int>) {
        assert(values.isNotEmpty())
        val first = values[0]
        val remaining = values.subList(1, values.size)
        while (true) {
            checkTimeout()
            val readValue: Int? = queue.poll()
            if (readValue != null && readValue == first)
                break
            yield()
        }
        // first value was found, now assume sequential values are an exact match
        for (value in remaining) {
            val readValue: Int? = queue.poll()
            if (readValue == null || readValue != value) {
                println("Nowind protocol error, sequence after %X not matched".format(first))
                throw IOException("Nowind protocol error, sequence after %X not matched".format(first))
            }
            yield()
        }
    }

    public suspend fun waitForBytes(size: Int) {
        while (true) {
            if (queue.size >= size) {
                return
            } else {
                if (queue.size > 0) {
                    println("waitForBytes yielded at %d bytes.".format(queue.size))
                }
            }
            yield()
        }
    }
}
