package nl.myquest.nowindcompanion

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

// owns everything that must live as long as the process, independent of activity re-creation
class NowindApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val state = HostState()
    lateinit var settings: SettingsRepository
        private set
    lateinit var client: FTDIClient
        private set
    lateinit var library: DiskLibrary
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        client = FTDIClient(this, state)
        val disksDir = getExternalFilesDir("disks") ?: File(filesDir, "disks")
        library = DiskLibrary(disksDir, appScope, client, state, settings)

        appScope.launch {
            // apply the stored settings before hosting starts, so 'debug information off' is honored from the start
            val initial = settings.settings.first()
            applySettings(initial)
            initial.hostedDisk?.let { hosted ->
                val file = File(disksDir, hosted)
                if (file.length() > 0) library.host(file)
            }
            client.start()
            settings.settings.collect { applySettings(it) }
        }
    }

    private var keepActive = false

    // FTDIClient reads these from its own thread, so mirror the persisted values into volatile fields
    private fun applySettings(value: Settings) {
        Debug.enabled = value.debugEnabled
        client.enableDos2 = value.enableDos2
        if (value.keepActive != keepActive) {
            keepActive = value.keepActive
            if (keepActive) HostingService.start(this) else HostingService.stop(this)
        }
    }
}

val Context.nowindApp: NowindApp
    get() = applicationContext as NowindApp
