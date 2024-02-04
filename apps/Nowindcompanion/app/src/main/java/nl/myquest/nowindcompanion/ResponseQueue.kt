package nl.myquest.nowindcompanion

import kotlinx.coroutines.yield
import java.io.IOException
import java.util.LinkedList

class ResponseQueue(
    private val queue: LinkedList<Int> = LinkedList<Int>()
) {

    public fun addHeader() {
        // leading 0xff required because the first read can fail (due to a hardware design choice!)
        add(listOf(0xff, 0xaf, 0x05))
    }

    public fun add(value: Int) {
        queue.add(value)
    }

    public fun add(data: List<Int>) {
        for (value in data) {
            queue.add(value)
        }
    }

    public fun clear() {
        queue.clear()
    }

    public fun size(): Int {
        return queue.size
    }
}
