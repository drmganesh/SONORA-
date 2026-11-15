package ui.home

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.DailyMix
import com.example.model.Song
import data.preferences.HomeAnimationPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.extractMixCoverSongs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeAnimationPreferencesTest {

    private fun song(id: Long, albumId: Long, title: String = "Track $id"): Song = Song(
        id = id,
        title = title,
        artist = "Artist $albumId",
        album = "Album $albumId",
        albumId = albumId,
        durationMs = 180_000L,
        uri = Uri.EMPTY,
        dataPath = "/music/$id.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 4_000_000L
    )

    @Test
    fun `animation level and daily mix style resolve from id safely`() {
        assertEquals(HomeAnimationLevel.OFF, HomeAnimationLevel.fromId("off"))
        assertEquals(HomeAnimationLevel.SUBTLE, HomeAnimationLevel.fromId("SUBTLE"))
        assertEquals(HomeAnimationLevel.BALANCED, HomeAnimationLevel.fromId("balanced"))
        assertEquals(HomeAnimationLevel.EXPRESSIVE, HomeAnimationLevel.fromId("expressive"))
        assertEquals(HomeAnimationLevel.DEFAULT, HomeAnimationLevel.fromId("unknown"))
        assertEquals(HomeAnimationLevel.DEFAULT, HomeAnimationLevel.fromId(null))

        assertEquals(DailyMixAnimationStyle.SLIDE, DailyMixAnimationStyle.fromId("slide"))
        assertEquals(DailyMixAnimationStyle.MIX, DailyMixAnimationStyle.fromId("MIX"))
        assertEquals(DailyMixAnimationStyle.FADE, DailyMixAnimationStyle.fromId("fade"))
        assertEquals(DailyMixAnimationStyle.PULSE, DailyMixAnimationStyle.fromId("pulse"))
        assertEquals(DailyMixAnimationStyle.GLITCH, DailyMixAnimationStyle.fromId("glitch"))
        assertEquals(DailyMixAnimationStyle.DEFAULT, DailyMixAnimationStyle.fromId("invalid"))
        assertEquals(DailyMixAnimationStyle.DEFAULT, DailyMixAnimationStyle.fromId(null))
    }

    @Test
    fun `HomeAnimationPreferences and SonoraPreferences persist settings across reloads`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = HomeAnimationPreferences(context)

        store.saveSettings(HomeAnimationLevel.EXPRESSIVE, DailyMixAnimationStyle.GLITCH)
        val saved = store.settingsFlow.first()
        assertEquals(HomeAnimationLevel.EXPRESSIVE, saved.animationLevel)
        assertEquals(DailyMixAnimationStyle.GLITCH, saved.dailyMixStyle)

        val reloadedPrefs = SonoraPreferences(context)
        assertEquals(HomeAnimationLevel.EXPRESSIVE, reloadedPrefs.homeAnimationLevel)
        assertEquals(DailyMixAnimationStyle.GLITCH, reloadedPrefs.dailyMixAnimationStyle)

        store.setHomeAnimationLevel(HomeAnimationLevel.SUBTLE)
        store.setDailyMixAnimationStyle(DailyMixAnimationStyle.MIX)
        val updated = HomeAnimationPreferences(context).settingsFlow.first()
        assertEquals(HomeAnimationLevel.SUBTLE, updated.animationLevel)
        assertEquals(DailyMixAnimationStyle.MIX, updated.dailyMixStyle)

        val initial = HomeAnimationPreferences(context).getInitialSettings()
        assertEquals(HomeAnimationLevel.SUBTLE, initial.animationLevel)
        assertEquals(DailyMixAnimationStyle.MIX, initial.dailyMixStyle)
    }

    @Test
    fun `extractMixCoverSongs picks real distinct album covers and handles sparse mixes`() {
        val distinctSongs = listOf(
            song(1L, 100L),
            song(2L, 100L),
            song(3L, 200L),
            song(4L, 300L),
            song(5L, 400L),
            song(6L, 500L)
        )
        val fullMix = DailyMix(
            id = "mix_full",
            title = "Daily Mix 1",
            subtitle = "Test",
            description = "Desc",
            badge = "FOR YOU",
            songs = distinctSongs,
            coverSong = distinctSongs.first(),
            gradientColors = listOf(0xFF112233, 0xFF445566)
        )
        val covers = extractMixCoverSongs(fullMix)
        assertEquals(4, covers.size)
        assertEquals(listOf(100L, 200L, 300L, 400L), covers.map { it.albumId })

        val twoSongs = listOf(song(10L, 900L), song(11L, 901L))
        val smallMix = fullMix.copy(id = "mix_small", songs = twoSongs, coverSong = twoSongs.first())
        val smallCovers = extractMixCoverSongs(smallMix)
        assertEquals(2, smallCovers.size)

        val coverOnlyMix = fullMix.copy(id = "mix_fallback", songs = emptyList(), coverSong = song(99L, 777L))
        val fallbackCovers = extractMixCoverSongs(coverOnlyMix)
        assertEquals(listOf(99L), fallbackCovers.map { it.id })

        val emptyMix = fullMix.copy(id = "mix_empty", songs = emptyList(), coverSong = null)
        assertTrue(extractMixCoverSongs(emptyMix).isEmpty())
    }
}
