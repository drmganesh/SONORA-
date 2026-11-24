package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.playbackLifecycleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_playback_lifecycle_prefs"
)

data class BackgroundPlaybackSettings(
    val backgroundPlaybackEnabled: Boolean = true,
    val resumeAfterPhoneCall: Boolean = true,
    val autoResumeOnAppLaunch: Boolean = false
)

data class SessionManagementSettings(
    val restorePreviousQueue: Boolean = true,
    val restorePlaybackPosition: Boolean = true,
    val stopPlaybackOnTaskRemoved: Boolean = true
)

/**
 * DataStore-backed preferences for Background Playback,
 * Phone-Call Audio Focus Resume, and Session Restoration / Recents Removal.
 */
class PlaybackLifecyclePreferences(context: Context) {

    private val dataStore = context.applicationContext.playbackLifecycleDataStore

    companion object {
        private val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback_enabled")
        private val KEY_RESUME_AFTER_PHONE_CALL = booleanPreferencesKey("resume_after_phone_call")
        private val KEY_AUTO_RESUME_ON_LAUNCH = booleanPreferencesKey("auto_resume_on_app_launch")

        private val KEY_RESTORE_PREVIOUS_QUEUE = booleanPreferencesKey("restore_previous_queue")
        private val KEY_RESTORE_PLAYBACK_POSITION = booleanPreferencesKey("restore_playback_position")
        private val KEY_STOP_ON_TASK_REMOVED = booleanPreferencesKey("stop_playback_on_task_removed")
    }

    val backgroundSettingsFlow: Flow<BackgroundPlaybackSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            BackgroundPlaybackSettings(
                backgroundPlaybackEnabled = prefs[KEY_BACKGROUND_PLAYBACK] ?: true,
                resumeAfterPhoneCall = prefs[KEY_RESUME_AFTER_PHONE_CALL] ?: true,
                autoResumeOnAppLaunch = prefs[KEY_AUTO_RESUME_ON_LAUNCH] ?: false
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

    suspend fun setRestorePreviousQueue(enabled: Boolean) {
        dataStore.edit { it[KEY_RESTORE_PREVIOUS_QUEUE] = enabled }
    }

    suspend fun setRestorePlaybackPosition(enabled: Boolean) {
        dataStore.edit { it[KEY_RESTORE_PLAYBACK_POSITION] = enabled }
    }

    suspend fun setStopPlaybackOnTaskRemoved(enabled: Boolean) {
        dataStore.edit { it[KEY_STOP_ON_TASK_REMOVED] = enabled }
    }
}
