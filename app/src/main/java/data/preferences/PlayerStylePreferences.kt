package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.SonoraPreferences
import com.example.model.PlayerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerStyleDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_player_style")

/**
 * DataStore Preferences implementation for persisting the active Player Style.
 * Keeps in sync with SonoraPreferences for synchronous immediate access on startup.
 */
class PlayerStylePreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_PLAYER_STYLE = stringPreferencesKey("key_player_style")
    }

    val playerStyleFlow: Flow<PlayerStyle> = context.playerStyleDataStore.data.map { preferences ->
        val styleId = preferences[KEY_PLAYER_STYLE] ?: sonoraPrefs.playerStyle.id
        PlayerStyle.fromId(styleId)
    }

    fun getInitialStyle(): PlayerStyle {
        return sonoraPrefs.playerStyle
    }

    suspend fun setPlayerStyle(style: PlayerStyle) {
        // Update synchronous SharedPreferences
        sonoraPrefs.playerStyle = style
        // Persist to DataStore
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_PLAYER_STYLE] = style.id
        }
    }
}
