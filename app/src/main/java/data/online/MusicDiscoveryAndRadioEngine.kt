package data.online

import com.example.model.Song
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteProfile
import domain.online.DiscoverMusicLanguage
import domain.online.OnlineRegionCatalog
import domain.online.OnlineTrack
import domain.online.RegionMusicEligibilityFilter
import java.util.Locale

enum class MusicMoodActivity(
    val id: String,
    val label: String,
    val querySuffix: String,
    val subtitle: String
) {
    FOCUS("focus", "Focus", "lofi instrumental focus study music", "Deep concentration & instrumental flow"),
    RELAX("relax", "Relax", "chill acoustic relaxing calm songs", "Unwind with soothing melodies"),
    WORKOUT("workout", "Workout", "high energy workout gym pump songs", "High-tempo beats to power your session"),
    TRAVEL("travel", "Travel", "road trip travel drive songs playlist", "Open-road soundtracks & sing-alongs"),
    PARTY("party", "Party", "dance party club hits songs", "Upbeat dancefloor & celebration tracks"),
    SLEEP("sleep", "Sleep", "ambient peaceful sleep night music", "Soft ambient soundscapes for rest"),
    ROMANTIC("romantic", "Romantic", "romantic love melody songs", "Warm love ballads & acoustic duets"),
    SAD("sad", "Sad", "emotional heartbreak sad soulful songs", "Soulful, reflective tracks"),
    ENERGETIC("energetic", "Energetic", "energetic upbeat feel good hits", "Bright, high-energy momentum");

    companion object {
        fun fromId(id: String?): MusicMoodActivity? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

enum class IndiaMusicCategory(
    val id: String,
    val label: String,
    val query: String,
    val subtitle: String
) {
    TRENDING_INDIA("trending_india", "Trending in India", "Trending India top music songs", "Hottest tracks across India"),
    HINDI_HITS("hindi", "Hindi Hits", "Hindi Bollywood hit songs", "Bollywood & Hindi chartbusters"),
    ODIA("odia", "Odia Music", "Odia hit songs melody", "Odia cinema, folk & modern hits"),
    BENGALI("bengali", "Bengali Music", "Bengali hit songs", "Bangla modern, film & acoustic"),
    PUNJABI("punjabi", "Punjabi Music", "Punjabi hit songs", "Bhangra, hip-hop & Punjabi pop"),
    TAMIL("tamil", "Tamil Music", "Tamil hit songs", "Kollywood & Tamil independent"),
    TELUGU("telugu", "Telugu Music", "Telugu hit songs", "Tollywood & Telugu melodies"),
    MALAYALAM("malayalam", "Malayalam Music", "Malayalam hit songs", "Mollywood & indie melodies"),
    KANNADA("kannada", "Kannada Music", "Kannada hit songs", "Sandalwood & Kannada favorites"),
    MARATHI("marathi", "Marathi Music", "Marathi hit songs", "Marathi film & classical hits"),
    REGIONAL_DISCOVERIES("regional", "Regional Discoveries", "Indian regional indie folk fusion songs", "Hidden gems across Indian states")
}

enum class MusicDiscoveryBadge(val label: String) {
    SONORA_GENERATED(domain.branding.AppBranding.MIX_NAME),
    PROVIDER_CURATED("Provider Playlist")
}

data class PersonalizedMusicMixCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val query: String,
    val badge: MusicDiscoveryBadge,
    val gradientColors: List<Long>,
    val previewSongs: List<Song> = emptyList()
)

data class SongRadioSession(
    val isActive: Boolean = false,
    val seedSong: Song? = null,
    val playedSongIds: Set<Long> = emptySet(),
    val skippedArtists: Map<String, Int> = emptyMap(),
    val likedArtists: Map<String, Int> = emptyMap(),
    val completedArtists: Map<String, Int> = emptyMap(),
    val currentQueryHint: String = ""
) {
    val seedLabel: String
        get() = seedSong?.let { "Radio • ${it.title}" } ?: domain.branding.AppBranding.RADIO_NAME
}

/**
 * Pure music discovery & continuous Song Radio engine.
 * Transparently separates SONORA-generated mixes from provider-curated playlists,
 * builds real search queries for NewPipe Extractor, avoids repeats, and adapts
 * radio ranking based on live skips, likes, and completions.
 */
object MusicDiscoveryAndRadioEngine {

    fun buildMadeForYouCards(
        recentSongs: List<Song>,
        favoriteSongs: List<Song>,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
    ): List<PersonalizedMusicMixCard> {
        val eligibleRecent = RegionMusicEligibilityFilter.filterSongsForRegion(recentSongs, countryCode)
        val eligibleFavs = RegionMusicEligibilityFilter.filterSongsForRegion(favoriteSongs, countryCode)
        val topArtist = (eligibleFavs + eligibleRecent)
            .map { it.artist.trim() }
            .firstOrNull { isValidArtist(it) }
            ?: musicTasteProfile.selectedArtists.firstOrNull()?.name?.takeIf { isValidArtist(it) }
        val topTasteGenre = musicTasteProfile.selectedGenres.firstOrNull()?.takeIf { it.isNotBlank() }
        val langHint = topTasteGenre ?: if (discoverLanguage != DiscoverMusicLanguage.ALL) discoverLanguage.label else {
            if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) "Indian" else "Top"
        }
        val pool = (eligibleFavs + eligibleRecent).distinctBy { it.id }

        fun scoped(raw: String): String = RegionMusicEligibilityFilter.scopeRecommendationQuery(
            baseQuery = raw,
            countryCode = countryCode,
            discoverLanguage = discoverLanguage
        )

        return listOf(
            PersonalizedMusicMixCard(
                id = "sonora_daily_mix",
                title = "Daily Mix",
                subtitle = if (topArtist != null) "Built around $topArtist & your daily rotation" else "$langHint favorites tailored for today",
                query = scoped(if (topArtist != null) "$topArtist mix songs" else "$langHint daily music mix songs"),
                badge = MusicDiscoveryBadge.SONORA_GENERATED,
                gradientColors = listOf(0xFF284B63, 0xFF3C6E71),
                previewSongs = pool.take(10)
            ),
            PersonalizedMusicMixCard(
                id = "sonora_weekly_discover",
                title = "Weekly Discover",
                subtitle = if (topArtist != null) "Fresh discoveries beyond $topArtist" else "New $langHint voices to explore this week",
                query = scoped(if (topArtist != null) "artists similar to $topArtist new songs" else "$langHint indie new music discovery"),
                badge = MusicDiscoveryBadge.SONORA_GENERATED,
                gradientColors = listOf(0xFF5F0F40, 0xFF9A031E),
                previewSongs = pool.drop(2).take(10)
            ),
            PersonalizedMusicMixCard(
                id = "sonora_music_journey",
                title = "Your Music Journey",
                subtitle = "Revisit your most-played tracks & eras",
                query = scoped(if (topArtist != null) "$topArtist best acoustic classics" else "$langHint evergreen classic hits"),
                badge = MusicDiscoveryBadge.SONORA_GENERATED,
                gradientColors = listOf(0xFF1D3557, 0xFF457B9D),
                previewSongs = eligibleRecent.sortedByDescending { it.playCount }.take(10)
            ),
            PersonalizedMusicMixCard(
                id = "sonora_personalized_radio",
                title = "Personalized Radio",
                subtitle = "Continuous adaptive stream tuned to your likes & skips",
                query = scoped(if (topArtist != null) "$topArtist radio hits" else "$langHint radio non stop songs"),
                badge = MusicDiscoveryBadge.SONORA_GENERATED,
                gradientColors = listOf(0xFF6C584C, 0xFFA98467),
                previewSongs = pool.shuffled().take(10)
            )
        )
    }

    fun buildBasedOnListeningCards(
        recentSongs: List<Song>,
        favoriteSongs: List<Song>,
        countryCode: String,
        musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
    ): List<PersonalizedMusicMixCard> {
        val eligibleRecent = RegionMusicEligibilityFilter.filterSongsForRegion(recentSongs, countryCode)
        val eligibleFavs = RegionMusicEligibilityFilter.filterSongsForRegion(favoriteSongs, countryCode)
        val anchorSong = eligibleRecent.firstOrNull() ?: eligibleFavs.firstOrNull()
        val anchorArtist = anchorSong?.artist?.takeIf { isValidArtist(it) }
            ?: musicTasteProfile.selectedArtists.firstOrNull()?.name?.takeIf { isValidArtist(it) }
        val anchorAlbum = anchorSong?.album?.takeIf {
            it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Downloads", ignoreCase = true)
        }
        val regionTag = if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) "India" else countryCode

        fun scoped(raw: String): String = RegionMusicEligibilityFilter.scopeRecommendationQuery(
            baseQuery = raw,
            countryCode = countryCode
        )

        return buildList {
            if (anchorSong != null) {
                add(
                    PersonalizedMusicMixCard(
                        id = "because_you_listened",
                        title = "Because You Listened To ${anchorSong.title}",
                        subtitle = "Related tracks in the same vibe",
                        query = scoped("${anchorSong.title} ${anchorSong.artist} similar songs"),
                        badge = MusicDiscoveryBadge.SONORA_GENERATED,
                        gradientColors = listOf(0xFF2B2D42, 0xFF8D99AE)
                    )
                )
            }
            if (anchorArtist != null) {
                add(
                    PersonalizedMusicMixCard(
                        id = "similar_artists",
                        title = "Similar Artists",
                        subtitle = "Creators loved by $anchorArtist listeners",
                        query = scoped("$anchorArtist similar artists collaboration songs"),
                        badge = MusicDiscoveryBadge.SONORA_GENERATED,
                        gradientColors = listOf(0xFF3A0CA3, 0xFF7209B7)
                    )
                )
                add(
                    PersonalizedMusicMixCard(
                        id = "similar_songs",
                        title = "Similar Songs",
                        subtitle = "Matched to $anchorArtist's sound & mood",
                        query = scoped("$anchorArtist best melody songs mix"),
                        badge = MusicDiscoveryBadge.SONORA_GENERATED,
                        gradientColors = listOf(0xFF005F73, 0xFF0A9396)
                    )
                )
            }
            if (anchorAlbum != null) {
                add(
                    PersonalizedMusicMixCard(
                        id = "more_like_album",
                        title = "More Like $anchorAlbum",
                        subtitle = "Soundtracks & albums with a matching tone",
                        query = scoped("$anchorAlbum soundtrack album songs"),
                        badge = MusicDiscoveryBadge.SONORA_GENERATED,
                        gradientColors = listOf(0xFF7F4F24, 0xFFB08968)
                    )
                )
            }
            add(
                PersonalizedMusicMixCard(
                    id = "discover_new_music",
                    title = "Discover New Music",
                    subtitle = "Fresh independent & breakthrough tracks in $regionTag",
                    query = scoped("$regionTag new indie breakthrough songs"),
                    badge = MusicDiscoveryBadge.SONORA_GENERATED,
                    gradientColors = listOf(0xFF1B4332, 0xFF40916C)
                )
            )
        }
    }

    /**
     * Builds a real search query for related tracks / Song Radio from the current song's metadata
     * (artist, genre, album, language/region) and live session feedback.
     * In strict India mode, foreign seed artists are replaced by India-scoped seeds so Song Radio
     * and Up Next stay strictly within verified Indian music.
     */
    fun buildRelatedSongsQuery(
        seedSong: Song,
        session: SongRadioSession,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ): String {
        val isIndiaStrict = RegionMusicEligibilityFilter.isIndiaRegion(countryCode)
        val seedIsRegionEligible = !isIndiaStrict || RegionMusicEligibilityFilter.isVerifiedIndianSong(seedSong)

        val preferredArtist = session.likedArtists.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .firstOrNull { artist ->
                isValidArtist(artist) && (!isIndiaStrict || RegionMusicEligibilityFilter.isVerifiedIndianMusic(title = artist, artist = artist))
            }
            ?: seedSong.artist.takeIf { artist ->
                isValidArtist(artist) && (!isIndiaStrict || RegionMusicEligibilityFilter.isVerifiedIndianMusic(title = artist, artist = artist))
            }

        val genreHint = seedSong.genre
            .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        val langHint = if (discoverLanguage != DiscoverMusicLanguage.ALL) {
            discoverLanguage.label
        } else if (isIndiaStrict) {
            "Indian"
        } else {
            ""
        }

        val rawQuery = buildList {
            if (!preferredArtist.isNullOrBlank()) add(preferredArtist)
            if (seedIsRegionEligible && seedSong.title.isNotBlank() && session.playedSongIds.size <= 2) {
                add(seedSong.title.substringBefore("(").substringBefore("[").trim())
            }
            if (!genreHint.isNullOrBlank()) add(genreHint)
            if (langHint.isNotBlank()) add(langHint)
            add("songs")
        }.joinToString(" ").trim()

        return RegionMusicEligibilityFilter.scopeRecommendationQuery(
            baseQuery = rawQuery,
            countryCode = countryCode,
            discoverLanguage = discoverLanguage
        )
    }

    /**
     * Filters and ranks radio candidates:
     * - Enforces strict regional eligibility FIRST (no foreign backfill in India mode)
     * - Removes already-played songs and active queue duplicates
     * - Boosts artists the user liked or listened to completely during this radio session
     * - Demotes artists the user skipped early during this radio session
     */
    fun rankRadioCandidates(
        candidates: List<Song>,
        seedSong: Song,
        session: SongRadioSession,
        existingQueueIds: Set<Long>,
        limit: Int = 12,
        countryCode: String = OnlineRegionCatalog.DEFAULT_COUNTRY_CODE,
        musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
    ): List<Song> {
        val regionEligible = RegionMusicEligibilityFilter.filterSongsForRegion(
            songs = candidates,
            countryCode = countryCode
        )
        if (regionEligible.isEmpty()) return emptyList()

        val blockedIds = session.playedSongIds + existingQueueIds + seedSong.id
        val passedUrls = musicTasteProfile.passedTrackUrls
        val preferredArtistKeys = musicTasteProfile.selectedArtistKeys
        val preferredGenreLower = musicTasteProfile.selectedGenres.map { it.lowercase(Locale.US) }.toSet()
        val seedArtistLower = seedSong.artist.trim().lowercase(Locale.US)
        val seedGenreLower = seedSong.genre.trim().lowercase(Locale.US)
        val seedAlbumLower = seedSong.album.trim().lowercase(Locale.US)

        return regionEligible
            .asSequence()
            .filter { it.id !in blockedIds }
            .filter { song ->
                val urlNorm = song.contentUrl.trim().substringBefore("#").trimEnd('/').lowercase(Locale.ROOT)
                urlNorm.isBlank() || urlNorm !in passedUrls
            }
            .distinctBy { it.id }
            .distinctBy { "${it.title.trim().lowercase(Locale.US)}|${it.artist.trim().lowercase(Locale.US)}" }
            .sortedByDescending { song ->
                val artistKey = song.artist.trim().lowercase(Locale.US)
                val normArtistKey = MusicTasteArtist.normalizeArtistName(song.artist)
                var score = 0.0
                if (artistKey == seedArtistLower && artistKey.isNotBlank()) score += 4.0
                if (normArtistKey.isNotBlank() && normArtistKey in preferredArtistKeys) score += 3.0
                if (seedGenreLower.isNotBlank() && song.genre.trim().lowercase(Locale.US) == seedGenreLower) score += 2.5
                if (preferredGenreLower.isNotEmpty() && song.genre.trim().lowercase(Locale.US) in preferredGenreLower) score += 2.0
                if (seedAlbumLower.isNotBlank() && song.album.trim().lowercase(Locale.US) == seedAlbumLower) score += 1.5
                score += (session.likedArtists[artistKey] ?: 0) * 5.0
                score += (session.completedArtists[artistKey] ?: 0) * 2.0
                score -= (session.skippedArtists[artistKey] ?: 0) * 6.0
                score
            }
            .take(limit)
            .toList()
    }

    fun recordRadioSkip(session: SongRadioSession, skippedSong: Song): SongRadioSession {
        if (!session.isActive) return session
        val key = skippedSong.artist.trim().lowercase(Locale.US)
        if (key.isBlank()) return session
        val updated = session.skippedArtists.toMutableMap()
        updated[key] = (updated[key] ?: 0) + 1
        return session.copy(skippedArtists = updated)
    }

    fun recordRadioLike(session: SongRadioSession, likedSong: Song): SongRadioSession {
        if (!session.isActive) return session
        val key = likedSong.artist.trim().lowercase(Locale.US)
        if (key.isBlank()) return session
        val updated = session.likedArtists.toMutableMap()
        updated[key] = (updated[key] ?: 0) + 1
        return session.copy(likedArtists = updated)
    }

    fun recordRadioCompletion(session: SongRadioSession, completedSong: Song): SongRadioSession {
        if (!session.isActive) return session
        val key = completedSong.artist.trim().lowercase(Locale.US)
        val updatedCompleted = session.completedArtists.toMutableMap()
        if (key.isNotBlank()) {
            updatedCompleted[key] = (updatedCompleted[key] ?: 0) + 1
        }
        return session.copy(
            playedSongIds = session.playedSongIds + completedSong.id,
            completedArtists = updatedCompleted
        )
    }

    private fun isValidArtist(raw: String?): Boolean {
        val clean = raw?.trim().orEmpty()
        if (clean.length < 2) return false
        return clean.lowercase(Locale.US) !in setOf(
            "unknown", "<unknown>", "unknown artist", "various artists",
            "youtube", "youtube music", "yt music", "soundcloud", "sonora", "gita"
        )
    }
}
