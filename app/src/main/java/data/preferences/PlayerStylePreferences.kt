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
import com.example.model.LockScreenStyle
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.PlayerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val PLAYER_STYLE_PREFS_TAG = "PlayerStylePreferences"

private val Context.playerStyleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_player_style",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(PLAYER_STYLE_PREFS_TAG, "Corrupted player style DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

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

    private val safeData: Flow<Preferences> = context.playerStyleDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "IO error reading player style DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val playerStyleFlow: Flow<PlayerStyle> = safeData.map { preferences ->
        val styleId = preferences[KEY_PLAYER_STYLE] ?: sonoraPrefs.playerStyle.id
        PlayerStyle.fromId(styleId)
    }

    val miniPlayerStyleFlow: Flow<MiniPlayerStyle> = safeData.map { preferences ->
        val id = preferences[KEY_MINI_PLAYER_STYLE] ?: sonoraPrefs.miniPlayerStyle.id
        MiniPlayerStyle.fromId(id)
    }

    val lockScreenStyleFlow: Flow<LockScreenStyle> = safeData.map { preferences ->
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
        try {
            context.playerStyleDataStore.edit { preferences ->
                preferences[KEY_PLAYER_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "Failed saving player style", e)
        }
    }

    suspend fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        sonoraPrefs.miniPlayerStyle = style
        try {
            context.playerStyleDataStore.edit { preferences ->
                preferences[KEY_MINI_PLAYER_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "Failed saving mini-player style", e)
        }
    }

    suspend fun setLockScreenStyle(style: LockScreenStyle) {
        sonoraPrefs.lockScreenStyle = style
        try {
            context.playerStyleDataStore.edit { preferences ->
                preferences[KEY_LOCK_SCREEN_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "Failed saving lock screen style", e)
        }
    }

    suspend fun setAppearanceConfig(config: PlayerAppearanceConfig) {
        sonoraPrefs.playerAppearanceConfig = config
        try {
            context.playerStyleDataStore.edit { preferences ->
                preferences[KEY_PLAYER_APPEARANCE] = SonoraPreferences.encodeAppearanceJson(config)
            }
        } catch (e: Exception) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "Failed saving appearance config", e)
        }
    }

    suspend fun setGestureConfig(config: PlayerGestureConfig) {
        sonoraPrefs.playerGestureConfig = config
        try {
            context.playerStyleDataStore.edit { preferences ->
                preferences[KEY_PLAYER_GESTURES] = SonoraPreferences.encodeGestureJson(config)
            }
        } catch (e: Exception) {
            Log.e(PLAYER_STYLE_PREFS_TAG, "Failed saving gesture config", e)
        }
    }
}
