package nl.myquest.nowindcompanion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class ConnectionState(val label: String) {
    Stopped("Stopped"),
    NoUsbHost("USB host not supported"),
    Searching("Searching for interface"),
    OpenFailed("Could not open interface"),
    Hosting("Connected"),
}

// state shared between the FTDIClient I/O thread and the UI, owned by NowindApp so it outlives the activity
class HostState {
    val messages = MutableStateFlow(MessageList())
    val deviceInfo = MutableStateFlow(DeviceInfo())
    val connection = MutableStateFlow(ConnectionState.Stopped)
    val hostedDisk = MutableStateFlow<String?>(null)
    val readStats = MutableStateFlow(TransferStats())
    val writeStats = MutableStateFlow(TransferStats())
    val recentReads = RecentTransfers()
    // counts the commands received from the MSX, the activity light blinks when it changes
    val commands = MutableStateFlow(0L)

    fun resetStats() {
        readStats.value = TransferStats()
        writeStats.value = TransferStats()
        recentReads.clear()
    }

    fun write(message: String) {
        if (!Debug.enabled) return
        println(message)
        messages.update { it.add(message) }
    }
}
