package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.SonoraPreferences
import com.example.model.LockScreenStyle
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.PlayerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerStyleDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_player_style")

/**
 * DataStore Preferences implementation for persisting Player Style,
 * Mini-Player & Lock-Screen styles, Player Appearance customization, and Player Gestures.
 * Keeps in sync with SonoraPreferences for synchronous immediate access on startup.
 */
class PlayerStylePreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_PLAYER_STYLE = stringPreferencesKey("key_player_style")
        val KEY_MINI_PLAYER_STYLE = stringPreferencesKey("key_mini_player_style")
        val KEY_LOCK_SCREEN_STYLE = stringPreferencesKey("key_lock_screen_style")
        val KEY_PLAYER_APPEARANCE = stringPreferencesKey("key_player_appearance_json")
        val KEY_PLAYER_GESTURES = stringPreferencesKey("key_player_gestures_json")
    }

    val playerStyleFlow: Flow<PlayerStyle> = context.playerStyleDataStore.data.map { preferences ->
        val styleId = preferences[KEY_PLAYER_STYLE] ?: sonoraPrefs.playerStyle.id
        PlayerStyle.fromId(styleId)
    }

    val miniPlayerStyleFlow: Flow<MiniPlayerStyle> = context.playerStyleDataStore.data.map { preferences ->
        val id = preferences[KEY_MINI_PLAYER_STYLE] ?: sonoraPrefs.miniPlayerStyle.id
        MiniPlayerStyle.fromId(id)
    }

    val lockScreenStyleFlow: Flow<LockScreenStyle> = context.playerStyleDataStore.data.map { preferences ->
        val id = preferences[KEY_LOCK_SCREEN_STYLE] ?: sonoraPrefs.lockScreenStyle.id
        LockScreenStyle.fromId(id)
    }

    fun getInitialStyle(): PlayerStyle = sonoraPrefs.playerStyle
    fun getInitialMiniPlayerStyle(): MiniPlayerStyle = sonoraPrefs.miniPlayerStyle
    fun getInitialLockScreenStyle(): LockScreenStyle = sonoraPrefs.lockScreenStyle
    fun getInitialAppearanceConfig(): PlayerAppearanceConfig = sonoraPrefs.playerAppearanceConfig
    fun getInitialGestureConfig(): PlayerGestureConfig = sonoraPrefs.playerGestureConfig

    suspend fun setPlayerStyle(style: PlayerStyle) {
        sonoraPrefs.playerStyle = style
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_PLAYER_STYLE] = style.id
        }
    }

    suspend fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        sonoraPrefs.miniPlayerStyle = style
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_MINI_PLAYER_STYLE] = style.id
        }
    }

    suspend fun setLockScreenStyle(style: LockScreenStyle) {
        sonoraPrefs.lockScreenStyle = style
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_LOCK_SCREEN_STYLE] = style.id
        }
    }

    suspend fun setAppearanceConfig(config: PlayerAppearanceConfig) {
        sonoraPrefs.playerAppearanceConfig = config
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_PLAYER_APPEARANCE] = SonoraPreferences.encodeAppearanceJson(config)
        }
    }

    suspend fun setGestureConfig(config: PlayerGestureConfig) {
        sonoraPrefs.playerGestureConfig = config
        context.playerStyleDataStore.edit { preferences ->
            preferences[KEY_PLAYER_GESTURES] = SonoraPreferences.encodeGestureJson(config)
        }
    }
}
