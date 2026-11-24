package data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.update.AppUpdatePersistedState
import domain.update.SonoraGitHubUpdateConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appUpdateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_app_update_prefs"
)

/**
 * Local DataStore persistence for SONORA's GitHub Release Update Checker,
 * cached release metadata, "Later" suppression, official install session tracking,
 * and one-time post-update security reminder dismissal.
 */
class AppUpdatePreferences(context: Context) {

    private val dataStore = context.applicationContext.appUpdateDataStore

    companion object {
        private val KEY_LAST_CHECKED_AT_MS = longPreferencesKey("update_last_checked_at_ms")
        private val KEY_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
        private val KEY_CACHED_RELEASE_JSON = stringPreferencesKey("update_cached_release_json")
        private val KEY_CACHED_LATEST_VERSION_NAME = stringPreferencesKey("update_cached_latest_version_name")
        private val KEY_CACHED_LATEST_VERSION_CODE = longPreferencesKey("update_cached_latest_version_code")
        private val KEY_DISMISSED_UPDATE_VERSION_CODE = longPreferencesKey("update_dismissed_version_code")
        private val KEY_DISMISSED_UPDATE_TAG = stringPreferencesKey("update_dismissed_tag")
        private val KEY_PENDING_FROM_VERSION_CODE = longPreferencesKey("update_pending_from_version_code")
        private val KEY_PENDING_TARGET_VERSION_CODE = longPreferencesKey("update_pending_target_version_code")
        private val KEY_PENDING_TARGET_VERSION_NAME = stringPreferencesKey("update_pending_target_version_name")
        private val KEY_PENDING_PACKAGE_NAME = stringPreferencesKey("update_pending_package_name")
        private val KEY_PENDING_INITIATED_AT_MS = longPreferencesKey("update_pending_initiated_at_ms")
        private val KEY_VERIFIED_COMPLETED_VERSION_CODE = longPreferencesKey("update_verified_completed_version_code")
        private val KEY_VERIFIED_COMPLETED_VERSION_NAME = stringPreferencesKey("update_verified_completed_version_name")
        private val KEY_LAST_REMINDER_DISMISSED_VERSION_CODE = longPreferencesKey("update_last_reminder_dismissed_version_code")
    }

    val stateFlow: Flow<AppUpdatePersistedState> = dataStore.data
        .catch { err ->
            if (err is IOException) emit(emptyPreferences()) else throw err
        }
        .map { prefs ->
            AppUpdatePersistedState(
                lastCheckedAtMs = prefs[KEY_LAST_CHECKED_AT_MS] ?: 0L,
                checkIntervalHours = (prefs[KEY_CHECK_INTERVAL_HOURS]
                    ?: SonoraGitHubUpdateConfig.DEFAULT_CHECK_INTERVAL_HOURS)
                    .coerceIn(
                        SonoraGitHubUpdateConfig.MIN_CHECK_INTERVAL_HOURS,
                        SonoraGitHubUpdateConfig.MAX_CHECK_INTERVAL_HOURS
                    ),
                cachedReleaseJson = prefs[KEY_CACHED_RELEASE_JSON] ?: "",
                cachedLatestVersionName = prefs[KEY_CACHED_LATEST_VERSION_NAME] ?: "",
                cachedLatestVersionCode = prefs[KEY_CACHED_LATEST_VERSION_CODE] ?: 0L,
                dismissedUpdateVersionCode = prefs[KEY_DISMISSED_UPDATE_VERSION_CODE] ?: 0L,
                dismissedUpdateTag = prefs[KEY_DISMISSED_UPDATE_TAG] ?: "",
                pendingInstallFromVersionCode = prefs[KEY_PENDING_FROM_VERSION_CODE] ?: 0L,
                pendingInstallTargetVersionCode = prefs[KEY_PENDING_TARGET_VERSION_CODE] ?: 0L,
                pendingInstallTargetVersionName = prefs[KEY_PENDING_TARGET_VERSION_NAME] ?: "",
                pendingInstallPackageName = prefs[KEY_PENDING_PACKAGE_NAME] ?: "",
                pendingInstallInitiatedAtMs = prefs[KEY_PENDING_INITIATED_AT_MS] ?: 0L,
                verifiedCompletedUpdateVersionCode = prefs[KEY_VERIFIED_COMPLETED_VERSION_CODE] ?: 0L,
                verifiedCompletedUpdateVersionName = prefs[KEY_VERIFIED_COMPLETED_VERSION_NAME] ?: "",
                lastReminderDismissedVersionCode = prefs[KEY_LAST_REMINDER_DISMISSED_VERSION_CODE] ?: 0L
            )
        }

    suspend fun getSnapshot(): AppUpdatePersistedState = stateFlow.first()

    suspend fun setCheckIntervalHours(hours: Int) {
        val clamped = hours.coerceIn(
            SonoraGitHubUpdateConfig.MIN_CHECK_INTERVAL_HOURS,
            SonoraGitHubUpdateConfig.MAX_CHECK_INTERVAL_HOURS
        )
        dataStore.edit { prefs ->
            prefs[KEY_CHECK_INTERVAL_HOURS] = clamped
        }
    }

    suspend fun saveReleaseCheckSuccess(
        checkedAtMs: Long,
        rawReleaseJson: String,
        latestVersionName: String,
        latestVersionCode: Long
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECKED_AT_MS] = checkedAtMs
            prefs[KEY_CACHED_RELEASE_JSON] = rawReleaseJson
            prefs[KEY_CACHED_LATEST_VERSION_NAME] = latestVersionName
            prefs[KEY_CACHED_LATEST_VERSION_CODE] = latestVersionCode
        }
    }

    suspend fun saveLastCheckedTimestamp(checkedAtMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECKED_AT_MS] = checkedAtMs
        }
    }

    suspend fun markReleaseDismissedLater(versionCode: Long, tagName: String) {
        dataStore.edit { prefs ->
            if (versionCode > 0L) {
                prefs[KEY_DISMISSED_UPDATE_VERSION_CODE] = versionCode
            }
            prefs[KEY_DISMISSED_UPDATE_TAG] = tagName
        }
    }

    suspend fun recordOfficialInstallInitiated(
        fromVersionCode: Long,
        targetVersionCode: Long,
        targetVersionName: String,
        packageName: String,
        initiatedAtMs: Long
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_PENDING_FROM_VERSION_CODE] = fromVersionCode
            prefs[KEY_PENDING_TARGET_VERSION_CODE] = targetVersionCode
            prefs[KEY_PENDING_TARGET_VERSION_NAME] = targetVersionName
            prefs[KEY_PENDING_PACKAGE_NAME] = packageName
            prefs[KEY_PENDING_INITIATED_AT_MS] = initiatedAtMs
        }
    }

    suspend fun clearPendingInstallSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PENDING_FROM_VERSION_CODE)
            prefs.remove(KEY_PENDING_TARGET_VERSION_CODE)
            prefs.remove(KEY_PENDING_TARGET_VERSION_NAME)
            prefs.remove(KEY_PENDING_PACKAGE_NAME)
            prefs.remove(KEY_PENDING_INITIATED_AT_MS)
        }
    }

    suspend fun markVerifiedCompletedUpdate(versionCode: Long, versionName: String) {
        dataStore.edit { prefs ->
            prefs[KEY_VERIFIED_COMPLETED_VERSION_CODE] = versionCode
            prefs[KEY_VERIFIED_COMPLETED_VERSION_NAME] = versionName
        }
    }

    suspend fun markPostUpdateReminderDismissed(installedVersionCode: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_REMINDER_DISMISSED_VERSION_CODE] = installedVersionCode
            prefs.remove(KEY_VERIFIED_COMPLETED_VERSION_CODE)
            prefs.remove(KEY_VERIFIED_COMPLETED_VERSION_NAME)
            prefs.remove(KEY_PENDING_FROM_VERSION_CODE)
            prefs.remove(KEY_PENDING_TARGET_VERSION_CODE)
            prefs.remove(KEY_PENDING_TARGET_VERSION_NAME)
            prefs.remove(KEY_PENDING_PACKAGE_NAME)
            prefs.remove(KEY_PENDING_INITIATED_AT_MS)
        }
    }

    suspend fun clearAllForTesting() {
        dataStore.edit { it.clear() }
    }
}
