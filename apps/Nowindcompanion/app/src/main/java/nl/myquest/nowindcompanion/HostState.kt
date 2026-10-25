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

// 'boot without disk': the MSX gets no disk for one boot, the boot after that finds it again
enum class SkipDisk {
    Off,
    NextBoot,   // armed, the next boot gets no disk
    ThisBoot,   // the MSX booted without disk, until the next boot
}

// what a boot of the MSX does with it: an armed skip takes effect, anything else ends
fun SkipDisk.atBoot(): SkipDisk = if (this == SkipDisk.NextBoot) SkipDisk.ThisBoot else SkipDisk.Off

// state shared between the FTDIClient I/O thread and the UI, owned by NowindApp so it outlives the activity
class HostState {
    val messages = MessageLog()
    val deviceInfo = MutableStateFlow(DeviceInfo())
    val connection = MutableStateFlow(ConnectionState.Stopped)
    val hostedDisk = MutableStateFlow<String?>(null)
    val readStats = MutableStateFlow(TransferStats())
    val writeStats = MutableStateFlow(TransferStats())
    val recentReads = RecentTransfers()
    // counts the commands received from the MSX, the activity light blinks when it changes
    val commands = MutableStateFlow(0L)
    val skipDisk = MutableStateFlow(SkipDisk.Off)

    fun resetStats() {
        readStats.value = TransferStats()
        writeStats.value = TransferStats()
        recentReads.clear()
    }

    fun write(message: String) {
        if (!Debug.enabled) return
        println(message)
        messages.add(message)
    }
}
