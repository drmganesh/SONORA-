package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSongCrossRef
import com.example.data.SonoraDatabase
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.service.SonoraMusicService
import data.analytics.ListeningAnalyticsRepository
import data.analytics.ListeningEventEntity
import data.analytics.PlaybackSessionSnapshotEntity
import data.download.DownloadedTrackEntity
import data.preferences.PlaybackLifecyclePreferences
import data.queue.MusicQueueRepository
import domain.analytics.AnalyticsTimeRange
import domain.analytics.ListeningAnalyticsCalculator
import domain.analytics.ListeningTimeTracker
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.player.MiniPlayerVisualState
import domain.player.MusicMiniPlayerVisibilityPolicy
import domain.player.PlaybackAudioFocusController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraBackgroundCircularAnalyticsRestoreTest {

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var analyticsRepo: ListeningAnalyticsRepository
    private lateinit var queueRepo: MusicQueueRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        analyticsRepo = ListeningAnalyticsRepository(db.dao())
        queueRepo = MusicQueueRepository(db.dao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleSong(
        id: Long,
        title: String,
        artist: String = "Arijit Singh",
        album: String = "Soulful Hits",
        genre: String = "Bollywood",
        isOnline: Boolean = false
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = id * 10,
        durationMs = 240_000L,
        uri = Uri.parse(if (isOnline) "https://music.youtube.com/watch?v=song$id" else "content://media/external/audio/media/$id"),
        dataPath = if (isOnline) "" else "/storage/emulated/0/Music/$title.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 4_500_000L,
        isOnline = isOnline,
        contentUrl = if (isOnline) "https://music.youtube.com/watch?v=song$id" else "",
        providerKey = if (isOnline) "youtube" else "local",
        providerName = if (isOnline) "YouTube Music" else "Local",
        genre = genre
    )

    // --- PART 2: Normal Mini-Player Visibility & Online Video Isolation ---

    @Test
    fun musicMiniPlayer_visibleInMusicMode_andHiddenWhenNoSongOrInOnlineVideoMode() {
        // Default music mode with song -> expanded bar
        assertEquals(
            MiniPlayerVisualState.EXPANDED_BAR,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = false
            )
        )
        // No current song -> hidden
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = false,
                isInOnlineVideoExperience = false
            )
        )
    }

    @Test
    fun onlineVideoMode_hidesMusicMiniPlayer() {
        val inVideoHome = MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
            currentTab = 0,
            homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
            librarySectionMode = LibrarySectionMode.OFFLINE,
            isVideoPlayerExpanded = false,
            isVideoMiniPlayerVisible = false,
            isChannelDetailOpen = false
        )
        assertTrue(inVideoHome)

        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inVideoHome
            )
        )
        assertFalse(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inVideoHome
            )
        )
    }

    // --- PART 3: Listening Time Measurement & Music Analytics ---

    @Test
    fun listeningTimeTracker_excludesPausedTime_andNeverDoubleCountsCheckpoints() {
        var now = 1_000_000L
        val tracker = ListeningTimeTracker(clockMs = { now })
        val song = sampleSong(1L, "Kesariya", artist = "Arijit Singh", genre = "Romantic", isOnline = false)

        // Start playback
        assertNull(tracker.onTrackStarted(song))

        // Play for 12 seconds -> checkpoint emits 12s play start
        now += 12_000L
        val slice1 = tracker.checkpointIfPlaying(minElapsedMs = 10_000L)
        assertNotNull(slice1)
        assertEquals(12_000L, slice1!!.listenedMs)
        assertTrue(slice1.isPlayStart)

        // Play 8 more seconds then pause
        now += 8_000L
        val slice2 = tracker.onPlaybackPausedOrStopped()
        assertNotNull(slice2)
        assertEquals(8_000L, slice2!!.listenedMs)
        assertFalse(slice2.isPlayStart)
        assertEquals(slice1.sessionToken, slice2.sessionToken)

        // Stay paused for 5 minutes -> MUST NOT accumulate any paused time!
        now += 300_000L
        assertNull(tracker.checkpointIfPlaying(minElapsedMs = 1_000L))
        assertNull(tracker.onPlaybackPausedOrStopped())

        // Resume same track for 10 seconds -> counts only those 10s, same session token
        assertNull(tracker.onPlaybackResumed(song))
        now += 10_000L
        val slice3 = tracker.onPlaybackPausedOrStopped()
        assertNotNull(slice3)
        assertEquals(10_000L, slice3!!.listenedMs)
        assertEquals(slice1.sessionToken, slice3.sessionToken)

        // Verify analytics totals: 30s total, 1 song play (NOT 3!), 1 unique track
        val dashboard = ListeningAnalyticsCalculator.calculate(
            allEvents = listOf(slice1, slice2, slice3),
            range = AnalyticsTimeRange.ALL_TIME,
            nowMs = now
        )
        assertEquals(30_000L, dashboard.totalListeningMs)
        assertEquals(1, dashboard.totalSongsPlayed)
        assertEquals(1, dashboard.uniqueTracksPlayed)
    }

    @Test
    fun analyticsCalculator_computesRankingsChartsOfflineOnlineSplitAndTimeFilters() {
        val dayMs = 86_400_000L
        val now = 1_760_000_000_000L // Fixed reference point

        val events = listOf(
            // 2 days ago (within 7d, 30d, 90d, All) - Offline Bollywood
            ListeningEventEntity(
                id = 1, sessionToken = "s1", songId = 10L, title = "Tum Hi Ho",
                artist = "Arijit Singh", album = "Aashiqui 2", genre = "Bollywood",
                isOnline = false, providerKey = "local", artworkUrl = null,
                listenedMs = 180_000L, startedAtMs = now - 2 * dayMs - 180_000L, endedAtMs = now - 2 * dayMs,
                dayEpoch = ListeningAnalyticsCalculator.dayEpochFor(now - 2 * dayMs), isPlayStart = true
            ),
            // Today - Online Indie
            ListeningEventEntity(
                id = 2, sessionToken = "s2", songId = 20L, title = "Husn",
                artist = "Anuv Jain", album = "Husn Single", genre = "Indie",
                isOnline = true, providerKey = "youtube", artworkUrl = "https://img/20.jpg",
                listenedMs = 240_000L, startedAtMs = now - 240_000L, endedAtMs = now,
                dayEpoch = ListeningAnalyticsCalculator.dayEpochFor(now), isPlayStart = true
            ),
            // 15 days ago (excluded from 7d, included in 30d/90d/All) - Offline Bollywood repeat
            ListeningEventEntity(
                id = 3, sessionToken = "s3", songId = 10L, title = "Tum Hi Ho",
                artist = "Arijit Singh", album = "Aashiqui 2", genre = "Bollywood",
                isOnline = false, providerKey = "local", artworkUrl = null,
                listenedMs = 200_000L, startedAtMs = now - 15 * dayMs - 200_000L, endedAtMs = now - 15 * dayMs,
                dayEpoch = ListeningAnalyticsCalculator.dayEpochFor(now - 15 * dayMs), isPlayStart = true
            ),
            // 120 days ago (only in All Time)
            ListeningEventEntity(
                id = 4, sessionToken = "s4", songId = 30L, title = "Kun Faya Kun",
                artist = "A.R. Rahman", album = "Rockstar", genre = "Sufi",
                isOnline = false, providerKey = "local", artworkUrl = null,
                listenedMs = 300_000L, startedAtMs = now - 120 * dayMs - 300_000L, endedAtMs = now - 120 * dayMs,
                dayEpoch = ListeningAnalyticsCalculator.dayEpochFor(now - 120 * dayMs), isPlayStart = true
            )
        )

        val stats7d = ListeningAnalyticsCalculator.calculate(events, AnalyticsTimeRange.LAST_7_DAYS, now)
        assertEquals(420_000L, stats7d.totalListeningMs)
        assertEquals(2, stats7d.totalSongsPlayed)
        assertEquals(2, stats7d.uniqueTracksPlayed)
        assertEquals(180_000L, stats7d.offlineListeningMs)
        assertEquals(240_000L, stats7d.onlineListeningMs)
        assertEquals("Husn", stats7d.mostPlayedSong?.title)
        assertEquals(7, stats7d.dailyActivity.size)

        val stats30d = ListeningAnalyticsCalculator.calculate(events, AnalyticsTimeRange.LAST_30_DAYS, now)
        assertEquals(620_000L, stats30d.totalListeningMs)
        assertEquals(3, stats30d.totalSongsPlayed)
        assertEquals(2, stats30d.uniqueTracksPlayed)
        assertEquals("Tum Hi Ho", stats30d.mostPlayedSong?.title)
        assertEquals("Arijit Singh", stats30d.mostPlayedArtist?.title)
        assertEquals("Aashiqui 2", stats30d.mostPlayedAlbum?.title)
        assertEquals("Bollywood", stats30d.mostPlayedGenre?.title)
        assertEquals(8, stats30d.weeklyActivity.size)
        assertEquals(6, stats30d.monthlyActivity.size)
        assertEquals(3, stats30d.timeline.size)

        val statsAll = ListeningAnalyticsCalculator.calculate(events, AnalyticsTimeRange.ALL_TIME, now)
        assertEquals(920_000L, statsAll.totalListeningMs)
        assertEquals(4, statsAll.totalSongsPlayed)
        assertEquals(3, statsAll.uniqueTracksPlayed)
    }

    @Test
    fun clearListeningHistory_clearsOnlyAnalytics_andPreservesDownloadsAndPlaylists() = runBlocking {
        // Insert a listening event, a downloaded track, and a playlist
        analyticsRepo.recordEvent(
            ListeningEventEntity(
                sessionToken = "tok1", songId = 101L, title = "Tracked Song",
                artist = "Artist", album = "Album", genre = "Pop",
                isOnline = false, providerKey = "local", artworkUrl = null,
                listenedMs = 45_000L, startedAtMs = 1000L, endedAtMs = 46_000L,
                dayEpoch = 20_000L, isPlayStart = true
            )
        )
        db.dao().upsertDownloadedTrack(
            DownloadedTrackEntity(
                id = 500L, sourceKey = "yt:500", serviceId = 0, providerKey = "youtube",
                providerName = "YouTube", contentUrl = "https://youtu.be/500", title = "Saved Offline",
                artist = "Artist", album = "Album", durationMs = 200_000L, artworkUrl = null,
                localArtworkPath = null, localFileUri = "content://downloads/500",
                treeFolderUri = "content://tree", displayFileName = "500.m4a",
                formatLabel = "M4A", mimeType = "audio/mp4", bitrateKbps = 256, sampleRateHz = 44100,
                fileSizeBytes = 5_000_000L, downloadedAt = 10_000L
            )
        )
        val playlistId = db.dao().insertPlaylist(PlaylistEntity(name = "My Favorites"))
        db.dao().insertPlaylistSong(PlaylistSongCrossRef(playlistId = playlistId, songId = 101L, orderIndex = 0))

        // Clear listening history
        analyticsRepo.clearListeningHistory()

        // Listening events must be empty
        assertTrue(analyticsRepo.getAllEventsOnce().isEmpty())

        // Downloads and playlists MUST remain completely untouched!
        val downloads = db.dao().getAllDownloadedTracksFlow().first()
        val playlists = db.dao().getAllPlaylistsFlow().first()
        assertEquals(1, downloads.size)
        assertEquals("Saved Offline", downloads.first().title)
        assertEquals(1, playlists.size)
        assertEquals("My Favorites", playlists.first().name)
    }

    // --- PART 4: Restore Music Session Across App Reopen ---

    @Test
    fun playbackSessionSnapshot_persistsAndRestoresFullQueuePositionModesAndExplicitStopFlag() = runBlocking {
        val songs = listOf(
            sampleSong(1L, "Offline Anthem", isOnline = false),
            sampleSong(2L, "Online Stream Hit", isOnline = true),
            sampleSong(3L, "Late Night Chill", isOnline = false)
        )
        val wrapped = queueRepo.wrapSongs(songs)
        queueRepo.persistQueue(wrapped, currentIndex = 1, playbackPositionMs = 73_500L)

        val savedSnapshot = PlaybackSessionSnapshotEntity(
            id = 1,
            lastTrackId = 2L,
            isOnline = true,
            positionMs = 73_500L,
            queueIndex = 1,
            repeatMode = RepeatMode.ALL.name,
            isShuffle = true,
            playbackSpeed = 1.25f,
            volume = 0.9f,
            contextType = "RADIO",
            contextLabel = "Online Stream Hit Radio",
            lastPlayerStyle = "VINYL",
            wasPlayingWhenSaved = true,
            explicitlyStopped = false,
            savedAtMs = 999_000L
        )
        analyticsRepo.savePlaybackSession(savedSnapshot)

        val restoredQueue = queueRepo.restoreQueue()
        val restoredSession = analyticsRepo.getPlaybackSession()

        assertEquals(3, restoredQueue.items.size)
        assertEquals(1, restoredQueue.currentIndex)
        assertEquals(73_500L, restoredQueue.playbackPositionMs)
        assertEquals("Online Stream Hit", restoredQueue.items[1].song.title)
        assertTrue(restoredQueue.items[1].song.isOnline)

        assertNotNull(restoredSession)
        assertEquals(2L, restoredSession!!.lastTrackId)
        assertTrue(restoredSession.isOnline)
        assertEquals(73_500L, restoredSession.positionMs)
        assertEquals("ALL", restoredSession.repeatMode)
        assertTrue(restoredSession.isShuffle)
        assertEquals(1.25f, restoredSession.playbackSpeed, 0.001f)
        assertEquals("RADIO", restoredSession.contextType)
        assertEquals("VINYL", restoredSession.lastPlayerStyle)
        assertTrue(restoredSession.wasPlayingWhenSaved)
        assertFalse(restoredSession.explicitlyStopped)
    }

    // --- PART 5: Phone Call Interruption & Audio Focus State Machine ---

    @Test
    fun audioFocusController_resumesAfterPhoneCall_onlyWhenPlayingAndSettingEnabled() {
        val controller = PlaybackAudioFocusController()
        controller.onUserStartedOrResumedPlayback()

        // Incoming call (transient loss) while playing -> pause WITHOUT abandoning focus, save position
        val lossAction = controller.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = true,
            resumeAfterPhoneCallEnabled = true
        )
        assertTrue(lossAction is PlaybackAudioFocusController.Action.PauseForTransientLoss)
        assertTrue((lossAction as PlaybackAudioFocusController.Action.PauseForTransientLoss).keepAudioFocusRequest)
        assertTrue(lossAction.saveExactPosition)
        assertTrue(controller.inTransientFocusLoss)
        assertTrue(controller.shouldResumeOnFocusGain)

        // Call ends -> AUDIOFOCUS_GAIN -> auto-resume!
        val gainAction = controller.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            resumeAfterPhoneCallEnabled = true
        )
        assertTrue(gainAction is PlaybackAudioFocusController.Action.ResumeAfterFocusGain)
        assertFalse(controller.inTransientFocusLoss)
    }

    @Test
    fun audioFocusController_doesNotResume_ifManualPauseDuringCall_orSettingOff_orPermanentLoss() {
        // Case 1: User manually pauses during the phone call -> must NOT auto-resume
        val c1 = PlaybackAudioFocusController()
        c1.onUserStartedOrResumedPlayback()
        c1.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isCurrentlyPlaying = true, resumeAfterPhoneCallEnabled = true)
        c1.onUserManualPause()
        val gain1 = c1.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isCurrentlyPlaying = false, resumeAfterPhoneCallEnabled = true)
        assertEquals(PlaybackAudioFocusController.Action.None, gain1)

        // Case 2: Resume After Phone Call setting disabled -> must NOT auto-resume
        val c2 = PlaybackAudioFocusController()
        c2.onUserStartedOrResumedPlayback()
        c2.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isCurrentlyPlaying = true, resumeAfterPhoneCallEnabled = false)
        val gain2 = c2.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isCurrentlyPlaying = false, resumeAfterPhoneCallEnabled = false)
        assertEquals(PlaybackAudioFocusController.Action.None, gain2)

        // Case 3: Permanent audio focus loss (another media app took focus) -> must NOT auto-resume
        val c3 = PlaybackAudioFocusController()
        c3.onUserStartedOrResumedPlayback()
        val perm = c3.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, isCurrentlyPlaying = true, resumeAfterPhoneCallEnabled = true)
        assertTrue(perm is PlaybackAudioFocusController.Action.PauseForPermanentLoss)
        val gain3 = c3.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isCurrentlyPlaying = false, resumeAfterPhoneCallEnabled = true)
        assertEquals(PlaybackAudioFocusController.Action.None, gain3)

        // Case 4: Ducking + Restore volume only (does not force play if already playing)
        val c4 = PlaybackAudioFocusController()
        c4.onUserStartedOrResumedPlayback()
        val duck = c4.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, isCurrentlyPlaying = true, resumeAfterPhoneCallEnabled = true)
        assertTrue(duck is PlaybackAudioFocusController.Action.DuckVolume)
        val unduck = c4.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isCurrentlyPlaying = true, resumeAfterPhoneCallEnabled = true)
        assertTrue(unduck is PlaybackAudioFocusController.Action.RestoreVolumeOnly)

        // Case 5: Becoming noisy (headphone unplug / BT disconnect) -> pause cleanly
        val c5 = PlaybackAudioFocusController()
        c5.onUserStartedOrResumedPlayback()
        val noisy = c5.onAudioBecomingNoisy(headsetAutoPauseEnabled = true)
        assertTrue(noisy is PlaybackAudioFocusController.Action.PauseForBecomingNoisy)
    }

    // --- PART 1 & PART 6: Service Explicit Stop & Recents Task Removal ---

    @Test
    fun sonoraMusicService_explicitStop_clearsNotificationAndMediaSession_andPreservesQueue() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java).create()
        val service = controller.get()

        val song1 = sampleSong(11L, "Midnight Echoes")
        val song2 = sampleSong(12L, "Dawn Horizon")
        service.updateQueueSnapshot(listOf(song1, song2), 0)
        service.updateForegroundNotification(song1, isPlaying = true)

        var remoteStopInvoked = false
        service.setPlaybackCallback(object : SonoraMusicService.ServicePlaybackCallback {
            override fun onRemotePlay() {}
            override fun onRemotePause() {}
            override fun onRemoteNext() {}
            override fun onRemotePrev() {}
            override fun onRemoteShuffle() {}
            override fun onRemoteRepeat() {}
            override fun onRemoteSeek(positionMs: Long) {}
            override fun onRemoteStop() {
                remoteStopInvoked = true
            }
        })

        // Trigger explicit Stop action (from notification Stop button / MediaSession.onStop / UI menu)
        service.handleExplicitStop()

        assertTrue(remoteStopInvoked)
        assertTrue(service.focusController.explicitlyStopped)
        assertFalse(service.notificationManager.isForegroundNotificationActive)
        assertFalse(service.mediaSession?.isActive ?: true)
        assertEquals(PlaybackStateCompat.STATE_NONE, service.mediaSession?.controller?.playbackState?.state)
        assertNull(service.mediaSession?.controller?.metadata)
        // Queue must remain cleanly preserved!
        assertEquals(2, service.currentQueueSnapshot.size)

        controller.destroy()
    }

    @Test
    fun sonoraMusicService_onTaskRemovedFromRecents_savesSessionStopsPlaybackAndClearsMediaSession() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java).create()
        val service = controller.get()

        val song = sampleSong(21L, "Evening Rain")
        service.updateQueueSnapshot(listOf(song), 0)
        service.updateLifecycleSettings(
            backgroundPlayback = true,
            resumeAfterPhoneCall = true,
            stopOnTaskRemoved = true
        )
        service.updateForegroundNotification(song, isPlaying = true)

        var taskRemovedCallbackCalled = false
        service.setPlaybackCallback(object : SonoraMusicService.ServicePlaybackCallback {
            override fun onRemotePlay() {}
            override fun onRemotePause() {}
            override fun onRemoteNext() {}
            override fun onRemotePrev() {}
            override fun onRemoteShuffle() {}
            override fun onRemoteRepeat() {}
            override fun onRemoteSeek(positionMs: Long) {}
            override fun onTaskRemovedStop(positionMs: Long, wasPlaying: Boolean) {
                taskRemovedCallbackCalled = true
            }
        })

        // Simulate user swiping SONORA away from Recents
        service.onTaskRemoved(Intent(Intent.ACTION_MAIN))

        assertTrue(taskRemovedCallbackCalled)
        assertFalse(service.notificationManager.isForegroundNotificationActive)
        assertFalse(service.mediaSession?.isActive ?: true)
        assertEquals(PlaybackStateCompat.STATE_NONE, service.mediaSession?.controller?.playbackState?.state)
        assertNull(service.mediaSession?.controller?.metadata)

        controller.destroy()
    }

    // --- PART 7: Persistent Settings in DataStore ---

    @Test
    fun playbackLifecyclePreferences_persistsAllBackgroundAndSessionSettings() = runBlocking {
        val prefs = PlaybackLifecyclePreferences(context)

        // Verify defaults (Auto Resume on App Launch MUST default to Off!)
        val defaultBg = prefs.backgroundSettingsFlow.first()
        val defaultSess = prefs.sessionSettingsFlow.first()
        assertTrue(defaultBg.backgroundPlaybackEnabled)
        assertTrue(defaultBg.resumeAfterPhoneCall)
        assertFalse(defaultBg.autoResumeOnAppLaunch)
        assertTrue(defaultSess.restorePreviousQueue)
        assertTrue(defaultSess.restorePlaybackPosition)
        assertTrue(defaultSess.stopPlaybackOnTaskRemoved)

        // Update all settings and verify persistence
        prefs.setBackgroundPlaybackEnabled(false)
        prefs.setResumeAfterPhoneCall(false)
        prefs.setAutoResumeOnAppLaunch(true)
        prefs.setRestorePreviousQueue(false)
        prefs.setRestorePlaybackPosition(false)
        prefs.setStopPlaybackOnTaskRemoved(false)

        val updatedBg = prefs.backgroundSettingsFlow.first()
        val updatedSess = prefs.sessionSettingsFlow.first()
        assertFalse(updatedBg.backgroundPlaybackEnabled)
        assertFalse(updatedBg.resumeAfterPhoneCall)
        assertTrue(updatedBg.autoResumeOnAppLaunch)
        assertFalse(updatedSess.restorePreviousQueue)
        assertFalse(updatedSess.restorePlaybackPosition)
        assertFalse(updatedSess.stopPlaybackOnTaskRemoved)
    }
}
