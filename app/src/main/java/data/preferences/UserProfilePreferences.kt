package data.preferences

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.SonoraApplication
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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

enum class ProfileOnboardingState {
    LOADING,
    REQUIRED,
    COMPLETED
}

sealed class ProfileNameValidationResult {
    data class Valid(val trimmedName: String) : ProfileNameValidationResult()
    data object Empty : ProfileNameValidationResult()
    data class TooLong(val maxLength: Int) : ProfileNameValidationResult()
}

/**
 * DataStore-backed local-only persistence for the user's SONORA profile, avatar URI,
 * and first-launch onboarding completion state.
 * Completely independent from audio playback, album artwork caches, and network/cloud accounts.
 */
class UserProfilePreferences(
    private val context: Context,
    private val hasPreExistingInstallProvider: () -> Boolean = {
        SonoraApplication.hadPreExistingInstallationDataOnProcessStart
    }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val KEY_USERNAME = stringPreferencesKey("profile_username")
        val KEY_AVATAR_URI = stringPreferencesKey("profile_avatar_uri")
        val KEY_PROFILE_ONBOARDING_COMPLETED = booleanPreferencesKey("profile_onboarding_completed")
        const val MAX_DISPLAY_NAME_LENGTH = 32
        private const val AVATAR_FILE_PREFIX = "sonora_profile_avatar_"

        /**
         * Validates and normalizes a user-entered profile name:
         * trims leading/trailing whitespace, rejects empty/whitespace-only names,
         * and enforces the sensible character limit.
         */
        fun validateProfileName(raw: String): ProfileNameValidationResult {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> ProfileNameValidationResult.Empty
                trimmed.length > MAX_DISPLAY_NAME_LENGTH -> ProfileNameValidationResult.TooLong(MAX_DISPLAY_NAME_LENGTH)
                else -> ProfileNameValidationResult.Valid(trimmed)
            }
        }
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

    private val _onboardingState = MutableStateFlow(ProfileOnboardingState.LOADING)
    val onboardingState: StateFlow<ProfileOnboardingState> = _onboardingState.asStateFlow()

    init {
        scope.launch {
            resolveInitialOnboardingState()
        }
    }

    /**
     * Resolves first-launch onboarding status safely without forcing existing installations
     * through onboarding after an update, while ensuring process death during fresh onboarding
     * resumes onboarding until completed.
     */
    suspend fun resolveInitialOnboardingState(): ProfileOnboardingState = withContext(Dispatchers.IO) {
        val prefs = safeData.first()
        val explicitFlag = prefs[KEY_PROFILE_ONBOARDING_COMPLETED]
        val resolved = when (explicitFlag) {
            true -> ProfileOnboardingState.COMPLETED
            false -> ProfileOnboardingState.REQUIRED
            null -> {
                val hasSavedProfileData = !prefs[KEY_DISPLAY_NAME].isNullOrBlank() ||
                    !prefs[KEY_AVATAR_URI].isNullOrBlank() ||
                    !prefs[KEY_USERNAME].isNullOrBlank()
                val isExistingInstallation = hasSavedProfileData || hasPreExistingInstallProvider()
                try {
                    context.userProfileDataStore.edit { mutablePrefs ->
                        if (mutablePrefs[KEY_PROFILE_ONBOARDING_COMPLETED] == null) {
                            mutablePrefs[KEY_PROFILE_ONBOARDING_COMPLETED] = isExistingInstallation
                        }
                    }
                } catch (e: Exception) {
                    Log.e(PROFILE_PREFS_TAG, "Failed persisting resolved onboarding state", e)
                }
                if (isExistingInstallation) {
                    ProfileOnboardingState.COMPLETED
                } else {
                    ProfileOnboardingState.REQUIRED
                }
            }
        }
        _onboardingState.value = resolved
        resolved
    }

    /**
     * Completes first-launch profile setup atomically: validates trimmed profile name,
     * copies optional picked photo into app-internal storage, persists profile + completion flag.
     */
    suspend fun completeFirstLaunchProfile(
        rawDisplayName: String,
        pickedAvatarUri: Uri?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val validation = validateProfileName(rawDisplayName)
        if (validation !is ProfileNameValidationResult.Valid) {
            val msg = when (validation) {
                is ProfileNameValidationResult.Empty -> "Profile name cannot be empty."
                is ProfileNameValidationResult.TooLong -> "Profile name must be at most ${validation.maxLength} characters."
                is ProfileNameValidationResult.Valid -> ""
            }
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        val cleanName = validation.trimmedName
        val persistedAvatarUri = if (pickedAvatarUri != null) {
            copyPickedAvatarIntoInternalStorage(pickedAvatarUri)
                ?: pickedAvatarUri.toString().takeIf { it.isNotBlank() }
        } else {
            null
        }

        return@withContext try {
            context.userProfileDataStore.edit { prefs ->
                prefs[KEY_DISPLAY_NAME] = cleanName
                if (persistedAvatarUri != null) {
                    prefs[KEY_AVATAR_URI] = persistedAvatarUri
                } else {
                    prefs.remove(KEY_AVATAR_URI)
                }
                prefs[KEY_PROFILE_ONBOARDING_COMPLETED] = true
            }
            _onboardingState.value = ProfileOnboardingState.COMPLETED
            Result.success(
                UserProfile(
                    displayName = cleanName,
                    username = "",
                    avatarUri = persistedAvatarUri
                )
            )
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed completing first-launch profile setup", e)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(rawDisplayName: String): Result<String> = withContext(Dispatchers.IO) {
        val validation = validateProfileName(rawDisplayName)
        if (validation !is ProfileNameValidationResult.Valid) {
            val msg = when (validation) {
                is ProfileNameValidationResult.Empty -> "Profile name cannot be empty."
                is ProfileNameValidationResult.TooLong -> "Profile name must be at most ${validation.maxLength} characters."
                is ProfileNameValidationResult.Valid -> ""
            }
            return@withContext Result.failure(IllegalArgumentException(msg))
        }
        return@withContext try {
            context.userProfileDataStore.edit { prefs ->
                prefs[KEY_DISPLAY_NAME] = validation.trimmedName
                prefs[KEY_PROFILE_ONBOARDING_COMPLETED] = true
            }
            _onboardingState.value = ProfileOnboardingState.COMPLETED
            Result.success(validation.trimmedName)
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed updating display name", e)
            Result.failure(e)
        }
    }

    suspend fun saveProfileDetails(displayName: String, username: String) {
        val cleanName = displayName.trim()
            .take(MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME }
        val cleanUsername = username.trim().removePrefix("@")
        try {
            context.userProfileDataStore.edit { prefs ->
                prefs[KEY_DISPLAY_NAME] = cleanName
                prefs[KEY_USERNAME] = cleanUsername
                prefs[KEY_PROFILE_ONBOARDING_COMPLETED] = true
            }
            _onboardingState.value = ProfileOnboardingState.COMPLETED
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
     * Copies the Photo Picker / Document Picker image into app-internal filesDir so the photo
     * remains reliably accessible across app restarts even after temporary picker grants expire.
     * Falls back to persisting the original URI if copying fails, or returns null on error.
     */
    suspend fun persistPickedAvatar(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val copiedUri = copyPickedAvatarIntoInternalStorage(sourceUri)
        val finalUriString = copiedUri ?: sourceUri.toString().takeIf { it.isNotBlank() }
        if (finalUriString != null) {
            saveAvatarUri(finalUriString)
        }
        finalUriString
    }

    private fun copyPickedAvatarIntoInternalStorage(sourceUri: Uri): String? {
        return try {
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

    /**
     * Erases personal profile fields (display name, username, custom avatar file & URI)
     * while preserving onboarding completion so the user stays in SONORA with default identity.
     */
    suspend fun resetPersonalProfileData() = withContext(Dispatchers.IO) {
        deleteOldInternalAvatars(exceptFileName = null)
        try {
            context.userProfileDataStore.edit { prefs ->
                prefs.remove(KEY_DISPLAY_NAME)
                prefs.remove(KEY_USERNAME)
                prefs.remove(KEY_AVATAR_URI)
                prefs[KEY_PROFILE_ONBOARDING_COMPLETED] = true
            }
            _onboardingState.value = ProfileOnboardingState.COMPLETED
        } catch (e: Exception) {
            Log.e(PROFILE_PREFS_TAG, "Failed resetting personal profile data", e)
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
