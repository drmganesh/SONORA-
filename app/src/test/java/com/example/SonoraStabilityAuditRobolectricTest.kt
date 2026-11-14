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
}
