package data.preferences

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val PROFILE_PREFS_TAG = "UserProfilePreferences"

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_user_profile",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(PROFILE_PREFS_TAG, "Corrupted user profile DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore-backed persistence for the user's personal SONORA profile and avatar URI.
 * Completely independent from audio playback and album artwork caches.
 */
class UserProfilePreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val KEY_USERNAME = stringPreferencesKey("profile_username")
        val KEY_AVATAR_URI = stringPreferencesKey("profile_avatar_uri")
        private const val AVATAR_FILE_PREFIX = "sonora_profile_avatar_"
    }

    private val safeData: Flow<Preferences> = context.userProfileDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(PROFILE_PREFS_TAG, "IO error reading user profile DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val userProfileFlow: Flow<UserProfile> = safeData.map { prefs ->
        val rawName = prefs[KEY_DISPLAY_NAME]?.trim().orEmpty()
        val rawUsername = prefs[KEY_USERNAME]?.trim().orEmpty()
        val rawAvatar = prefs[KEY_AVATAR_URI]?.trim()?.takeIf { it.isNotEmpty() }
        UserProfile(
            displayName = rawName.ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME },
            username = rawUsername,
            avatarUri = rawAvatar
        )
    }

    val userProfile: StateFlow<UserProfile> = userProfileFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = UserProfile()
    )

    suspend fun saveProfileDetails(displayName: String, username: String) {
        val cleanName = displayName.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME }
        val cleanUsername = username.trim().removePrefix("@")
        try {
            context.userProfileDataStore.edit { prefs ->
                prefs[KEY_DISPLAY_NAME] = cleanName
                prefs[KEY_USERNAME] = cleanUsername
            }
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed saving profile details", e)
        }
    }

    suspend fun saveAvatarUri(uriString: String?) {
        try {
            context.userProfileDataStore.edit { prefs ->
                val clean = uriString?.trim()?.takeIf { it.isNotEmpty() }
                if (clean != null) {
                    prefs[KEY_AVATAR_URI] = clean
                } else {
                    prefs.remove(KEY_AVATAR_URI)
                }
            }
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed saving avatar URI", e)
        }
    }

    /**
     * Copies the Photo Picker image into app-internal filesDir so the photo remains
     * reliably accessible across app restarts even after temporary picker grants expire.
     * Falls back to persisting the original URI if copying fails, or returns null on error.
     */
    suspend fun persistPickedAvatar(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val copiedUri = try {
            val destFile = File(context.filesDir, "${AVATAR_FILE_PREFIX}${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val copiedBytes = input.copyTo(output)
                    if (copiedBytes <= 0L) {
                        destFile.delete()
                        return@use null
                    }
                }
            }
            if (destFile.exists() && destFile.length() > 0L) {
                deleteOldInternalAvatars(exceptFileName = destFile.name)
                Uri.fromFile(destFile).toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(PROFILE_PREFS_TAG, "Failed copying picked avatar into internal storage", e)
            null
        }

        val finalUriString = copiedUri ?: sourceUri.toString().takeIf { it.isNotBlank() }
        if (finalUriString != null) {
            saveAvatarUri(finalUriString)
        }
        finalUriString
    }

    suspend fun removeAvatar() = withContext(Dispatchers.IO) {
        deleteOldInternalAvatars(exceptFileName = null)
        try {
            context.userProfileDataStore.edit { prefs ->
                prefs.remove(KEY_AVATAR_URI)
            }
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed removing avatar URI from DataStore", e)
        }
    }

    fun setProfileDetailsAsync(displayName: String, username: String) {
        scope.launch { saveProfileDetails(displayName, username) }
    }

    private fun deleteOldInternalAvatars(exceptFileName: String?) {
        try {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(AVATAR_FILE_PREFIX) && file.name != exceptFileName) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(PROFILE_PREFS_TAG, "Failed cleaning up old internal avatar files", e)
        }
    }
}
