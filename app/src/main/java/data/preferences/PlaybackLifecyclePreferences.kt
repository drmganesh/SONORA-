package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.playbackLifecycleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_playback_lifecycle_prefs"
)

enum class CircularFloatingSide(val label: String) {
    LEFT("Left"),
    RIGHT("Right");

    companion object {
        fun fromStored(value: String?): CircularFloatingSide =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RIGHT
    }
}

enum class CircularAnimationIntensity(val label: String, val rotationDurationMs: Int) {
    OFF("Off", 0),
    SUBTLE("Subtle", 18_000),
    BALANCED("Balanced", 10_000);

    companion object {
        fun fromStored(value: String?): CircularAnimationIntensity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SUBTLE
    }
}

data class BackgroundPlaybackSettings(
    val backgroundPlaybackEnabled: Boolean = true,
    val resumeAfterPhoneCall: Boolean = true,
    val autoResumeOnAppLaunch: Boolean = false,
    val circularMiniPlayerEnabled: Boolean = true,
    val circularFloatingSide: CircularFloatingSide = CircularFloatingSide.RIGHT,
    val circularAnimationIntensity: CircularAnimationIntensity = CircularAnimationIntensity.SUBTLE,
    val circularVerticalBias: Float = 0.85f
)

data class SessionManagementSettings(
    val restorePreviousQueue: Boolean = true,
    val restorePlaybackPosition: Boolean = true,
    val stopPlaybackOnTaskRemoved: Boolean = true
)

/**
 * DataStore-backed preferences for Background Playback, Circular Mini Player,
 * Phone-Call Audio Focus Resume, and Session Restoration / Recents Removal.
 */
class PlaybackLifecyclePreferences(context: Context) {

    private val dataStore = context.applicationContext.playbackLifecycleDataStore

    companion object {
        private val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback_enabled")
        private val KEY_RESUME_AFTER_PHONE_CALL = booleanPreferencesKey("resume_after_phone_call")
        private val KEY_AUTO_RESUME_ON_LAUNCH = booleanPreferencesKey("auto_resume_on_app_launch")
        private val KEY_CIRCULAR_MINI_PLAYER = booleanPreferencesKey("circular_mini_player_enabled")
        private val KEY_CIRCULAR_FLOATING_SIDE = stringPreferencesKey("circular_floating_side")
        private val KEY_CIRCULAR_ANIMATION = stringPreferencesKey("circular_animation_intensity")
        private val KEY_CIRCULAR_VERTICAL_BIAS = floatPreferencesKey("circular_vertical_bias")

        private val KEY_RESTORE_PREVIOUS_QUEUE = booleanPreferencesKey("restore_previous_queue")
        private val KEY_RESTORE_PLAYBACK_POSITION = booleanPreferencesKey("restore_playback_position")
        private val KEY_STOP_ON_TASK_REMOVED = booleanPreferencesKey("stop_playback_on_task_removed")
        private val KEY_MINI_PLAYER_DISPLAY_STATE = stringPreferencesKey("mini_player_display_state")
    }

    val miniPlayerDisplayStateFlow: Flow<domain.player.MiniPlayerDisplayState> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            domain.player.MiniPlayerDisplayState.fromStored(prefs[KEY_MINI_PLAYER_DISPLAY_STATE])
        }

    val backgroundSettingsFlow: Flow<BackgroundPlaybackSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            BackgroundPlaybackSettings(
                backgroundPlaybackEnabled = prefs[KEY_BACKGROUND_PLAYBACK] ?: true,
                resumeAfterPhoneCall = prefs[KEY_RESUME_AFTER_PHONE_CALL] ?: true,
                autoResumeOnAppLaunch = prefs[KEY_AUTO_RESUME_ON_LAUNCH] ?: false,
                circularMiniPlayerEnabled = prefs[KEY_CIRCULAR_MINI_PLAYER] ?: true,
                circularFloatingSide = CircularFloatingSide.fromStored(prefs[KEY_CIRCULAR_FLOATING_SIDE]),
                circularAnimationIntensity = CircularAnimationIntensity.fromStored(prefs[KEY_CIRCULAR_ANIMATION]),
                circularVerticalBias = (prefs[KEY_CIRCULAR_VERTICAL_BIAS] ?: 0.85f).coerceIn(0.12f, 0.96f)
            )
        }

    val sessionSettingsFlow: Flow<SessionManagementSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            SessionManagementSettings(
                restorePreviousQueue = prefs[KEY_RESTORE_PREVIOUS_QUEUE] ?: true,
                restorePlaybackPosition = prefs[KEY_RESTORE_PLAYBACK_POSITION] ?: true,
                stopPlaybackOnTaskRemoved = prefs[KEY_STOP_ON_TASK_REMOVED] ?: true
            )
        }

    suspend fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_PLAYBACK] = enabled }
    }

    suspend fun setResumeAfterPhoneCall(enabled: Boolean) {
        dataStore.edit { it[KEY_RESUME_AFTER_PHONE_CALL] = enabled }
    }

    suspend fun setAutoResumeOnAppLaunch(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_RESUME_ON_LAUNCH] = enabled }
    }

    suspend fun setCircularMiniPlayerEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CIRCULAR_MINI_PLAYER] = enabled }
    }

    suspend fun setCircularFloatingSide(side: CircularFloatingSide) {
        dataStore.edit { it[KEY_CIRCULAR_FLOATING_SIDE] = side.name }
    }

    suspend fun setCircularAnimationIntensity(intensity: CircularAnimationIntensity) {
        dataStore.edit { it[KEY_CIRCULAR_ANIMATION] = intensity.name }
    }

    suspend fun setCircularPosition(side: CircularFloatingSide, verticalBias: Float) {
        dataStore.edit {
            it[KEY_CIRCULAR_FLOATING_SIDE] = side.name
            it[KEY_CIRCULAR_VERTICAL_BIAS] = verticalBias.coerceIn(0.12f, 0.96f)
        }
    }

    suspend fun setRestorePreviousQueue(enabled: Boolean) {
        dataStore.edit { it[KEY_RESTORE_PREVIOUS_QUEUE] = enabled }
    }

    suspend fun setRestorePlaybackPosition(enabled: Boolean) {
        dataStore.edit { it[KEY_RESTORE_PLAYBACK_POSITION] = enabled }
    }

    suspend fun setStopPlaybackOnTaskRemoved(enabled: Boolean) {
        dataStore.edit { it[KEY_STOP_ON_TASK_REMOVED] = enabled }
    }

    suspend fun setMiniPlayerDisplayState(state: domain.player.MiniPlayerDisplayState) {
        dataStore.edit { it[KEY_MINI_PLAYER_DISPLAY_STATE] = state.name }
    }
}
