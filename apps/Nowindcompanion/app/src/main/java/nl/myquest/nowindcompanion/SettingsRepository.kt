package nl.myquest.nowindcompanion

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private const val SETTINGS_FILE = "settings.json"

data class Settings(
    val enableDos2: Boolean = false,    // off by default, some games (Puyo Puyo) do not like the way DOS2 is skipped
    val debugEnabled: Boolean = BuildConfig.DEBUG,   // on while developing, off for users of the release build
    val keepActive: Boolean = false,    // foreground service + wake lock, so hosting continues in the background
    val hostedDisk: String? = null,     // file name in the disk library, restored at startup
    val defaultSourceEnabled: Boolean = true,
    val defaultSourceName: String = "",     // as entered in Settings, see expandSourceName; empty: no default source
    val defaultSourceKind: SourceKind = SourceKind.NowindApi,
    val sources: List<DiskSource> = emptyList(),    // added by the user, the default source is not in here
    val firmwareSource: String = "",    // as entered, see expandFirmwareName; empty: not set
    val firmwareErase: EraseMethod = EraseMethod.Chip,
) {
    // all places to find disk images, the built-in default first
    val allSources: List<DiskSource>
        get() = listOf(defaultSource(defaultSourceName, defaultSourceKind, defaultSourceEnabled)) + sources
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLE_DOS2 = booleanPreferencesKey("enable_dos2")
        val DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        val KEEP_ACTIVE = booleanPreferencesKey("keep_active")
        val HOSTED_DISK = stringPreferencesKey("hosted_disk")
        val DEFAULT_SOURCE_ENABLED = booleanPreferencesKey("default_source_enabled")
        val DEFAULT_SOURCE_NAME = stringPreferencesKey("default_source_name")
        val DEFAULT_SOURCE_KIND = stringPreferencesKey("default_source_kind")
        val SOURCES = stringPreferencesKey("sources")
        val FIRMWARE_SOURCE = stringPreferencesKey("firmware_source")
        val FIRMWARE_ERASE = stringPreferencesKey("firmware_erase")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        enableDos2 = this[Keys.ENABLE_DOS2] ?: false,
        debugEnabled = this[Keys.DEBUG_ENABLED] ?: BuildConfig.DEBUG,
        keepActive = this[Keys.KEEP_ACTIVE] ?: false,
        hostedDisk = this[Keys.HOSTED_DISK],
        defaultSourceEnabled = this[Keys.DEFAULT_SOURCE_ENABLED] ?: true,
        defaultSourceName = this[Keys.DEFAULT_SOURCE_NAME] ?: "",
        defaultSourceKind = SourceKind.values().find { it.name == this[Keys.DEFAULT_SOURCE_KIND] } ?: SourceKind.NowindApi,
        sources = decodeSources(this[Keys.SOURCES]),
        firmwareSource = this[Keys.FIRMWARE_SOURCE] ?: "",
        firmwareErase = EraseMethod.values().find { it.name == this[Keys.FIRMWARE_ERASE] } ?: EraseMethod.Chip,
    )

    suspend fun setEnableDos2(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_DOS2] = value }
    }

    suspend fun setDebugEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_ENABLED] = value }
    }

    suspend fun setKeepActive(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ACTIVE] = value }
    }

    suspend fun addSource(source: DiskSource) {
        editSources { sources -> sources.filter { it.url != source.url } + source }
    }

    suspend fun removeSource(url: String) {
        editSources { sources -> sources.filter { it.url != url } }
    }

    suspend fun setSourceEnabled(source: DiskSource, enabled: Boolean) {
        if (source.builtIn) {
            context.dataStore.edit { it[Keys.DEFAULT_SOURCE_ENABLED] = enabled }
        } else {
            editSources { sources -> sources.map { if (it.url == source.url) it.copy(enabled = enabled) else it } }
        }
    }

    suspend fun setDefaultSource(name: String, kind: SourceKind) {
        context.dataStore.edit {
            it[Keys.DEFAULT_SOURCE_NAME] = name.trim()
            it[Keys.DEFAULT_SOURCE_KIND] = kind.name
        }
    }

    suspend fun setFirmwareSource(text: String) {
        context.dataStore.edit { it[Keys.FIRMWARE_SOURCE] = text.trim() }
    }

    suspend fun setFirmwareErase(method: EraseMethod) {
        context.dataStore.edit { it[Keys.FIRMWARE_ERASE] = method.name }
    }

    private suspend fun editSources(change: (List<DiskSource>) -> List<DiskSource>) {
        context.dataStore.edit { it[Keys.SOURCES] = encodeSources(change(decodeSources(it[Keys.SOURCES]))) }
    }

    suspend fun setHostedDisk(fileName: String) {
        context.dataStore.edit { it[Keys.HOSTED_DISK] = fileName }
    }

    // settings.json in the storage folder is the copy that survives a reinstall: restore it when the folder becomes
    // available, then save it on every change. Blocking, call on Dispatchers.IO
    suspend fun restoreFrom(storage: StorageFolder, root: DocumentFile) {
        val text = readSettingsFile(storage, root) ?: return
        try {
            val json = JSONObject(text)
            context.dataStore.edit { it.readJson(json) }
        } catch (e: Exception) {
            Debug.error("reading $SETTINGS_FILE failed: $e")
        }
    }

    // suspends while saving
    suspend fun saveTo(storage: StorageFolder, root: DocumentFile) {
        var written = readSettingsFile(storage, root)
        context.dataStore.data.collect { preferences ->
            val text = preferences.toJson().toString(2)
            if (text == written) return@collect
            try {
                val file = root.findFile(SETTINGS_FILE) ?: root.createFile("application/json", SETTINGS_FILE)
                if (file != null) {
                    storage.write(file) { it.write(text.toByteArray()) }
                    written = text
                }
            } catch (e: Exception) {
                Debug.error("writing $SETTINGS_FILE failed: $e")
            }
        }
    }

    private fun readSettingsFile(storage: StorageFolder, root: DocumentFile): String? =
        root.findFile(SETTINGS_FILE)?.takeIf { it.isFile }?.let { file ->
            try {
                storage.open(file).use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                Debug.error("reading $SETTINGS_FILE failed: $e")
                null
            }
        }

    // the settings are booleans and strings only, so the json types tell which kind of key to restore
    private fun Preferences.toJson() = JSONObject().apply {
        asMap().forEach { (key, value) -> put(key.name, value) }
    }

    private fun MutablePreferences.readJson(json: JSONObject) {
        clear()
        json.keys().forEach { name ->
            when (val value = json.get(name)) {
                is Boolean -> this[booleanPreferencesKey(name)] = value
                is String -> this[stringPreferencesKey(name)] = value
            }
        }
    }
}
