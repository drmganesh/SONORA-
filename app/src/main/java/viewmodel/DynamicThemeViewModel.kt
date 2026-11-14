package viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.DynamicThemeMode
import com.example.model.Song
import com.example.util.AlbumArtHelper
import data.cache.AlbumColorCache
import data.preferences.ThemePreferences
import domain.color.AlbumArtColorExtractor
import domain.color.AlbumArtPalette
import domain.color.DynamicThemeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.theme.DynamicColorPalette

/**
 * Dedicated ViewModel for the Dynamic Album Art-Based Theme Engine.
 */
class DynamicThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val themePrefs = ThemePreferences(application)

    val dynamicColorsEnabled: StateFlow<Boolean> = themePrefs.dynamicColorsEnabled
    val dynamicThemeMode: StateFlow<DynamicThemeMode> = themePrefs.dynamicThemeMode
    val dynamicAppearanceMode: StateFlow<DynamicAppearanceMode> = themePrefs.dynamicAppearanceMode
    val dynamicColorIntensity: StateFlow<DynamicColorIntensity> = themePrefs.dynamicColorIntensity
    val dynamicGradientEnabled: StateFlow<Boolean> = themePrefs.dynamicGradientEnabled

    private val _dynamicPalette = MutableStateFlow(DynamicColorPalette.default())
    val dynamicPalette: StateFlow<DynamicColorPalette> = _dynamicPalette.asStateFlow()

    private var paletteJob: Job? = null

    fun setDynamicColorsEnabled(enabled: Boolean) {
        themePrefs.setDynamicColorsEnabled(enabled)
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        themePrefs.setDynamicThemeMode(mode)
    }

    fun setDynamicAppearanceMode(mode: DynamicAppearanceMode) {
        themePrefs.setDynamicAppearanceMode(mode)
    }

    fun setDynamicColorIntensity(intensity: DynamicColorIntensity) {
        themePrefs.setDynamicColorIntensity(intensity)
    }

    fun setDynamicGradientEnabled(enabled: Boolean) {
        themePrefs.setDynamicGradientEnabled(enabled)
    }

    fun resetToDefault() {
        themePrefs.resetToDefault()
    }

    fun onSongChanged(song: Song?, systemIsDark: Boolean = true) {
        paletteJob?.cancel()
        paletteJob = viewModelScope.launch(Dispatchers.Default) {
            val enabled = dynamicColorsEnabled.value
            val mode = dynamicThemeMode.value
            val appMode = dynamicAppearanceMode.value
            val intensity = dynamicColorIntensity.value
            val gradEnabled = dynamicGradientEnabled.value

            if (!enabled || mode == DynamicThemeMode.STATIC_THEME || song == null) {
                _dynamicPalette.value = DynamicThemeGenerator.generate(
                    rawPalette = AlbumArtPalette.default(),
                    mode = mode,
                    enabled = enabled,
                    appearanceMode = appMode,
                    intensity = intensity,
                    gradientEnabled = gradEnabled,
                    systemIsDark = systemIsDark
                )
                return@launch
            }

            val artKey = "song_${song.id}_${song.albumId}"
            val cached = AlbumColorCache.get(artKey)
            if (cached != null) {
                _dynamicPalette.value = DynamicThemeGenerator.generate(
                    rawPalette = cached,
                    mode = mode,
                    enabled = enabled,
                    appearanceMode = appMode,
                    intensity = intensity,
                    gradientEnabled = gradEnabled,
                    systemIsDark = systemIsDark
                )
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                AlbumArtHelper.loadAlbumArt(getApplication(), song)
            }
            if (!isActive) return@launch

            val rawPalette = AlbumArtColorExtractor.extractPalette(bitmap, artKey)
            if (!isActive) return@launch

            _dynamicPalette.value = DynamicThemeGenerator.generate(
                rawPalette = rawPalette,
                mode = mode,
                enabled = enabled,
                appearanceMode = appMode,
                intensity = intensity,
                gradientEnabled = gradEnabled,
                systemIsDark = systemIsDark
            )
        }
    }
}
