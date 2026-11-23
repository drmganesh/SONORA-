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
import domain.library.LibrarySectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val LIBRARY_MODE_PREFS_TAG = "LibraryModePreferences"

private val Context.libraryModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_library_mode",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(LIBRARY_MODE_PREFS_TAG, "Corrupted library mode DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

private val libraryModeWriteMutex = Mutex()

/**
 * DataStore-backed persistence for the active Library section (OFFLINE vs ONLINE).
 * Mirrors initial value synchronously via [SonoraPreferences] so cold launch renders
 * the user's last selected library immediately with zero flicker.
 */
class LibraryModePreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_LIBRARY_SECTION_MODE = stringPreferencesKey("key_library_section_mode")
    }

    val librarySectionModeFlow: Flow<LibrarySectionMode> = context.libraryModeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(LIBRARY_MODE_PREFS_TAG, "IO error reading library mode DataStore, using default", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val raw = prefs[KEY_LIBRARY_SECTION_MODE] ?: sonoraPrefs.librarySectionMode.id
            LibrarySectionMode.fromId(raw)
        }

    fun getInitialLibrarySectionMode(): LibrarySectionMode = sonoraPrefs.librarySectionMode

    fun setCachedLibrarySectionMode(mode: LibrarySectionMode) {
        sonoraPrefs.librarySectionMode = mode
    }

    suspend fun setLibrarySectionMode(mode: LibrarySectionMode) {
        sonoraPrefs.librarySectionMode = mode
        libraryModeWriteMutex.withLock {
            try {
                context.libraryModeDataStore.edit { prefs ->
                    prefs[KEY_LIBRARY_SECTION_MODE] = mode.id
                }
            } catch (e: Exception) {
                Log.e(LIBRARY_MODE_PREFS_TAG, "Failed persisting library section mode=$mode", e)
            }
        }
    }
}
