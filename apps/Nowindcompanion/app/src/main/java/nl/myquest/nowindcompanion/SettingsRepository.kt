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
    val hostedDisk: String? = null,     // file name in the disk library, restored at startup
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLE_DOS2 = booleanPreferencesKey("enable_dos2")
        val DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        val HOSTED_DISK = stringPreferencesKey("hosted_disk")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        enableDos2 = this[Keys.ENABLE_DOS2] ?: false,
        debugEnabled = this[Keys.DEBUG_ENABLED] ?: true,
        hostedDisk = this[Keys.HOSTED_DISK],
    )

    suspend fun setEnableDos2(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_DOS2] = value }
    }

    suspend fun setDebugEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_ENABLED] = value }
    }

    suspend fun setHostedDisk(fileName: String) {
        context.dataStore.edit { it[Keys.HOSTED_DISK] = fileName }
    }
}
