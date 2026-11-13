package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val dynamicColorsEnabledFlow: Flow<Boolean> = context.themeDataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLORS_ENABLED] ?: true
    }

    val dynamicThemeModeFlow: Flow<DynamicThemeMode> = context.themeDataStore.data.map { preferences ->
        val id = preferences[KEY_DYNAMIC_THEME_MODE]
        DynamicThemeMode.fromId(id)
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
                val id = context.themeDataStore.data.first()[KEY_DYNAMIC_THEME_MODE]
                DynamicThemeMode.fromId(id)
            } catch (e: Exception) {
                DynamicThemeMode.DEFAULT
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

    fun resetToDefault() {
        scope.launch {
            context.themeDataStore.edit { preferences ->
                preferences[KEY_DYNAMIC_COLORS_ENABLED] = true
                preferences[KEY_DYNAMIC_THEME_MODE] = DynamicThemeMode.DEFAULT.id
            }
        }
    }
}
