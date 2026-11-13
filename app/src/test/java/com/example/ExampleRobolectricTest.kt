package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.equalizer.AdaptiveEqualizerSettings
import com.example.equalizer.EqualizerMode
import com.example.equalizer.EqualizerPreferences
import com.example.equalizer.MacroProfile
import com.example.model.Song
import com.example.service.SonoraNotificationManager
import domain.filter.MusicFolderFilter
import domain.model.FolderFilterSettings
import domain.model.FolderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private fun songAt(id: Long, path: String, isDemo: Boolean = false): Song = Song(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        album = "Album $id",
        albumId = id * 10L,
        durationMs = 200_000L,
        uri = Uri.EMPTY,
        dataPath = path,
        mimeType = "audio/flac",
        sizeBytes = 10_000_000L,
        isDemoTrack = isDemo
    )

    @Test
    fun `verify app name resource matches SONORA`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SONORA", appName)
    }

    @Test
    fun testNotificationConfigurationAndActions() {
        assertEquals(2026, SonoraNotificationManager.NOTIFICATION_ID)
        assertEquals("sonora_playback_channel", SonoraApplication.PLAYBACK_CHANNEL_ID)

        assertEquals("com.example.sonora.ACTION_PLAY", SonoraNotificationManager.ACTION_PLAY)
        assertEquals("com.example.sonora.ACTION_PAUSE", SonoraNotificationManager.ACTION_PAUSE)
        assertEquals("com.example.sonora.ACTION_NEXT", SonoraNotificationManager.ACTION_NEXT)
        assertEquals("com.example.sonora.ACTION_PREV", SonoraNotificationManager.ACTION_PREV)
        assertEquals("com.example.sonora.ACTION_SHUFFLE", SonoraNotificationManager.ACTION_SHUFFLE)
        assertEquals("com.example.sonora.ACTION_REPEAT", SonoraNotificationManager.ACTION_REPEAT)
        assertEquals("com.example.sonora.ACTION_STOP", SonoraNotificationManager.ACTION_STOP)
        assertEquals("com.example.sonora.ACTION_SEEK", SonoraNotificationManager.ACTION_SEEK)
    }

    @Test
    fun `multi-folder whitelist includes subfolders while blacklist overrides whitelist`() {
        val musicRoot = FolderInfo(path = "/storage/emulated/0/Music", name = "Music")
        val sdFlac = FolderInfo(path = "/storage/9C33-6BBD/Lossless", name = "Lossless")
        val blacklistedSubfolder = FolderInfo(path = "/storage/emulated/0/Music/VoiceMemos", name = "VoiceMemos")

        val songs = listOf(
            songAt(1L, "/storage/emulated/0/Music/Indie/Track1.flac"),
            songAt(2L, "/storage/9C33-6BBD/Lossless/Jazz/Miles.flac"),
            songAt(3L, "/storage/emulated/0/Music/VoiceMemos/Note.m4a"),
            songAt(4L, "/storage/emulated/0/Downloads/Podcast.mp3"),
            songAt(5L, "/storage/emulated/0/MusicExtra/NotMatched.mp3"),
            songAt(1L, "/storage/emulated/0/Music/Indie/Track1.flac") // duplicate ID
        )

        val settings = FolderFilterSettings(
            whitelistEnabled = true,
            whitelistedFolders = listOf(musicRoot, sdFlac),
            blacklistedFolders = listOf(blacklistedSubfolder)
        )

        val filtered = MusicFolderFilter.filterSongs(songs, settings)
        val filteredIds = filtered.map { it.id }

        assertEquals(listOf(1L, 2L), filteredIds)
        assertFalse("Blacklist must exclude VoiceMemos even inside /Music", filteredIds.contains(3L))
        assertFalse("Non-whitelisted Downloads must be excluded", filteredIds.contains(4L))
        assertFalse("Prefix-similar folder MusicExtra must not match Music", filteredIds.contains(5L))
    }

    @Test
    fun `whitelist disabled includes all songs except blacklisted folders and subfolders`() {
        val whatsappAudio = FolderInfo(path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio", name = "WhatsApp Audio")
        val songs = listOf(
            songAt(10L, "/storage/emulated/0/Music/SongA.mp3"),
            songAt(11L, "/storage/emulated/0/Downloads/SongB.flac"),
            songAt(12L, "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/Sent/Voice.opus")
        )

        val settings = FolderFilterSettings(
            whitelistEnabled = false,
            whitelistedFolders = listOf(FolderInfo.defaultMusicFolder()),
            blacklistedFolders = listOf(whatsappAudio)
        )

        val filteredIds = MusicFolderFilter.filterSongs(songs, settings).map { it.id }
        assertTrue(filteredIds.contains(10L))
        assertTrue(filteredIds.contains(11L))
        assertFalse(filteredIds.contains(12L))
    }

    @Test
    fun `equalizer preferences persist and restore 15-band settings cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = EqualizerPreferences(context)

        val bands = FloatArray(15) { i -> (i - 7) * 0.5f }
        val expected = AdaptiveEqualizerSettings(
            isEnabled = false,
            mode = EqualizerMode.MANUAL,
            tilt = -0.35f,
            contour = 0.65f,
            macroProfile = MacroProfile.FOCUSED,
            selectedProfileId = "forward",
            manualBands = bands,
            volumeBoostDb = 4.2f
        )

        prefs.saveSettings(expected)
        val restored = prefs.loadSettings()

        assertEquals(expected.isEnabled, restored.isEnabled)
        assertEquals(expected.mode, restored.mode)
        assertEquals(expected.tilt, restored.tilt, 0.001f)
        assertEquals(expected.contour, restored.contour, 0.001f)
        assertEquals(expected.macroProfile, restored.macroProfile)
        assertEquals(expected.selectedProfileId, restored.selectedProfileId)
        assertEquals(expected.volumeBoostDb, restored.volumeBoostDb, 0.001f)
        assertTrue(expected.manualBands.contentEquals(restored.manualBands))
    }
}
