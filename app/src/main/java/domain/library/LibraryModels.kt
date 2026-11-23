package domain.library

import com.example.model.Song
import domain.online.OnlineItemKind
import domain.online.OnlineTrack

/**
 * Top-level segmented Library section selector.
 * Default is [OFFLINE].
 */
enum class LibrarySectionMode(val id: String, val label: String, val shortLabel: String = label) {
    OFFLINE("offline", "Offline Library", "Offline"),
    ONLINE("online", "Online Library", "Music"),
    ONLINE_VIDEO("online_video", "Online Video", "Video");

    companion object {
        val DEFAULT: LibrarySectionMode = OFFLINE

        fun fromId(raw: String?): LibrarySectionMode {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class OnlineLibrarySortOption(val label: String) {
    RECENTLY_SAVED("Recently Saved"),
    TITLE_ASC("Title (A – Z)"),
    ARTIST_ASC("Artist"),
    PROVIDER_ASC("Provider"),
    DURATION_DESC("Duration")
}

data class SavedOnlineAlbum(
    val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val trackCount: Long = -1L,
    val savedTimestamp: Long = 0L
) {
    fun toOnlineTrack(): OnlineTrack = OnlineTrack(
        stableId = stableId,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        title = title,
        artist = artist,
        album = title,
        artworkUrl = artworkUrl,
        kind = OnlineItemKind.ALBUM,
        trackCount = trackCount,
        isSaved = true
    )
}

data class FollowedOnlineArtist(
    val stableId: Long,
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val name: String,
    val artworkUrl: String? = null,
    val subscriberCount: Long = -1L,
    val followedTimestamp: Long = 0L
) {
    fun toOnlineTrack(): OnlineTrack = OnlineTrack(
        stableId = stableId,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        title = name,
        artist = name,
        artistUrl = contentUrl,
        artworkUrl = artworkUrl,
        kind = OnlineItemKind.ARTIST,
        subscriberCount = subscriberCount,
        isSaved = true
    )
}

data class OnlinePlaylist(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val externalContentUrl: String? = null,
    val providerKey: String = "",
    val providerName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val tracks: List<Song> = emptyList()
) {
    val trackCount: Int get() = tracks.size
    val displayArtworkUrl: String?
        get() = artworkUrl?.takeIf { it.isNotBlank() }
            ?: tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
}

data class OnlineLibrarySearchResults(
    val query: String,
    val tracks: List<Song>,
    val albums: List<SavedOnlineAlbum>,
    val artists: List<FollowedOnlineArtist>,
    val playlists: List<OnlinePlaylist>,
    val history: List<Song>,
    val favorites: List<Song>
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() &&
            playlists.isEmpty() && history.isEmpty() && favorites.isEmpty()
}

sealed interface LocalSongDeleteResult {
    data class Deleted(val deletedCount: Int) : LocalSongDeleteResult
    data class RequiresSystemConfirmation(
        val intentSender: android.content.IntentSender,
        val pendingSongIds: List<Long>
    ) : LocalSongDeleteResult
    data class Failed(val message: String) : LocalSongDeleteResult
}
