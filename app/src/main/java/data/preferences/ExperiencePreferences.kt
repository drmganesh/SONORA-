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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val EXPERIENCE_PREFS_TAG = "ExperiencePreferences"

private val Context.experienceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_experience_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(EXPERIENCE_PREFS_TAG, "Corrupted experience DataStore recovered", ex)
        emptyPreferences()
    }
)

enum class AlbumArtworkQualityMode(
    val id: String,
    val label: String,
    val subtitle: String
) {
    AUTO(
        id = "auto",
        label = "Auto (Recommended)",
        subtitle = "SD on mobile data or weak connections; HD on Wi-Fi, Now Playing & album headers"
    ),
    SD(
        id = "sd",
        label = "Standard (SD)",
        subtitle = "Fast loading and lower data usage across lists and cards"
    ),
    HD(
        id = "hd",
        label = "High Definition (HD)",
        subtitle = "Crisp full-resolution artwork whenever the provider supplies it"
    );

    companion object {
        fun fromId(raw: String?): AlbumArtworkQualityMode =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
    }
}

data class VideoExperienceSettings(
    val autoPipEnabled: Boolean = true,
    val inAppSwipeMiniPlayerEnabled: Boolean = true,
    val pinchToZoomEnabled: Boolean = true,
    val autoVideoQualityEnabled: Boolean = true,
    val preferHighestQualityOnWifi: Boolean = true,
    val mobileDataSaverEnabled: Boolean = false,
    val allowHighQualityOnMobileData: Boolean = true,
    val preferredWifiQualityHeight: Int = 2160,
    val lastSelectedVideoQualityHeight: Int = 0 // 0 = Auto: Best Available Quality
)

data class MusicExperienceSettings(
    val musicRecommendationsEnabled: Boolean = true,
    val autoSongRadioEnabled: Boolean = true,
    val unlimitedQueueEnabled: Boolean = true,
    val albumArtworkQualityMode: AlbumArtworkQualityMode = AlbumArtworkQualityMode.AUTO
)

/**
 * Jetpack DataStore persistence for SONORA's Video Experience & Music Experience settings.
 */
class ExperiencePreferences(private val context: Context) {

    companion object {
        // Video Experience keys
        val KEY_AUTO_PIP_ENABLED = booleanPreferencesKey("video_auto_pip_enabled")
        val KEY_IN_APP_SWIPE_MINI_PLAYER_ENABLED = booleanPreferencesKey("video_in_app_swipe_mini_player_enabled")
        val KEY_PINCH_TO_ZOOM_ENABLED = booleanPreferencesKey("video_pinch_to_zoom_enabled")
        val KEY_AUTO_VIDEO_QUALITY_ENABLED = booleanPreferencesKey("video_auto_quality_enabled")
        val KEY_PREFER_HIGHEST_WIFI_QUALITY = booleanPreferencesKey("video_prefer_highest_wifi_quality")
        val KEY_MOBILE_DATA_SAVER = booleanPreferencesKey("video_mobile_data_saver")
        val KEY_ALLOW_HIGH_QUALITY_MOBILE = booleanPreferencesKey("video_allow_high_quality_mobile")
        val KEY_PREFERRED_WIFI_QUALITY_HEIGHT = intPreferencesKey("video_preferred_wifi_quality_height")
        val KEY_LAST_SELECTED_VIDEO_QUALITY_HEIGHT = intPreferencesKey("video_last_selected_quality_height")

        // Music Experience keys
        val KEY_MUSIC_RECOMMENDATIONS_ENABLED = booleanPreferencesKey("music_recommendations_enabled")
        val KEY_AUTO_SONG_RADIO_ENABLED = booleanPreferencesKey("music_auto_song_radio_enabled")
        val KEY_UNLIMITED_QUEUE_ENABLED = booleanPreferencesKey("music_unlimited_queue_enabled")
        val KEY_ALBUM_ARTWORK_QUALITY = stringPreferencesKey("music_album_artwork_quality_mode")
        val KEY_HARMONIC_TRACK_MATCHING_ENABLED = booleanPreferencesKey("harmonic_track_matching_enabled")
    }

    private val safeData: Flow<Preferences> = context.experienceDataStore.data.catch { ex ->
        if (ex is IOException) {
            Log.e(EXPERIENCE_PREFS_TAG, "IO error reading experience prefs", ex)
            emit(emptyPreferences())
        } else {
            throw ex
        }
    }

    val videoExperienceFlow: Flow<VideoExperienceSettings> = safeData.map { prefs ->
        VideoExperienceSettings(
            autoPipEnabled = prefs[KEY_AUTO_PIP_ENABLED] ?: true,
            inAppSwipeMiniPlayerEnabled = prefs[KEY_IN_APP_SWIPE_MINI_PLAYER_ENABLED] ?: true,
            pinchToZoomEnabled = prefs[KEY_PINCH_TO_ZOOM_ENABLED] ?: true,
            autoVideoQualityEnabled = prefs[KEY_AUTO_VIDEO_QUALITY_ENABLED] ?: true,
            preferHighestQualityOnWifi = prefs[KEY_PREFER_HIGHEST_WIFI_QUALITY] ?: true,
            mobileDataSaverEnabled = prefs[KEY_MOBILE_DATA_SAVER] ?: false,
            allowHighQualityOnMobileData = prefs[KEY_ALLOW_HIGH_QUALITY_MOBILE] ?: true,
            preferredWifiQualityHeight = (prefs[KEY_PREFERRED_WIFI_QUALITY_HEIGHT] ?: 2160).coerceIn(360, 4320),
            lastSelectedVideoQualityHeight = (prefs[KEY_LAST_SELECTED_VIDEO_QUALITY_HEIGHT] ?: 0).coerceAtLeast(0)
        )
    }

    val musicExperienceFlow: Flow<MusicExperienceSettings> = safeData.map { prefs ->
        MusicExperienceSettings(
            musicRecommendationsEnabled = prefs[KEY_MUSIC_RECOMMENDATIONS_ENABLED] ?: true,
            autoSongRadioEnabled = prefs[KEY_AUTO_SONG_RADIO_ENABLED] ?: true,
            unlimitedQueueEnabled = prefs[KEY_UNLIMITED_QUEUE_ENABLED] ?: true,
            albumArtworkQualityMode = AlbumArtworkQualityMode.fromId(prefs[KEY_ALBUM_ARTWORK_QUALITY])
        )
    }

    /**
     * Emits the persisted Harmonic Track Matching preference from DataStore.
     * Defaults to false on fresh installations while preserving any explicit legacy value.
     */
    fun harmonicTrackMatchingFlow(legacyExplicitProvider: () -> Boolean? = { null }): Flow<Boolean> =
        safeData.map { prefs ->
            prefs[KEY_HARMONIC_TRACK_MATCHING_ENABLED] ?: legacyExplicitProvider() ?: false
        }

    suspend fun getHarmonicTrackMatchingEnabled(legacyExplicitValue: Boolean? = null): Boolean {
        return harmonicTrackMatchingFlow { legacyExplicitValue }.first()
    }

    suspend fun getVideoExperience(): VideoExperienceSettings = videoExperienceFlow.first()
    suspend fun getMusicExperience(): MusicExperienceSettings = musicExperienceFlow.first()

    // --- Video setters ---
    suspend fun setAutoPipEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_AUTO_PIP_ENABLED] = enabled }
    }

    suspend fun setInAppSwipeMiniPlayerEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_IN_APP_SWIPE_MINI_PLAYER_ENABLED] = enabled }
    }

    suspend fun setPinchToZoomEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_PINCH_TO_ZOOM_ENABLED] = enabled }
    }

    suspend fun setAutoVideoQualityEnabled(enabled: Boolean) {
        context.experienceDataStore.edit {
            it[KEY_AUTO_VIDEO_QUALITY_ENABLED] = enabled
            if (enabled) {
                it[KEY_LAST_SELECTED_VIDEO_QUALITY_HEIGHT] = 0
            }
        }
    }

    suspend fun setPreferHighestQualityOnWifi(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_PREFER_HIGHEST_WIFI_QUALITY] = enabled }
    }

    suspend fun setMobileDataSaverEnabled(enabled: Boolean) {
        context.experienceDataStore.edit {
            it[KEY_MOBILE_DATA_SAVER] = enabled
            if (enabled) {
                it[KEY_ALLOW_HIGH_QUALITY_MOBILE] = false
            }
        }
    }

    suspend fun setAllowHighQualityOnMobileData(enabled: Boolean) {
        context.experienceDataStore.edit {
            it[KEY_ALLOW_HIGH_QUALITY_MOBILE] = enabled
            if (enabled) {
                it[KEY_MOBILE_DATA_SAVER] = false
            }
        }
    }

    suspend fun setPreferredWifiQualityHeight(heightPx: Int) {
        context.experienceDataStore.edit { it[KEY_PREFERRED_WIFI_QUALITY_HEIGHT] = heightPx.coerceIn(360, 2160) }
    }

    suspend fun setLastSelectedVideoQualityHeight(heightPx: Int) {
        context.experienceDataStore.edit {
            it[KEY_LAST_SELECTED_VIDEO_QUALITY_HEIGHT] = heightPx.coerceAtLeast(0)
            it[KEY_AUTO_VIDEO_QUALITY_ENABLED] = (heightPx <= 0)
        }
    }

    // --- Music setters ---
    suspend fun setMusicRecommendationsEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_MUSIC_RECOMMENDATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoSongRadioEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_AUTO_SONG_RADIO_ENABLED] = enabled }
    }

    suspend fun setUnlimitedQueueEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_UNLIMITED_QUEUE_ENABLED] = enabled }
    }

    suspend fun setAlbumArtworkQualityMode(mode: AlbumArtworkQualityMode) {
        context.experienceDataStore.edit { it[KEY_ALBUM_ARTWORK_QUALITY] = mode.id }
    }

    suspend fun setHarmonicTrackMatchingEnabled(enabled: Boolean) {
        context.experienceDataStore.edit { it[KEY_HARMONIC_TRACK_MATCHING_ENABLED] = enabled }
    }
}
