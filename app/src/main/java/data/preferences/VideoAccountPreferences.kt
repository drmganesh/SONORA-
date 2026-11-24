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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.video.ProviderAuthState
import domain.video.VideoAspectRatioMode
import domain.video.VideoPlayerGestureSettings
import domain.video.VideoRegionalLanguage
import domain.video.VideoSwipeSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val VIDEO_ACCOUNT_PREFS_TAG = "VideoAccountPreferences"

private val Context.videoAccountDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_video_account_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(VIDEO_ACCOUNT_PREFS_TAG, "Corrupted video account DataStore recovered", ex)
        emptyPreferences()
    }
)

/**
 * Stores OAuth2 access token metadata (never passwords), selected regional video language,
 * preferred video quality ceiling, aspect ratio preference, and Online Video Player Gestures
 * settings using Jetpack DataStore.
 */
class VideoAccountPreferences(private val context: Context) {

    companion object {
        val KEY_OAUTH_ACCOUNT_LABEL = stringPreferencesKey("video_oauth_account_label")
        val KEY_OAUTH_ACCESS_TOKEN = stringPreferencesKey("video_oauth_access_token")
        val KEY_OAUTH_EXPIRES_AT_MS = longPreferencesKey("video_oauth_expires_at_ms")
        val KEY_OAUTH_SCOPES = stringSetPreferencesKey("video_oauth_scopes")
        val KEY_REGIONAL_LANGUAGE = stringPreferencesKey("video_regional_language")
        val KEY_PREFERRED_MAX_HEIGHT = stringPreferencesKey("video_preferred_max_height")

        // Aspect Ratio & Smart Swipe Fullscreen Gesture Preferences
        val KEY_ASPECT_RATIO_MODE = stringPreferencesKey("video_aspect_ratio_mode")
        val KEY_SWIPE_FULLSCREEN_ENABLED = booleanPreferencesKey("video_swipe_fullscreen_enabled")
        val KEY_AUTO_ASPECT_RATIO_ENABLED = booleanPreferencesKey("video_auto_aspect_ratio_enabled")
        val KEY_SWIPE_SENSITIVITY = stringPreferencesKey("video_swipe_sensitivity")
        val KEY_DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("video_double_tap_seek_seconds")
        val KEY_AUTO_ORIENTATION_ENABLED = booleanPreferencesKey("video_auto_orientation_enabled")
    }

    private val safeData: Flow<Preferences> = context.videoAccountDataStore.data.catch { ex ->
        if (ex is IOException) {
            Log.e(VIDEO_ACCOUNT_PREFS_TAG, "IO error reading video account prefs", ex)
            emit(emptyPreferences())
        } else {
            throw ex
        }
    }

    val authStateFlow: Flow<ProviderAuthState> = safeData.map { prefs ->
        val storedToken = prefs[KEY_OAUTH_ACCESS_TOKEN].orEmpty()
        val token = util.LocalTokenCipher.decrypt(storedToken).orEmpty()
        val expiresAt = prefs[KEY_OAUTH_EXPIRES_AT_MS] ?: 0L
        val label = prefs[KEY_OAUTH_ACCOUNT_LABEL].orEmpty()
        val scopes = prefs[KEY_OAUTH_SCOPES].orEmpty()
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis()) {
            ProviderAuthState.Connected(
                accountLabel = label.ifBlank { "Connected Account" },
                accessToken = token,
                expiresAtMs = expiresAt,
                scopes = scopes
            )
        } else {
            ProviderAuthState.Disconnected
        }
    }

    val regionalLanguageFlow: Flow<VideoRegionalLanguage> = safeData.map { prefs ->
        VideoRegionalLanguage.fromId(prefs[KEY_REGIONAL_LANGUAGE])
    }

    val preferredMaxHeightFlow: Flow<Int> = safeData.map { prefs ->
        prefs[KEY_PREFERRED_MAX_HEIGHT]?.toIntOrNull() ?: 1080
    }

    val aspectRatioModeFlow: Flow<VideoAspectRatioMode> = safeData.map { prefs ->
        VideoAspectRatioMode.fromId(prefs[KEY_ASPECT_RATIO_MODE])
    }

    val gestureSettingsFlow: Flow<VideoPlayerGestureSettings> = safeData.map { prefs ->
        VideoPlayerGestureSettings(
            swipeFullscreenEnabled = prefs[KEY_SWIPE_FULLSCREEN_ENABLED] ?: true,
            autoAspectRatioEnabled = prefs[KEY_AUTO_ASPECT_RATIO_ENABLED] ?: true,
            defaultAspectRatio = VideoAspectRatioMode.fromId(prefs[KEY_ASPECT_RATIO_MODE]),
            swipeSensitivity = VideoSwipeSensitivity.fromId(prefs[KEY_SWIPE_SENSITIVITY]),
            doubleTapSeekSeconds = (prefs[KEY_DOUBLE_TAP_SEEK_SECONDS] ?: 10).coerceIn(5, 30),
            autoOrientationEnabled = prefs[KEY_AUTO_ORIENTATION_ENABLED] ?: true
        )
    }

    suspend fun saveOAuthSession(
        accountLabel: String,
        accessToken: String,
        expiresAtMs: Long,
        scopes: Set<String>
    ) {
        val encryptedToken = util.LocalTokenCipher.encrypt(accessToken)
            ?: throw IOException("Unable to encrypt OAuth access token locally.")
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_OAUTH_ACCOUNT_LABEL] = accountLabel.trim()
            prefs[KEY_OAUTH_ACCESS_TOKEN] = encryptedToken
            prefs[KEY_OAUTH_EXPIRES_AT_MS] = expiresAtMs
            prefs[KEY_OAUTH_SCOPES] = scopes
        }
    }

    suspend fun clearOAuthSession() {
        context.videoAccountDataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_ACCOUNT_LABEL)
            prefs.remove(KEY_OAUTH_ACCESS_TOKEN)
            prefs.remove(KEY_OAUTH_EXPIRES_AT_MS)
            prefs.remove(KEY_OAUTH_SCOPES)
        }
    }

    suspend fun setRegionalLanguage(language: VideoRegionalLanguage) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_REGIONAL_LANGUAGE] = language.id
        }
    }

    suspend fun setPreferredMaxHeight(heightPx: Int) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_PREFERRED_MAX_HEIGHT] = heightPx.toString()
        }
    }

    suspend fun setAspectRatioMode(mode: VideoAspectRatioMode) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_ASPECT_RATIO_MODE] = mode.id
        }
    }

    suspend fun setSwipeFullscreenEnabled(enabled: Boolean) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_SWIPE_FULLSCREEN_ENABLED] = enabled
        }
    }

    suspend fun setAutoAspectRatioEnabled(enabled: Boolean) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_AUTO_ASPECT_RATIO_ENABLED] = enabled
        }
    }

    suspend fun setSwipeSensitivity(sensitivity: VideoSwipeSensitivity) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_SWIPE_SENSITIVITY] = sensitivity.id
        }
    }

    suspend fun setDoubleTapSeekSeconds(seconds: Int) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_DOUBLE_TAP_SEEK_SECONDS] = seconds.coerceIn(5, 30)
        }
    }

    suspend fun setAutoOrientationEnabled(enabled: Boolean) {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_AUTO_ORIENTATION_ENABLED] = enabled
        }
    }

    suspend fun resetPlayerGestureSettings() {
        context.videoAccountDataStore.edit { prefs ->
            prefs[KEY_ASPECT_RATIO_MODE] = VideoAspectRatioMode.AUTO.id
            prefs[KEY_SWIPE_FULLSCREEN_ENABLED] = true
            prefs[KEY_AUTO_ASPECT_RATIO_ENABLED] = true
            prefs[KEY_SWIPE_SENSITIVITY] = VideoSwipeSensitivity.BALANCED.id
            prefs[KEY_DOUBLE_TAP_SEEK_SECONDS] = 10
            prefs[KEY_AUTO_ORIENTATION_ENABLED] = true
        }
    }
}
