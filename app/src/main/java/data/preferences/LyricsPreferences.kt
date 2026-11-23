package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.lyrics.LyricsAppearanceConfig
import domain.lyrics.LyricsFontFamilyOption
import domain.lyrics.LyricsPreset
import domain.lyrics.LyricsScrollSpeed
import domain.lyrics.LyricsTimingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_lyrics_preferences"
)

/**
 * DataStore-backed persistence for Full-Screen Lyrics appearance customization and global timing offset.
 */
class LyricsPreferences(private val context: Context) {

    private object Keys {
        val PRESET = stringPreferencesKey("lyrics_preset")
        val FONT_SIZE_SP = floatPreferencesKey("lyrics_font_size_sp")
        val FONT_FAMILY = stringPreferencesKey("lyrics_font_family")
        val LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
        val USE_DYNAMIC_HIGHLIGHT = booleanPreferencesKey("lyrics_use_dynamic_highlight")
        val CUSTOM_HIGHLIGHT_ARGB = longPreferencesKey("lyrics_custom_highlight_argb")
        val BACKGROUND_BLUR_DP = floatPreferencesKey("lyrics_bg_blur_dp")
        val BACKGROUND_OPACITY = floatPreferencesKey("lyrics_bg_opacity")
        val CURRENT_LINE_SCALE = floatPreferencesKey("lyrics_current_line_scale")
        val SCROLL_SPEED = stringPreferencesKey("lyrics_scroll_speed")
        val WORD_HIGHLIGHT_ENABLED = booleanPreferencesKey("lyrics_word_highlight_enabled")
        val AMOLED_DARK = booleanPreferencesKey("lyrics_amoled_dark")
        val GLOBAL_TIMING_OFFSET_MS = longPreferencesKey("lyrics_global_timing_offset_ms")
    }

    val appearanceFlow: Flow<LyricsAppearanceConfig> = context.lyricsDataStore.data
        .catch { e ->
            if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw e
        }
        .map { prefs ->
            LyricsAppearanceConfig(
                preset = prefs[Keys.PRESET]
                    ?.let { runCatching { LyricsPreset.valueOf(it) }.getOrNull() }
                    ?: LyricsAppearanceConfig.DEFAULT.preset,
                fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: LyricsAppearanceConfig.DEFAULT.fontSizeSp)
                    .coerceIn(LyricsAppearanceConfig.MIN_FONT_SP, LyricsAppearanceConfig.MAX_FONT_SP),
                fontFamily = prefs[Keys.FONT_FAMILY]
                    ?.let { runCatching { LyricsFontFamilyOption.valueOf(it) }.getOrNull() }
                    ?: LyricsAppearanceConfig.DEFAULT.fontFamily,
                lineSpacingMultiplier = (prefs[Keys.LINE_SPACING] ?: LyricsAppearanceConfig.DEFAULT.lineSpacingMultiplier)
                    .coerceIn(LyricsAppearanceConfig.MIN_LINE_SPACING, LyricsAppearanceConfig.MAX_LINE_SPACING),
                useDynamicHighlightColor = prefs[Keys.USE_DYNAMIC_HIGHLIGHT]
                    ?: LyricsAppearanceConfig.DEFAULT.useDynamicHighlightColor,
                customHighlightColorArgb = prefs[Keys.CUSTOM_HIGHLIGHT_ARGB]
                    ?: LyricsAppearanceConfig.DEFAULT.customHighlightColorArgb,
                backgroundBlurDp = (prefs[Keys.BACKGROUND_BLUR_DP] ?: LyricsAppearanceConfig.DEFAULT.backgroundBlurDp)
                    .coerceIn(LyricsAppearanceConfig.MIN_BLUR_DP, LyricsAppearanceConfig.MAX_BLUR_DP),
                backgroundOverlayOpacity = (prefs[Keys.BACKGROUND_OPACITY] ?: LyricsAppearanceConfig.DEFAULT.backgroundOverlayOpacity)
                    .coerceIn(LyricsAppearanceConfig.MIN_OPACITY, LyricsAppearanceConfig.MAX_OPACITY),
                currentLineScale = (prefs[Keys.CURRENT_LINE_SCALE] ?: LyricsAppearanceConfig.DEFAULT.currentLineScale)
                    .coerceIn(LyricsAppearanceConfig.MIN_SCALE, LyricsAppearanceConfig.MAX_SCALE),
                scrollSpeed = prefs[Keys.SCROLL_SPEED]
                    ?.let { runCatching { LyricsScrollSpeed.valueOf(it) }.getOrNull() }
                    ?: LyricsAppearanceConfig.DEFAULT.scrollSpeed,
                wordHighlightEnabled = prefs[Keys.WORD_HIGHLIGHT_ENABLED]
                    ?: LyricsAppearanceConfig.DEFAULT.wordHighlightEnabled,
                amoledDarkBackground = prefs[Keys.AMOLED_DARK]
                    ?: LyricsAppearanceConfig.DEFAULT.amoledDarkBackground,
                globalTimingOffsetMs = (prefs[Keys.GLOBAL_TIMING_OFFSET_MS] ?: 0L)
                    .coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
            )
        }

    suspend fun saveAppearance(config: LyricsAppearanceConfig) {
        context.lyricsDataStore.edit { prefs ->
            prefs[Keys.PRESET] = config.preset.name
            prefs[Keys.FONT_SIZE_SP] = config.fontSizeSp.coerceIn(
                LyricsAppearanceConfig.MIN_FONT_SP,
                LyricsAppearanceConfig.MAX_FONT_SP
            )
            prefs[Keys.FONT_FAMILY] = config.fontFamily.name
            prefs[Keys.LINE_SPACING] = config.lineSpacingMultiplier.coerceIn(
                LyricsAppearanceConfig.MIN_LINE_SPACING,
                LyricsAppearanceConfig.MAX_LINE_SPACING
            )
            prefs[Keys.USE_DYNAMIC_HIGHLIGHT] = config.useDynamicHighlightColor
            prefs[Keys.CUSTOM_HIGHLIGHT_ARGB] = config.customHighlightColorArgb
            prefs[Keys.BACKGROUND_BLUR_DP] = config.backgroundBlurDp.coerceIn(
                LyricsAppearanceConfig.MIN_BLUR_DP,
                LyricsAppearanceConfig.MAX_BLUR_DP
            )
            prefs[Keys.BACKGROUND_OPACITY] = config.backgroundOverlayOpacity.coerceIn(
                LyricsAppearanceConfig.MIN_OPACITY,
                LyricsAppearanceConfig.MAX_OPACITY
            )
            prefs[Keys.CURRENT_LINE_SCALE] = config.currentLineScale.coerceIn(
                LyricsAppearanceConfig.MIN_SCALE,
                LyricsAppearanceConfig.MAX_SCALE
            )
            prefs[Keys.SCROLL_SPEED] = config.scrollSpeed.name
            prefs[Keys.WORD_HIGHLIGHT_ENABLED] = config.wordHighlightEnabled
            prefs[Keys.AMOLED_DARK] = config.amoledDarkBackground
            prefs[Keys.GLOBAL_TIMING_OFFSET_MS] = config.globalTimingOffsetMs.coerceIn(
                LyricsTimingState.MIN_OFFSET_MS,
                LyricsTimingState.MAX_OFFSET_MS
            )
        }
    }

    suspend fun setGlobalTimingOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
        context.lyricsDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_TIMING_OFFSET_MS] = clamped
        }
    }
}
