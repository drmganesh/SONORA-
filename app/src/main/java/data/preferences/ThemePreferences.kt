package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private const val THEME_PREFS_TAG = "ThemePreferences"

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_theme_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(THEME_PREFS_TAG, "Corrupted theme DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore Preferences manager for Dynamic Album Art Theming.
 */
class ThemePreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val KEY_DYNAMIC_COLORS_ENABLED = booleanPreferencesKey("dynamic_colors_enabled")
        val KEY_DYNAMIC_THEME_MODE = stringPreferencesKey("dynamic_theme_mode")
        val KEY_DYNAMIC_APPEARANCE_MODE = stringPreferencesKey("dynamic_appearance_mode")
        val KEY_DYNAMIC_COLOR_INTENSITY = stringPreferencesKey("dynamic_color_intensity")
        val KEY_DYNAMIC_GRADIENT_ENABLED = booleanPreferencesKey("dynamic_gradient_enabled")
    }

    private val safeData: Flow<Preferences> = context.themeDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(THEME_PREFS_TAG, "IO error reading theme preferences, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val dynamicColorsEnabledFlow: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_DYNAMIC_COLORS_ENABLED] ?: true
    }

    val dynamicThemeModeFlow: Flow<DynamicThemeMode> = safeData.map { preferences ->
        DynamicThemeMode.fromId(preferences[KEY_DYNAMIC_THEME_MODE])
    }

    val dynamicAppearanceModeFlow: Flow<DynamicAppearanceMode> = safeData.map { preferences ->
        DynamicAppearanceMode.fromId(preferences[KEY_DYNAMIC_APPEARANCE_MODE])
    }

    val dynamicColorIntensityFlow: Flow<DynamicColorIntensity> = safeData.map { preferences ->
        DynamicColorIntensity.fromId(preferences[KEY_DYNAMIC_COLOR_INTENSITY])
    }

    val dynamicGradientEnabledFlow: Flow<Boolean> = safeData.map { preferences ->
        preferences[KEY_DYNAMIC_GRADIENT_ENABLED] ?: true
    }

    val dynamicColorsEnabled: StateFlow<Boolean> = dynamicColorsEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val dynamicThemeMode: StateFlow<DynamicThemeMode> = dynamicThemeModeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DynamicThemeMode.DEFAULT
    )

    val dynamicAppearanceMode: StateFlow<DynamicAppearanceMode> = dynamicAppearanceModeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DynamicAppearanceMode.DEFAULT
    )

    val dynamicColorIntensity: StateFlow<DynamicColorIntensity> = dynamicColorIntensityFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DynamicColorIntensity.DEFAULT
    )

    val dynamicGradientEnabled: StateFlow<Boolean> = dynamicGradientEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    fun setDynamicColorsEnabled(enabled: Boolean) {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_COLORS_ENABLED] = enabled
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed writing KEY_DYNAMIC_COLORS_ENABLED", e)
            }
        }
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_THEME_MODE] = mode.id
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed writing KEY_DYNAMIC_THEME_MODE", e)
            }
        }
    }

    fun setDynamicAppearanceMode(mode: DynamicAppearanceMode) {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_APPEARANCE_MODE] = mode.id
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed writing KEY_DYNAMIC_APPEARANCE_MODE", e)
            }
        }
    }

    fun setDynamicColorIntensity(intensity: DynamicColorIntensity) {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_COLOR_INTENSITY] = intensity.id
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed writing KEY_DYNAMIC_COLOR_INTENSITY", e)
            }
        }
    }

    fun setDynamicGradientEnabled(enabled: Boolean) {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_GRADIENT_ENABLED] = enabled
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed writing KEY_DYNAMIC_GRADIENT_ENABLED", e)
            }
        }
    }

    fun resetToDefault() {
        scope.launch {
            try {
                context.themeDataStore.edit { preferences ->
                    preferences[KEY_DYNAMIC_COLORS_ENABLED] = true
                    preferences[KEY_DYNAMIC_THEME_MODE] = DynamicThemeMode.DEFAULT.id
                    preferences[KEY_DYNAMIC_APPEARANCE_MODE] = DynamicAppearanceMode.DEFAULT.id
                    preferences[KEY_DYNAMIC_COLOR_INTENSITY] = DynamicColorIntensity.DEFAULT.id
                    preferences[KEY_DYNAMIC_GRADIENT_ENABLED] = true
                }
            } catch (e: Exception) {
                Log.e(THEME_PREFS_TAG, "Failed resetting theme preferences", e)
            }
        }
    }
}
