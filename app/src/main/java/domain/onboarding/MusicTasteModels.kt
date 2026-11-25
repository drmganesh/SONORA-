package domain.onboarding

import com.example.model.Song
import data.online.OnlineTrackMapper
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import java.util.Locale

enum class MusicTasteOnboardingState {
    LOADING,
    REQUIRED,
    COMPLETED
}

enum class MusicTasteStep {
    ARTISTS,
    GENRES,
    DISCOVER_SONGS,
    WELCOME
}

enum class MusicTasteSwipeDecision {
    LIKE,
    PASS,
    ADD_TO_LIKED_SONGS
}

enum class MusicTasteEditLaunchMode {
    FULL_ONBOARDING,
    EDIT_ARTISTS,
    EDIT_GENRES,
    RESTART_DISCOVERY
}

data class MusicTasteArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val channelUrl: String? = null,
    val providerKey: String = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
    val serviceId: Int = 0,
    val relatedToArtistName: String? = null
) {
    val normalizedKey: String
        get() = normalizeArtistName(name)

    companion object {
        fun normalizeArtistName(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}

data class MusicTasteDiscoveryTrack(
    val stableId: Long,
    val serviceId: Int = 0,
    val providerKey: String = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
    val providerName: String = "YouTube Music",
    val contentUrl: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val reasonLabel: String = "For Your Taste"
) {
    val normalizedUrl: String
        get() = contentUrl.trim().substringBefore("#").trimEnd('/').lowercase(Locale.ROOT)

    fun toOnlineTrack(): OnlineTrack = OnlineTrack(
        stableId = stableId,
        serviceId = serviceId,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = contentUrl,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        kind = OnlineItemKind.TRACK
    )

    fun toSong(isFavorite: Boolean = false): Song =
        OnlineTrackMapper.toUnifiedSong(toOnlineTrack().copy(isFavorite = isFavorite))
}

data class MusicTasteProfile(
    val selectedArtists: List<MusicTasteArtist> = emptyList(),
    val selectedGenres: List<String> = emptyList(),
    val likedDiscoveryTracks: List<MusicTasteDiscoveryTrack> = emptyList(),
    val passedDiscoveryTracks: List<MusicTasteDiscoveryTrack> = emptyList(),
    val addedToLikedSongUrls: Set<String> = emptySet(),
    val onboardingCompleted: Boolean = false,
    val lastUpdatedAtMs: Long = 0L
) {
    val hasAnyPreferences: Boolean
        get() = selectedArtists.isNotEmpty() ||
            selectedGenres.isNotEmpty() ||
            likedDiscoveryTracks.isNotEmpty() ||
            passedDiscoveryTracks.isNotEmpty()

    val selectedArtistKeys: Set<String>
        get() = selectedArtists.map { it.normalizedKey }.toSet()

    val passedTrackUrls: Set<String>
        get() = passedDiscoveryTracks.map { it.normalizedUrl }.filter { it.isNotBlank() }.toSet()

    val likedTrackUrls: Set<String>
        get() = likedDiscoveryTracks.map { it.normalizedUrl }.filter { it.isNotBlank() }.toSet()

    fun summarySubtitle(): String {
        val parts = buildList {
            if (selectedArtists.isNotEmpty()) {
                val label = if (selectedArtists.size == 1) "1 artist" else "${selectedArtists.size} artists"
                add(label)
            }
            if (selectedGenres.isNotEmpty()) {
                val label = if (selectedGenres.size == 1) "1 genre" else "${selectedGenres.size} genres"
                add(label)
            }
            if (likedDiscoveryTracks.isNotEmpty()) {
                val label = if (likedDiscoveryTracks.size == 1) "1 liked pick" else "${likedDiscoveryTracks.size} liked picks"
                add(label)
            }
        }
        return if (parts.isEmpty()) {
            "Personalize your artists, genres & song picks"
        } else {
            parts.joinToString(" • ")
        }
    }
}

data class MusicTasteArtistDiscoveryUiState(
    val artists: List<MusicTasteArtist> = emptyList(),
    val searchQuery: String = "",
    val isLoadingInitial: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingRelated: Boolean = false,
    val lastRelatedSourceName: String? = null,
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)

data class MusicTasteDiscoverySessionUiState(
    val tracks: List<MusicTasteDiscoveryTrack> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val sessionDecisions: Map<String, MusicTasteSwipeDecision> = emptyMap(),
    val historyStack: List<Pair<Int, MusicTasteSwipeDecision>> = emptyList()
) {
    val currentTrack: MusicTasteDiscoveryTrack?
        get() = tracks.getOrNull(currentIndex)

    val isSessionComplete: Boolean
        get() = tracks.isNotEmpty() && currentIndex >= tracks.size

    val totalCount: Int
        get() = tracks.size

    val displayPosition: Int
        get() = if (tracks.isEmpty()) 0 else (currentIndex + 1).coerceAtMost(tracks.size)
}

data class MusicTastePreviewState(
    val activeTrackUrl: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

object MusicTasteCatalog {
    /**
     * Curated multi-region genre catalog presented in Step 2 ("What do you listen to?").
     */
    val CURATED_GENRES: List<String> = listOf(
        "Pop",
        "Hip-hop",
        "Rock",
        "R&B",
        "Electronic",
        "Indie",
        "Alternative",
        "Metal",
        "Jazz",
        "Classical",
        "Country",
        "Folk",
        "Soul",
        "Funk",
        "Blues",
        "Punk",
        "House",
        "Techno",
        "Drum and Bass",
        "Lo-fi",
        "Ambient",
        "Reggae",
        "K-Pop",
        "Afrobeats",
        "Reggaeton",
        "Soundtracks",
        "Indian Classical",
        "Bollywood",
        "Odia",
        "Punjabi",
        "Bengali",
        "Tamil",
        "Telugu",
        "Malayalam",
        "Kannada",
        "Marathi",
        "Assamese",
        "Gujarati"
    )

    /**
     * Maps regional genres to SONORA's Indian language preference labels so selecting
     * regional genres in onboarding immediately strengthens regional recommendations.
     */
    val REGIONAL_GENRE_TO_LANGUAGE: Map<String, String> = mapOf(
        "bollywood" to "Hindi",
        "indian classical" to "Hindi",
        "odia" to "Odia",
        "punjabi" to "Punjabi",
        "bengali" to "Bengali",
        "tamil" to "Tamil",
        "telugu" to "Telugu",
        "malayalam" to "Malayalam",
        "kannada" to "Kannada",
        "marathi" to "Marathi",
        "assamese" to "Assamese",
        "gujarati" to "Gujarati"
    )

    const val DEFAULT_DISCOVERY_SESSION_SIZE = 18
}
