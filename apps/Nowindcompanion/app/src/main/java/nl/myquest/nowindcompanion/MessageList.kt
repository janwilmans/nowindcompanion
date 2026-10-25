package nl.myquest.nowindcompanion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val MAX_MESSAGES = 1000

// the last MAX_MESSAGES debug messages in a cyclic buffer: adding a message copies nothing.
// Written by the I/O thread, read by the UI; the UI watches version and takes a snapshot when it changes
class MessageLog(private val capacity: Int = MAX_MESSAGES) {
    private val buffer = arrayOfNulls<String>(capacity)
    private var start = 0       // index of the oldest message
    private var count = 0

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    @Synchronized
    fun add(message: String) {
        if (count < capacity) {
            buffer[(start + count) % capacity] = message
            count++
        } else {
            buffer[start] = message
            start = (start + 1) % capacity
        }
        _version.value++
    }

    // oldest first
    @Synchronized
    fun snapshot(): List<String> = List(count) { buffer[(start + it) % capacity]!! }
}
