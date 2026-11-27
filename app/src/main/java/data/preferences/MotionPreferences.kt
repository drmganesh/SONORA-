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
import com.example.data.SonoraPreferences
import domain.motion.GestureSensitivityLevel
import domain.motion.GlobalMotionConfig
import domain.motion.MotionEffectsLevel
import domain.motion.MotionIntensity
import domain.motion.ScreenTransitionStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val MOTION_PREFS_TAG = "MotionPreferences"
private const val MOTION_CACHE_PREFS = "sonora_motion_sync_cache"

private val Context.sonoraMotionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_motion_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(MOTION_PREFS_TAG, "Corrupted motion DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore-backed persistence for SONORA global motion, Motion Effects level, transitions, and gesture settings.
 * Mirrors lightweight synchronous cache for flicker-free initial composition.
 */
class MotionPreferences(private val context: Context) {
    private val cachePrefs = context.getSharedPreferences(MOTION_CACHE_PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs = SonoraPreferences(context)
    private val writeMutex = Mutex()

    companion object {
        val KEY_EFFECTS_LEVEL = stringPreferencesKey("motion_effects_level")
        val KEY_INTENSITY = stringPreferencesKey("motion_intensity")
        val KEY_TRANSITION_STYLE = stringPreferencesKey("motion_transition_style")
        val KEY_GESTURE_SENSITIVITY = stringPreferencesKey("motion_gesture_sensitivity")
        val KEY_SWIPE_NAV = booleanPreferencesKey("motion_swipe_nav")
        val KEY_ARTWORK_TRANSITIONS = booleanPreferencesKey("motion_artwork_transitions")
        val KEY_HAPTICS = booleanPreferencesKey("motion_haptic_feedback")
        val KEY_PARALLAX = booleanPreferencesKey("motion_parallax_effects")
        val KEY_ANIMATED_COLORS = booleanPreferencesKey("motion_animated_album_colors")

        private const val CACHE_KEY_INITIALIZED = "cache_initialized"
        private const val CACHE_KEY_EFFECTS_LEVEL = "effects_level"
        private const val CACHE_KEY_INTENSITY = "intensity"
        private const val CACHE_KEY_TRANSITION = "transition"
        private const val CACHE_KEY_SENSITIVITY = "sensitivity"
        private const val CACHE_KEY_SWIPE_NAV = "swipe_nav"
        private const val CACHE_KEY_ARTWORK = "artwork_transitions"
        private const val CACHE_KEY_HAPTIC = "haptics"
        private const val CACHE_KEY_PARALLAX = "parallax"
        private const val CACHE_KEY_COLORS = "animated_colors"
    }

    private val safeData: Flow<Preferences> = context.sonoraMotionDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(MOTION_PREFS_TAG, "IO error reading motion DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val motionConfigFlow: Flow<GlobalMotionConfig> = safeData.map { prefs ->
        val fallback = getInitialConfig()
        val intensity = prefs[KEY_INTENSITY]?.let { MotionIntensity.fromName(it) } ?: fallback.intensity
        val effectsLevel = prefs[KEY_EFFECTS_LEVEL]?.let { MotionEffectsLevel.fromName(it) }
            ?: inferEffectsLevel(intensity, fallback.motionEffectsLevel)
        GlobalMotionConfig(
            motionEffectsLevel = effectsLevel,
            intensity = intensity,
            transitionStyle = prefs[KEY_TRANSITION_STYLE]?.let { ScreenTransitionStyle.fromName(it) } ?: fallback.transitionStyle,
            gestureSensitivity = prefs[KEY_GESTURE_SENSITIVITY]?.let { GestureSensitivityLevel.fromName(it) } ?: fallback.gestureSensitivity,
            swipeNavigationEnabled = prefs[KEY_SWIPE_NAV] ?: fallback.swipeNavigationEnabled,
            artworkTransitionsEnabled = prefs[KEY_ARTWORK_TRANSITIONS] ?: fallback.artworkTransitionsEnabled,
            hapticFeedbackEnabled = prefs[KEY_HAPTICS] ?: fallback.hapticFeedbackEnabled,
            parallaxEffectsEnabled = prefs[KEY_PARALLAX] ?: fallback.parallaxEffectsEnabled,
            animatedAlbumColorsEnabled = prefs[KEY_ANIMATED_COLORS] ?: fallback.animatedAlbumColorsEnabled
        )
    }

    private fun inferEffectsLevel(
        intensity: MotionIntensity,
        fallbackLevel: MotionEffectsLevel
    ): MotionEffectsLevel = when (intensity) {
        MotionIntensity.OFF -> MotionEffectsLevel.MINIMAL_MOTION
        MotionIntensity.SUBTLE -> if (fallbackLevel == MotionEffectsLevel.REDUCED_MOTION) {
            MotionEffectsLevel.REDUCED_MOTION
        } else {
            fallbackLevel
        }
        else -> fallbackLevel
    }

    fun getInitialConfig(): GlobalMotionConfig {
        if (!cachePrefs.getBoolean(CACHE_KEY_INITIALIZED, false)) {
            val existingGesture = legacyPrefs.playerGestureConfig
            return GlobalMotionConfig(
                motionEffectsLevel = if (legacyPrefs.reduceAnimations) {
                    MotionEffectsLevel.MINIMAL_MOTION
                } else {
                    MotionEffectsLevel.FULL_MOTION
                },
                intensity = if (legacyPrefs.reduceAnimations) MotionIntensity.OFF else MotionIntensity.BALANCED,
                transitionStyle = ScreenTransitionStyle.SMOOTH,
                gestureSensitivity = GestureSensitivityLevel.fromPlayerConfig(
                    sensitivity = existingGesture.swipeSensitivity,
                    thresholdDp = existingGesture.swipeThresholdDp
                ),
                swipeNavigationEnabled = true,
                artworkTransitionsEnabled = !legacyPrefs.reduceAnimations,
                hapticFeedbackEnabled = existingGesture.hapticFeedbackEnabled,
                parallaxEffectsEnabled = !legacyPrefs.reduceAnimations,
                animatedAlbumColorsEnabled = !legacyPrefs.reduceAnimations
            )
        }
        val intensity = MotionIntensity.fromName(cachePrefs.getString(CACHE_KEY_INTENSITY, null))
        val cachedLevelRaw = cachePrefs.getString(CACHE_KEY_EFFECTS_LEVEL, null)
        val effectsLevel = if (cachedLevelRaw != null) {
            MotionEffectsLevel.fromName(cachedLevelRaw)
        } else if (intensity == MotionIntensity.OFF) {
            MotionEffectsLevel.MINIMAL_MOTION
        } else {
            MotionEffectsLevel.FULL_MOTION
        }
        return GlobalMotionConfig(
            motionEffectsLevel = effectsLevel,
            intensity = intensity,
            transitionStyle = ScreenTransitionStyle.fromName(cachePrefs.getString(CACHE_KEY_TRANSITION, null)),
            gestureSensitivity = GestureSensitivityLevel.fromName(cachePrefs.getString(CACHE_KEY_SENSITIVITY, null)),
            swipeNavigationEnabled = cachePrefs.getBoolean(CACHE_KEY_SWIPE_NAV, true),
            artworkTransitionsEnabled = cachePrefs.getBoolean(CACHE_KEY_ARTWORK, true),
            hapticFeedbackEnabled = cachePrefs.getBoolean(CACHE_KEY_HAPTIC, true),
            parallaxEffectsEnabled = cachePrefs.getBoolean(CACHE_KEY_PARALLAX, true),
            animatedAlbumColorsEnabled = cachePrefs.getBoolean(CACHE_KEY_COLORS, true)
        )
    }

    suspend fun saveConfig(config: GlobalMotionConfig) {
        writeMutex.withLock {
            writeCache(config)
            try {
                context.sonoraMotionDataStore.edit { prefs ->
                    prefs[KEY_EFFECTS_LEVEL] = config.motionEffectsLevel.name
                    prefs[KEY_INTENSITY] = config.intensity.name
                    prefs[KEY_TRANSITION_STYLE] = config.transitionStyle.name
                    prefs[KEY_GESTURE_SENSITIVITY] = config.gestureSensitivity.name
                    prefs[KEY_SWIPE_NAV] = config.swipeNavigationEnabled
                    prefs[KEY_ARTWORK_TRANSITIONS] = config.artworkTransitionsEnabled
                    prefs[KEY_HAPTICS] = config.hapticFeedbackEnabled
                    prefs[KEY_PARALLAX] = config.parallaxEffectsEnabled
                    prefs[KEY_ANIMATED_COLORS] = config.animatedAlbumColorsEnabled
                }
            } catch (e: Exception) {
                Log.e(MOTION_PREFS_TAG, "Failed persisting global motion config", e)
            }
        }
    }

    suspend fun resetToDefaults(): GlobalMotionConfig {
        val defaults = GlobalMotionConfig.DEFAULT
        saveConfig(defaults)
        return defaults
    }

    private fun writeCache(config: GlobalMotionConfig) {
        cachePrefs.edit()
            .putBoolean(CACHE_KEY_INITIALIZED, true)
            .putString(CACHE_KEY_EFFECTS_LEVEL, config.motionEffectsLevel.name)
            .putString(CACHE_KEY_INTENSITY, config.intensity.name)
            .putString(CACHE_KEY_TRANSITION, config.transitionStyle.name)
            .putString(CACHE_KEY_SENSITIVITY, config.gestureSensitivity.name)
            .putBoolean(CACHE_KEY_SWIPE_NAV, config.swipeNavigationEnabled)
            .putBoolean(CACHE_KEY_ARTWORK, config.artworkTransitionsEnabled)
            .putBoolean(CACHE_KEY_HAPTIC, config.hapticFeedbackEnabled)
            .putBoolean(CACHE_KEY_PARALLAX, config.parallaxEffectsEnabled)
            .putBoolean(CACHE_KEY_COLORS, config.animatedAlbumColorsEnabled)
            .apply()
    }
}
