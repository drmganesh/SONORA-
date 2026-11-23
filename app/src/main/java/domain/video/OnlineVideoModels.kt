package domain.video

import domain.online.OnlineErrorKind
import domain.online.StreamDeliveryType
import java.util.Locale

/**
 * Home discovery sections for SONORA's Online Video mode.
 */
enum class VideoHomeSectionType(
    val id: String,
    val title: String,
    val subtitle: String,
    val isCarousel: Boolean = true
) {
    RECOMMENDED("recommended", "Recommended Videos", "Picked from your region & watch activity", isCarousel = false),
    TRENDING("trending", "Trending Videos", "Popular right now", isCarousel = true),
    POPULAR_MUSIC_VIDEOS("popular_music_videos", "Popular Music Videos", "Official music videos & chart toppers", isCarousel = true),
    RECENTLY_UPLOADED("recently_uploaded", "Recently Uploaded", "Fresh uploads across India & beyond", isCarousel = true),
    POPULAR_INDIAN("popular_indian", "Popular Indian Videos", "Top Indian creators, cinema & viral hits", isCarousel = true),
    MUSIC_VIDEOS("music_videos", "Music Videos", "Live sessions, studio videos & official MVs", isCarousel = true),
    GAMING("gaming", "Gaming", "Gameplay, esports & Indian gaming creators", isCarousel = true),
    ENTERTAINMENT("entertainment", "Entertainment", "Comedy, cinema trailers & talk shows", isCarousel = true),
    EDUCATIONAL("educational", "Educational Videos", "Science, history, coding & exam explainers", isCarousel = true),
    TECHNOLOGY("technology", "Technology", "Reviews, Android, AI & hardware deep dives", isCarousel = true),
    REGIONAL("regional", "Regional Videos", "Hindi, Odia, Tamil, Telugu & more", isCarousel = true),
    RECENTLY_WATCHED("recently_watched", "Recently Watched", "Continue watching from your local history", isCarousel = true)
}

/**
 * Regional Indian language filters for Online Video discovery.
 */
enum class VideoRegionalLanguage(
    val id: String,
    val label: String,
    val queryKeyword: String
) {
    ALL_INDIA("all_india", "All India", "India"),
    HINDI("hindi", "Hindi", "Hindi"),
    ODIA("odia", "Odia", "Odia"),
    TAMIL("tamil", "Tamil", "Tamil"),
    TELUGU("telugu", "Telugu", "Telugu"),
    MALAYALAM("malayalam", "Malayalam", "Malayalam"),
    KANNADA("kannada", "Kannada", "Kannada"),
    BENGALI("bengali", "Bengali", "Bengali"),
    PUNJABI("punjabi", "Punjabi", "Punjabi");

    companion object {
        fun fromId(raw: String?): VideoRegionalLanguage =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: ALL_INDIA
    }
}

enum class VideoSearchFilter(val id: String, val label: String, val newPipeFilter: String) {
    VIDEOS("videos", "Videos", "videos"),
    CHANNELS("channels", "Channels", "channels"),
    PLAYLISTS("playlists", "Playlists", "playlists")
}

enum class VideoRatingState {
    NONE,
    LIKED,
    DISLIKED;

    companion object {
        fun fromName(raw: String?): VideoRatingState =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

enum class ChannelNotificationPreference(val id: String, val label: String, val description: String) {
    ALL("all", "All", "Receive every upload and live alert from this channel"),
    PERSONALIZED("personalized", "Personalized", "Receive occasional highlights and popular uploads"),
    NONE("none", "None", "Mute notifications from this channel");

    companion object {
        fun fromId(raw: String?): ChannelNotificationPreference =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

data class OnlineVideoItem(
    val videoId: String,
    val stableId: Long = videoId.hashCode().toLong(),
    val contentUrl: String,
    val title: String,
    val channelName: String,
    val channelUrl: String? = null,
    val channelAvatarUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L,
    val viewCount: Long? = null,
    val uploadDateText: String? = null,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val providerKey: String = "youtube",
    val providerName: String = "YouTube",
    val isWatchLater: Boolean = false,
    val isSaved: Boolean = false,
    val lastPositionMs: Long = 0L,
    val lastWatchedAt: Long = 0L,
    val likedAt: Long = 0L
) {
    val durationMs: Long
        get() = (durationSeconds.coerceAtLeast(0L)) * 1000L

    fun formattedDuration(): String {
        if (isLive) return "LIVE"
        val totalSec = durationSeconds.coerceAtLeast(0L)
        if (totalSec <= 0L) return ""
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }

    fun formattedViewCount(): String? {
        val count = viewCount ?: return null
        if (count < 0L) return null
        return when {
            count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB views", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format(Locale.US, "%.1fM views", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale.US, "%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
    }

    fun formattedMetadataLine(): String {
        return listOfNotNull(
            channelName.takeIf { it.isNotBlank() },
            formattedViewCount(),
            uploadDateText?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }

    fun formattedLikedDate(nowMs: Long = System.currentTimeMillis()): String? {
        if (likedAt <= 0L) return null
        val diffMs = (nowMs - likedAt).coerceAtLeast(0L)
        val minuteMs = 60_000L
        val hourMs = 60 * minuteMs
        val dayMs = 24 * hourMs
        return when {
            diffMs < minuteMs -> "Liked just now"
            diffMs < hourMs -> "Liked ${diffMs / minuteMs}m ago"
            diffMs < dayMs -> "Liked ${diffMs / hourMs}h ago"
            diffMs < 7 * dayMs -> {
                val days = diffMs / dayMs
                if (days == 1L) "Liked yesterday" else "Liked ${days}d ago"
            }
            else -> {
                val fmt = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                "Liked ${fmt.format(java.util.Date(likedAt))}"
            }
        }
    }
}

enum class LikedVideoSortOption(val id: String, val label: String) {
    RECENTLY_LIKED("recently_liked", "Recently Liked"),
    TITLE("title", "Title"),
    CHANNEL("channel", "Channel"),
    DATE("date", "Date");

    companion object {
        fun sort(videos: List<OnlineVideoItem>, option: LikedVideoSortOption): List<OnlineVideoItem> {
            return when (option) {
                RECENTLY_LIKED -> videos.sortedWith(
                    compareByDescending<OnlineVideoItem> { it.likedAt }
                        .thenByDescending { it.lastWatchedAt }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
                TITLE -> videos.sortedWith(
                    compareBy<OnlineVideoItem> { it.title.lowercase(Locale.ROOT) }
                        .thenByDescending { it.likedAt }
                )
                CHANNEL -> videos.sortedWith(
                    compareBy<OnlineVideoItem> { it.channelName.lowercase(Locale.ROOT) }
                        .thenByDescending { it.likedAt }
                )
                DATE -> videos.sortedWith(
                    compareBy<OnlineVideoItem> { estimateUploadAgeRank(it.uploadDateText) }
                        .thenByDescending { it.likedAt }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
            }
        }

        private fun estimateUploadAgeRank(uploadDateText: String?): Long {
            val raw = uploadDateText?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (raw.isBlank()) return Long.MAX_VALUE / 2
            val num = Regex("(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 1L
            return when {
                raw.contains("just now") || raw.contains("second") -> num
                raw.contains("minute") -> num * 60L
                raw.contains("hour") -> num * 3_600L
                raw.contains("day") -> num * 86_400L
                raw.contains("week") -> num * 604_800L
                raw.contains("month") -> num * 2_592_000L
                raw.contains("year") -> num * 31_536_000L
                else -> Long.MAX_VALUE / 4
            }
        }
    }
}

data class OnlineVideoChannel(
    val channelId: String,
    val stableId: Long = channelId.hashCode().toLong(),
    val channelUrl: String,
    val name: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: Long? = null,
    val description: String? = null,
    val isVerified: Boolean = false,
    val isSubscribedLocal: Boolean = false,
    val isSubscribedOnProvider: Boolean = false,
    val notificationPreference: ChannelNotificationPreference = ChannelNotificationPreference.NONE,
    val videos: List<OnlineVideoItem> = emptyList(),
    val handle: String? = null,
    val tags: List<String> = emptyList(),
    val externalLinks: List<ChannelExternalLink> = emptyList(),
    val joinedDateText: String? = null,
    val totalViews: Long? = null,
    val availableTabs: List<ChannelTabType> = listOf(ChannelTabType.VIDEOS, ChannelTabType.ABOUT)
) {
    fun formattedSubscribers(): String? {
        val count = subscriberCount ?: return null
        if (count < 0L) return null
        return when {
            count >= 1_000_000L -> String.format(Locale.US, "%.2fM subscribers", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale.US, "%.1fK subscribers", count / 1_000.0)
            else -> "$count subscribers"
        }
    }

    fun formattedTotalViews(): String? {
        val count = totalViews ?: return null
        if (count < 0L) return null
        return when {
            count >= 1_000_000_000L -> String.format(Locale.US, "%.2fB views", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format(Locale.US, "%.1fM views", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale.US, "%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
    }
}

enum class ChannelTabType(val id: String, val label: String) {
    VIDEOS("videos", "VIDEOS"),
    SHORTS("shorts", "SHORTS"),
    PLAYLISTS("playlists", "PLAYLISTS"),
    ABOUT("about", "ABOUT")
}

data class ChannelExternalLink(
    val label: String,
    val url: String
)

sealed interface ChannelTabPageState {
    data object Idle : ChannelTabPageState
    data object Loading : ChannelTabPageState
    data class Success(
        val videos: List<OnlineVideoItem> = emptyList(),
        val playlists: List<OnlineVideoPlaylistSummary> = emptyList(),
        val nextPageToken: String? = null,
        val isLoadingMore: Boolean = false
    ) : ChannelTabPageState
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val isRetryable: Boolean = true
    ) : ChannelTabPageState
}

sealed interface ChannelPlaylistDetailState {
    data object Idle : ChannelPlaylistDetailState
    data class Loading(val title: String, val thumbnailUrl: String? = null) : ChannelPlaylistDetailState
    data class Success(
        val playlist: OnlineVideoPlaylistSummary,
        val nextPageToken: String? = null,
        val isLoadingMore: Boolean = false
    ) : ChannelPlaylistDetailState
    data class Error(
        val message: String,
        val playlistUrl: String,
        val title: String
    ) : ChannelPlaylistDetailState
}

object ChannelDetailUtils {
    private val URL_REGEX = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", RegexOption.IGNORE_CASE)
    private val HANDLE_REGEX = Regex("/(@[A-Za-z0-9._\\-]{2,64})")

    /**
     * Normalizes a channel identifier/URL to a stable cache key so channel pages are keyed
     * by actual channel identity rather than display name.
     */
    fun canonicalChannelKey(channelUrl: String, channelId: String? = null): String {
        val cleanId = channelId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanId != null) return cleanId.lowercase(Locale.US)
        val cleanUrl = channelUrl.trim()
            .substringBefore("?")
            .substringBefore("#")
            .trimEnd('/')
        val lastSegment = cleanUrl.substringAfterLast('/')
        return lastSegment.ifBlank { cleanUrl }.lowercase(Locale.US)
    }

    /**
     * Extracts a public @handle from a channel URL when present (e.g. youtube.com/@sonora -> @sonora).
     */
    fun extractHandleFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val match = HANDLE_REGEX.find(url.trim()) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.startsWith("@") }
    }

    /**
     * Parses real external http/https links embedded in a channel's description text.
     */
    fun extractExternalLinks(description: String?): List<ChannelExternalLink> {
        if (description.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<ChannelExternalLink>()
        URL_REGEX.findAll(description).forEach { match ->
            val rawUrl = match.value.trimEnd('.', ',', ';', ')', ']', '"', '\'')
            val normalizedKey = rawUrl.lowercase(Locale.US)
            if (rawUrl.length > 10 && seen.add(normalizedKey)) {
                val host = rawUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore('/')
                    .ifBlank { rawUrl }
                results.add(ChannelExternalLink(label = host, url = rawUrl))
            }
        }
        return results.take(8)
    }

    /**
     * Builds the ordered list of visible channel tabs from the extractor-supported tab filters.
     * Unsupported tabs are omitted rather than populated with fake content.
     */
    fun resolveAvailableTabs(supportedContentTabs: Set<ChannelTabType>): List<ChannelTabType> {
        val ordered = mutableListOf<ChannelTabType>()
        if (ChannelTabType.VIDEOS in supportedContentTabs) ordered.add(ChannelTabType.VIDEOS)
        if (ChannelTabType.SHORTS in supportedContentTabs) ordered.add(ChannelTabType.SHORTS)
        if (ChannelTabType.PLAYLISTS in supportedContentTabs) ordered.add(ChannelTabType.PLAYLISTS)
        ordered.add(ChannelTabType.ABOUT)
        return ordered
    }

    /**
     * Merges paginated video items without duplicating existing video IDs while preserving order.
     */
    fun mergeVideosUnique(
        existing: List<OnlineVideoItem>,
        incoming: List<OnlineVideoItem>
    ): List<OnlineVideoItem> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming.distinctBy { it.videoId }
        val seen = HashSet<String>(existing.size + incoming.size)
        val merged = ArrayList<OnlineVideoItem>(existing.size + incoming.size)
        existing.forEach { item ->
            if (seen.add(item.videoId)) merged.add(item)
        }
        incoming.forEach { item ->
            if (seen.add(item.videoId)) merged.add(item)
        }
        return merged
    }

    /**
     * Merges paginated channel playlists without duplicating playlist URLs/titles.
     */
    fun mergePlaylistsUnique(
        existing: List<OnlineVideoPlaylistSummary>,
        incoming: List<OnlineVideoPlaylistSummary>
    ): List<OnlineVideoPlaylistSummary> {
        if (incoming.isEmpty()) return existing
        val keyOf: (OnlineVideoPlaylistSummary) -> String = { pl ->
            pl.externalUrl?.trim()?.lowercase(Locale.US)
                ?: "${pl.id}:${pl.name.trim().lowercase(Locale.US)}"
        }
        val seen = HashSet<String>(existing.size + incoming.size)
        val merged = ArrayList<OnlineVideoPlaylistSummary>(existing.size + incoming.size)
        existing.forEach { pl ->
            if (seen.add(keyOf(pl))) merged.add(pl)
        }
        incoming.forEach { pl ->
            if (seen.add(keyOf(pl))) merged.add(pl)
        }
        return merged
    }

    /**
     * Computes a 0f..1f watch progress fraction for a video when valid duration & position exist.
     */
    fun computeWatchProgress(lastPositionMs: Long, durationSeconds: Long): Float? {
        if (lastPositionMs <= 2_000L || durationSeconds <= 0L) return null
        val durationMs = durationSeconds * 1000L
        val ratio = lastPositionMs.toFloat() / durationMs.toFloat()
        if (ratio < 0.02f) return null
        return ratio.coerceIn(0f, 1f)
    }
}

data class OnlineVideoPlaylistSummary(
    val id: Long = 0L,
    val externalUrl: String? = null,
    val name: String,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val videoCount: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val videos: List<OnlineVideoItem> = emptyList()
) {
    val displayThumbnailUrl: String?
        get() = thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: videos.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
}

data class VideoStreamOption(
    val id: String,
    val url: String,
    val resolutionLabel: String,
    val heightPx: Int,
    val widthPx: Int = 0,
    val fps: Int = 30,
    val mimeType: String,
    val formatLabel: String,
    val isVideoOnly: Boolean,
    val bitrateKbps: Int = 0,
    val codec: String = "",
    val deliveryType: StreamDeliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
    val headers: Map<String, String> = emptyMap()
) {
    val isAv1Codec: Boolean
        get() = codec.startsWith("av01", ignoreCase = true) || mimeType.contains("av01", ignoreCase = true)

    val isVp9Codec: Boolean
        get() = codec.startsWith("vp9", ignoreCase = true) ||
            codec.startsWith("vp09", ignoreCase = true) ||
            mimeType.contains("webm", ignoreCase = true)

    val isAvcH264Codec: Boolean
        get() = codec.startsWith("avc", ignoreCase = true) ||
            (mimeType.contains("mp4", ignoreCase = true) && !isAv1Codec && !codec.startsWith("vp", ignoreCase = true))

    val codecFamily: String
        get() = when {
            isAv1Codec -> "AV1"
            isVp9Codec -> "VP9"
            isAvcH264Codec -> "H.264"
            codec.isNotBlank() -> codec.uppercase()
            else -> formatLabel.ifBlank { "Video" }
        }

    val qualityTierLabel: String
        get() = VideoAdaptiveQualitySelector.formatResolutionLabel(heightPx, resolutionLabel)

    val displayLabel: String
        get() = buildString {
            append(qualityTierLabel)
            if (fps >= 50) append(" ${fps}fps")
            if (formatLabel.isNotBlank()) append(" • $formatLabel")
        }
}

data class VideoAudioOption(
    val id: String,
    val url: String,
    val languageLabel: String,
    val locale: String? = null,
    val mimeType: String,
    val formatLabel: String,
    val bitrateKbps: Int = 0,
    val headers: Map<String, String> = emptyMap()
) {
    val displayLabel: String
        get() = buildString {
            append(languageLabel.ifBlank { "Original Audio" })
            if (bitrateKbps > 0) append(" (${bitrateKbps} kbps)")
        }
}

data class VideoSubtitleOption(
    val id: String,
    val url: String,
    val languageTag: String,
    val displayName: String,
    val mimeType: String,
    val isAutoGenerated: Boolean = false
)

data class VideoCommentItem(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    val likeCount: Long? = null,
    val publishedText: String? = null,
    val replyCount: Int = 0
)

data class ResolvedVideoDetails(
    val video: OnlineVideoItem,
    val description: String? = null,
    val likeCount: Long? = null,
    val dislikeCount: Long? = null,
    val viewCount: Long? = null,
    val uploadDateText: String? = null,
    val channelSubscriberCount: Long? = null,
    val channelAvatarUrl: String? = null,
    val videoStreams: List<VideoStreamOption> = emptyList(),
    val audioStreams: List<VideoAudioOption> = emptyList(),
    val subtitles: List<VideoSubtitleOption> = emptyList(),
    val hlsUrl: String? = null,
    val dashMpdUrl: String? = null,
    val relatedVideos: List<OnlineVideoItem> = emptyList(),
    val comments: List<VideoCommentItem> = emptyList(),
    val extractedAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long = System.currentTimeMillis() + 15 * 60_000L
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= (expiresAtMs - 30_000L)

    fun formattedLikeCount(): String? {
        val count = likeCount ?: return null
        if (count < 0L) return null
        return when {
            count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}

sealed interface VideoSectionState {
    data object Idle : VideoSectionState
    data object Loading : VideoSectionState
    data class Success(
        val items: List<OnlineVideoItem>,
        val nextPageToken: String? = null,
        val isLoadingMore: Boolean = false,
        val sourceLabel: String = "YouTube"
    ) : VideoSectionState
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val isRetryable: Boolean = true
    ) : VideoSectionState
}

fun defaultVideoSectionsState(): Map<VideoHomeSectionType, VideoSectionState> =
    VideoHomeSectionType.entries.associateWith { VideoSectionState.Idle }

sealed interface VideoSearchUiState {
    data object Idle : VideoSearchUiState
    data class Loading(val query: String) : VideoSearchUiState
    data class Success(
        val query: String,
        val filter: VideoSearchFilter,
        val videos: List<OnlineVideoItem> = emptyList(),
        val channels: List<OnlineVideoChannel> = emptyList(),
        val playlists: List<OnlineVideoPlaylistSummary> = emptyList(),
        val nextPageToken: String? = null,
        val isLoadingMore: Boolean = false
    ) : VideoSearchUiState {
        val isEmpty: Boolean
            get() = videos.isEmpty() && channels.isEmpty() && playlists.isEmpty()
    }
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val isRetryable: Boolean = true
    ) : VideoSearchUiState
}

sealed interface VideoChannelUiState {
    data object Idle : VideoChannelUiState
    data class Loading(
        val name: String,
        val channelUrl: String = "",
        val avatarUrl: String? = null
    ) : VideoChannelUiState
    data class Success(
        val channel: OnlineVideoChannel,
        val selectedTab: ChannelTabType = channel.availableTabs.firstOrNull() ?: ChannelTabType.VIDEOS,
        val availableTabs: List<ChannelTabType> = channel.availableTabs,
        val tabStates: Map<ChannelTabType, ChannelTabPageState> = emptyMap()
    ) : VideoChannelUiState {
        val currentVideos: List<OnlineVideoItem>
            get() = (tabStates[ChannelTabType.VIDEOS] as? ChannelTabPageState.Success)?.videos
                ?: channel.videos
    }
    data class Error(
        val message: String,
        val channelUrl: String,
        val channelName: String,
        val kind: OnlineErrorKind = OnlineErrorKind.EXTRACTION_FAILED
    ) : VideoChannelUiState
}

sealed interface ProviderAuthState {
    data object Disconnected : ProviderAuthState
    data object Authorizing : ProviderAuthState
    data class Connected(
        val accountLabel: String,
        val accessToken: String,
        val expiresAtMs: Long,
        val scopes: Set<String>
    ) : ProviderAuthState {
        fun isTokenValid(nowMs: Long = System.currentTimeMillis()): Boolean =
            accessToken.isNotBlank() && nowMs < (expiresAtMs - 60_000L)
    }
    data class Error(val message: String) : ProviderAuthState
}

sealed interface VideoAccountActionResult {
    data class Confirmed(
        val message: String,
        val updatedRating: VideoRatingState? = null,
        val updatedSubscribed: Boolean? = null,
        val updatedNotificationPref: ChannelNotificationPreference? = null
    ) : VideoAccountActionResult

    data class RequiresAuthorization(
        val actionTitle: String,
        val explanation: String,
        val officialWebUrl: String,
        val canStartOAuthFlow: Boolean
    ) : VideoAccountActionResult

    data class UnsupportedByProviderApi(
        val actionTitle: String,
        val explanation: String,
        val officialWebUrl: String
    ) : VideoAccountActionResult

    data class Failed(
        val message: String,
        val officialWebUrl: String? = null
    ) : VideoAccountActionResult
}

data class VideoPlayerUiState(
    val activeVideo: OnlineVideoItem? = null,
    val resolvedDetails: ResolvedVideoDetails? = null,
    val isResolving: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val selectedVideoStream: VideoStreamOption? = null,
    val selectedAudioStream: VideoAudioOption? = null,
    val selectedSubtitle: VideoSubtitleOption? = null,
    val isPlayerExpanded: Boolean = false,
    val isFullScreenLandscape: Boolean = false,
    val isInPictureInPicture: Boolean = false,
    val queue: List<OnlineVideoItem> = emptyList(),
    val queueIndex: Int = -1,
    val errorMessage: String? = null,
    val errorKind: OnlineErrorKind? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
    val unappliedRotationDegrees: Int = 0,
    val hasKnownVideoDimensions: Boolean = false,
    val aspectRatioMode: VideoAspectRatioMode = VideoAspectRatioMode.AUTO,
    val gestureSettings: VideoPlayerGestureSettings = VideoPlayerGestureSettings(),
    val activeSubtitleCues: List<String> = emptyList(),
    val ratingState: VideoRatingState = VideoRatingState.NONE,
    val isSubscribedLocally: Boolean = false,
    val isSubscribedOnProvider: Boolean = false,
    val notificationPreference: ChannelNotificationPreference = ChannelNotificationPreference.NONE,
    val supportsNotificationApi: Boolean = false,
    val backgroundPlaybackAllowed: Boolean = false,
    val isAutoQualityMode: Boolean = true,
    val qualityStatusLabel: String = "Auto: Best Available",
    val estimatedBandwidthBps: Long = 0L,
    val actualVideoCodec: String = "",
    val actualVideoMimeType: String = "",
    val actualVideoBitrateKbps: Int = 0,
    val actualVideoFps: Float = 0f,
    val isUsingAdaptiveManifest: Boolean = false,
    val videoExperienceSettings: data.preferences.VideoExperienceSettings = data.preferences.VideoExperienceSettings(),
    val hasVideoEnded: Boolean = false,
    val isLocalOfflineVideo: Boolean = false
) {
    val hasActiveVideo: Boolean get() = activeVideo != null
    val hasPreviousInQueue: Boolean get() = queueIndex > 0 && queue.isNotEmpty()
    val hasNextInQueue: Boolean get() = queueIndex in 0 until (queue.size - 1)

    /**
     * Actual rendered resolution height grounded in ExoPlayer's live video track/surface dimensions,
     * falling back to selected stream height only before the first frame decodes.
     */
    val activePlaybackHeightPx: Int
        get() = when {
            videoHeight > 0 && videoWidth > 0 -> minOf(videoWidth, videoHeight)
            videoHeight > 0 -> videoHeight
            else -> selectedVideoStream?.heightPx ?: 0
        }

    val actualResolutionDisplay: String
        get() {
            val h = activePlaybackHeightPx
            val label = if (h > 0) VideoAdaptiveQualitySelector.formatResolutionLabel(h) else "Resolving..."
            return if (videoWidth > 0 && videoHeight > 0) {
                "$label (${videoWidth}×${videoHeight})"
            } else {
                label
            }
        }

    val actualFormatDisplay: String
        get() {
            val codecLabel = when {
                actualVideoCodec.startsWith("av01", ignoreCase = true) -> "AV1"
                actualVideoCodec.startsWith("vp09", ignoreCase = true) || actualVideoCodec.startsWith("vp9", ignoreCase = true) -> "VP9"
                actualVideoCodec.startsWith("avc", ignoreCase = true) -> "H.264"
                actualVideoCodec.isNotBlank() -> actualVideoCodec
                selectedVideoStream != null -> selectedVideoStream.codecFamily
                else -> "Standard"
            }
            val deliveryLabel = when {
                isLocalOfflineVideo -> "Offline File"
                isUsingAdaptiveManifest -> "Adaptive Stream"
                selectedVideoStream?.isVideoOnly == true -> "Adaptive Video + Audio"
                selectedVideoStream != null -> "Muxed Stream"
                else -> "Stream"
            }
            val fpsValue = if (actualVideoFps >= 1f) actualVideoFps.toInt() else (selectedVideoStream?.fps ?: 0)
            val bitrateValue = actualVideoBitrateKbps.takeIf { it > 0 } ?: (selectedVideoStream?.bitrateKbps ?: 0)
            return buildList {
                add(codecLabel)
                add(deliveryLabel)
                if (fpsValue >= 50) add("${fpsValue}fps")
                if (bitrateValue > 0) add("${bitrateValue} kbps")
            }.joinToString(" • ")
        }

    val effectiveVideoDimensions: Pair<Float, Float>
        get() = VideoAspectRatioCalculator.resolveEffectiveDimensions(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )

    val aspectRatio: Float
        get() = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            pixelWidthHeightRatio = pixelWidthHeightRatio,
            unappliedRotationDegrees = unappliedRotationDegrees
        )

    val detectedAspectRatioLabel: String
        get() = VideoAspectRatioCalculator.detectRatioLabel(aspectRatio)
}

/**
 * Isolated high-frequency playback timeline state so periodic progress ticks
 * only recompose the player timeline & mini-player progress strip.
 */
data class VideoPlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val estimatedBandwidthBps: Long = 0L
)

enum class VideoNotificationType(val id: String, val badgeLabel: String) {
    NEW_VIDEO("new_video", "New video"),
    NEW_CHANNEL_CONTENT("new_channel_content", "New content"),
    CHANNEL_UPDATE("channel_update", "Channel update");

    companion object {
        fun fromId(raw: String?): VideoNotificationType =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: NEW_VIDEO
    }
}

data class VideoNotificationItem(
    val notificationId: String,
    val type: VideoNotificationType,
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val channelAvatarUrl: String? = null,
    val videoId: String? = null,
    val videoUrl: String? = null,
    val videoTitle: String? = null,
    val thumbnailUrl: String? = null,
    val uploadDateText: String? = null,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    val isVideoNotification: Boolean
        get() = !videoId.isNullOrBlank() && !videoUrl.isNullOrBlank()
}

