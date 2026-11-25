package domain.online

/**
 * Configurable ranking weights for the local-only Sonora Recommend engine.
 * Never hardcoded to a single signal: every factor contributes transparently
 * and can be tuned or tested independently.
 */
data class SonoraRecommendWeights(
    val followedArtistWeight: Double = 5.5,
    val likedSongsSimilarityWeight: Double = 2.8,
    val playlistSimilarityWeight: Double = 2.2,
    val listeningHistoryWeight: Double = 2.0,
    val recentListeningWeight: Double = 1.6,
    val genreAndLanguageWeight: Double = 1.4,
    val freshnessAndAvailabilityWeight: Double = 1.2,
    val positiveInteractionBoost: Double = 1.5,
    val skipPenalty: Double = -1.6,
    val dislikeArtistPenalty: Double = -4.0,
    val alreadyLikedPenalty: Double = -2.6,
    val previouslyShownPenalty: Double = -1.8,
    val maxTracksPerArtist: Int = 2,
    val familiarFavoriteRatio: Double = 0.20,
    val targetCount: Int = 16,
    val minMeaningfulListenMs: Long = 20_000L,
    val completedSessionMs: Long = 90_000L,
    val completedSessionRatio: Double = 0.70,
    val recentHalfLifeDays: Double = 14.0,
    val onboardingArtistWeight: Double = 3.6,
    val onboardingGenreWeight: Double = 1.9,
    val onboardingLikedTrackBoost: Double = 2.4,
    val onboardingPassedArtistPenalty: Double = -1.6
)

/**
 * Local-only user interaction feedback captured from SONORA playback & UI actions:
 * Like, Dislike, Early Skip, Play Next, Repeated Listening, and Recently Shown set.
 */
data class UserMusicInteractionSignal(
    val dislikedTrackIds: Set<Long> = emptySet(),
    val dislikedTrackUrls: Set<String> = emptySet(),
    val dislikedArtistCounts: Map<String, Int> = emptyMap(),
    val skippedTrackCounts: Map<Long, Int> = emptyMap(),
    val skippedArtistCounts: Map<String, Int> = emptyMap(),
    val playNextTrackIds: Set<Long> = emptySet(),
    val playNextArtistCounts: Map<String, Int> = emptyMap(),
    val repeatedTrackCounts: Map<Long, Int> = emptyMap(),
    val repeatedArtistCounts: Map<String, Int> = emptyMap(),
    val recentlyShownTrackUrls: List<String> = emptyList(),
    val lastUpdatedAtMs: Long = 0L
) {
    val hasAnyPositiveSignals: Boolean
        get() = playNextArtistCounts.isNotEmpty() || repeatedArtistCounts.isNotEmpty()
}

/**
 * A real NewPipe Extractor search query seed derived from user taste or cold-start discovery.
 */
data class SonoraSeedQuery(
    val query: String,
    val reasonHint: String,
    val priority: Double,
    val artistSeed: String? = null,
    val languageTag: String? = null
)

/**
 * Aggregated local user taste profile constructed solely from on-device SONORA data:
 * Liked Songs, Followed Artists, Local & Online Playlists, Listening Analytics History,
 * and Interaction Feedback.
 */
data class SonoraUserTasteProfile(
    val hasPersonalizationSignals: Boolean,
    val countryCode: String,
    val languageCode: String,
    val discoverLanguage: DiscoverMusicLanguage,
    val strictIndiaOnly: Boolean,
    val followedArtistScores: Map<String, Double> = emptyMap(),
    val likedArtistScores: Map<String, Double> = emptyMap(),
    val playlistArtistScores: Map<String, Double> = emptyMap(),
    val historyArtistScores: Map<String, Double> = emptyMap(),
    val recentArtistScores: Map<String, Double> = emptyMap(),
    val preferredGenres: Map<String, Double> = emptyMap(),
    val preferredLanguages: Map<String, Double> = emptyMap(),
    val preferredAlbums: Set<String> = emptySet(),
    val preferredMoods: Map<String, Double> = emptyMap(),
    val playlistNamesByArtist: Map<String, String> = emptyMap(),
    val likedTrackKeys: Set<String> = emptySet(),
    val familiarFavoriteKeys: Set<String> = emptySet(),
    val topDisplayArtists: List<String> = emptyList(),
    val seedQueries: List<SonoraSeedQuery> = emptyList(),
    val summarySubtitle: String = "",
    val onboardingArtistScores: Map<String, Double> = emptyMap(),
    val onboardingLikedTrackUrls: Set<String> = emptySet(),
    val onboardingPassedTrackUrls: Set<String> = emptySet(),
    val onboardingPassedArtistCounts: Map<String, Int> = emptyMap()
)

/**
 * A verified, deduplicated, ranked track ready to display in the Sonora Recommend carousel.
 */
data class RecommendedSongItem(
    val track: OnlineTrack,
    val score: Double,
    val reasonLabel: String,
    val isFamiliarFavorite: Boolean = false,
    val detectedLanguage: String? = null
)

/**
 * UI state for the Online Music Home "Sonora Recommend" section.
 */
data class SonoraRecommendUiState(
    val items: List<RecommendedSongItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isColdStartDiscovery: Boolean = false,
    val isServingCachedOffline: Boolean = false,
    val errorMessage: String? = null,
    val profileSummary: String = "",
    val lastUpdatedAtMs: Long = 0L,
    val refreshGeneration: Int = 0
)
