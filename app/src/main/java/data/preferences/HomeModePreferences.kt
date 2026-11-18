package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.SonoraPreferences
import domain.home.HomeMusicMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val HOME_MODE_PREFS_TAG = "HomeModePreferences"

private val Context.homeModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_home_mode",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(HOME_MODE_PREFS_TAG, "Corrupted home mode DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

private val homeModeWriteMutex = Mutex()

/**
 * DataStore-backed persistence for the active Home Screen Music Mode (OFFLINE vs ONLINE).
 * Mirrors initial state synchronously via [SonoraPreferences] so startup launches directly
 * in the last selected mode without UI flicker or redundant reloads.
 */
class HomeModePreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_HOME_MUSIC_MODE = stringPreferencesKey("key_home_music_mode")
    }

    val homeMusicModeFlow: Flow<HomeMusicMode> = context.homeModeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(HOME_MODE_PREFS_TAG, "IO error reading home mode DataStore, using defaults", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val raw = prefs[KEY_HOME_MUSIC_MODE] ?: sonoraPrefs.homeMusicMode.id
            HomeMusicMode.fromId(raw)
        }

    fun getInitialHomeMusicMode(): HomeMusicMode = sonoraPrefs.homeMusicMode

    fun setCachedHomeMusicMode(mode: HomeMusicMode) {
        sonoraPrefs.homeMusicMode = mode
    }

    suspend fun setHomeMusicMode(mode: HomeMusicMode) {
        sonoraPrefs.homeMusicMode = mode
        homeModeWriteMutex.withLock {
            try {
                context.homeModeDataStore.edit { prefs ->
                    prefs[KEY_HOME_MUSIC_MODE] = mode.id
                }
            } catch (e: Exception) {
                Log.e(HOME_MODE_PREFS_TAG, "Failed persisting home music mode=$mode", e)
            }
        }
    }
}
