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
import ui.player.seekbar.SeekbarStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val SEEKBAR_PREFS_TAG = "SeekbarPreferences"

private val Context.seekbarDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_seekbar_style",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(SEEKBAR_PREFS_TAG, "Corrupted seekbar DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore Preferences implementation for persisting the chosen Seekbar Style.
 * Defaults to BLOCKS for first-time users.
 */
class SeekbarPreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_SEEKBAR_STYLE = stringPreferencesKey("key_seekbar_style")
    }

    val seekbarStyleFlow: Flow<SeekbarStyle> = context.seekbarDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(SEEKBAR_PREFS_TAG, "IO error reading seekbar DataStore, using defaults", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val styleId = preferences[KEY_SEEKBAR_STYLE] ?: sonoraPrefs.seekbarStyle.id
            SeekbarStyle.fromId(styleId)
        }

    fun getInitialStyle(): SeekbarStyle {
        return sonoraPrefs.seekbarStyle
    }

    suspend fun setSeekbarStyle(style: SeekbarStyle) {
        sonoraPrefs.seekbarStyle = style
        try {
            context.seekbarDataStore.edit { preferences ->
                preferences[KEY_SEEKBAR_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(SEEKBAR_PREFS_TAG, "Failed saving seekbar style", e)
        }
    }

    suspend fun resetToDefault() {
        setSeekbarStyle(SeekbarStyle.DEFAULT)
    }
}
