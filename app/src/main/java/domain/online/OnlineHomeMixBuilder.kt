package domain.online

import com.example.model.DailyMix
import com.example.model.Song
import data.online.OnlineTrackMapper
import ui.home.MixArtistChip

data class OnlineYourMixBundle(
    val songs: List<Song>,
    val tracks: List<OnlineTrack>,
    val contributorsSummary: String,
    val artistChips: List<MixArtistChip>
)

/**
 * Pure builder deriving Online Home hero "Your Mix", Artist shortcuts, and Online Daily Mixes
 * strictly from real NewPipe-extracted sections and Room-persisted online listening history.
 * Never fabricates songs, artists, or chart positions.
 */
object OnlineHomeMixBuilder {

    fun buildOnlineYourMix(
        discoverSections: Map<DiscoverSectionType, DiscoverSectionState>,
        recentlyPlayedOnline: List<Song>,
        savedOnlineTracks: List<Song>,
        recommendationProfile: OnlineRecommendationProfile
    ): OnlineYourMixBundle {
        val recTracks = sectionTracks(discoverSections[DiscoverSectionType.MUSIC_RECOMMENDATIONS])
        val trendingTracks = sectionTracks(discoverSections[DiscoverSectionType.TRENDING_MUSIC])
        val popularTracks = sectionTracks(discoverSections[DiscoverSectionType.POPULAR_SONGS])
        val artistItems = (discoverSections[DiscoverSectionType.POPULAR_ARTISTS] as? DiscoverSectionState.Success)
            ?.items.orEmpty()

        val orderedOnline = LinkedHashMap<Long, Song>()
        savedOnlineTracks.take(6).forEach { orderedOnline.putIfAbsent(it.id, it) }
        recentlyPlayedOnline.take(6).forEach { orderedOnline.putIfAbsent(it.id, it) }
        recTracks.forEach { t ->
            if (orderedOnline.size < 24) {
                val song = OnlineTrackMapper.toUnifiedSong(t)
                orderedOnline.putIfAbsent(song.id, song)
            }
        }
        trendingTracks.forEach { t ->
            if (orderedOnline.size < 24) {
                val song = OnlineTrackMapper.toUnifiedSong(t)
                orderedOnline.putIfAbsent(song.id, song)
            }
        }
        popularTracks.forEach { t ->
            if (orderedOnline.size < 24) {
                val song = OnlineTrackMapper.toUnifiedSong(t)
                orderedOnline.putIfAbsent(song.id, song)
            }
        }

        val mixSongs = orderedOnline.values.toList()
        val mixTracks = (recTracks + trendingTracks + popularTracks)
            .distinctBy { it.contentUrl }
            .take(24)

        val artistNamesFromTracks = mixSongs
            .map { it.artist.trim() }
            .filter { isMeaningfulArtist(it) }
            .distinct()

        val contributors = when {
            artistNamesFromTracks.isEmpty() -> recommendationProfile.subtitle
            artistNamesFromTracks.size <= 3 -> artistNamesFromTracks.joinToString(", ")
            else -> "${artistNamesFromTracks.take(3).joinToString(", ")}..."
        }

        val artistChipList = mutableListOf<MixArtistChip>()
        val seenArtists = HashSet<String>()
        artistItems.take(8).forEach { item ->
            val name = item.title.trim().ifBlank { item.artist.trim() }
            if (isMeaningfulArtist(name) && seenArtists.add(name.lowercase())) {
                artistChipList.add(
                    MixArtistChip(
                        artistName = name,
                        representativeSong = OnlineTrackMapper.toUnifiedSong(item)
                    )
                )
            }
        }
        mixSongs.forEach { song ->
            val name = song.artist.trim()
            if (artistChipList.size < 10 && isMeaningfulArtist(name) && seenArtists.add(name.lowercase())) {
                artistChipList.add(MixArtistChip(artistName = name, representativeSong = song))
            }
        }

        return OnlineYourMixBundle(
            songs = mixSongs,
            tracks = mixTracks,
            contributorsSummary = contributors,
            artistChips = artistChipList
        )
    }

    /**
     * Generates online Daily Mixes strictly from real extracted tracks without synthetic placeholders.
     */
    fun buildOnlineDailyMixes(
        discoverSections: Map<DiscoverSectionType, DiscoverSectionState>,
        recentlyPlayedOnline: List<Song>,
        savedOnlineTracks: List<Song>,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ): List<DailyMix> {
        val trendingSongs = sectionTracks(discoverSections[DiscoverSectionType.TRENDING_MUSIC])
            .map { OnlineTrackMapper.toUnifiedSong(it) }
        val popularSongs = sectionTracks(discoverSections[DiscoverSectionType.POPULAR_SONGS])
            .map { OnlineTrackMapper.toUnifiedSong(it) }
        val recSongs = sectionTracks(discoverSections[DiscoverSectionType.MUSIC_RECOMMENDATIONS])
            .map { OnlineTrackMapper.toUnifiedSong(it) }
        val newReleaseSongs = sectionTracks(discoverSections[DiscoverSectionType.NEW_RELEASES])
            .map { OnlineTrackMapper.toUnifiedSong(it) }

        val regionName = if (countryCode.equals("IN", ignoreCase = true)) "India" else countryCode.uppercase()
        val langPrefix = if (discoverLanguage != DiscoverMusicLanguage.ALL) "${discoverLanguage.label} " else ""

        val mixes = mutableListOf<DailyMix>()

        if (recSongs.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "online_daily_mix_1",
                    title = "Daily Mix 1",
                    subtitle = "${langPrefix}For You",
                    description = "Personalized picks streamed for your taste",
                    badge = "FOR YOU",
                    songs = recSongs.take(15),
                    coverSong = recSongs.firstOrNull(),
                    gradientColors = listOf(0xFF4A6FA5, 0xFF8AA8E6)
                )
            )
        }

        if (trendingSongs.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "online_daily_mix_2",
                    title = "${regionName} Charts",
                    subtitle = "Trending in $regionName",
                    description = "Live trending music charts extracted right now",
                    badge = "TRENDING",
                    songs = trendingSongs.take(15),
                    coverSong = trendingSongs.firstOrNull(),
                    gradientColors = listOf(0xFFC85A53, 0xFFE9967A)
                )
            )
        }

        if (popularSongs.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "online_daily_mix_3",
                    title = "${langPrefix}Top Hits",
                    subtitle = "Most Streamed",
                    description = "Popular tracks performing strongly today",
                    badge = "TOP HITS",
                    songs = popularSongs.take(15),
                    coverSong = popularSongs.firstOrNull(),
                    gradientColors = listOf(0xFF6B5B95, 0xFFB8A9C9)
                )
            )
        }

        if (newReleaseSongs.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "online_daily_mix_4",
                    title = "Fresh Drops",
                    subtitle = "${langPrefix}New Releases",
                    description = "Latest online tracks & new drops",
                    badge = "NEW",
                    songs = newReleaseSongs.take(15),
                    coverSong = newReleaseSongs.firstOrNull(),
                    gradientColors = listOf(0xFF3E7B75, 0xFF81C3B9)
                )
            )
        }

        val personalOnline = (savedOnlineTracks + recentlyPlayedOnline).distinctBy { it.id }
        if (personalOnline.isNotEmpty()) {
            mixes.add(
                DailyMix(
                    id = "online_daily_mix_saved",
                    title = "Online Vault",
                    subtitle = "Saved & Replayed",
                    description = "Your saved and recently streamed online tracks",
                    badge = "VAULT",
                    songs = personalOnline.take(15),
                    coverSong = personalOnline.firstOrNull(),
                    gradientColors = listOf(0xFF8C6D46, 0xFFD9BA8C)
                )
            )
        }

        return mixes
    }

    private fun sectionTracks(state: DiscoverSectionState?): List<OnlineTrack> {
        val items = (state as? DiscoverSectionState.Success)?.items.orEmpty()
        return items.filter { it.kind == OnlineItemKind.TRACK }
    }

    private fun isMeaningfulArtist(name: String): Boolean {
        val clean = name.trim()
        if (clean.length < 2) return false
        return clean.lowercase() !in setOf(
            "unknown", "<unknown>", "unknown artist", "various artists",
            "yt music", "youtube", "youtube music", "soundcloud", "bandcamp"
        )
    }
}
