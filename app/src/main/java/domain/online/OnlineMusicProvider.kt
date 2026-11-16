package domain.online

enum class OnlineSearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    VIDEOS("Videos")
}

data class OnlineMusicProvider(
    val serviceId: Int,
    val key: String,
    val displayName: String,
    val shortLabel: String,
    val baseUrl: String,
    val supportedFilters: List<OnlineSearchFilter>,
    val defaultFilter: OnlineSearchFilter,
    val supportsSuggestions: Boolean,
    val supportsDiscoveryFeed: Boolean,
    val availableKiosks: List<String> = emptyList(),
    val isMusicFocused: Boolean = false
) {
    companion object {
        const val KEY_YOUTUBE_MUSIC = "youtube_music"
        const val KEY_YOUTUBE = "youtube"
        const val KEY_SOUNDCLOUD = "soundcloud"
        const val KEY_BANDCAMP = "bandcamp"
        const val KEY_PEERTUBE = "peertube"
        const val KEY_MEDIA_CCC = "media_ccc"
    }
}
