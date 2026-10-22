package nl.myquest.nowindcompanion

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val enableDos2: Boolean = false,    // off by default, some games (Puyo Puyo) do not like the way DOS2 is skipped
    val debugEnabled: Boolean = true,
    val keepActive: Boolean = false,    // foreground service + wake lock, so hosting continues in the background
    val hostedDisk: String? = null,     // file name in the disk library, restored at startup
    val defaultSourceEnabled: Boolean = true,
    val sources: List<DiskSource> = emptyList(),    // added by the user, the default source is not in here
) {
    // all places to find disk images, the built-in default first
    val allSources: List<DiskSource>
        get() = listOf(DEFAULT_SOURCE.copy(enabled = defaultSourceEnabled)) + sources
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLE_DOS2 = booleanPreferencesKey("enable_dos2")
        val DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        val KEEP_ACTIVE = booleanPreferencesKey("keep_active")
        val HOSTED_DISK = stringPreferencesKey("hosted_disk")
        val DEFAULT_SOURCE_ENABLED = booleanPreferencesKey("default_source_enabled")
        val SOURCES = stringPreferencesKey("sources")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        enableDos2 = this[Keys.ENABLE_DOS2] ?: false,
        debugEnabled = this[Keys.DEBUG_ENABLED] ?: true,
        keepActive = this[Keys.KEEP_ACTIVE] ?: false,
        hostedDisk = this[Keys.HOSTED_DISK],
        defaultSourceEnabled = this[Keys.DEFAULT_SOURCE_ENABLED] ?: true,
        sources = decodeSources(this[Keys.SOURCES]),
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

    private suspend fun editSources(change: (List<DiskSource>) -> List<DiskSource>) {
        context.dataStore.edit { it[Keys.SOURCES] = encodeSources(change(decodeSources(it[Keys.SOURCES]))) }
    }

    suspend fun setHostedDisk(fileName: String) {
        context.dataStore.edit { it[Keys.HOSTED_DISK] = fileName }
    }
}
