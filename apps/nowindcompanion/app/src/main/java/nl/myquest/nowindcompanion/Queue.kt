package nl.myquest.nowindcompanion

import kotlinx.coroutines.yield
import java.util.LinkedList

class Queue(
    private val queue: LinkedList<Int> = LinkedList<Int>()
) {

    public suspend fun add(data: ByteArray) {
        for (value in data) {
            queue.add(value.toInt() and 0xff)
        }
        yield()
    }

    public fun readByte(): Int {
        return queue.pop()
    }

    public fun readWord(): Int {
        val low = queue.pop()
        return (queue.pop() * 256) + low
    }

    public suspend fun waitFor(value: Int) {
        while (true) {
            val readValue: Int? = queue.poll()
            if (readValue != null && readValue == value)
                return;
            yield()
        }
    }

    public suspend fun waitForBytes(size: Int) {
        while (true) {
            if (queue.size == size) {
                return
            }
            yield()
        }
    }
}
