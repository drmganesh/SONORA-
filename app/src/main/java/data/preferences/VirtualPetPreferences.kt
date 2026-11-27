package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.pet.MayurAnimationQuality
import domain.pet.MayurDanceIntensity
import domain.pet.MayurPetSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val PET_PREFS_TAG = "VirtualPetPreferences"
private const val PET_CACHE_PREFS = "mayur_virtual_pet_sync_cache"

private val Context.mayurVirtualPetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mayur_virtual_pet_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(PET_PREFS_TAG, "Corrupted Mayur virtual pet DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * Local DataStore + synchronous mirror cache persistence for Mayur Virtual Pet settings.
 * Zero network calls, zero telemetry, strictly isolated from existing app storage.
 */
class VirtualPetPreferences(private val context: Context) {
    private val cachePrefs = context.getSharedPreferences(PET_CACHE_PREFS, Context.MODE_PRIVATE)
    private val writeMutex = Mutex()

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("pet_enabled")
        val KEY_TOUCH_REACTIONS = booleanPreferencesKey("pet_touch_reactions")
        val KEY_MUSIC_DANCING = booleanPreferencesKey("pet_music_dancing")
        val KEY_DANCE_INTENSITY = stringPreferencesKey("pet_dance_intensity")
        val KEY_NIGHTTIME_SLEEP = booleanPreferencesKey("pet_nighttime_sleep")
        val KEY_BEDTIME_MINUTES = intPreferencesKey("pet_bedtime_minutes")
        val KEY_WAKEUP_MINUTES = intPreferencesKey("pet_wakeup_minutes")
        val KEY_WEATHER_COSTUMES = booleanPreferencesKey("pet_weather_costumes")
        val KEY_FESTIVAL_COSTUMES = booleanPreferencesKey("pet_festival_costumes")
        val KEY_ANIMATION_QUALITY = stringPreferencesKey("pet_animation_quality")

        private const val CACHE_INITIALIZED = "initialized"
        private const val CACHE_ENABLED = "enabled"
        private const val CACHE_TOUCH = "touch"
        private const val CACHE_DANCE = "dance"
        private const val CACHE_INTENSITY = "intensity"
        private const val CACHE_SLEEP = "sleep"
        private const val CACHE_BEDTIME = "bedtime"
        private const val CACHE_WAKEUP = "wakeup"
        private const val CACHE_WEATHER = "weather"
        private const val CACHE_FESTIVAL = "festival"
        private const val CACHE_QUALITY = "quality"
    }

    private val safeData: Flow<Preferences> = context.mayurVirtualPetDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(PET_PREFS_TAG, "IO error reading virtual pet DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val settingsFlow: Flow<MayurPetSettings> = safeData.map { prefs ->
        val fallback = getInitialSettings()
        MayurPetSettings(
            enabled = prefs[KEY_ENABLED] ?: fallback.enabled,
            touchReactionsEnabled = prefs[KEY_TOUCH_REACTIONS] ?: fallback.touchReactionsEnabled,
            musicDancingEnabled = prefs[KEY_MUSIC_DANCING] ?: fallback.musicDancingEnabled,
            danceIntensity = prefs[KEY_DANCE_INTENSITY]?.let { MayurDanceIntensity.fromId(it) } ?: fallback.danceIntensity,
            nighttimeSleepEnabled = prefs[KEY_NIGHTTIME_SLEEP] ?: fallback.nighttimeSleepEnabled,
            bedtimeMinutes = (prefs[KEY_BEDTIME_MINUTES] ?: fallback.bedtimeMinutes)
                .coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1),
            wakeUpMinutes = (prefs[KEY_WAKEUP_MINUTES] ?: fallback.wakeUpMinutes)
                .coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1),
            weatherCostumesEnabled = prefs[KEY_WEATHER_COSTUMES] ?: fallback.weatherCostumesEnabled,
            festivalCostumesEnabled = prefs[KEY_FESTIVAL_COSTUMES] ?: fallback.festivalCostumesEnabled,
            animationQuality = prefs[KEY_ANIMATION_QUALITY]?.let { MayurAnimationQuality.fromId(it) }
                ?: fallback.animationQuality
        )
    }

    fun getInitialSettings(): MayurPetSettings {
        if (!cachePrefs.getBoolean(CACHE_INITIALIZED, false)) {
            return MayurPetSettings.DEFAULT
        }
        return MayurPetSettings(
            enabled = cachePrefs.getBoolean(CACHE_ENABLED, true),
            touchReactionsEnabled = cachePrefs.getBoolean(CACHE_TOUCH, true),
            musicDancingEnabled = cachePrefs.getBoolean(CACHE_DANCE, true),
            danceIntensity = MayurDanceIntensity.fromId(cachePrefs.getString(CACHE_INTENSITY, null)),
            nighttimeSleepEnabled = cachePrefs.getBoolean(CACHE_SLEEP, true),
            bedtimeMinutes = cachePrefs.getInt(CACHE_BEDTIME, MayurPetSettings.DEFAULT_BEDTIME_MINUTES)
                .coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1),
            wakeUpMinutes = cachePrefs.getInt(CACHE_WAKEUP, MayurPetSettings.DEFAULT_WAKEUP_MINUTES)
                .coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1),
            weatherCostumesEnabled = cachePrefs.getBoolean(CACHE_WEATHER, true),
            festivalCostumesEnabled = cachePrefs.getBoolean(CACHE_FESTIVAL, true),
            animationQuality = MayurAnimationQuality.fromId(cachePrefs.getString(CACHE_QUALITY, null))
        )
    }

    suspend fun saveSettings(settings: MayurPetSettings) {
        val sanitized = settings.copy(
            bedtimeMinutes = settings.bedtimeMinutes.coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1),
            wakeUpMinutes = settings.wakeUpMinutes.coerceIn(0, MayurPetSettings.MINUTES_PER_DAY - 1)
        )
        writeMutex.withLock {
            writeCache(sanitized)
            try {
                context.mayurVirtualPetDataStore.edit { prefs ->
                    prefs[KEY_ENABLED] = sanitized.enabled
                    prefs[KEY_TOUCH_REACTIONS] = sanitized.touchReactionsEnabled
                    prefs[KEY_MUSIC_DANCING] = sanitized.musicDancingEnabled
                    prefs[KEY_DANCE_INTENSITY] = sanitized.danceIntensity.id
                    prefs[KEY_NIGHTTIME_SLEEP] = sanitized.nighttimeSleepEnabled
                    prefs[KEY_BEDTIME_MINUTES] = sanitized.bedtimeMinutes
                    prefs[KEY_WAKEUP_MINUTES] = sanitized.wakeUpMinutes
                    prefs[KEY_WEATHER_COSTUMES] = sanitized.weatherCostumesEnabled
                    prefs[KEY_FESTIVAL_COSTUMES] = sanitized.festivalCostumesEnabled
                    prefs[KEY_ANIMATION_QUALITY] = sanitized.animationQuality.id
                }
            } catch (e: Exception) {
                Log.e(PET_PREFS_TAG, "Failed persisting Mayur virtual pet settings", e)
            }
        }
    }

    suspend fun resetToDefaults(): MayurPetSettings {
        val defaults = MayurPetSettings.DEFAULT
        saveSettings(defaults)
        return defaults
    }

    private fun writeCache(settings: MayurPetSettings) {
        cachePrefs.edit()
            .putBoolean(CACHE_INITIALIZED, true)
            .putBoolean(CACHE_ENABLED, settings.enabled)
            .putBoolean(CACHE_TOUCH, settings.touchReactionsEnabled)
            .putBoolean(CACHE_DANCE, settings.musicDancingEnabled)
            .putString(CACHE_INTENSITY, settings.danceIntensity.id)
            .putBoolean(CACHE_SLEEP, settings.nighttimeSleepEnabled)
            .putInt(CACHE_BEDTIME, settings.bedtimeMinutes)
            .putInt(CACHE_WAKEUP, settings.wakeUpMinutes)
            .putBoolean(CACHE_WEATHER, settings.weatherCostumesEnabled)
            .putBoolean(CACHE_FESTIVAL, settings.festivalCostumesEnabled)
            .putString(CACHE_QUALITY, settings.animationQuality.id)
            .apply()
    }
}
