package domain.online

import android.net.Uri
import com.example.model.Artist
import com.example.model.DailyMix
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineRecommendationEngineTest {

    private fun testSong(
        id: Long,
        title: String,
        artist: String,
        genre: String = "Indie",
        isFavorite: Boolean = false,
        playCount: Int = 0,
        isOnline: Boolean = false
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album $id",
        albumId = id * 10L,
        durationMs = 210_000L,
        uri = Uri.EMPTY,
        dataPath = if (isOnline) "https://youtube.com/watch?v=$id" else "/music/$id.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 4_000_000L,
        genre = genre,
        isFavorite = isFavorite,
        playCount = playCount,
        isOnline = isOnline
    )

    @Test
    fun `buildDailyRecommendationProfile prioritizes real recent online and favorite artists with India context`() {
        val recentOnline = listOf(
            testSong(
                id = -101L,
                title = "Kesariya",
                artist = "Arijit Singh",
                genre = "Bollywood",
                isFavorite = true,
                playCount = 4,
                isOnline = true
            )
        )
        val favArtists = listOf(
            Artist(id = 1L, name = "A.R. Rahman", songCount = 8, albumCount = 2)
        )

        val profile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = recentOnline,
            favouriteArtists = favArtists,
            allSongs = emptyList(),
            onlineTracks = recentOnline,
            onlineSearchHistory = listOf("lofi hindi"),
            dailyMixes = listOf(
                DailyMix(
                    id = "mix_1",
                    title = "Daily Mix 1",
                    subtitle = "Acoustic Soul",
                    description = "Desc",
                    badge = "MIX",
                    songs = recentOnline,
                    coverSong = recentOnline.first(),
                    gradientColors = listOf(0xFF112233, 0xFF334455)
                )
            ),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            dayKey = "2026-11-16"
        )

        assertTrue(profile.primaryArtistSeed == "Arijit Singh" || profile.primaryArtistSeed == "A.R. Rahman")
        assertTrue(profile.seedQuery.contains(profile.primaryArtistSeed!!))
        assertTrue(profile.subtitle.contains("India"))
        assertEquals("IN", profile.regionCode)
    }

    @Test
    fun `buildDailyRecommendationProfile honors regional language filter for Hindi Odia Tamil etc`() {
        val favArtist = Artist(id = 2L, name = "Humane Sagar", songCount = 5, albumCount = 1)
        val odiaProfile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = listOf(favArtist),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "or",
            discoverLanguage = DiscoverMusicLanguage.ODIA,
            dayKey = "2026-11-16"
        )
        assertTrue(odiaProfile.seedQuery.contains("Odia", ignoreCase = true))
        assertTrue(odiaProfile.subtitle.contains("Odia", ignoreCase = true))
        assertEquals(DiscoverMusicLanguage.ODIA.id, odiaProfile.languageId)

        val tamilProfile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "ta",
            discoverLanguage = DiscoverMusicLanguage.TAMIL,
            dayKey = "2026-11-16"
        )
        assertTrue(tamilProfile.seedQuery.contains("Tamil", ignoreCase = true))
        assertTrue(tamilProfile.subtitle.contains("Tamil", ignoreCase = true))
    }

    @Test
    fun `buildDailyRecommendationProfile filters placeholder artists and falls back gracefully without fabricating artists`() {
        val noisySongs = listOf(
            testSong(id = 1L, title = "Sample", artist = "<unknown>", genre = "Unknown", isFavorite = true, playCount = 10),
            testSong(id = 2L, title = "Sample 2", artist = "Various Artists", genre = "Unknown", isFavorite = false, playCount = 5),
            testSong(id = 3L, title = "Sample 3", artist = "YT Music", genre = "Unknown", isFavorite = true, playCount = 3, isOnline = true)
        )

        val profile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = noisySongs,
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = noisySongs,
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            dayKey = "2026-11-16"
        )

        assertNull(profile.primaryArtistSeed)
        assertFalse(profile.seedQuery.contains("<unknown>", ignoreCase = true))
        assertFalse(profile.seedQuery.contains("Various Artists", ignoreCase = true))
        assertTrue(profile.subtitle.contains("India"))
        assertTrue(profile.seedQuery.contains("Indian", ignoreCase = true))
    }

    @Test
    fun `buildDailyRecommendationProfile incorporates search history and daily mix subtitle when no artists exist`() {
        val profileFromSearch = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = listOf("Pritam Arijit mix"),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            dayKey = "2026-11-16"
        )
        assertTrue(profileFromSearch.seedQuery.contains("Pritam Arijit mix"))
        assertTrue(profileFromSearch.subtitle.contains("Pritam Arijit mix"))

        val profileFromDailyMix = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = listOf(
                DailyMix(
                    id = "mix_chill",
                    title = "Chill Wave",
                    subtitle = "Midnight Melodies",
                    description = "Relax",
                    badge = "DAILY",
                    songs = emptyList(),
                    coverSong = null,
                    gradientColors = emptyList()
                )
            ),
            countryCode = "IN",
            languageCode = "hi",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            dayKey = "2026-11-16"
        )
        assertTrue(profileFromDailyMix.seedQuery.contains("Midnight Melodies"))
        assertTrue(profileFromDailyMix.subtitle.contains("Midnight Melodies"))
    }
}
