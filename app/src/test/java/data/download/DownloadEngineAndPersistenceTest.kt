package data.download

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import data.online.NetworkConnectivityMonitor
import data.online.OnlineMusicRepository
import data.preferences.DownloadPreferences
import domain.download.DownloadFormatCandidate
import domain.download.DownloadQualityOption
import domain.download.DownloadState
import domain.download.DownloadabilityResult
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class DownloadEngineAndPersistenceTest {

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var dao: SonoraDao
    private lateinit var connectivityMonitor: NetworkConnectivityMonitor
    private lateinit var onlineRepo: OnlineMusicRepository
    private lateinit var storageHelper: DownloadStorageHelper
    private lateinit var streamSelector: DownloadStreamSelector
    private lateinit var downloadPrefs: DownloadPreferences
    private lateinit var tempRootDir: File
    private lateinit var targetFolderDir: File
    private var serverSocket: ServerSocket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().build()
            )
        }
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        connectivityMonitor = NetworkConnectivityMonitor(context)
        onlineRepo = OnlineMusicRepository(dao, connectivityMonitor)
        storageHelper = DownloadStorageHelper(context)
        streamSelector = DownloadStreamSelector(onlineRepo, connectivityMonitor, storageHelper)
        downloadPrefs = DownloadPreferences(context)
        runBlocking { downloadPrefs.clearDownloadLocation() }

        tempRootDir = File(context.cacheDir, "dl_test_${System.nanoTime()}").apply { mkdirs() }
        targetFolderDir = File(tempRootDir, "SavedMusic").apply { mkdirs() }
        val stagingDir = storageHelper.getStagingDir()
        ShadowStatFs.registerStats(context.cacheDir.absolutePath, 10_000_000, 10_000_000, 10_000_000)
        ShadowStatFs.registerStats(stagingDir.absolutePath, 10_000_000, 10_000_000, 10_000_000)
        ShadowStatFs.registerStats(targetFolderDir.absolutePath, 10_000_000, 10_000_000, 10_000_000)
    }

    @After
    fun teardown() {
        runCatching { serverSocket?.close() }
        ShadowStatFs.reset()
        db.close()
        tempRootDir.deleteRecursively()
        connectivityMonitor.unregister()
    }

    private fun sampleOnlineTrack(
        suffix: String = "track1",
        isLive: Boolean = false,
        kind: OnlineItemKind = OnlineItemKind.TRACK
    ): OnlineTrack {
        val url = "https://music.youtube.com/watch?v=$suffix"
        return OnlineTrack(
            stableId = -1000L - suffix.hashCode().toLong(),
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = url,
            title = "Midnight Waves $suffix",
            artist = "Neon Echo",
            album = "Coastal Signal",
            durationMs = 195_000L,
            artworkUrl = null,
            kind = kind,
            isLive = isLive
        )
    }

    @Test
    fun `Room MIGRATION_2_3 creates download_tasks and downloaded_tracks tables cleanly`() {
        val dbFile = File(tempRootDir, "migration_v2.db")
        val helper = SQLiteOpenHelperFactoryCompat.createV2(context, dbFile.absolutePath)
        val sqliteDb = helper.writableDatabase
        sqliteDb.execSQL(
            "INSERT INTO `song_metadata` (`id`, `isFavorite`, `playCount`, `lastPlayedTimestamp`, `customTitle`, `customArtist`, `customAlbum`, `customGenre`, `customLyrics`) " +
                "VALUES (42, 1, 5, 1700000000, NULL, NULL, NULL, NULL, NULL)"
        )
        sqliteDb.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, SonoraDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                SonoraDatabase.MIGRATION_1_2,
                SonoraDatabase.MIGRATION_2_3,
                SonoraDatabase.MIGRATION_3_4,
                SonoraDatabase.MIGRATION_4_5,
                SonoraDatabase.MIGRATION_5_6,
                SonoraDatabase.MIGRATION_6_7,
                SonoraDatabase.MIGRATION_7_8,
                SonoraDatabase.MIGRATION_8_9,
                SonoraDatabase.MIGRATION_9_10,
                SonoraDatabase.MIGRATION_10_11,
                SonoraDatabase.MIGRATION_11_12,
                SonoraDatabase.MIGRATION_12_13
            )
            .allowMainThreadQueries()
            .build()
        try {
            val migratedDao = migrated.dao()
            runBlocking {
                val preservedMeta = migratedDao.getMetadata(42L)
                assertNotNull(preservedMeta)
                assertTrue(preservedMeta!!.isFavorite)
                assertTrue(migratedDao.getAllDownloadTasksFlow().first().isEmpty())
                assertTrue(migratedDao.getAllDownloadedTracksFlow().first().isEmpty())
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun `DownloadPreferences persists SAF tree URI cleanly and changes update storage summary`() = runBlocking {
        val initial = downloadPrefs.getLocationConfig()
        assertFalse(initial.isConfigured)
        assertNotNull(storageHelper.buildSuggestedInitialTreeUri())

        val firstUri = Uri.fromFile(targetFolderDir).toString()
        downloadPrefs.saveDownloadLocation(firstUri, "SavedMusic")

        val afterSave = downloadPrefs.getLocationConfig()
        assertTrue(afterSave.isConfigured)
        assertEquals(firstUri, afterSave.treeUri)
        assertEquals("SavedMusic", afterSave.folderLabel)

        val secondDir = File(tempRootDir, "HiResMusic").apply { mkdirs() }
        val secondUri = Uri.fromFile(secondDir).toString()
        downloadPrefs.saveDownloadLocation(secondUri, "HiResMusic")

        val updated = downloadPrefs.getLocationConfig()
        assertEquals(secondUri, updated.treeUri)
        assertEquals("HiResMusic", updated.folderLabel)
    }

    @Test
    fun `DownloadStreamSelector maps real streams accurately without fabricating sizes or mislabeling lossy codecs as lossless`() {
        val track = sampleOnlineTrack("codec_audit")
        val resolved = ResolvedOnlineStreamResult(
            serviceId = 0,
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl,
            title = track.title,
            artist = track.artist,
            artistUrl = null,
            durationMs = track.durationMs,
            artworkUrl = null,
            selectedStream = OnlineAudioStreamOption(
                id = "opus_160",
                url = "https://cdn.example.com/audio.webm",
                mimeType = "audio/webm",
                codec = "opus",
                formatName = "webm",
                averageBitrateKbps = 160,
                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                isAudioOnly = true,
                isLossless = true // Deliberately poisoned flag on Opus to verify guard!
            ),
            availableStreams = listOf(
                OnlineAudioStreamOption(
                    id = "opus_160",
                    url = "https://cdn.example.com/opus160.webm",
                    mimeType = "audio/webm",
                    codec = "opus",
                    formatName = "webm",
                    averageBitrateKbps = 160,
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    isAudioOnly = true,
                    isLossless = true
                ),
                OnlineAudioStreamOption(
                    id = "aac_256",
                    url = "https://cdn.example.com/aac256.m4a",
                    mimeType = "audio/mp4",
                    codec = "mp4a.40.2",
                    formatName = "m4a",
                    averageBitrateKbps = 256,
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    isAudioOnly = true,
                    isLossless = false
                ),
                OnlineAudioStreamOption(
                    id = "aac_unknown_bitrate",
                    url = "https://cdn.example.com/aac_raw.m4a",
                    mimeType = "audio/mp4",
                    codec = "aac",
                    formatName = "m4a",
                    averageBitrateKbps = 0,
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    isAudioOnly = true,
                    isLossless = false
                ),
                OnlineAudioStreamOption(
                    id = "hls_segmented",
                    url = "https://cdn.example.com/playlist.m3u8",
                    mimeType = "application/x-mpegURL",
                    codec = "aac",
                    formatName = "hls",
                    averageBitrateKbps = 192,
                    deliveryType = StreamDeliveryType.HLS,
                    isAudioOnly = true,
                    isLossless = false
                ),
                OnlineAudioStreamOption(
                    id = "drm_stream",
                    url = "https://cdn.example.com/drm.mp4",
                    mimeType = "audio/mp4+widevine+drm",
                    codec = "widevine",
                    formatName = "mp4",
                    averageBitrateKbps = 320,
                    deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                    isAudioOnly = true,
                    isLossless = false
                )
            )
        )

        val candidatesResult = streamSelector.buildCandidatesFromResolved(resolved)
        assertTrue(candidatesResult.isSuccess)
        val candidates = candidatesResult.getOrThrow()

        // Segmented HLS and DRM streams excluded
        assertEquals(3, candidates.size)
        assertFalse(candidates.any { it.streamId == "hls_segmented" || it.streamId == "drm_stream" })

        // Opus never mislabeled as FLAC/lossless
        val opusCandidate = candidates.first { it.streamId == "opus_160" }
        assertEquals("OPUS", opusCandidate.codecLabel)
        assertFalse(opusCandidate.isLossless)

        // Unknown bitrate & unknown Content-Length never fabricated
        val unknownCandidate = candidates.first { it.streamId == "aac_unknown_bitrate" }
        assertEquals(-1L, unknownCandidate.contentLengthBytes)
        assertEquals("AAC / M4A", unknownCandidate.summaryLabel)

        val recs = streamSelector.buildPresetRecommendations(candidates, isMeteredNetwork = false)
        assertEquals("aac_256", recs[DownloadQualityOption.BEST_AVAILABLE]?.streamId)
        assertEquals("aac_256", recs[DownloadQualityOption.HIGH_QUALITY]?.streamId)
        assertEquals("opus_160", recs[DownloadQualityOption.ORIGINAL_FORMAT]?.streamId)
        assertEquals("aac_256", recs[DownloadQualityOption.AUTO]?.streamId)

        // Live & segmented-only tracks rejected with informative message
        val liveCheck = streamSelector.checkPreconditions(sampleOnlineTrack(isLive = true), null, null)
        assertTrue(liveCheck is DownloadabilityResult.Unsupported)

        val dashOnlyResolved = resolved.copy(
            availableStreams = listOf(
                OnlineAudioStreamOption(
                    id = "dash_only",
                    url = "https://cdn.example.com/manifest.mpd",
                    mimeType = "audio/mp4",
                    codec = "aac",
                    formatName = "dash",
                    averageBitrateKbps = 128,
                    deliveryType = StreamDeliveryType.DASH,
                    isAudioOnly = true
                )
            )
        )
        val dashRes = streamSelector.buildCandidatesFromResolved(dashOnlyResolved)
        assertTrue(dashRes.isFailure)
        assertTrue(dashRes.exceptionOrNull()?.message?.contains("HLS/DASH") == true)
    }

    @Test
    fun `DownloadExecutionEngine executes full HTTP download, pause with Range 206 resume, cancel cleanup, and clearHistory keeps files`() = runBlocking {
        val payload = ByteArray(128 * 1024) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        val pauseSignalTriggered = AtomicInteger(0)
        val rangeRequestsSeen = AtomicInteger(0)

        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        // Background HTTP server supporting full GET + HTTP 206 Range resume with throttling on first request so pause works deterministically
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                headers[line.substring(0, colon).trim().lowercase()] =
                                    line.substring(colon + 1).trim()
                            }
                        }
                        val out = sock.getOutputStream()
                        val rangeHeader = headers["range"]
                        if (requestLine.contains("/rate429")) {
                            out.write("HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            out.flush()
                            return@use
                        }
                        val startOffset = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                            rangeRequestsSeen.incrementAndGet()
                            rangeHeader.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                        } else {
                            0
                        }

                        if (startOffset > 0) {
                            val remaining = payload.size - startOffset
                            val respHeaders = "HTTP/1.1 206 Partial Content\r\n" +
                                "Content-Type: audio/mp4\r\n" +
                                "Content-Length: $remaining\r\n" +
                                "Content-Range: bytes $startOffset-${payload.size - 1}/${payload.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            out.write(respHeaders.toByteArray())
                            out.write(payload, startOffset, remaining)
                            out.flush()
                        } else {
                            val respHeaders = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: audio/mp4\r\n" +
                                "Content-Length: ${payload.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            out.write(respHeaders.toByteArray())
                            // Stream in 8KB chunks; if pauseSignalTriggered == 0, throttle slightly for deterministic mid-stream pause
                            var offset = 0
                            while (offset < payload.size) {
                                val len = minOf(8 * 1024, payload.size - offset)
                                out.write(payload, offset, len)
                                out.flush()
                                offset += len
                                if (requestLine.contains("/slow") && pauseSignalTriggered.get() == 0) {
                                    Thread.sleep(35L)
                                }
                            }
                        }
                    }
                }
            }
        }

        val folderUri = Uri.fromFile(targetFolderDir).toString()
        downloadPrefs.saveDownloadLocation(folderUri, "SavedMusic")

        val engine = DownloadExecutionEngine(
            dao = dao,
            onlineRepository = onlineRepo,
            storageHelper = storageHelper,
            allowPrivateHostsForTests = true,
            streamSelector = streamSelector,
            okHttpClient = OkHttpClient(),
            requirePersistedSafPermission = false,
            streamResolverOverride = { task, _ ->
                val url = if (task.contentUrl.contains("slow")) {
                    "http://127.0.0.1:$port/slow.m4a"
                } else {
                    "http://127.0.0.1:$port/fast.m4a"
                }
                OnlineResult.Success(
                    ResolvedOnlineStreamResult(
                        serviceId = 0,
                        providerKey = task.providerKey,
                        providerName = task.providerName,
                        contentUrl = task.contentUrl,
                        title = task.title,
                        artist = task.artist,
                        artistUrl = null,
                        durationMs = task.durationMs,
                        artworkUrl = null,
                        selectedStream = OnlineAudioStreamOption(
                            id = "m4a_192",
                            url = url,
                            mimeType = "audio/mp4",
                            codec = "mp4a.40.2",
                            formatName = "m4a",
                            averageBitrateKbps = 192,
                            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                            isAudioOnly = true
                        ),
                        availableStreams = listOf(
                            OnlineAudioStreamOption(
                                id = "m4a_192",
                                url = url,
                                mimeType = "audio/mp4",
                                codec = "mp4a.40.2",
                                formatName = "m4a",
                                averageBitrateKbps = 192,
                                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                                isAudioOnly = true
                            )
                        )
                    )
                )
            }
        )

        val coordinator = DownloadManagerCoordinator(
            appContext = context,
            dao = dao,
            preferences = downloadPrefs,
            storageHelper = storageHelper,
            onlineRepository = onlineRepo,
            connectivityMonitor = connectivityMonitor,
            streamSelector = streamSelector,
            executionEngine = engine
        )

        // 1. Complete a track and verify Pause + Resume with Range 206
        val slowTrack = sampleOnlineTrack("slow_resume")
        val candidate = DownloadFormatCandidate(
            streamId = "m4a_192",
            codecLabel = "AAC / M4A",
            containerExtension = "m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 192,
            contentLengthBytes = payload.size.toLong(),
            streamUrl = "http://127.0.0.1:$port/slow.m4a",
            isLossless = false,
            isAudioOnly = true,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
        )
        val task = coordinator.enqueueDownload(
            track = slowTrack,
            qualityOption = DownloadQualityOption.HIGH_QUALITY,
            selectedCandidate = candidate,
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()

        val firstRun = async { engine.executeTask(task.taskId) }
        // Wait until the staging file is created and at least one chunk has been flushed to disk
        val deadlineMs = System.currentTimeMillis() + 4_000L
        while (System.currentTimeMillis() < deadlineMs) {
            val current = dao.getDownloadTaskById(task.taskId)
            val path = current?.stagingFilePath
            if (!path.isNullOrBlank() && File(path).length() > 0L) {
                break
            }
            delay(20L)
        }
        pauseSignalTriggered.set(1)
        coordinator.pauseDownload(task.taskId)
        val pauseOutcome = firstRun.await()
        assertEquals(DownloadExecutionOutcome.Paused, pauseOutcome)

        val pausedTask = dao.getDownloadTaskById(task.taskId)!!
        assertEquals(DownloadState.PAUSED.name, pausedTask.state)
        val stagingFile = File(pausedTask.stagingFilePath!!)
        assertTrue("Staging file must retain partial bytes", stagingFile.exists() && stagingFile.length() > 0L)

        // Resume via Range 206
        coordinator.resumeDownload(task.taskId, scheduleWork = false)
        val resumeOutcome = engine.executeTask(task.taskId)
        assertEquals(DownloadExecutionOutcome.Completed, resumeOutcome)
        assertTrue("HTTP Range header should be sent on resume", rangeRequestsSeen.get() >= 1)

        val completedTask = dao.getDownloadTaskById(task.taskId)!!
        assertEquals(DownloadState.COMPLETED.name, completedTask.state)
        assertFalse("Staging temp file cleaned up after commit", stagingFile.exists())

        val downloadedList = coordinator.downloadedTracksFlow.first()
        assertEquals(1, downloadedList.size)
        val dlTrack = downloadedList.first()
        val savedAudioFile = File(Uri.parse(dlTrack.localFileUri).path!!)
        assertTrue(savedAudioFile.exists())
        assertArrayEquals("Resumed output file bytes must match original source", payload, savedAudioFile.readBytes())

        // 2. Verify offline Song mapping (isOnline = false)
        val offlineSong = dlTrack.toSong()
        assertFalse(offlineSong.isOnline)
        assertEquals("DOWNLOADED", offlineSong.sourceType)
        assertEquals(dlTrack.localFileUri, offlineSong.uri.toString())

        // 3. Duplicate prevention
        val dupCheck = coordinator.checkDownloadability(slowTrack)
        assertTrue(dupCheck is DownloadabilityResult.AlreadyDownloaded)

        // 4. Clear history removes task row ONLY and keeps audio file intact!
        coordinator.clearDownloadHistory()
        assertNull(dao.getDownloadTaskById(task.taskId))
        assertTrue("clearDownloadHistory must NEVER delete audio files", savedAudioFile.exists())
        assertEquals(1, coordinator.downloadedTracksFlow.first().size)

        // 5. Deleting downloaded track removes SONORA downloaded file and never touches unrelated user files
        val unrelatedUserFile = File(tempRootDir, "MyPersonalSong.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        coordinator.deleteDownloadedTracks(listOf(dlTrack.id))
        assertFalse("Selected downloaded file deleted", savedAudioFile.exists())
        assertTrue("Unrelated local file untouched", unrelatedUserFile.exists())
        assertTrue(coordinator.downloadedTracksFlow.first().isEmpty())

        // 6. Cancel deletes staging file and sets CANCELLED
        val fastTrack = sampleOnlineTrack("cancel_me")
        val cancelTask = coordinator.enqueueDownload(
            track = fastTrack,
            qualityOption = DownloadQualityOption.AUTO,
            selectedCandidate = candidate.copy(streamUrl = "http://127.0.0.1:$port/fast.m4a"),
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        val cancelStaging = File(cancelTask.stagingFilePath!!).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(9, 8, 7)) }
        assertTrue(cancelStaging.exists())
        coordinator.cancelDownload(cancelTask.taskId)
        assertFalse(cancelStaging.exists())
        assertEquals(DownloadState.CANCELLED.name, dao.getDownloadTaskById(cancelTask.taskId)?.state)

        // 7. Process restart reconciliation recovers interrupted DOWNLOADING task cleanly
        val interruptedTrack = sampleOnlineTrack("interrupted")
        val interruptedTask = coordinator.enqueueDownload(
            track = interruptedTrack,
            qualityOption = DownloadQualityOption.BEST_AVAILABLE,
            selectedCandidate = candidate,
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        dao.updateDownloadState(interruptedTask.taskId, DownloadState.DOWNLOADING.name, null)
        val recoveredCount = coordinator.reconcileInterruptedTasks(autoResume = false)
        assertEquals(1, recoveredCount)
        assertEquals(DownloadState.PAUSED.name, dao.getDownloadTaskById(interruptedTask.taskId)?.state)

        runCatching { server.close() }
        serverThread.join(1000L)
    }

    @Test
    fun `DownloadExecutionEngine refreshes URL on HTTP 403 once and fails cleanly on HTTP 429`() = runBlocking {
        val payload = ByteArray(8 * 1024) { 0x42 }
        val resolveCount = AtomicInteger(0)

        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                        }
                        val out = sock.getOutputStream()
                        when {
                            requestLine.contains("/expired") -> {
                                out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            }
                            requestLine.contains("/rate429") -> {
                                out.write("HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            }
                            else -> {
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: audio/mp4\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n").toByteArray()
                                )
                                out.write(payload)
                            }
                        }
                        out.flush()
                    }
                }
            }
        }

        val folderUri = Uri.fromFile(targetFolderDir).toString()
        downloadPrefs.saveDownloadLocation(folderUri, "SavedMusic")

        val engine = DownloadExecutionEngine(
            dao = dao,
            onlineRepository = onlineRepo,
            storageHelper = storageHelper,
            allowPrivateHostsForTests = true,
            streamSelector = streamSelector,
            okHttpClient = OkHttpClient(),
            requirePersistedSafPermission = false,
            streamResolverOverride = { task, forceRefresh ->
                val attempt = resolveCount.incrementAndGet()
                val streamUrl = when {
                    task.contentUrl.contains("429") -> "http://127.0.0.1:$port/rate429.m4a"
                    !forceRefresh && attempt == 1 -> "http://127.0.0.1:$port/expired.m4a"
                    else -> "http://127.0.0.1:$port/fresh.m4a"
                }
                OnlineResult.Success(
                    ResolvedOnlineStreamResult(
                        serviceId = 0,
                        providerKey = task.providerKey,
                        providerName = task.providerName,
                        contentUrl = task.contentUrl,
                        title = task.title,
                        artist = task.artist,
                        artistUrl = null,
                        durationMs = task.durationMs,
                        artworkUrl = null,
                        selectedStream = OnlineAudioStreamOption(
                            id = "m4a_192",
                            url = streamUrl,
                            mimeType = "audio/mp4",
                            codec = "mp4a.40.2",
                            formatName = "m4a",
                            averageBitrateKbps = 192,
                            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                            isAudioOnly = true
                        ),
                        availableStreams = listOf(
                            OnlineAudioStreamOption(
                                id = "m4a_192",
                                url = streamUrl,
                                mimeType = "audio/mp4",
                                codec = "mp4a.40.2",
                                formatName = "m4a",
                                averageBitrateKbps = 192,
                                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                                isAudioOnly = true
                            )
                        )
                    )
                )
            }
        )

        val coordinator = DownloadManagerCoordinator(
            appContext = context,
            dao = dao,
            preferences = downloadPrefs,
            storageHelper = storageHelper,
            onlineRepository = onlineRepo,
            connectivityMonitor = connectivityMonitor,
            streamSelector = streamSelector,
            executionEngine = engine
        )

        val candidate = DownloadFormatCandidate(
            streamId = "m4a_192",
            codecLabel = "AAC / M4A",
            containerExtension = "m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 192,
            contentLengthBytes = payload.size.toLong(),
            streamUrl = "http://127.0.0.1:$port/expired.m4a",
            isLossless = false,
            isAudioOnly = true,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
        )

        // HTTP 403 triggers forceRefresh and completes cleanly on 2nd URL
        val expiredTask = coordinator.enqueueDownload(
            track = sampleOnlineTrack("expired_track"),
            qualityOption = DownloadQualityOption.AUTO,
            selectedCandidate = candidate,
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        assertEquals(DownloadExecutionOutcome.Completed, engine.executeTask(expiredTask.taskId))
        assertTrue(resolveCount.get() >= 2)

        // HTTP 429 transitions cleanly to FAILED with retryable reason
        val rateTask = coordinator.enqueueDownload(
            track = sampleOnlineTrack("rate_429"),
            qualityOption = DownloadQualityOption.AUTO,
            selectedCandidate = candidate.copy(streamUrl = "http://127.0.0.1:$port/rate429.m4a"),
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        val failOutcome = engine.executeTask(rateTask.taskId)
        assertTrue(failOutcome is DownloadExecutionOutcome.Failed && failOutcome.isRetryable)
        assertEquals(DownloadState.FAILED.name, dao.getDownloadTaskById(rateTask.taskId)?.state)
    }

    @Test
    fun `DownloadExecutionEngine rejects insufficient free storage and does not commit truncated HTTP streams`() = runBlocking {
        val folderUri = Uri.fromFile(targetFolderDir).toString()
        downloadPrefs.saveDownloadLocation(folderUri, "SavedMusic")

        val server = ServerSocket(0)
        serverSocket = server
        val port = server.localPort

        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                        }
                        val out = sock.getOutputStream()
                        // Advertise 8192 bytes, write only 512 bytes, then close abruptly
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mp4\r\nContent-Length: 8192\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(ByteArray(512) { 0x11 })
                        out.flush()
                    }
                }
            }
        }

        val streamUrl = "http://127.0.0.1:$port/truncated.m4a"
        val engine = DownloadExecutionEngine(
            dao = dao,
            onlineRepository = onlineRepo,
            storageHelper = storageHelper,
            allowPrivateHostsForTests = true,
            streamSelector = streamSelector,
            okHttpClient = OkHttpClient(),
            requirePersistedSafPermission = false,
            streamResolverOverride = { task, _ ->
                OnlineResult.Success(
                    ResolvedOnlineStreamResult(
                        serviceId = 0,
                        providerKey = task.providerKey,
                        providerName = task.providerName,
                        contentUrl = task.contentUrl,
                        title = task.title,
                        artist = task.artist,
                        artistUrl = null,
                        durationMs = task.durationMs,
                        artworkUrl = null,
                        selectedStream = OnlineAudioStreamOption(
                            id = "m4a_192",
                            url = streamUrl,
                            mimeType = "audio/mp4",
                            codec = "mp4a.40.2",
                            formatName = "m4a",
                            averageBitrateKbps = 192,
                            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                            isAudioOnly = true
                        ),
                        availableStreams = listOf(
                            OnlineAudioStreamOption(
                                id = "m4a_192",
                                url = streamUrl,
                                mimeType = "audio/mp4",
                                codec = "mp4a.40.2",
                                formatName = "m4a",
                                averageBitrateKbps = 192,
                                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                                isAudioOnly = true
                            )
                        )
                    )
                )
            }
        )

        val coordinator = DownloadManagerCoordinator(
            appContext = context,
            dao = dao,
            preferences = downloadPrefs,
            storageHelper = storageHelper,
            onlineRepository = onlineRepo,
            connectivityMonitor = connectivityMonitor,
            streamSelector = streamSelector,
            executionEngine = engine
        )

        val candidate = DownloadFormatCandidate(
            streamId = "m4a_192",
            codecLabel = "AAC / M4A",
            containerExtension = "m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 192,
            contentLengthBytes = 8192L,
            streamUrl = streamUrl,
            isLossless = false,
            isAudioOnly = true,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
        )

        // 1. Insufficient storage fails cleanly before HTTP write
        val stagingDir = storageHelper.getStagingDir()
        ShadowStatFs.registerStats(stagingDir.absolutePath, 100, 1, 1)
        val noSpaceTask = coordinator.enqueueDownload(
            track = sampleOnlineTrack("no_space"),
            qualityOption = DownloadQualityOption.AUTO,
            selectedCandidate = candidate,
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        val spaceOutcome = engine.executeTask(noSpaceTask.taskId)
        assertTrue(spaceOutcome is DownloadExecutionOutcome.Failed && !spaceOutcome.isRetryable)
        assertEquals(DownloadState.FAILED.name, dao.getDownloadTaskById(noSpaceTask.taskId)?.state)

        // 2. Restore space & verify truncated HTTP stream is rejected and NOT committed to downloaded_tracks
        ShadowStatFs.registerStats(stagingDir.absolutePath, 10_000_000, 10_000_000, 10_000_000)
        val truncatedTask = coordinator.enqueueDownload(
            track = sampleOnlineTrack("truncated"),
            qualityOption = DownloadQualityOption.AUTO,
            selectedCandidate = candidate,
            targetFolderUri = folderUri,
            scheduleWork = false
        ).getOrThrow()
        val truncOutcome = engine.executeTask(truncatedTask.taskId)
        assertTrue("Truncated body should fail", truncOutcome is DownloadExecutionOutcome.Failed)
        assertEquals(DownloadState.FAILED.name, dao.getDownloadTaskById(truncatedTask.taskId)?.state)
        assertTrue("Partial truncated file must NOT be committed to DownloadedTrackEntity", coordinator.downloadedTracksFlow.first().isEmpty())
    }

    private object SQLiteOpenHelperFactoryCompat {
        fun createV2(context: Context, dbPath: String): SupportSQLiteOpenHelper {
            val callback = object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `song_metadata` (`id` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, `customTitle` TEXT, `customArtist` TEXT, `customAlbum` TEXT, `customGenre` TEXT, `customLyrics` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `coverArtUri` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `custom_eq_presets` (`name` TEXT NOT NULL, `bandLevelsJson` TEXT NOT NULL, `bassBoost` INTEGER NOT NULL, `virtualizer` INTEGER NOT NULL, `reverbPreset` INTEGER NOT NULL, PRIMARY KEY(`name`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `online_tracks` (`stableId` INTEGER NOT NULL, `serviceId` INTEGER NOT NULL, `providerKey` TEXT NOT NULL, `providerName` TEXT NOT NULL, `contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `artistUrl` TEXT, `album` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `artworkUrl` TEXT, `isSaved` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, `savedTimestamp` INTEGER NOT NULL, PRIMARY KEY(`stableId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `online_search_history` (`query` TEXT NOT NULL, `providerKey` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbPath)
                .callback(callback)
                .build()
            return FrameworkSQLiteOpenHelperFactory().create(config)
        }
    }
}
