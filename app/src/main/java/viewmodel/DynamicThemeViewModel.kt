package viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DynamicThemeMode
import com.example.model.Song
import com.example.util.AlbumArtHelper
import data.cache.AlbumColorCache
import data.preferences.ThemePreferences
import domain.color.AlbumArtColorExtractor
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

    private val _dynamicPalette = MutableStateFlow(DynamicColorPalette.default())
    val dynamicPalette: StateFlow<DynamicColorPalette> = _dynamicPalette.asStateFlow()

    private var paletteJob: Job? = null

    fun setDynamicColorsEnabled(enabled: Boolean) {
        themePrefs.setDynamicColorsEnabled(enabled)
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        themePrefs.setDynamicThemeMode(mode)
    }

    fun resetToDefault() {
        themePrefs.resetToDefault()
    }

    fun onSongChanged(song: Song?) {
        paletteJob?.cancel()
        paletteJob = viewModelScope.launch(Dispatchers.Default) {
            val enabled = dynamicColorsEnabled.value
            val mode = dynamicThemeMode.value
            if (!enabled || mode == DynamicThemeMode.STATIC_THEME || song == null) {
                _dynamicPalette.value = DynamicColorPalette.default()
                return@launch
            }

            val artKey = "song_${song.id}_${song.albumId}"
            val cached = AlbumColorCache.get(artKey)
            if (cached != null) {
                _dynamicPalette.value = DynamicThemeGenerator.generate(cached, mode, enabled)
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                AlbumArtHelper.loadAlbumArt(getApplication(), song)
            }
            if (!isActive) return@launch

            val rawPalette = AlbumArtColorExtractor.extractPalette(bitmap, artKey)
            if (!isActive) return@launch

            _dynamicPalette.value = DynamicThemeGenerator.generate(rawPalette, mode, enabled)
        }
    }
}
