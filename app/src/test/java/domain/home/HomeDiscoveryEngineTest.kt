package domain.home

import android.net.Uri
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HomeDiscoveryEngineTest {

    private fun sampleSongs(): List<Song> = listOf(
        Song(
            id = 1L,
            title = "Morning Dew",
            artist = "Shilpa Rao",
            album = "Coastal Echoes",
            albumId = 101L,
            durationMs = 215_000L,
            uri = Uri.EMPTY,
            dataPath = "/music/1.flac",
            mimeType = "audio/flac",
            sizeBytes = 20_000_000L,
            genre = "Acoustic",
            dateAdded = 1000L,
            isFavorite = true,
            playCount = 12,
            lastPlayedTimestamp = 5000L
        ),
        Song(
            id = 2L,
            title = "Midnight Drive",
            artist = "Shashwat Sachdev",
            album = "Neon Horizon",
            albumId = 102L,
            durationMs = 190_000L,
            uri = Uri.EMPTY,
            dataPath = "/music/2.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 6_000_000L,
            genre = "Electronic Pop",
            dateAdded = 2000L,
            isFavorite = false,
            playCount = 8,
            lastPlayedTimestamp = 7000L
        ),
        Song(
            id = 3L,
            title = "Quiet Harbor",
            artist = "Shilpa Rao",
            album = "Deep Ambient",
            albumId = 103L,
            durationMs = 310_000L,
            uri = Uri.EMPTY,
            dataPath = "/music/3.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 7_500_000L,
            genre = "Ambient Chill",
            dateAdded = 3000L,
            isFavorite = false,
            playCount = 0,
            lastPlayedTimestamp = 0L
        )
    )

    @Test
    fun `greeting matches local time windows`() {
        assertEquals("Good morning", HomeDiscoveryEngine.greetingForHour(6))
        assertEquals("Good morning", HomeDiscoveryEngine.greetingForHour(11))
        assertEquals("Good afternoon", HomeDiscoveryEngine.greetingForHour(12))
        assertEquals("Good afternoon", HomeDiscoveryEngine.greetingForHour(16))
        assertEquals("Good evening", HomeDiscoveryEngine.greetingForHour(17))
        assertEquals("Good evening", HomeDiscoveryEngine.greetingForHour(23))
        assertEquals("Good evening", HomeDiscoveryEngine.greetingForHour(2))
    }

    @Test
    fun `your mix prioritizes favorites and deduplicates tracks`() {
        val songs = sampleSongs()
        val mix = HomeDiscoveryEngine.buildYourMix(songs)
        assertEquals(3, mix.size)
        assertEquals(1L, mix.first().id) // favorite first
        assertEquals(songs.size, mix.distinctBy { it.id }.size)
    }

    @Test
    fun `daily mixes cover expected personalized categories without empty lists`() {
        val songs = sampleSongs()
        val mixes = HomeDiscoveryEngine.buildDailyMixes(songs)
        assertEquals(6, mixes.size)
        val titles = mixes.map { it.title }
        assertTrue(titles.contains("Daily Mix 1"))
        assertTrue(titles.contains("Daily Mix 2"))
        assertTrue(titles.contains("Daily Mix 3"))
        assertTrue(titles.contains("Energy Mix"))
        assertTrue(titles.contains("Chill Mix"))
        assertTrue(titles.contains("Acoustic Mix"))
        assertTrue(mixes.all { it.songs.isNotEmpty() })
    }

    @Test
    fun `empty library returns empty collections safely`() {
        assertTrue(HomeDiscoveryEngine.buildYourMix(emptyList()).isEmpty())
        assertTrue(HomeDiscoveryEngine.buildDailyMixes(emptyList()).isEmpty())
        assertTrue(HomeDiscoveryEngine.rankFavouriteAlbums(emptyList(), emptyList<Album>()).isEmpty())
        assertTrue(HomeDiscoveryEngine.rankFavouriteArtists(emptyList(), emptyList<Artist>()).isEmpty())
    }
}
