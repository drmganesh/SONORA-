package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.home.HomeMusicMode
import domain.weather.AtmosphereIntensityPreset
import domain.weather.ManualAtmospherePreset
import domain.weather.OdishaWeatherLocation
import domain.weather.WeatherEffectMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val AMBIENT_PREFS_TAG = "AmbientEffectsPrefs"

private val Context.ambientEffectsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_ambient_effects",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(AMBIENT_PREFS_TAG, "Corrupted ambient effects DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

enum class SnowfallIntensity(
    val id: String,
    val label: String,
    val baseParticleCount: Int
) {
    LOW("low", "Low", 28),
    MEDIUM("medium", "Medium", 48),
    HIGH("high", "High", 72);

    companion object {
        fun fromId(raw: String?): SnowfallIntensity =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: LOW
    }
}

enum class SnowfallFlakeSize(
    val id: String,
    val label: String,
    val scaleMultiplier: Float
) {
    SMALL("small", "Small", 0.78f),
    MEDIUM("medium", "Medium", 1.0f),
    LARGE("large", "Large", 1.32f);

    companion object {
        fun fromId(raw: String?): SnowfallFlakeSize =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}

enum class SnowfallSpeed(
    val id: String,
    val label: String,
    val speedMultiplier: Float
) {
    SLOW("slow", "Slow", 0.65f),
    NORMAL("normal", "Normal", 1.0f),
    FAST("fast", "Fast", 1.45f);

    companion object {
        fun fromId(raw: String?): SnowfallSpeed =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: NORMAL
    }
}

data class SnowfallSettings(
    val enabled: Boolean = true,
    val intensity: SnowfallIntensity = SnowfallIntensity.LOW,
    val flakeSize: SnowfallFlakeSize = SnowfallFlakeSize.MEDIUM,
    val speed: SnowfallSpeed = SnowfallSpeed.NORMAL,
    val opacity: Float = 0.42f,
    val reduceAnimationsForBattery: Boolean = false
) {
    val clampedOpacity: Float
        get() = opacity.coerceIn(0.12f, 0.85f)

    fun effectiveParticleCount(
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false
    ): Int {
        var count = intensity.baseParticleCount
        if (reduceAnimationsForBattery || globalReduceAnimations) {
            count = (count * 0.48f).toInt().coerceAtLeast(10)
        }
        if (isLowRamDevice) {
            count = (count * 0.55f).toInt().coerceAtLeast(8)
        }
        return count.coerceIn(8, 76)
    }

    fun effectiveSpeedMultiplier(globalReduceAnimations: Boolean = false): Float {
        val base = speed.speedMultiplier
        return if (reduceAnimationsForBattery || globalReduceAnimations) {
            (base * 0.58f).coerceAtLeast(0.35f)
        } else {
            base
        }
    }

    fun minFrameIntervalNanos(globalReduceAnimations: Boolean = false): Long {
        return if (reduceAnimationsForBattery || globalReduceAnimations) 33_333_333L else 0L
    }

    companion object {
        val DEFAULT = SnowfallSettings()

        fun shouldShowSnowfall(
            homeMusicMode: HomeMusicMode,
            isMusicPlaying: Boolean,
            hasCurrentSong: Boolean,
            settings: SnowfallSettings
        ): Boolean {
            if (!settings.enabled) return false
            return when (homeMusicMode) {
                HomeMusicMode.OFFLINE -> true
                HomeMusicMode.ONLINE -> isMusicPlaying && hasCurrentSong
                HomeMusicMode.ONLINE_VIDEO -> false
            }
        }
    }
}

/**
 * Full Weather-Synced Ambient Effects configuration persisted in DataStore.
 * Default location: Paikamal, Bargarh (Odisha, India). Default mode: Automatic Weather.
 */
data class WeatherAmbientSettings(
    val selectedLocation: OdishaWeatherLocation = OdishaWeatherLocation.PAIKAMAL,
    val mode: WeatherEffectMode = WeatherEffectMode.AUTOMATIC,
    val manualPreset: ManualAtmospherePreset = ManualAtmospherePreset.RAIN,
    val effectIntensity: AtmosphereIntensityPreset = AtmosphereIntensityPreset.MEDIUM,
    val particleDensity: Float = 0.72f,
    val animationSpeed: Float = 1.0f,
    val backgroundOpacity: Float = 0.65f,
    val sunlightIntensity: Float = 0.78f,
    val coldFireplaceThresholdC: Int = 12,
    val enableRain: Boolean = true,
    val enableWind: Boolean = true,
    val enableSunlight: Boolean = true,
    val enableClouds: Boolean = true,
    val enableFog: Boolean = true,
    val enableSnow: Boolean = true,
    val enableFireplace: Boolean = true,
    val enableThunderstorm: Boolean = true,
    val batterySaverMode: Boolean = false,
    val enableMiniPlayerEffects: Boolean = true
) {
    val clampedParticleDensity: Float
        get() = particleDensity.coerceIn(0.25f, 1.0f)

    val clampedAnimationSpeed: Float
        get() = animationSpeed.coerceIn(0.40f, 1.60f)

    val clampedBackgroundOpacity: Float
        get() = backgroundOpacity.coerceIn(0.15f, 1.0f)

    val clampedSunlightIntensity: Float
        get() = sunlightIntensity.coerceIn(0.15f, 1.0f)

    val clampedFireplaceThresholdC: Int
        get() = coldFireplaceThresholdC.coerceIn(4, 22)

    /**
     * Computes a device-safe, battery-aware particle count for single-Canvas rendering.
     */
    fun effectiveParticleBudget(
        baseCount: Int,
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false
    ): Int {
        var scaled = baseCount * effectIntensity.multiplier * clampedParticleDensity
        if (batterySaverMode || globalReduceAnimations) {
            scaled *= 0.48f
        }
        if (isLowRamDevice) {
            scaled *= 0.56f
        }
        return scaled.toInt().coerceIn(8, 120)
    }

    fun effectiveSpeedMultiplier(globalReduceAnimations: Boolean = false): Float {
        val base = clampedAnimationSpeed
        return if (batterySaverMode || globalReduceAnimations) {
            (base * 0.60f).coerceAtLeast(0.32f)
        } else {
            base
        }
    }

    /**
     * Frame rate throttle in nanoseconds (30fps in Battery Saver / Low-RAM / Reduce Animations).
     */
    fun minFrameIntervalNanos(
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false
    ): Long {
        return if (batterySaverMode || isLowRamDevice || globalReduceAnimations) 33_333_333L else 0L
    }

    companion object {
        val DEFAULT = WeatherAmbientSettings()
    }
}

/**
 * DataStore-backed persistence for SONORA's Weather-Synced Ambient Effects settings,
 * per-location cached Open-Meteo snapshots, and legacy Snowfall settings.
 */
class AmbientEffectsPreferences(private val context: Context) {

    companion object {
        // Legacy snowfall keys preserved for seamless migration & test compatibility
        val KEY_SNOWFALL_ENABLED = booleanPreferencesKey("snowfall_enabled")
        val KEY_SNOWFALL_INTENSITY = stringPreferencesKey("snowfall_intensity")
        val KEY_SNOWFALL_SIZE = stringPreferencesKey("snowfall_flake_size")
        val KEY_SNOWFALL_SPEED = stringPreferencesKey("snowfall_speed")
        val KEY_SNOWFALL_OPACITY = floatPreferencesKey("snowfall_opacity")
        val KEY_SNOWFALL_REDUCE_BATTERY = booleanPreferencesKey("snowfall_reduce_battery")

        // Weather-Synced Ambient Effects keys
        val KEY_WEATHER_LOCATION = stringPreferencesKey("weather_selected_location_id")
        val KEY_WEATHER_MODE = stringPreferencesKey("weather_effect_mode")
        val KEY_WEATHER_MANUAL_PRESET = stringPreferencesKey("weather_manual_preset")
        val KEY_WEATHER_INTENSITY = stringPreferencesKey("weather_effect_intensity")
        val KEY_WEATHER_PARTICLE_DENSITY = floatPreferencesKey("weather_particle_density")
        val KEY_WEATHER_ANIMATION_SPEED = floatPreferencesKey("weather_animation_speed")
        val KEY_WEATHER_BG_OPACITY = floatPreferencesKey("weather_bg_opacity")
        val KEY_WEATHER_SUNLIGHT_INTENSITY = floatPreferencesKey("weather_sunlight_intensity")
        val KEY_WEATHER_FIREPLACE_THRESHOLD_C = intPreferencesKey("weather_fireplace_threshold_c")
        val KEY_WEATHER_ENABLE_RAIN = booleanPreferencesKey("weather_enable_rain")
        val KEY_WEATHER_ENABLE_WIND = booleanPreferencesKey("weather_enable_wind")
        val KEY_WEATHER_ENABLE_SUNLIGHT = booleanPreferencesKey("weather_enable_sunlight")
        val KEY_WEATHER_ENABLE_CLOUDS = booleanPreferencesKey("weather_enable_clouds")
        val KEY_WEATHER_ENABLE_FOG = booleanPreferencesKey("weather_enable_fog")
        val KEY_WEATHER_ENABLE_SNOW = booleanPreferencesKey("weather_enable_snow")
        val KEY_WEATHER_ENABLE_FIREPLACE = booleanPreferencesKey("weather_enable_fireplace")
        val KEY_WEATHER_ENABLE_THUNDERSTORM = booleanPreferencesKey("weather_enable_thunderstorm")
        val KEY_WEATHER_BATTERY_SAVER = booleanPreferencesKey("weather_battery_saver_mode")
        val KEY_WEATHER_MINI_PLAYER_EFFECTS = booleanPreferencesKey("weather_enable_mini_player")

        fun cacheKeyForLocation(locationId: String): Preferences.Key<String> =
            stringPreferencesKey("weather_cache_json_${locationId.lowercase()}")
    }

    private val safeData: Flow<Preferences> = context.ambientEffectsDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(AMBIENT_PREFS_TAG, "IO error reading ambient effects DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val snowfallSettingsFlow: Flow<SnowfallSettings> = safeData.map { prefs ->
        SnowfallSettings(
            enabled = prefs[KEY_SNOWFALL_ENABLED] ?: SnowfallSettings.DEFAULT.enabled,
            intensity = SnowfallIntensity.fromId(prefs[KEY_SNOWFALL_INTENSITY]),
            flakeSize = SnowfallFlakeSize.fromId(prefs[KEY_SNOWFALL_SIZE]),
            speed = SnowfallSpeed.fromId(prefs[KEY_SNOWFALL_SPEED]),
            opacity = (prefs[KEY_SNOWFALL_OPACITY] ?: SnowfallSettings.DEFAULT.opacity).coerceIn(0.12f, 0.85f),
            reduceAnimationsForBattery = prefs[KEY_SNOWFALL_REDUCE_BATTERY]
                ?: SnowfallSettings.DEFAULT.reduceAnimationsForBattery
        )
    }

    val weatherAmbientSettingsFlow: Flow<WeatherAmbientSettings> = safeData.map { prefs ->
        readWeatherSettings(prefs)
    }

    val cachedWeatherJsonByLocationFlow: Flow<Map<String, String>> = safeData.map { prefs ->
        buildMap {
            for (loc in OdishaWeatherLocation.entries) {
                val raw = prefs[cacheKeyForLocation(loc.id)]
                if (!raw.isNullOrBlank()) {
                    put(loc.id, raw)
                }
            }
        }
    }

    private fun readWeatherSettings(prefs: Preferences): WeatherAmbientSettings {
        val d = WeatherAmbientSettings.DEFAULT
        return WeatherAmbientSettings(
            selectedLocation = OdishaWeatherLocation.fromId(prefs[KEY_WEATHER_LOCATION]),
            mode = WeatherEffectMode.fromId(prefs[KEY_WEATHER_MODE]),
            manualPreset = ManualAtmospherePreset.fromId(prefs[KEY_WEATHER_MANUAL_PRESET]),
            effectIntensity = AtmosphereIntensityPreset.fromId(prefs[KEY_WEATHER_INTENSITY]),
            particleDensity = (prefs[KEY_WEATHER_PARTICLE_DENSITY] ?: d.particleDensity).coerceIn(0.25f, 1.0f),
            animationSpeed = (prefs[KEY_WEATHER_ANIMATION_SPEED] ?: d.animationSpeed).coerceIn(0.40f, 1.60f),
            backgroundOpacity = (prefs[KEY_WEATHER_BG_OPACITY] ?: d.backgroundOpacity).coerceIn(0.15f, 1.0f),
            sunlightIntensity = (prefs[KEY_WEATHER_SUNLIGHT_INTENSITY] ?: d.sunlightIntensity).coerceIn(0.15f, 1.0f),
            coldFireplaceThresholdC = (prefs[KEY_WEATHER_FIREPLACE_THRESHOLD_C] ?: d.coldFireplaceThresholdC).coerceIn(4, 22),
            enableRain = prefs[KEY_WEATHER_ENABLE_RAIN] ?: d.enableRain,
            enableWind = prefs[KEY_WEATHER_ENABLE_WIND] ?: d.enableWind,
            enableSunlight = prefs[KEY_WEATHER_ENABLE_SUNLIGHT] ?: d.enableSunlight,
            enableClouds = prefs[KEY_WEATHER_ENABLE_CLOUDS] ?: d.enableClouds,
            enableFog = prefs[KEY_WEATHER_ENABLE_FOG] ?: d.enableFog,
            enableSnow = prefs[KEY_WEATHER_ENABLE_SNOW] ?: d.enableSnow,
            enableFireplace = prefs[KEY_WEATHER_ENABLE_FIREPLACE] ?: d.enableFireplace,
            enableThunderstorm = prefs[KEY_WEATHER_ENABLE_THUNDERSTORM] ?: d.enableThunderstorm,
            batterySaverMode = prefs[KEY_WEATHER_BATTERY_SAVER] ?: d.batterySaverMode,
            enableMiniPlayerEffects = prefs[KEY_WEATHER_MINI_PLAYER_EFFECTS] ?: d.enableMiniPlayerEffects
        )
    }

    private fun writeWeatherSettings(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        s: WeatherAmbientSettings
    ) {
        prefs[KEY_WEATHER_LOCATION] = s.selectedLocation.id
        prefs[KEY_WEATHER_MODE] = s.mode.id
        prefs[KEY_WEATHER_MANUAL_PRESET] = s.manualPreset.id
        prefs[KEY_WEATHER_INTENSITY] = s.effectIntensity.id
        prefs[KEY_WEATHER_PARTICLE_DENSITY] = s.clampedParticleDensity
        prefs[KEY_WEATHER_ANIMATION_SPEED] = s.clampedAnimationSpeed
        prefs[KEY_WEATHER_BG_OPACITY] = s.clampedBackgroundOpacity
        prefs[KEY_WEATHER_SUNLIGHT_INTENSITY] = s.clampedSunlightIntensity
        prefs[KEY_WEATHER_FIREPLACE_THRESHOLD_C] = s.clampedFireplaceThresholdC
        prefs[KEY_WEATHER_ENABLE_RAIN] = s.enableRain
        prefs[KEY_WEATHER_ENABLE_WIND] = s.enableWind
        prefs[KEY_WEATHER_ENABLE_SUNLIGHT] = s.enableSunlight
        prefs[KEY_WEATHER_ENABLE_CLOUDS] = s.enableClouds
        prefs[KEY_WEATHER_ENABLE_FOG] = s.enableFog
        prefs[KEY_WEATHER_ENABLE_SNOW] = s.enableSnow
        prefs[KEY_WEATHER_ENABLE_FIREPLACE] = s.enableFireplace
        prefs[KEY_WEATHER_ENABLE_THUNDERSTORM] = s.enableThunderstorm
        prefs[KEY_WEATHER_BATTERY_SAVER] = s.batterySaverMode
        prefs[KEY_WEATHER_MINI_PLAYER_EFFECTS] = s.enableMiniPlayerEffects
    }

    suspend fun saveWeatherSettings(settings: WeatherAmbientSettings) {
        try {
            context.ambientEffectsDataStore.edit { prefs ->
                writeWeatherSettings(prefs, settings)
            }
        } catch (e: Exception) {
            Log.e(AMBIENT_PREFS_TAG, "Failed saving weather ambient settings", e)
        }
    }

    suspend fun updateWeatherSettings(transform: (WeatherAmbientSettings) -> WeatherAmbientSettings) {
        try {
            context.ambientEffectsDataStore.edit { prefs ->
                val current = readWeatherSettings(prefs)
                val next = transform(current)
                writeWeatherSettings(prefs, next)
            }
        } catch (e: Exception) {
            Log.e(AMBIENT_PREFS_TAG, "Failed updating weather ambient settings", e)
        }
    }

    suspend fun resetWeatherSettingsToDefaults() {
        saveWeatherSettings(WeatherAmbientSettings.DEFAULT)
    }

    suspend fun saveCachedWeatherJson(locationId: String, json: String) {
        try {
            context.ambientEffectsDataStore.edit { prefs ->
                prefs[cacheKeyForLocation(locationId)] = json
            }
        } catch (e: Exception) {
            Log.e(AMBIENT_PREFS_TAG, "Failed caching weather JSON for $locationId", e)
        }
    }

    // Legacy SnowfallSettings methods preserved for compatibility
    suspend fun saveSettings(settings: SnowfallSettings) {
        try {
            context.ambientEffectsDataStore.edit { prefs ->
                prefs[KEY_SNOWFALL_ENABLED] = settings.enabled
                prefs[KEY_SNOWFALL_INTENSITY] = settings.intensity.id
                prefs[KEY_SNOWFALL_SIZE] = settings.flakeSize.id
                prefs[KEY_SNOWFALL_SPEED] = settings.speed.id
                prefs[KEY_SNOWFALL_OPACITY] = settings.clampedOpacity
                prefs[KEY_SNOWFALL_REDUCE_BATTERY] = settings.reduceAnimationsForBattery
            }
        } catch (e: Exception) {
            Log.e(AMBIENT_PREFS_TAG, "Failed saving snowfall settings", e)
        }
    }

    suspend fun updateSettings(transform: (SnowfallSettings) -> SnowfallSettings) {
        try {
            context.ambientEffectsDataStore.edit { prefs ->
                val current = SnowfallSettings(
                    enabled = prefs[KEY_SNOWFALL_ENABLED] ?: SnowfallSettings.DEFAULT.enabled,
                    intensity = SnowfallIntensity.fromId(prefs[KEY_SNOWFALL_INTENSITY]),
                    flakeSize = SnowfallFlakeSize.fromId(prefs[KEY_SNOWFALL_SIZE]),
                    speed = SnowfallSpeed.fromId(prefs[KEY_SNOWFALL_SPEED]),
                    opacity = (prefs[KEY_SNOWFALL_OPACITY] ?: SnowfallSettings.DEFAULT.opacity).coerceIn(0.12f, 0.85f),
                    reduceAnimationsForBattery = prefs[KEY_SNOWFALL_REDUCE_BATTERY]
                        ?: SnowfallSettings.DEFAULT.reduceAnimationsForBattery
                )
                val next = transform(current)
                prefs[KEY_SNOWFALL_ENABLED] = next.enabled
                prefs[KEY_SNOWFALL_INTENSITY] = next.intensity.id
                prefs[KEY_SNOWFALL_SIZE] = next.flakeSize.id
                prefs[KEY_SNOWFALL_SPEED] = next.speed.id
                prefs[KEY_SNOWFALL_OPACITY] = next.clampedOpacity
                prefs[KEY_SNOWFALL_REDUCE_BATTERY] = next.reduceAnimationsForBattery
            }
        } catch (e: Exception) {
            Log.e(AMBIENT_PREFS_TAG, "Failed updating snowfall settings", e)
        }
    }

    suspend fun resetToDefaults() {
        saveSettings(SnowfallSettings.DEFAULT)
    }
}
