package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.SonoraPreferences
import ui.player.seekbar.SeekbarStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seekbarDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_seekbar_style")

/**
 * DataStore Preferences implementation for persisting the chosen Seekbar Style.
 * Defaults to BLOCKS for first-time users.
 */
class SeekbarPreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_SEEKBAR_STYLE = stringPreferencesKey("key_seekbar_style")
    }

    val seekbarStyleFlow: Flow<SeekbarStyle> = context.seekbarDataStore.data.map { preferences ->
        val styleId = preferences[KEY_SEEKBAR_STYLE] ?: sonoraPrefs.seekbarStyle.id
        SeekbarStyle.fromId(styleId)
    }

    fun getInitialStyle(): SeekbarStyle {
        return sonoraPrefs.seekbarStyle
    }

    suspend fun setSeekbarStyle(style: SeekbarStyle) {
        sonoraPrefs.seekbarStyle = style
        context.seekbarDataStore.edit { preferences ->
            preferences[KEY_SEEKBAR_STYLE] = style.id
        }
    }

    suspend fun resetToDefault() {
        setSeekbarStyle(SeekbarStyle.DEFAULT)
    }
}
