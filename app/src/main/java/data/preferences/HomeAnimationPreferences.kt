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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.HomeAnimationSettingsState
import java.io.IOException

private const val HOME_ANIM_PREFS_TAG = "HomeAnimationPrefs"

private val Context.homeAnimationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_home_animations",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(HOME_ANIM_PREFS_TAG, "Corrupted home animation DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore-backed persistence for Home Screen artwork animations and Daily Mix transition styles.
 * Synchronizes initial state with [SonoraPreferences] so startup launches cleanly without frame flicker.
 */
class HomeAnimationPreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_HOME_ANIMATION_LEVEL = stringPreferencesKey("home_animation_level")
        val KEY_DAILY_MIX_STYLE = stringPreferencesKey("daily_mix_animation_style")
    }

    private val safeData: Flow<Preferences> = context.homeAnimationDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(HOME_ANIM_PREFS_TAG, "IO error reading home animation DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val settingsFlow: Flow<HomeAnimationSettingsState> = safeData.map { preferences ->
        val levelId = preferences[KEY_HOME_ANIMATION_LEVEL] ?: sonoraPrefs.homeAnimationLevel.id
        val styleId = preferences[KEY_DAILY_MIX_STYLE] ?: sonoraPrefs.dailyMixAnimationStyle.id
        HomeAnimationSettingsState(
            animationLevel = HomeAnimationLevel.fromId(levelId),
            dailyMixStyle = DailyMixAnimationStyle.fromId(styleId)
        )
    }

    fun getInitialSettings(): HomeAnimationSettingsState = HomeAnimationSettingsState(
        animationLevel = sonoraPrefs.homeAnimationLevel,
        dailyMixStyle = sonoraPrefs.dailyMixAnimationStyle
    )

    suspend fun setHomeAnimationLevel(level: HomeAnimationLevel) {
        sonoraPrefs.homeAnimationLevel = level
        try {
            context.homeAnimationDataStore.edit { preferences ->
                preferences[KEY_HOME_ANIMATION_LEVEL] = level.id
            }
        } catch (e: Exception) {
            Log.e(HOME_ANIM_PREFS_TAG, "Failed saving home animation level", e)
        }
    }

    suspend fun setDailyMixAnimationStyle(style: DailyMixAnimationStyle) {
        sonoraPrefs.dailyMixAnimationStyle = style
        try {
            context.homeAnimationDataStore.edit { preferences ->
                preferences[KEY_DAILY_MIX_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(HOME_ANIM_PREFS_TAG, "Failed saving Daily Mix animation style", e)
        }
    }

    suspend fun saveSettings(level: HomeAnimationLevel, style: DailyMixAnimationStyle) {
        sonoraPrefs.homeAnimationLevel = level
        sonoraPrefs.dailyMixAnimationStyle = style
        try {
            context.homeAnimationDataStore.edit { preferences ->
                preferences[KEY_HOME_ANIMATION_LEVEL] = level.id
                preferences[KEY_DAILY_MIX_STYLE] = style.id
            }
        } catch (e: Exception) {
            Log.e(HOME_ANIM_PREFS_TAG, "Failed saving home animation settings", e)
        }
    }
}
