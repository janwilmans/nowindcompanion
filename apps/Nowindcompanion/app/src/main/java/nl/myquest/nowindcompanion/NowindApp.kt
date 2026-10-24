package nl.myquest.nowindcompanion

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// owns everything that must live as long as the process, independent of activity re-creation
class NowindApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val state = HostState()
    lateinit var storage: StorageFolder
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var client: FTDIClient
        private set
    lateinit var library: DiskLibrary
        private set
    lateinit var firmware: FirmwareUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        storage = StorageFolder(this)
        settings = SettingsRepository(this)
        client = FTDIClient(this, state)
        library = DiskLibrary(storage, appScope, client, state, settings)
        client.deviceFiles = StorageDeviceFiles(this, storage)
        firmware = FirmwareUpdater(this, appScope, client, settings, storage)

        appScope.launch {
            // apply the stored settings before hosting starts, so 'debug information off' is honored from the start
            applySettings(settings.settings.first())
            client.start()
            settings.settings.collect { applySettings(it) }
        }

        // at startup, or once the user granted access to the storage folder (first run, after a reinstall)
        appScope.launch(Dispatchers.IO) {
            storage.root.collectLatest { root ->
                try {
                    library.setFolder(root?.folder("disks"))
                } catch (e: Exception) {
                    Debug.error("storage folder: $e")
                    library.setFolder(null)
                    return@collectLatest
                }
                if (root == null) return@collectLatest
                // after a reinstall this brings back the settings, including the last hosted disk
                settings.restoreFrom(storage, root)
                launch { settings.saveTo(storage, root) }
                val hosted = settings.settings.first().hostedDisk
                if (hosted != null && state.hostedDisk.value == null) library.hostByName(hosted)
            }
        }

        // a connectedDevice foreground service may only run while the interface is connected (Play policy,
        // and Android 14+ refuses to start it without access to the USB device)
        appScope.launch {
            combine(settings.settings, state.connection) { value, connection ->
                value.keepActive && connection == ConnectionState.Hosting
            }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) HostingService.start(this@NowindApp) else HostingService.stop(this@NowindApp)
                }
        }
    }

    // FTDIClient reads these from its own thread, so mirror the persisted values into volatile fields
    private fun applySettings(value: Settings) {
        Debug.enabled = value.debugEnabled
        client.enableDos2 = value.enableDos2
    }
}

val Context.nowindApp: NowindApp
    get() = applicationContext as NowindApp
