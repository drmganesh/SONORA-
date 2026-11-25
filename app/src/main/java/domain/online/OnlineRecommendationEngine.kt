package domain.online

import com.example.model.Artist
import com.example.model.DailyMix
import com.example.model.Song
import domain.onboarding.MusicTasteProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Real, transparent personalization profile used to query NewPipe Extractor
 * for Daily Online Music Recommendations without fabricating rankings or fake tracks.
 */
data class OnlineRecommendationProfile(
    val seedQuery: String,
    val fallbackQuery: String,
    val subtitle: String,
    val primaryArtistSeed: String? = null,
    val regionCode: String = OnlineRegionCatalog.DEFAULT_COUNTRY_CODE,
    val languageId: String = DiscoverMusicLanguage.DEFAULT.id,
    val dayKey: String = ""
)

object OnlineRecommendationEngine {

    private val PLACEHOLDER_ARTISTS = setOf(
        "",
        "unknown",
        "<unknown>",
        "unknown artist",
        "various artists",
        "yt music",
        "youtube",
        "youtube music",
        "soundcloud",
        "bandcamp",
        "sonora"
    )

    fun currentDayKey(nowMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    }

    /**
     * Derives personalized daily online recommendation query seeds strictly from:
     * - Recently played tracks (local + online)
     * - Favourite artists & favorite tracks
     * - Listening history (play counts, genres, recent online searches)
     * - Selected region & music language preferences
     * - Existing Daily Mix preferences/themes
     */
    fun buildDailyRecommendationProfile(
        recentlyPlayedLocal: List<Song>,
        recentlyPlayedOnline: List<Song>,
        favouriteArtists: List<Artist>,
        allSongs: List<Song>,
        onlineTracks: List<Song>,
        onlineSearchHistory: List<String>,
        dailyMixes: List<DailyMix>,
        countryCode: String,
        languageCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        dayKey: String = currentDayKey(),
        musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
    ): OnlineRecommendationProfile {
        val cleanCountry = countryCode.trim().uppercase(Locale.US).ifBlank {
            OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
        }
        val cleanLangCode = languageCode.trim().lowercase(Locale.US).ifBlank {
            OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE
        }

        val isIndiaStrict = RegionMusicEligibilityFilter.isIndiaRegion(cleanCountry)
        val eligibleRecentOnline = RegionMusicEligibilityFilter.filterSongsForRegion(recentlyPlayedOnline, cleanCountry)
        val eligibleRecentLocal = RegionMusicEligibilityFilter.filterSongsForRegion(recentlyPlayedLocal, cleanCountry)
        val eligibleOnlineTracks = RegionMusicEligibilityFilter.filterSongsForRegion(onlineTracks, cleanCountry)
        val eligibleAllSongs = RegionMusicEligibilityFilter.filterSongsForRegion(allSongs, cleanCountry)
        val eligibleFavArtists = if (isIndiaStrict) {
            favouriteArtists.filter { RegionMusicEligibilityFilter.isVerifiedIndianMusic(title = it.name, artist = it.name) }
        } else {
            favouriteArtists
        }

        // Collect real artist candidates scored by recency, favorites, and play count
        val artistScores = LinkedHashMap<String, Int>()
        fun addArtistCandidate(rawArtist: String?, weight: Int) {
            val clean = rawArtist?.trim().orEmpty()
            if (clean.length < 2) return
            if (clean.lowercase(Locale.US) in PLACEHOLDER_ARTISTS) return
            if (isIndiaStrict && !RegionMusicEligibilityFilter.isVerifiedIndianMusic(title = clean, artist = clean)) {
                return
            }
            artistScores[clean] = (artistScores[clean] ?: 0) + weight
        }

        eligibleRecentOnline.take(8).forEachIndexed { idx, s ->
            addArtistCandidate(s.artist, weight = (12 - idx) + if (s.isFavorite) 6 else 0)
        }
        eligibleRecentLocal.take(8).forEachIndexed { idx, s ->
            addArtistCandidate(s.artist, weight = (10 - idx) + if (s.isFavorite) 6 else 0)
        }
        eligibleFavArtists.take(8).forEachIndexed { idx, a ->
            addArtistCandidate(a.name, weight = 14 - idx)
        }
        eligibleOnlineTracks.filter { it.isFavorite || it.playCount > 0 }.take(12).forEach { s ->
            addArtistCandidate(s.artist, weight = (if (s.isFavorite) 10 else 0) + s.playCount.coerceAtMost(10))
        }
        eligibleAllSongs.filter { it.isFavorite || it.playCount > 0 }
            .sortedByDescending { (if (it.isFavorite) 10 else 0) + it.playCount }
            .take(12)
            .forEach { s ->
                addArtistCandidate(s.artist, weight = (if (s.isFavorite) 8 else 0) + s.playCount.coerceAtMost(8))
            }

        musicTasteProfile.selectedArtists.take(10).forEachIndexed { idx, artist ->
            addArtistCandidate(artist.name, weight = (15 - idx).coerceAtLeast(6))
        }
        musicTasteProfile.likedDiscoveryTracks.take(12).forEach { track ->
            addArtistCandidate(track.artist, weight = 9)
        }

        val rankedArtists = artistScores.entries
            .sortedByDescending { it.value }
            .map { it.key }

        // Genre & Daily Mix signals from region-eligible user library + selected Music Taste genres
        val topGenre = musicTasteProfile.selectedGenres.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: (eligibleAllSongs + eligibleOnlineTracks)
                .asSequence()
                .filter { it.genre.isNotBlank() && !it.genre.equals("Unknown", ignoreCase = true) }
                .groupBy { it.genre.trim() }
                .maxByOrNull { (_, group) -> group.sumOf { (if (it.isFavorite) 5 else 1) + it.playCount } }
                ?.key

        val topMixPreference = dailyMixes
            .filter { mix ->
                !isIndiaStrict || mix.songs.any { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) }
            }
            .firstOrNull { mix ->
                mix.subtitle.isNotBlank() &&
                    !mix.subtitle.equals("Heavy Rotation", ignoreCase = true) &&
                    !mix.subtitle.equals("Fresh Discovery", ignoreCase = true)
            }?.subtitle ?: dailyMixes.firstOrNull { mix ->
                !isIndiaStrict || mix.songs.any { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) }
            }?.title

        val recentSearchSeed = onlineSearchHistory
            .map { it.trim() }
            .firstOrNull { q ->
                q.length >= 3 && (!isIndiaStrict || RegionMusicEligibilityFilter.isVerifiedIndianMusic(title = q, artist = q))
            }

        val localeHint = resolveLocaleMusicHint(cleanCountry, cleanLangCode, discoverLanguage)
        val rawFallbackQuery = if (localeHint.isNotBlank()) {
            "$localeHint music mix playlist"
        } else {
            "music mix playlist $cleanCountry"
        }
        val fallbackQuery = RegionMusicEligibilityFilter.scopeRecommendationQuery(
            baseQuery = rawFallbackQuery,
            countryCode = cleanCountry,
            discoverLanguage = discoverLanguage
        )

        // Rotate deterministically by calendar day among top personalization signals so recommendations refresh daily
        val dayRotationIndex = abs(dayKey.hashCode())
        val signalPool = buildList {
            if (rankedArtists.isNotEmpty()) {
                val dailyArtist = rankedArtists[dayRotationIndex % rankedArtists.take(3).size]
                add(
                    Triple(
                        dailyArtist,
                        if (localeHint.isNotBlank() && discoverLanguage != DiscoverMusicLanguage.ALL) {
                            "$dailyArtist ${discoverLanguage.searchKeyword} mix songs"
                        } else {
                            "$dailyArtist mix songs playlist"
                        },
                        "Inspired by $dailyArtist"
                    )
                )
            }
            if (!recentSearchSeed.isNullOrBlank()) {
                add(
                    Triple(
                        null,
                        if (localeHint.isNotBlank() && discoverLanguage != DiscoverMusicLanguage.ALL) {
                            "$recentSearchSeed ${discoverLanguage.searchKeyword} music"
                        } else {
                            "$recentSearchSeed music mix"
                        },
                        "Based on \"$recentSearchSeed\""
                    )
                )
            }
            if (!topGenre.isNullOrBlank()) {
                val mixSuffix = topMixPreference?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                add(
                    Triple(
                        null,
                        if (localeHint.isNotBlank()) "$topGenre $localeHint music mix" else "$topGenre music mix",
                        "Tailored for $topGenre$mixSuffix"
                    )
                )
            }
            if (!topMixPreference.isNullOrBlank()) {
                add(
                    Triple(
                        null,
                        if (localeHint.isNotBlank()) "$localeHint $topMixPreference music playlist" else "$topMixPreference music playlist",
                        "Inspired by $topMixPreference"
                    )
                )
            }
        }

        // If user explicitly picked a regional language filter (e.g., Odia, Tamil, Hindi), honor it directly while incorporating top artist if available
        if (discoverLanguage != DiscoverMusicLanguage.ALL) {
            val artistForLang = rankedArtists.firstOrNull()
            val query = RegionMusicEligibilityFilter.scopeRecommendationQuery(
                baseQuery = if (artistForLang != null && dayRotationIndex % 2 == 0) {
                    "${discoverLanguage.searchKeyword} $artistForLang hits mix"
                } else {
                    "${discoverLanguage.searchKeyword} music playlist mix"
                },
                countryCode = cleanCountry,
                discoverLanguage = discoverLanguage
            )
            val reason = if (artistForLang != null) {
                "${discoverLanguage.label} picks • $artistForLang & Mixes"
            } else {
                "${discoverLanguage.label} daily mix • $cleanCountry"
            }
            return OnlineRecommendationProfile(
                seedQuery = query,
                fallbackQuery = fallbackQuery,
                subtitle = reason,
                primaryArtistSeed = artistForLang,
                regionCode = cleanCountry,
                languageId = discoverLanguage.id,
                dayKey = dayKey
            )
        }

        val picked = signalPool.firstOrNull()
        return if (picked != null) {
            val (artistSeed, query, reason) = picked
            val regionLabel = if (cleanCountry == "IN") "India" else cleanCountry
            OnlineRecommendationProfile(
                seedQuery = RegionMusicEligibilityFilter.scopeRecommendationQuery(
                    baseQuery = query,
                    countryCode = cleanCountry,
                    discoverLanguage = discoverLanguage
                ),
                fallbackQuery = fallbackQuery,
                subtitle = "$reason • $regionLabel",
                primaryArtistSeed = artistSeed ?: rankedArtists.firstOrNull(),
                regionCode = cleanCountry,
                languageId = discoverLanguage.id,
                dayKey = dayKey
            )
        } else {
            val regionLabel = if (cleanCountry == "IN") "India" else cleanCountry
            val baseQuery = RegionMusicEligibilityFilter.scopeRecommendationQuery(
                baseQuery = if (localeHint.isNotBlank()) {
                    "$localeHint top music mix playlist"
                } else {
                    "top music hits mix playlist $cleanCountry"
                },
                countryCode = cleanCountry,
                discoverLanguage = discoverLanguage
            )
            OnlineRecommendationProfile(
                seedQuery = baseQuery,
                fallbackQuery = fallbackQuery,
                subtitle = "Curated for $regionLabel • $cleanLangCode",
                primaryArtistSeed = null,
                regionCode = cleanCountry,
                languageId = discoverLanguage.id,
                dayKey = dayKey
            )
        }
    }

    fun resolveLocaleMusicHint(
        countryCode: String,
        languageCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ): String {
        return when {
            discoverLanguage != DiscoverMusicLanguage.ALL -> discoverLanguage.searchKeyword
            languageCode == "hi" -> "Hindi Bollywood"
            languageCode == "or" -> "Odia"
            languageCode == "ta" -> "Tamil"
            languageCode == "te" -> "Telugu"
            languageCode == "ml" -> "Malayalam"
            languageCode == "kn" -> "Kannada"
            languageCode == "bn" -> "Bengali"
            languageCode == "pa" -> "Punjabi"
            languageCode == "mr" -> "Marathi"
            countryCode.equals("IN", ignoreCase = true) -> "Indian Hindi Bollywood"
            else -> ""
        }
    }
}
