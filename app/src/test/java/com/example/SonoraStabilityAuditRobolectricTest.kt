package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import com.example.data.SongMetadataEntity
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.service.SonoraMusicService
import com.example.viewmodel.SonoraViewModel
import android.app.NotificationManager
import com.example.data.LocalMusicScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class SonoraStabilityAuditRobolectricTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: SonoraDatabase
    private lateinit var dao: SonoraDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = inMemoryDb.dao()
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun `application registers playback notification channel and scanner + notification manager work cleanly`(): Unit = runBlocking {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(SonoraApplication.PLAYBACK_CHANNEL_ID)
        assertNotNull("Playback notification channel must exist on API 26+", channel)

        val scanner = LocalMusicScanner(context, dao)
        val scanned = scanner.scanSongs()
        assertTrue("LocalMusicScanner should safely return tracks or demo fallback", scanned.isNotEmpty())

        val controller = Robolectric.buildService(SonoraMusicService::class.java)
            .create()
            .startCommand(0, 0)
        val service = controller.get()

        service.notificationManager.updateNotification(
            song = scanned.first(),
            isPlaying = true,
            isShuffle = false,
            deviceName = "Speaker"
        )
        assertNotNull(service.notificationManager.mediaSession)

        service.notificationManager.clearNotification()
        controller.destroy()
        Unit
    }

    @Test
    fun `dao upsertFavorite and upsertIncrementPlayCount persist new song rows without silent 0-row drop`() = runBlocking {
        val songId = 9001L
        dao.upsertFavorite(songId, true)
        dao.upsertIncrementPlayCount(songId, timestamp = 123456789L)

        val stored = dao.getMetadata(songId)
        assertNotNull("Expected metadata row to be inserted for first-time favorite/play", stored)
        assertTrue(stored!!.isFavorite)
        assertEquals(1, stored.playCount)
        assertEquals(123456789L, stored.lastPlayedTimestamp)

        dao.upsertIncrementPlayCount(songId, timestamp = 987654321L)
        val incremented = dao.getMetadata(songId)
        assertEquals(2, incremented!!.playCount)
        assertTrue(incremented.isFavorite)
    }

    @Test
    fun `service audio focus ducking and transient loss callbacks work safely`() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java)
            .create()
            .startCommand(0, 0)
        val service = controller.get()

        var remotePauseCalls = 0
        var remotePlayCalls = 0
        service.setPlaybackCallback(object : SonoraMusicService.ServicePlaybackCallback {
            override fun onRemotePlay() { remotePlayCalls++ }
            override fun onRemotePause() { remotePauseCalls++ }
            override fun onRemoteNext() = Unit
            override fun onRemotePrev() = Unit
            override fun onRemoteShuffle() = Unit
            override fun onRemoteRepeat() = Unit
            override fun onRemoteSeek(positionMs: Long) = Unit
        })
        service.updateAudioPolicySettings(focusEnabled = true, headsetPauseEnabled = true)

        // Duck -> gain -> transient pause -> noisy pause
        service.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(0.25f, service.audioEngine.focusDuckScale, 0.02f)

        service.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1.0f, service.audioEngine.focusDuckScale, 0.02f)

        service.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, remotePauseCalls)

        // Headset unplug ACTION_STOP / ACTION_PAUSE triggers onRemotePause (never resumes when paused)
        service.onStartCommand(Intent(context, SonoraMusicService::class.java).setAction(SonoraMusicService.ACTION_PAUSE), 0, 1)
        assertEquals(2, remotePauseCalls)
        assertEquals(0, remotePlayCalls)

        controller.destroy()
    }

    @Test
    fun `viewModel pausePlayback keeps paused state and toggleShuffle restores album sub-queue`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)

        val albumQueue = listOf(
            Song(
                id = 101L,
                title = "Track A",
                artist = "Artist 1",
                album = "Album X",
                albumId = 10L,
                durationMs = 180000L,
                uri = Uri.EMPTY,
                dataPath = "/music/a.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = 4_000_000L
            ),
            Song(
                id = 102L,
                title = "Track B",
                artist = "Artist 1",
                album = "Album X",
                albumId = 10L,
                durationMs = 200000L,
                uri = Uri.EMPTY,
                dataPath = "/music/b.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = 5_000_000L
            ),
            Song(
                id = 103L,
                title = "Track C",
                artist = "Artist 1",
                album = "Album X",
                albumId = 10L,
                durationMs = 210000L,
                uri = Uri.EMPTY,
                dataPath = "/music/c.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = 6_000_000L
            )
        )

        // Calling pausePlayback while already paused must NOT flip playback to playing
        vm.pausePlayback()
        assertFalse(vm.playbackInfo.value.isPlaying)

        // Play from an album queue, toggle shuffle ON then OFF -> original 3-track album order preserved
        if (vm.playbackInfo.value.shuffleMode == ShuffleMode.ON) {
            vm.toggleShuffle()
        }
        vm.playSong(albumQueue[1], newQueue = albumQueue, startIndex = 1)
        assertEquals(listOf(101L, 102L, 103L), vm.queue.value.map { it.id })

        vm.toggleShuffle()
        assertEquals(ShuffleMode.ON, vm.playbackInfo.value.shuffleMode)
        assertEquals(102L, vm.queue.value.first().id)

        vm.toggleShuffle()
        assertEquals(ShuffleMode.OFF, vm.playbackInfo.value.shuffleMode)
        assertEquals(listOf(101L, 102L, 103L), vm.queue.value.map { it.id })
        assertEquals(1, vm.queueIndex.value)

        // Edit metadata preserves existing Room favorite & playCount and updates queue song fields
        val realDbDao = SonoraDatabase.getDatabase(app).dao()
        realDbDao.upsertMetadata(SongMetadataEntity(id = 102L, isFavorite = true, playCount = 7, lastPlayedTimestamp = 555L))
        vm.updateSongMetadata(
            songId = 102L,
            newTitle = "Track B (Remastered)",
            newArtist = "Artist 1",
            newAlbum = "Album X",
            newGenre = "Ambient",
            lyrics = "[00:01.00]Hello"
        )
        // Allow Dispatchers.IO coroutine inside updateSongMetadata to finish cleanly
        repeat(20) {
            if (vm.queue.value.find { it.id == 102L }?.title == "Track B (Remastered)") return@repeat
            delay(50)
        }
        val persisted = realDbDao.getMetadata(102L)
        assertNotNull(persisted)
        assertTrue("Editing metadata must preserve favorite flag", persisted!!.isFavorite)
        assertEquals("Editing metadata must preserve play count", 7, persisted.playCount)
        assertEquals("Track B (Remastered)", vm.queue.value.find { it.id == 102L }?.title)
    }

    @Test
    fun `preferences recover legacy String spatial_intensity collision and persist Float plus preset cleanly`() {
        val rawPrefs = context.getSharedPreferences("sonora_app_settings", Context.MODE_PRIVATE)
        rawPrefs.edit()
            .clear()
            .putString("spatial_intensity", com.example.model.SpatialIntensity.IMMERSIVE.id)
            .commit()

        val prefs = com.example.data.SonoraPreferences(context)
        val migrated = prefs.spatialConfig
        assertEquals(com.example.model.SpatialIntensity.IMMERSIVE, migrated.spatialIntensity)
        assertEquals(0.75f, migrated.intensity, 0.01f)

        val updated = com.example.model.SpatialAudioConfig(
            isEnabled = true,
            mode = com.example.model.SpatialMode.CINEMA_360,
            intensity = 0.88f,
            spatialProfile = com.example.model.SpatialProfile.CINEMATIC,
            spatialIntensity = com.example.model.SpatialIntensity.SUBTLE
        )
        prefs.spatialConfig = updated

        val reloaded = com.example.data.SonoraPreferences(context).spatialConfig
        assertEquals(0.88f, reloaded.intensity, 0.01f)
        assertEquals(com.example.model.SpatialIntensity.SUBTLE, reloaded.spatialIntensity)
        assertEquals(com.example.model.SpatialProfile.CINEMATIC, reloaded.spatialProfile)
        assertEquals(com.example.model.SpatialMode.CINEMA_360, reloaded.mode)
    }

    @Test
    fun `dsp processor renders 15 band EQ 8D orbit and reverb without NaN Inf or silent drop across sample rates`() {
        val dsp = com.example.audio.SonoraDspProcessor()
        val gains = FloatArray(15) { idx -> if (idx < 4) 6.5f else if (idx > 11) 4.0f else -1.5f }
        dsp.updateSnapshot(
            com.example.audio.SonoraDspSnapshot(
                globalBypass = false,
                eqEnabled = true,
                eqGainsDb = gains,
                bassBoostStrength = 700,
                virtualizerStrength = 800,
                reverbPreset = android.media.audiofx.PresetReverb.PRESET_MEDIUMROOM,
                spatialConfig = com.example.model.SpatialAudioConfig(
                    isEnabled = true,
                    mode = com.example.model.SpatialMode.CINEMA_360,
                    spatialProfile = com.example.model.SpatialProfile.DYNAMIC_360
                ),
                orbit8dEnabled = true,
                orbit8dSpeed = 0.6f,
                orbit8dDepth = 0.85f,
                volumeScale = 1.0f
            )
        )

        for (sr in listOf(44100, 48000, 96000)) {
            dsp.configureSampleRate(sr)
            val frames = 1024
            val pcm = FloatArray(frames * 2) { i ->
                val sampleIdx = i / 2
                0.35f * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * sampleIdx / sr).toFloat()
            }
            dsp.processStereoBuffer(pcm, frames)
            assertFalse("DSP must not trigger safety bypass on valid PCM at $sr Hz", dsp.isSafetyBypassed)
            var peak = 0f
            for (s in pcm) {
                assertTrue("DSP sample must stay finite at $sr Hz", s.isFinite())
                val a = kotlin.math.abs(s)
                if (a > peak) peak = a
            }
            assertTrue("DSP output peak must remain audible at $sr Hz", peak > 0.05f)
        }
    }

    @Test
    fun `service handles ACTION_STOP safely on API 26+ and autonomous remote actions when callback cleared`() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java)
            .create()
            .startCommand(0, 0)
        val service = controller.get()

        val testSong = Song(
            id = 505L,
            title = "Autonomous Track",
            artist = "Sonora",
            album = "Test",
            albumId = 5L,
            durationMs = 180000L,
            uri = Uri.EMPTY,
            dataPath = "",
            mimeType = "audio/mpeg",
            sizeBytes = 1024L
        )
        service.updateQueueSnapshot(listOf(testSong), 0)
        service.updateForegroundNotification(testSong, isPlaying = false)

        // Toggling shuffle/repeat works autonomously even when playbackCallback == null
        service.setPlaybackCallback(null)
        service.handleRemoteShuffle()
        assertTrue(service.currentIsShuffleSnapshot)
        service.handleRemoteRepeat()
        assertEquals(com.example.model.RepeatMode.ALL, service.currentRepeatModeSnapshot)

        // ACTION_STOP completes cleanly on both API 26 and API 36 without ForegroundServiceDidNotStartInTimeException
        service.onStartCommand(Intent(context, SonoraMusicService::class.java).setAction(SonoraMusicService.ACTION_STOP), 0, 2)
        assertFalse(service.notificationManager.isForegroundNotificationActive)
        controller.destroy()
    }

    @Test
    fun `lyrics extractor and folder filter handle plain text LRC tags and blacklist precedence`() {
        val plain = com.example.util.LyricsExtractor.parseLrcLines("Line one\nLine two")
        assertEquals(2, plain.size)
        assertEquals(-1L, plain[0].timestampMs)

        val lrc = com.example.util.LyricsExtractor.parseLrcLines("[00:10.50]First line\n[00:25.00]Second line")
        assertEquals(2, lrc.size)
        assertEquals(10_500L, lrc[0].timestampMs)
        assertEquals(0, com.example.util.LyricsExtractor.findActiveLineIndex(lrc, 12_000L))
        assertEquals(1, com.example.util.LyricsExtractor.findActiveLineIndex(lrc, 30_000L))

        val sAllowed = Song(1L, "Allowed", "A", "Alb", 1L, 1000L, Uri.EMPTY, "/storage/emulated/0/Music/a.mp3", "audio/mpeg", 10L)
        val sBlockedSub = Song(2L, "Blocked", "A", "Alb", 1L, 1000L, Uri.EMPTY, "/storage/emulated/0/Music/CallRecordings/b.mp3", "audio/mpeg", 10L)
        val filtered = domain.filter.MusicFolderFilter.filterSongs(
            listOf(sAllowed, sBlockedSub),
            domain.model.FolderFilterSettings(
                whitelistEnabled = true,
                whitelistedFolders = listOf(domain.model.FolderInfo(path = "/storage/emulated/0/Music", name = "Music")),
                blacklistedFolders = listOf(domain.model.FolderInfo(path = "/storage/emulated/0/Music/CallRecordings", name = "CallRecordings"))
            )
        )
        assertEquals(listOf(1L), filtered.map { it.id })
    }
}
