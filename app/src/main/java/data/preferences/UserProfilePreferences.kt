package data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_user_profile")

/**
 * DataStore-backed persistence for the user's personal SONORA profile and avatar URI.
 * Completely independent from audio playback and album artwork caches.
 */
class UserProfilePreferences(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val KEY_USERNAME = stringPreferencesKey("profile_username")
        val KEY_AVATAR_URI = stringPreferencesKey("profile_avatar_uri")
        private const val AVATAR_FILE_PREFIX = "sonora_profile_avatar_"
    }

    val userProfileFlow: Flow<UserProfile> = context.userProfileDataStore.data.map { prefs ->
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
        initialValue = runBlocking {
            try {
                val prefs = context.userProfileDataStore.data.first()
                UserProfile(
                    displayName = prefs[KEY_DISPLAY_NAME]?.trim()?.ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME }
                        ?: UserProfile.DEFAULT_DISPLAY_NAME,
                    username = prefs[KEY_USERNAME]?.trim().orEmpty(),
                    avatarUri = prefs[KEY_AVATAR_URI]?.trim()?.takeIf { it.isNotEmpty() }
                )
            } catch (_: Exception) {
                UserProfile()
            }
        }
    )

    suspend fun saveProfileDetails(displayName: String, username: String) {
        val cleanName = displayName.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME }
        val cleanUsername = username.trim().removePrefix("@")
        context.userProfileDataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = cleanName
            prefs[KEY_USERNAME] = cleanUsername
        }
    }

    suspend fun saveAvatarUri(uriString: String?) {
        context.userProfileDataStore.edit { prefs ->
            val clean = uriString?.trim()?.takeIf { it.isNotEmpty() }
            if (clean != null) {
                prefs[KEY_AVATAR_URI] = clean
            } else {
                prefs.remove(KEY_AVATAR_URI)
            }
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
        } catch (_: Exception) {
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
        context.userProfileDataStore.edit { prefs ->
            prefs.remove(KEY_AVATAR_URI)
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
        } catch (_: Exception) {
            // Safe ignore
        }
    }
}
