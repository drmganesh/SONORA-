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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.home.HomeMusicMode
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

    /**
     * Computes a safe, battery- and memory-aware particle budget for Canvas rendering.
     */
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

    /**
     * Target frame interval in nanoseconds (30fps in battery-saving mode, native display rate otherwise).
     */
    fun minFrameIntervalNanos(globalReduceAnimations: Boolean = false): Long {
        return if (reduceAnimationsForBattery || globalReduceAnimations) 33_333_333L else 0L
    }

    companion object {
        val DEFAULT = SnowfallSettings()

        /**
         * Determines whether the Music Home atmospheric snowfall layer should be visible and active:
         * - Offline Music mode: active whenever enabled.
         * - Online Music mode: active ONLY while music is actively playing (stops/fades out on pause/stop).
         * - Online Video mode: always hidden/inactive.
         */
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
 * DataStore-backed persistence for SONORA's Ambient Effects (Snowfall) settings.
 */
class AmbientEffectsPreferences(private val context: Context) {

    companion object {
        val KEY_SNOWFALL_ENABLED = booleanPreferencesKey("snowfall_enabled")
        val KEY_SNOWFALL_INTENSITY = stringPreferencesKey("snowfall_intensity")
        val KEY_SNOWFALL_SIZE = stringPreferencesKey("snowfall_flake_size")
        val KEY_SNOWFALL_SPEED = stringPreferencesKey("snowfall_speed")
        val KEY_SNOWFALL_OPACITY = floatPreferencesKey("snowfall_opacity")
        val KEY_SNOWFALL_REDUCE_BATTERY = booleanPreferencesKey("snowfall_reduce_battery")
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
