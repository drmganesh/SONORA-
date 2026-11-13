package com.example.equalizer

import android.content.Context
import android.content.SharedPreferences

class EqualizerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sonora_adaptive_equalizer_prefs"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_MODE = "eq_mode"
        private const val KEY_TILT = "eq_tilt"
        private const val KEY_CONTOUR = "eq_contour"
        private const val KEY_MACRO_PROFILE = "eq_macro_profile"
        private const val KEY_PROFILE_ID = "eq_profile_id"
        private const val KEY_MANUAL_GAINS = "eq_manual_gains"
        private const val KEY_VOLUME_BOOST = "eq_volume_boost"
    }

    fun loadSettings(): AdaptiveEqualizerSettings {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val modeStr = prefs.getString(KEY_MODE, EqualizerMode.DYNAMIC.name) ?: EqualizerMode.DYNAMIC.name
        val mode = try { EqualizerMode.valueOf(modeStr) } catch (_: Exception) { EqualizerMode.DYNAMIC }
        val tilt = prefs.getFloat(KEY_TILT, 0f)
        val contour = prefs.getFloat(KEY_CONTOUR, 0f)
        val macroStr = prefs.getString(KEY_MACRO_PROFILE, MacroProfile.BROAD.name) ?: MacroProfile.BROAD.name
        val macro = try { MacroProfile.valueOf(macroStr) } catch (_: Exception) { MacroProfile.BROAD }
        val profileId = prefs.getString(KEY_PROFILE_ID, "balanced") ?: "balanced"
        val boost = prefs.getFloat(KEY_VOLUME_BOOST, 0f)

        val manualGains = FloatArray(15) { 0f }
        val gainsStr = prefs.getString(KEY_MANUAL_GAINS, null)
        if (!gainsStr.isNullOrBlank()) {
            val tokens = gainsStr.split(",")
            for (i in 0 until minOf(15, tokens.size)) {
                manualGains[i] = tokens[i].toFloatOrNull() ?: 0f
            }
        }

        return AdaptiveEqualizerSettings(
            isEnabled = enabled,
            mode = mode,
            tilt = tilt,
            contour = contour,
            macroProfile = macro,
            selectedProfileId = profileId,
            manualBands = manualGains,
            volumeBoostDb = boost
        )
    }

    fun saveSettings(settings: AdaptiveEqualizerSettings) {
        val gainsStr = settings.manualBands.joinToString(",") { it.toString() }
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.isEnabled)
            .putString(KEY_MODE, settings.mode.name)
            .putFloat(KEY_TILT, settings.tilt)
            .putFloat(KEY_CONTOUR, settings.contour)
            .putString(KEY_MACRO_PROFILE, settings.macroProfile.name)
            .putString(KEY_PROFILE_ID, settings.selectedProfileId)
            .putString(KEY_MANUAL_GAINS, gainsStr)
            .putFloat(KEY_VOLUME_BOOST, settings.volumeBoostDb)
            .apply()
    }
}
