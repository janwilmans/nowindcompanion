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

        // FTDIClient reads these from its own thread, so mirror the persisted values into volatile fields
        appScope.launch {
            settings.settings.collect {
                Debug.enabled = it.debugEnabled
                client.enableDos2 = it.enableDos2
            }
        }
        // host the disk that was hosted last time
        appScope.launch {
            val hosted = settings.settings.first().hostedDisk ?: return@launch
            val file = File(disksDir, hosted)
            if (file.exists()) library.host(file)
        }
        client.start()
    }
}

val Context.nowindApp: NowindApp
    get() = applicationContext as NowindApp
