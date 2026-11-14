package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_theme_preferences")

/**
 * DataStore Preferences manager for Dynamic Album Art Theming.
 */
class ThemePreferences(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val KEY_DYNAMIC_COLORS_ENABLED = booleanPreferencesKey("dynamic_colors_enabled")
        val KEY_DYNAMIC_THEME_MODE = stringPreferencesKey("dynamic_theme_mode")
        val KEY_DYNAMIC_APPEARANCE_MODE = stringPreferencesKey("dynamic_appearance_mode")
        val KEY_DYNAMIC_COLOR_INTENSITY = stringPreferencesKey("dynamic_color_intensity")
        val KEY_DYNAMIC_GRADIENT_ENABLED = booleanPreferencesKey("dynamic_gradient_enabled")
    }

    val dynamicColorsEnabledFlow: Flow<Boolean> = context.themeDataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLORS_ENABLED] ?: true
    }

    val dynamicThemeModeFlow: Flow<DynamicThemeMode> = context.themeDataStore.data.map { preferences ->
        DynamicThemeMode.fromId(preferences[KEY_DYNAMIC_THEME_MODE])
    }

    val dynamicAppearanceModeFlow: Flow<DynamicAppearanceMode> = context.themeDataStore.data.map { preferences ->
        DynamicAppearanceMode.fromId(preferences[KEY_DYNAMIC_APPEARANCE_MODE])
    }

    val dynamicColorIntensityFlow: Flow<DynamicColorIntensity> = context.themeDataStore.data.map { preferences ->
        DynamicColorIntensity.fromId(preferences[KEY_DYNAMIC_COLOR_INTENSITY])
    }

    val dynamicGradientEnabledFlow: Flow<Boolean> = context.themeDataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_GRADIENT_ENABLED] ?: true
    }

    val dynamicColorsEnabled: StateFlow<Boolean> = dynamicColorsEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking {
            try {
                context.themeDataStore.data.first()[KEY_DYNAMIC_COLORS_ENABLED] ?: true
            } catch (e: Exception) {
                true
            }
        }
    )

    val dynamicThemeMode: StateFlow<DynamicThemeMode> = dynamicThemeModeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking {
            try {
                DynamicThemeMode.fromId(context.themeDataStore.data.first()[KEY_DYNAMIC_THEME_MODE])
            } catch (e: Exception) {
                DynamicThemeMode.DEFAULT
            }
        }
    )

    val dynamicAppearanceMode: StateFlow<DynamicAppearanceMode> = dynamicAppearanceModeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking {
            try {
                DynamicAppearanceMode.fromId(context.themeDataStore.data.first()[KEY_DYNAMIC_APPEARANCE_MODE])
            } catch (e: Exception) {
                DynamicAppearanceMode.DEFAULT
            }
        }
    )

    val dynamicColorIntensity: StateFlow<DynamicColorIntensity> = dynamicColorIntensityFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking {
            try {
                DynamicColorIntensity.fromId(context.themeDataStore.data.first()[KEY_DYNAMIC_COLOR_INTENSITY])
            } catch (e: Exception) {
                DynamicColorIntensity.DEFAULT
            }
        }
    )

    val dynamicGradientEnabled: StateFlow<Boolean> = dynamicGradientEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking {
            try {
                context.themeDataStore.data.first()[KEY_DYNAMIC_GRADIENT_ENABLED] ?: true
            } catch (e: Exception) {
                true
            }
        }
    )

    fun setDynamicColorsEnabled(enabled: Boolean) {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_COLORS_ENABLED] = enabled
            }
        }
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_THEME_MODE] = mode.id
            }
        }
    }

    fun setDynamicAppearanceMode(mode: DynamicAppearanceMode) {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_APPEARANCE_MODE] = mode.id
            }
        }
    }

    fun setDynamicColorIntensity(intensity: DynamicColorIntensity) {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_COLOR_INTENSITY] = intensity.id
            }
        }
    }

    fun setDynamicGradientEnabled(enabled: Boolean) {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_GRADIENT_ENABLED] = enabled
            }
        }
    }

    fun resetToDefault() {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_COLORS_ENABLED] = true
                preferences[KEY_DYNAMIC_THEME_MODE] = DynamicThemeMode.DEFAULT.id
                preferences[KEY_DYNAMIC_APPEARANCE_MODE] = DynamicAppearanceMode.DEFAULT.id
                preferences[KEY_DYNAMIC_COLOR_INTENSITY] = DynamicColorIntensity.DEFAULT.id
                preferences[KEY_DYNAMIC_GRADIENT_ENABLED] = true
            }
        }
    }
}
