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

    fun write(message: String) {
        println(message)
        messages.update { it.add(message) }
    }
}
