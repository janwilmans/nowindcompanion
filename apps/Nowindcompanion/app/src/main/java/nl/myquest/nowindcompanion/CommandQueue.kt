package nl.myquest.nowindcompanion

import kotlinx.coroutines.yield
import java.util.LinkedList
import java.util.concurrent.TimeoutException

class CommandQueue(
    private val queue: LinkedList<Int> = LinkedList<Int>()
) {

    override fun toString(): String {
        return toHexString(queue)
    }

    private var startOfTimeout = System.currentTimeMillis()
    fun restartTimeout() {
        startOfTimeout = System.currentTimeMillis()
    }

    private fun ReadTimeout(): Int {
        return 700
    }

    fun timeoutExipired(): Boolean {
        val duration = System.currentTimeMillis() - startOfTimeout
        return duration > ReadTimeout()
    }

    private fun checkTimeout(from: String) {
        if (timeoutExipired()) {
            throw TimeoutException("Nowind protocol timeout (${from}) after %dms".format(ReadTimeout()))
        }
    }

    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }

    fun clear() {
        queue.clear()
    }

    fun add(data: ByteArray) {
        for (value in data) {
            queue.add(value.toInt() and 0xff)
        }
    }

    public fun size(): Int {
        return queue.size
    }

    fun readByte(): Int {
        return queue.pop()
    }

    fun readWord(): Int {
        val low = queue.pop()
        return (queue.pop() * 256) + low
    }

    suspend fun next(): Int {
        while (true) {
            val readValue: Int? = queue.poll()
            if (readValue != null) {
                return readValue
            }
            checkTimeout("next")
            //println("next yielded...")
            yield()
        }
    }

    suspend fun waitFor(values: List<Int>) {
        require(values.isNotEmpty())    // can throw IllegalArgumentException
        restartTimeout()
        var discardedBytes = 0
        try {
            var index: Int = 0
            while (index < values.size) {
                //println("go for index %d for %X".format(index, values[index]))
                var readValue: Int = next()
                if (readValue == values[index]) {
                    //println("match %X".format(readValue))
                    index += 1
                    continue
                }

                // if we get here, the sequence failed and we restart from the beginning
                // and the current value has to be re-checked against the value at index 0
                //println("restart for %X".format(readValue))
                discardedBytes += (index + 1)
                if (readValue == values[0]) {
                    index = 1
                } else {
                    index = 0
                }
            }
            if (discardedBytes > 0) {
                println("waitFor discarded $discardedBytes bytes.")
            }
        } catch (e: TimeoutException) {
            throw TimeoutException(e.message + ", discarding $discardedBytes bytes")
        }
        //println("waitFor ${values} returned...")
    }

    suspend fun waitForBytes(size: Int) {
        var lastYieldSize = -1
        while (true) {
            if (queue.size >= size) {
                //println("waitForBytes returned immediately with ${size} bytes: ${queue}")
                return
            }

            if (queue.size != lastYieldSize) {
                //println("waitForBytes yielded at ${queue.size}/${size} bytes.")
                lastYieldSize = queue.size
            }
            yield()
        }
    }
}
