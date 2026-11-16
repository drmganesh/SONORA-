package domain.online

import android.net.Uri
import com.example.model.Song

enum class OnlineItemKind {
    TRACK,
    ARTIST,
    ALBUM,
    PLAYLIST
}

data class OnlineTrack(
    val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val artistUrl: String? = null,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val viewCount: Long = -1L,
    val isLive: Boolean = false,
    val kind: OnlineItemKind = OnlineItemKind.TRACK,
    val trackCount: Long = -1L,
    val subscriberCount: Long = -1L,
    val isSaved: Boolean = false,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
) {
    val durationFormatted: String
        get() {
            if (isLive) return "LIVE"
            if (durationMs <= 0L) return "--:--"
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
}

data class OnlineSearchPageResult(
    val query: String,
    val providerKey: String,
    val filter: OnlineSearchFilter,
    val items: List<OnlineTrack>,
    val nextPageToken: String? = null,
    val hasNextPage: Boolean = false,
    val correctedSuggestion: String? = null
)

data class OnlineCollectionDetails(
    val title: String,
    val subtitle: String,
    val providerName: String,
    val contentUrl: String,
    val artworkUrl: String?,
    val tracks: List<OnlineTrack>
)

enum class OnlineErrorKind {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED_OR_CAPTCHA,
    UNSUPPORTED,
    AGE_OR_GEO_RESTRICTED,
    STREAM_EXPIRED,
    EXTRACTION_FAILED,
    NO_PLAYABLE_AUDIO_STREAM
}

sealed interface OnlineResult<out T> {
    data class Success<T>(val value: T) : OnlineResult<T>
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : OnlineResult<Nothing>
}

sealed interface PlaybackSource {
    data class LocalFile(
        val songId: Long,
        val uri: Uri,
        val fallbackPath: String?
    ) : PlaybackSource

    data class OnlineStream(
        val song: Song,
        val serviceId: Int,
        val providerKey: String,
        val contentUrl: String
    ) : PlaybackSource
}
