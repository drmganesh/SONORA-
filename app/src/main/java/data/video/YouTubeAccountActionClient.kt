package data.video

import android.util.Log
import domain.video.ChannelNotificationPreference
import domain.video.ProviderAuthState
import domain.video.VideoAccountActionResult
import domain.video.VideoRatingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Abstraction over official provider account actions (YouTube Data API v3 + OAuth2).
 *
 * Strict Honesty & Compliance Contract:
 * 1. Never fakes a Like, Dislike, or Subscription unless the official API confirms HTTP 2xx.
 * 2. Never stores account passwords — only short-lived OAuth2 access tokens with explicit scopes.
 * 3. Transparently reports that YouTube Data API v3 does NOT expose per-channel notification bell
 *    preferences (All / Personalized / None) to third-party apps, providing a direct link to the
 *    official provider page instead.
 */
interface VideoAccountActionProvider {
    val supportsNotificationPreferenceApi: Boolean

    suspend fun getVideoRating(videoId: String, authState: ProviderAuthState): VideoRatingState?

    suspend fun rateVideo(
        videoId: String,
        targetRating: VideoRatingState,
        authState: ProviderAuthState
    ): VideoAccountActionResult

    suspend fun isChannelSubscribed(channelId: String, authState: ProviderAuthState): Boolean?

    suspend fun setChannelSubscription(
        channelId: String,
        channelUrl: String,
        subscribe: Boolean,
        authState: ProviderAuthState
    ): VideoAccountActionResult

    suspend fun updateChannelNotificationPreference(
        channelId: String,
        channelUrl: String,
        preference: ChannelNotificationPreference,
        authState: ProviderAuthState
    ): VideoAccountActionResult
}

/**
 * Real YouTube Data API v3 implementation using OAuth2 Bearer tokens.
 */
class YouTubeAccountActionClient(
    private val httpClient: OkHttpClient = sharedHttpClient,
    private val oauthClientConfigured: Boolean = false
) : VideoAccountActionProvider {

    companion object {
        private const val TAG = "YouTubeAccountClient"
        private const val API_BASE = "https://www.googleapis.com/youtube/v3"
        private const val MAX_API_RESPONSE_BYTES = 512L * 1024L
        const val SCOPE_YOUTUBE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl"
        const val SCOPE_YOUTUBE = "https://www.googleapis.com/auth/youtube"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        fun buildVideoWebUrl(videoId: String): String =
            "https://www.youtube.com/watch?v=${URLEncoder.encode(videoId.trim(), "UTF-8")}"

        fun buildChannelWebUrl(channelId: String, fallbackUrl: String?): String {
            val safeFallback = util.SecureUrlValidator.parseSafeHttpUrl(
                rawUrl = fallbackUrl,
                allowHttp = false,
                allowPrivateHosts = false
            )
            return safeFallback?.toString()
                ?: "https://www.youtube.com/channel/${URLEncoder.encode(channelId.trim(), "UTF-8")}"
        }
    }

    /**
     * YouTube Data API v3 does not expose an authorized endpoint to read or modify
     * per-channel notification bell states (All / Personalized / None).
     */
    override val supportsNotificationPreferenceApi: Boolean = false

    private fun extractValidToken(authState: ProviderAuthState): String? {
        val connected = authState as? ProviderAuthState.Connected ?: return null
        if (!connected.isTokenValid()) return null
        val hasScope = connected.scopes.isEmpty() ||
            connected.scopes.any { it == SCOPE_YOUTUBE_FORCE_SSL || it == SCOPE_YOUTUBE }
        return if (hasScope) connected.accessToken else null
    }

    override suspend fun getVideoRating(videoId: String, authState: ProviderAuthState): VideoRatingState? =
        withContext(Dispatchers.IO) {
            val token = extractValidToken(authState) ?: return@withContext null
            runCatching {
                val encodedId = URLEncoder.encode(videoId, "UTF-8")
                val request = Request.Builder()
                    .url("$API_BASE/videos/getRating?id=$encodedId")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = util.SecureUrlValidator.readBoundedString(response.body, MAX_API_RESPONSE_BYTES)
                    val items = JSONObject(body).optJSONArray("items") ?: return@use null
                    if (items.length() == 0) return@use VideoRatingState.NONE
                    when (items.getJSONObject(0).optString("rating", "none").lowercase()) {
                        "like" -> VideoRatingState.LIKED
                        "dislike" -> VideoRatingState.DISLIKED
                        else -> VideoRatingState.NONE
                    }
                }
            }.onFailure {
                Log.w(TAG, "getVideoRating failed for $videoId: ${it.javaClass.simpleName}")
            }.getOrNull()
        }

    override suspend fun rateVideo(
        videoId: String,
        targetRating: VideoRatingState,
        authState: ProviderAuthState
    ): VideoAccountActionResult = withContext(Dispatchers.IO) {
        val webUrl = buildVideoWebUrl(videoId)
        val token = extractValidToken(authState)
            ?: return@withContext VideoAccountActionResult.RequiresAuthorization(
                actionTitle = when (targetRating) {
                    VideoRatingState.LIKED -> "Like Video"
                    VideoRatingState.DISLIKED -> "Dislike Video"
                    VideoRatingState.NONE -> "Update Video Rating"
                },
                explanation = "NewPipe Extractor cannot submit account likes or dislikes. Sign in with an authorized YouTube Data API v3 OAuth token, or open this video directly in YouTube.",
                officialWebUrl = webUrl,
                canStartOAuthFlow = oauthClientConfigured
            )

        val apiRating = when (targetRating) {
            VideoRatingState.LIKED -> "like"
            VideoRatingState.DISLIKED -> "dislike"
            VideoRatingState.NONE -> "none"
        }

        return@withContext try {
            val encodedId = URLEncoder.encode(videoId, "UTF-8")
            val request = Request.Builder()
                .url("$API_BASE/videos/rate?id=$encodedId&rating=$apiRating")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> VideoAccountActionResult.Confirmed(
                        message = when (targetRating) {
                            VideoRatingState.LIKED -> "Liked on YouTube"
                            VideoRatingState.DISLIKED -> "Disliked on YouTube"
                            VideoRatingState.NONE -> "Rating cleared on YouTube"
                        },
                        updatedRating = targetRating
                    )
                    response.code == 401 || response.code == 403 -> VideoAccountActionResult.RequiresAuthorization(
                        actionTitle = "Re-authorize YouTube Account",
                        explanation = "Your YouTube OAuth session expired or lacks youtube.force-ssl permission (HTTP ${response.code}).",
                        officialWebUrl = webUrl,
                        canStartOAuthFlow = oauthClientConfigured
                    )
                    else -> VideoAccountActionResult.Failed(
                        message = "YouTube API rejected the rating request (HTTP ${response.code}).",
                        officialWebUrl = webUrl
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rateVideo failed for $videoId: ${t.javaClass.simpleName}")
            VideoAccountActionResult.Failed(
                message = "Could not reach YouTube API due to a network error. Please try again.",
                officialWebUrl = webUrl
            )
        }
    }

    override suspend fun isChannelSubscribed(channelId: String, authState: ProviderAuthState): Boolean? =
        withContext(Dispatchers.IO) {
            val token = extractValidToken(authState) ?: return@withContext null
            findSubscriptionResourceId(channelId, token) != null
        }

    private fun findSubscriptionResourceId(channelId: String, token: String): String? {
        return runCatching {
            val encodedChannel = URLEncoder.encode(channelId, "UTF-8")
            val request = Request.Builder()
                .url("$API_BASE/subscriptions?part=id&mine=true&forChannelId=$encodedChannel")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = util.SecureUrlValidator.readBoundedString(response.body, MAX_API_RESPONSE_BYTES)
                val items = JSONObject(body).optJSONArray("items") ?: return@use null
                if (items.length() > 0) items.getJSONObject(0).optString("id").takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    override suspend fun setChannelSubscription(
        channelId: String,
        channelUrl: String,
        subscribe: Boolean,
        authState: ProviderAuthState
    ): VideoAccountActionResult = withContext(Dispatchers.IO) {
        val webUrl = buildChannelWebUrl(channelId, channelUrl)
        val token = extractValidToken(authState)
            ?: return@withContext VideoAccountActionResult.RequiresAuthorization(
                actionTitle = if (subscribe) "Subscribe to Channel" else "Unsubscribe from Channel",
                explanation = "Account subscriptions require official YouTube Data API v3 OAuth authorization. You can bookmark this channel locally in ${domain.branding.AppBranding.APP_NAME} or open it in YouTube to manage your account subscription.",
                officialWebUrl = webUrl,
                canStartOAuthFlow = oauthClientConfigured
            )

        return@withContext try {
            if (subscribe) {
                val payload = JSONObject().apply {
                    put("snippet", JSONObject().apply {
                        put("resourceId", JSONObject().apply {
                            put("kind", "youtube#channel")
                            put("channelId", channelId)
                        })
                    })
                }.toString()
                val request = Request.Builder()
                    .url("$API_BASE/subscriptions?part=snippet")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> VideoAccountActionResult.Confirmed(
                            message = "Subscribed on YouTube",
                            updatedSubscribed = true
                        )
                        response.code == 401 || response.code == 403 -> VideoAccountActionResult.RequiresAuthorization(
                            actionTitle = "Re-authorize Subscription Access",
                            explanation = "Your OAuth token is invalid or missing YouTube subscription permissions (HTTP ${response.code}).",
                            officialWebUrl = webUrl,
                            canStartOAuthFlow = oauthClientConfigured
                        )
                        else -> VideoAccountActionResult.Failed(
                            message = "YouTube API could not complete subscription (HTTP ${response.code}).",
                            officialWebUrl = webUrl
                        )
                    }
                }
            } else {
                val subId = findSubscriptionResourceId(channelId, token)
                    ?: return@withContext VideoAccountActionResult.Confirmed(
                        message = "Not subscribed on YouTube",
                        updatedSubscribed = false
                    )
                val encodedSubId = URLEncoder.encode(subId, "UTF-8")
                val request = Request.Builder()
                    .url("$API_BASE/subscriptions?id=$encodedSubId")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> VideoAccountActionResult.Confirmed(
                            message = "Unsubscribed on YouTube",
                            updatedSubscribed = false
                        )
                        response.code == 401 || response.code == 403 -> VideoAccountActionResult.RequiresAuthorization(
                            actionTitle = "Re-authorize Subscription Access",
                            explanation = "Your OAuth token is invalid or missing YouTube subscription permissions (HTTP ${response.code}).",
                            officialWebUrl = webUrl,
                            canStartOAuthFlow = oauthClientConfigured
                        )
                        else -> VideoAccountActionResult.Failed(
                            message = "YouTube API could not remove subscription (HTTP ${response.code}).",
                            officialWebUrl = webUrl
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setChannelSubscription failed for $channelId: ${t.javaClass.simpleName}")
            VideoAccountActionResult.Failed(
                message = "Could not reach YouTube API due to a network error. Please try again.",
                officialWebUrl = webUrl
            )
        }
    }

    override suspend fun updateChannelNotificationPreference(
        channelId: String,
        channelUrl: String,
        preference: ChannelNotificationPreference,
        authState: ProviderAuthState
    ): VideoAccountActionResult {
        val webUrl = buildChannelWebUrl(channelId, channelUrl)
        return VideoAccountActionResult.UnsupportedByProviderApi(
            actionTitle = "Channel Notification Preferences",
            explanation = "The official YouTube Data API v3 does not expose per-channel notification bell settings (All / Personalized / None) to third-party apps. Open this channel directly in YouTube to change alert preferences.",
            officialWebUrl = webUrl
        )
    }
}
