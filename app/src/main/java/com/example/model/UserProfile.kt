package com.example.model

/**
 * Independent User Profile model for SONORA personalization.
 * Strictly decoupled from Song and Album artwork data sources so profile photo
 * and identity remain permanent across track transitions.
 */
data class UserProfile(
    val displayName: String = DEFAULT_DISPLAY_NAME,
    val username: String = "",
    val avatarUri: String? = null
) {
    val hasCustomPhoto: Boolean
        get() = !avatarUri.isNullOrBlank()

    val formattedUsername: String
        get() = username.trim().removePrefix("@")

    companion object {
        const val DEFAULT_DISPLAY_NAME = "Music Lover"
    }
}
