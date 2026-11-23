package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.festival.FestivalDensityLevel
import domain.festival.FestivalEffectId
import domain.festival.FestivalIntensityLevel
import domain.festival.FestivalRegionMode
import domain.festival.IndianFestivalRegion
import domain.festival.RegionalObservanceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.festivalAtmosphereDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_festival_atmosphere_prefs"
)

/**
 * Persisted configuration for SONORA's Festival Atmosphere Engine.
 * Defaults follow battery-first guidelines:
 * - Festival Atmosphere: On
 * - Automatic Detection: On
 * - Festival Region: All India
 * - Animation Intensity: Low
 * - Particle Density: Low
 * - Show Home Indicator: On
 * - Reduce Motion: Off
 * - Battery Saver: On
 */
data class FestivalAtmosphereSettings(
    val enabled: Boolean = true,
    val automaticDetection: Boolean = true,
    val regionMode: FestivalRegionMode = FestivalRegionMode.ALL_INDIA,
    val selectedState: IndianFestivalRegion = IndianFestivalRegion.ODISHA,
    val includeRegionalInAllIndia: Boolean = false,
    val observanceRule: RegionalObservanceRule = RegionalObservanceRule.PREFER_REGIONAL_PANCHANG,
    val animationIntensity: FestivalIntensityLevel = FestivalIntensityLevel.LOW,
    val particleDensity: FestivalDensityLevel = FestivalDensityLevel.LOW,
    val showHomeIndicator: Boolean = true,
    val reduceMotion: Boolean = false,
    val batterySaver: Boolean = true,
    /** Optional effect selected inside Settings ONLY for live preview; never forces home/player festival when auto is off. */
    val previewEffectId: FestivalEffectId? = null
) {
    /**
     * Computes adaptive particle cap respecting SONORA's strict low-battery budgets:
     * Low: 8–15, Medium: 15–25, High: 25–40 — reduced further under Battery Saver, Low-RAM, or Reduce Motion.
     */
    fun effectiveParticleLimit(
        descriptorBaseCap: Int,
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false,
        globalBudgetMultiplier: Float = 1f
    ): Int {
        if (reduceMotion || globalReduceAnimations) {
            return 6
        }
        val tierCap = descriptorBaseCap.coerceIn(particleDensity.minParticles, particleDensity.maxParticles)
        var scaled = tierCap * globalBudgetMultiplier
        if (batterySaver) {
            scaled *= 0.65f
        }
        if (isLowRamDevice) {
            scaled *= 0.65f
        }
        val minAllowed = if (batterySaver || isLowRamDevice || globalBudgetMultiplier < 0.99f) 6 else particleDensity.minParticles
        return scaled.toInt().coerceIn(minAllowed, 40)
    }

    fun effectiveSpeedMultiplier(globalReduceAnimations: Boolean = false): Float {
        if (reduceMotion || globalReduceAnimations) return 0f
        var speed = animationIntensity.speedMultiplier
        if (batterySaver) {
            speed *= 0.72f
        }
        return speed.coerceIn(0.35f, 1.25f)
    }

    /**
     * Frame cadence throttle in nanoseconds:
     * - Reduce Motion: static (Long.MAX_VALUE -> draw static motif once, zero continuous loop)
     * - Battery Saver + Low-RAM: 20 fps (50ms)
     * - Battery Saver or Low-RAM: 30 fps (33.3ms)
     * - Standard: 45 fps cap (22.2ms) to stay ultra-lightweight without taxing audio DSP
     */
    fun minFrameIntervalNanos(
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false
    ): Long = when {
        reduceMotion || globalReduceAnimations -> Long.MAX_VALUE
        batterySaver && isLowRamDevice -> 50_000_000L
        batterySaver || isLowRamDevice -> 33_333_333L
        else -> 22_222_222L
    }

    val effectiveRegionForResolution: IndianFestivalRegion
        get() = when (regionMode) {
            FestivalRegionMode.ALL_INDIA -> IndianFestivalRegion.ALL_INDIA
            FestivalRegionMode.SPECIFIC_STATE,
            FestivalRegionMode.ALL_INDIA_PLUS_STATE -> selectedState
        }

    companion object {
        val DEFAULT = FestivalAtmosphereSettings()
    }
}

class FestivalAtmospherePreferences(private val context: Context) {

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("festival_enabled")
        val KEY_AUTO_DETECTION = booleanPreferencesKey("festival_auto_detection")
        val KEY_REGION_MODE = stringPreferencesKey("festival_region_mode")
        val KEY_SELECTED_STATE = stringPreferencesKey("festival_selected_state")
        val KEY_INCLUDE_REGIONAL_ALL_INDIA = booleanPreferencesKey("festival_include_regional_all_india")
        val KEY_OBSERVANCE_RULE = stringPreferencesKey("festival_observance_rule")
        val KEY_INTENSITY = stringPreferencesKey("festival_animation_intensity")
        val KEY_DENSITY = stringPreferencesKey("festival_particle_density")
        val KEY_SHOW_HOME_INDICATOR = booleanPreferencesKey("festival_show_home_indicator")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("festival_reduce_motion")
        val KEY_BATTERY_SAVER = booleanPreferencesKey("festival_battery_saver")
    }

    private val safeData: Flow<Preferences> = context.festivalAtmosphereDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settingsFlow: Flow<FestivalAtmosphereSettings> = safeData.map { prefs ->
        FestivalAtmosphereSettings(
            enabled = prefs[KEY_ENABLED] ?: FestivalAtmosphereSettings.DEFAULT.enabled,
            automaticDetection = prefs[KEY_AUTO_DETECTION] ?: FestivalAtmosphereSettings.DEFAULT.automaticDetection,
            regionMode = FestivalRegionMode.fromId(prefs[KEY_REGION_MODE]),
            selectedState = IndianFestivalRegion.fromId(prefs[KEY_SELECTED_STATE]).let {
                if (it == IndianFestivalRegion.ALL_INDIA) IndianFestivalRegion.ODISHA else it
            },
            includeRegionalInAllIndia = prefs[KEY_INCLUDE_REGIONAL_ALL_INDIA]
                ?: FestivalAtmosphereSettings.DEFAULT.includeRegionalInAllIndia,
            observanceRule = RegionalObservanceRule.fromId(prefs[KEY_OBSERVANCE_RULE]),
            animationIntensity = FestivalIntensityLevel.fromId(prefs[KEY_INTENSITY]),
            particleDensity = FestivalDensityLevel.fromId(prefs[KEY_DENSITY]),
            showHomeIndicator = prefs[KEY_SHOW_HOME_INDICATOR] ?: FestivalAtmosphereSettings.DEFAULT.showHomeIndicator,
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: FestivalAtmosphereSettings.DEFAULT.reduceMotion,
            batterySaver = prefs[KEY_BATTERY_SAVER] ?: FestivalAtmosphereSettings.DEFAULT.batterySaver
        )
    }

    suspend fun updateSettings(
        transform: (FestivalAtmosphereSettings) -> FestivalAtmosphereSettings
    ) {
        context.festivalAtmosphereDataStore.edit { prefs ->
            val current = FestivalAtmosphereSettings(
                enabled = prefs[KEY_ENABLED] ?: FestivalAtmosphereSettings.DEFAULT.enabled,
                automaticDetection = prefs[KEY_AUTO_DETECTION] ?: FestivalAtmosphereSettings.DEFAULT.automaticDetection,
                regionMode = FestivalRegionMode.fromId(prefs[KEY_REGION_MODE]),
                selectedState = IndianFestivalRegion.fromId(prefs[KEY_SELECTED_STATE]).let {
                    if (it == IndianFestivalRegion.ALL_INDIA) IndianFestivalRegion.ODISHA else it
                },
                includeRegionalInAllIndia = prefs[KEY_INCLUDE_REGIONAL_ALL_INDIA]
                    ?: FestivalAtmosphereSettings.DEFAULT.includeRegionalInAllIndia,
                observanceRule = RegionalObservanceRule.fromId(prefs[KEY_OBSERVANCE_RULE]),
                animationIntensity = FestivalIntensityLevel.fromId(prefs[KEY_INTENSITY]),
                particleDensity = FestivalDensityLevel.fromId(prefs[KEY_DENSITY]),
                showHomeIndicator = prefs[KEY_SHOW_HOME_INDICATOR] ?: FestivalAtmosphereSettings.DEFAULT.showHomeIndicator,
                reduceMotion = prefs[KEY_REDUCE_MOTION] ?: FestivalAtmosphereSettings.DEFAULT.reduceMotion,
                batterySaver = prefs[KEY_BATTERY_SAVER] ?: FestivalAtmosphereSettings.DEFAULT.batterySaver
            )
            val updated = transform(current)
            prefs[KEY_ENABLED] = updated.enabled
            prefs[KEY_AUTO_DETECTION] = updated.automaticDetection
            prefs[KEY_REGION_MODE] = updated.regionMode.id
            prefs[KEY_SELECTED_STATE] = updated.selectedState.id
            prefs[KEY_INCLUDE_REGIONAL_ALL_INDIA] = updated.includeRegionalInAllIndia
            prefs[KEY_OBSERVANCE_RULE] = updated.observanceRule.id
            prefs[KEY_INTENSITY] = updated.animationIntensity.id
            prefs[KEY_DENSITY] = updated.particleDensity.id
            prefs[KEY_SHOW_HOME_INDICATOR] = updated.showHomeIndicator
            prefs[KEY_REDUCE_MOTION] = updated.reduceMotion
            prefs[KEY_BATTERY_SAVER] = updated.batterySaver
        }
    }

    suspend fun resetToDefaults() {
        updateSettings { FestivalAtmosphereSettings.DEFAULT }
    }
}
