package ui.lyrics

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import data.lyrics.LyricsRepository
import data.lyrics.OnlineLyricsProvider
import data.preferences.LyricsPreferences
import domain.lyrics.LrcParser
import domain.lyrics.LyricsAppearanceConfig
import domain.lyrics.LyricsDocument
import domain.lyrics.LyricsLoadState
import domain.lyrics.LyricsPreset
import domain.lyrics.LyricsSearchCandidate
import domain.lyrics.LyricsSourceType
import domain.lyrics.LyricsSyncEngine
import domain.lyrics.LyricsSyncType
import domain.lyrics.LyricsTimingState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.theme.DynamicColorPalette
import ui.theme.HomeThemeMode
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class SynchronizedLyricsExperienceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun testSong(
        id: Long = 101L,
        title: String = "Midnight Echoes",
        artist: String = "Aurelia",
        dataPath: String = "",
        isOnline: Boolean = false,
        contentUrl: String = ""
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Nocturne",
        albumId = id,
        durationMs = 210_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = dataPath,
        mimeType = "audio/mpeg",
        sizeBytes = 4_000_000L,
        isOnline = isOnline,
        contentUrl = contentUrl
    )

    // 1. Local LRC synchronization
    @Test
    fun localSidecarLrc_extractsExpandsMultiTimestampsAndSyncsToPosition(): Unit = runBlocking {
        val tempDir = File(context.cacheDir, "lrc_test_${System.nanoTime()}").apply { mkdirs() }
        val audioFile = File(tempDir, "midnight_echoes.mp3").apply { writeBytes(ByteArray(64)) }
        val lrcFile = File(tempDir, "midnight_echoes.lrc").apply {
            writeText(
                """
                [ti:Midnight Echoes]
                [ar:Aurelia]
                [00:05.00]First verse softly plays
                [00:12.50][00:30.00]Echoes in the midnight rain
                [00:20.00]Second verse carries on
                """.trimIndent()
            )
        }

        val repo = LyricsRepository(context, db.dao(), object : OnlineLyricsProvider {
            override suspend fun fetchExactLyrics(title: String, artist: String, album: String, durationMs: Long) = null
            override suspend fun searchLyrics(query: String, trackName: String?, artistName: String?) = emptyList<LyricsSearchCandidate>()
        })
        val song = testSong(dataPath = audioFile.absolutePath)

        val doc = repo.loadLyricsForSong(song, allowNetwork = false)
        assertNotNull(doc)
        assertEquals(LyricsSourceType.LOCAL_SIDECAR_LRC, doc!!.sourceType)
        assertEquals(LyricsSyncType.LINE_SYNCED, doc.syncType)
        assertEquals(4, doc.lines.size)
        assertEquals(5_000L, doc.lines[0].startTimeMs)
        assertEquals(12_500L, doc.lines[1].startTimeMs)
        assertEquals(20_000L, doc.lines[2].startTimeMs)
        assertEquals(30_000L, doc.lines[3].startTimeMs)

        // Verify synchronization across playback positions
        assertEquals(-1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 2_000L))
        assertEquals(0, LyricsSyncEngine.findActiveLineIndex(doc.lines, 6_000L))
        assertEquals(1, LyricsSyncEngine.findActiveLineIndex(doc.lines, 14_000L))
        assertEquals(2, LyricsSyncEngine.findActiveLineIndex(doc.lines, 24_000L))
        assertEquals(3, LyricsSyncEngine.findActiveLineIndex(doc.lines, 31_000L))

        lrcFile.delete()
        audioFile.delete()
        tempDir.delete()
        Unit
    }

    // 2. Online lyrics synchronization + Room caching
    @Test
    fun onlineLyricsProvider_loadsSyncedLyricsAndCachesLocallyInRoom(): Unit = runBlocking {
        var fetchCalls = 0
        val fakeProvider = object : OnlineLyricsProvider {
            override suspend fun fetchExactLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsSearchCandidate {
                fetchCalls++
                return LyricsSearchCandidate(
                    id = "lrclib-77",
                    title = title,
                    artist = artist,
                    album = album,
                    durationSec = 210,
                    syncedLyrics = "[00:04.00]Floating through the neon skies\n[00:10.00]City lights reflect your eyes",
                    plainLyrics = "Floating through the neon skies\nCity lights reflect your eyes"
                )
            }

            override suspend fun searchLyrics(query: String, trackName: String?, artistName: String?): List<LyricsSearchCandidate> = emptyList()
        }

        val repo = LyricsRepository(context, db.dao(), fakeProvider)
        val onlineSong = testSong(
            id = 505L,
            title = "Neon Skies",
            artist = "Solaris",
            isOnline = true,
            contentUrl = "https://music.youtube.com/watch?v=neon77"
        )

        val firstLoad = repo.loadLyricsForSong(onlineSong, allowNetwork = true)
        assertNotNull(firstLoad)
        assertEquals(LyricsSourceType.ONLINE_LRCLIB, firstLoad!!.sourceType)
        assertEquals(LyricsSyncType.LINE_SYNCED, firstLoad.syncType)
        assertEquals(1, fetchCalls)

        // Clear memory cache and verify Room serves cached lyrics offline without a second network call
        repo.invalidateMemoryCache(onlineSong)
        val cachedLoad = repo.loadLyricsForSong(onlineSong, allowNetwork = false)
        assertNotNull(cachedLoad)
        assertEquals(2, cachedLoad!!.lines.size)
        assertEquals(1, fetchCalls)
        assertEquals(1, LyricsSyncEngine.findActiveLineIndex(cachedLoad.lines, 11_000L))
        Unit
    }

    // 3. Enhanced LRC word-level highlighting (and strict non-simulation for line-only LRC)
    @Test
    fun enhancedLrc_highlightsWordsProgressivelyAndNeverSimulatesForLineOnlyLrc() {
        val enhancedRaw = """
            [00:10.00]<00:10.00>Shine <00:10.50>bright <00:11.00>tonight<00:12.00>
            [00:14.00]Plain line without word tags
        """.trimIndent()

        val parsed = LrcParser.parse(enhancedRaw)
        assertEquals(LyricsSyncType.WORD_SYNCED, parsed.syncType)
        assertEquals(2, parsed.lines.size)

        val wordLine = parsed.lines[0]
        assertTrue(wordLine.hasWordTiming)
        assertEquals(3, wordLine.words.size)
        assertEquals("Shine bright tonight", wordLine.text)

        val progressAt10250 = LyricsSyncEngine.findWordHighlightState(wordLine, 10_250L)
        assertTrue(progressAt10250.isWordSynced)
        assertEquals(0, progressAt10250.activeWordIndex)
        assertEquals(0.5f, progressAt10250.activeWordProgress, 0.05f)

        val progressAt11500 = LyricsSyncEngine.findWordHighlightState(wordLine, 11_500L)
        assertEquals(2, progressAt11500.activeWordIndex)
        assertEquals(0.5f, progressAt11500.activeWordProgress, 0.05f)

        // Line without word tags MUST NOT simulate word highlighting
        val lineOnly = parsed.lines[1]
        assertFalse(lineOnly.hasWordTiming)
        val nonWordState = LyricsSyncEngine.findWordHighlightState(lineOnly, 14_500L)
        assertFalse(nonWordState.isWordSynced)
        assertEquals(-1, nonWordState.activeWordIndex)
    }

    // 4. Manual timing adjustment (-10s..+10s, per-track + global persistence, line + word sync)
    @Test
    fun manualTimingAdjustment_appliesToLineAndWordSyncAndPersistsPerTrackAndGlobally(): Unit = runBlocking {
        val parsed = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:11.00>world<00:12.00>\n[00:15.00]Next line")

        // Positive offset (+1000ms): lyrics ahead of audio -> at 9.2s playback, 10.0s line is already active
        val aheadState = LyricsTimingState(trackKey = "k1", trackOffsetMs = 1_000L, hasCustomTrackOffset = true)
        assertEquals("Lyrics ahead of audio", aheadState.statusLabel)
        assertEquals("+1000 ms", aheadState.formattedOffset())
        assertEquals(0, LyricsSyncEngine.findActiveLineIndex(parsed.lines, 9_200L, aheadState.effectiveOffsetMs))
        val aheadWord = LyricsSyncEngine.findWordHighlightState(parsed.lines[0], 10_200L, aheadState.effectiveOffsetMs)
        assertEquals(1, aheadWord.activeWordIndex)

        // Negative offset (-2000ms): lyrics behind audio -> at 11.0s playback, 10.0s line is not active yet
        val behindState = LyricsTimingState(trackKey = "k1", trackOffsetMs = -2_000L, hasCustomTrackOffset = true)
        assertEquals("Lyrics behind audio", behindState.statusLabel)
        assertEquals("-2000 ms", behindState.formattedOffset())
        assertEquals(-1, LyricsSyncEngine.findActiveLineIndex(parsed.lines, 11_000L, behindState.effectiveOffsetMs))

        // Clamping range -10s..+10s
        val clampedHigh = LyricsTimingState(trackKey = "k1", trackOffsetMs = 25_000L, hasCustomTrackOffset = true)
        val clampedLow = LyricsTimingState(trackKey = "k1", trackOffsetMs = -25_000L, hasCustomTrackOffset = true)
        assertEquals(10_000L, clampedHigh.effectiveOffsetMs)
        assertEquals(-10_000L, clampedLow.effectiveOffsetMs)

        // Synchronized label
        assertEquals("Synchronized", LyricsTimingState().statusLabel)

        // Persist per-track offset in Room and global default in DataStore
        val repo = LyricsRepository(context, db.dao())
        val song = testSong(id = 202L)
        repo.saveTrackTimingOffset(song, 500L, hasCustomOffset = true)
        val (savedOffset, hasCustom) = repo.getSavedTrackOffset(song)
        assertTrue(hasCustom)
        assertEquals(500L, savedOffset)

        val prefs = LyricsPreferences(context)
        prefs.setGlobalTimingOffset(-300L)
        assertEquals(-300L, prefs.appearanceFlow.first().globalTimingOffsetMs)
        Unit
    }

    // 5, 6, 7. Seeking, Pause & Resume, Previous & Next Track + UI rendering across API 26 & 36
    @Test
    fun fullScreenLyricsUi_supportsSeekingPauseResumeNextPrevAndTimingSheet() {
        val song = testSong(id = 303L, title = "Starlight", artist = "Nova")
        val raw = "[00:05.00]Line one start\n[00:12.00]Line two chorus\n[00:20.00]Line three finale"
        val parsed = LrcParser.parse(raw)
        val doc = LyricsDocument(
            trackKey = "local:303",
            title = song.title,
            artist = song.artist,
            rawText = raw,
            lines = parsed.lines,
            syncType = parsed.syncType,
            sourceType = LyricsSourceType.LOCAL_SIDECAR_LRC
        )

        var currentPosMs by mutableLongStateOf(6_000L)
        var isPlaying by mutableStateOf(true)
        var timingState by mutableStateOf(LyricsTimingState(trackKey = "local:303"))
        var lastSeekMs = -1L
        var nextClicks = 0
        var prevClicks = 0

        val colors = editorialColorsFor(HomeThemeMode.DYNAMIC_ALBUM_ART, 0xFF1A73E8L, DynamicColorPalette.default())

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDynamicColorPalette provides DynamicColorPalette.default()
            ) {
                FullScreenLyricsContent(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = isPlaying, currentPositionMs = currentPosMs, durationMs = 210_000L),
                    currentPositionMs = currentPosMs,
                    loadState = LyricsLoadState.Loaded(doc),
                    appearance = LyricsAppearanceConfig.DEFAULT,
                    timingState = timingState,
                    availableSources = emptyList(),
                    searchResults = emptyList(),
                    isSearching = false,
                    feedbackMessage = null,
                    colors = colors,
                    palette = DynamicColorPalette.default(),
                    onBack = {},
                    onTogglePlayPause = { isPlaying = !isPlaying },
                    onPlayNext = { nextClicks++ },
                    onPlayPrevious = { prevClicks++ },
                    onSeekTo = { lastSeekMs = it; currentPosMs = it },
                    onSeekToLyricLine = { line ->
                        val target = LyricsSyncEngine.calculateSeekPositionForLine(line, timingState.effectiveOffsetMs)
                        lastSeekMs = target
                        currentPosMs = target
                    },
                    onAdjustTimingDelta = { delta ->
                        timingState = timingState.copy(
                            trackOffsetMs = (timingState.effectiveOffsetMs + delta).coerceIn(-10_000L, 10_000L),
                            hasCustomTrackOffset = true
                        )
                    },
                    onSetTimingOffset = { offset ->
                        timingState = timingState.copy(trackOffsetMs = offset, hasCustomTrackOffset = true)
                    },
                    onResetTimingOffset = {
                        timingState = timingState.copy(trackOffsetMs = 0L, hasCustomTrackOffset = true)
                    },
                    onSaveGlobalTimingOffset = {},
                    onUseGlobalTimingOffset = {},
                    onApplyPreset = {},
                    onUpdateAppearance = {},
                    onSelectSource = {},
                    onRefreshLyrics = {},
                    onRequestImportLrc = {},
                    onSearchOnline = {},
                    onApplySearchCandidate = {},
                    onSaveEditedLyrics = {},
                    onSaveLyricsLocally = {},
                    onDismissFeedback = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Active line at 6s is line 0
        composeTestRule.onNodeWithTag("lyrics_active_line_0").assertIsDisplayed()

        // Scroll to and tap line 1 -> seeks to 12_000 ms and activates line 1
        composeTestRule.onNodeWithTag("lyrics_lazy_column").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_line_1").performClick()
        composeTestRule.mainClock.advanceTimeBy(450L)
        composeTestRule.waitForIdle()
        assertEquals(12_000L, lastSeekMs)
        composeTestRule.onNodeWithTag("lyrics_active_line_1").assertIsDisplayed()

        // Pause & Resume
        composeTestRule.onNodeWithTag("lyrics_play_pause_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(isPlaying)
        // Even while paused, active line stays accurately synced to 12_000ms
        composeTestRule.onNodeWithTag("lyrics_active_line_1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("lyrics_play_pause_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(isPlaying)

        // Advance playback position after resume -> recalculates to line 2
        currentPosMs = 21_000L
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_active_line_2").assertIsDisplayed()

        // Previous & Next buttons
        composeTestRule.onNodeWithTag("lyrics_next_button").performClick()
        composeTestRule.onNodeWithTag("lyrics_previous_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, nextClicks)
        assertEquals(1, prevClicks)

        // Open timing adjustment sheet and test +500ms / -100ms / Reset without altering playback position
        val posBeforeTiming = currentPosMs
        composeTestRule.onNodeWithTag("lyrics_timing_shortcut_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_timing_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_timing_plus_500").performClick()
        composeTestRule.onNodeWithTag("lyrics_timing_minus_100").performClick()
        composeTestRule.waitForIdle()
        assertEquals(400L, timingState.effectiveOffsetMs)
        assertEquals(posBeforeTiming, currentPosMs) // ExoPlayer position untouched!
        composeTestRule.onNodeWithTag("lyrics_timing_reset").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0L, timingState.effectiveOffsetMs)
    }

    // 8. Track changes while lyrics load (stale response must not overwrite new track)
    @Test
    fun trackChangeDuringSlowLyricsLoad_preventsStaleLyricsFromAppearing(): Unit = runBlocking {
        val slowFirstTrackGate = CompletableDeferred<Unit>()
        val fakeProvider = object : OnlineLyricsProvider {
            override suspend fun fetchExactLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsSearchCandidate {
                if (title == "Slow Track A") {
                    slowFirstTrackGate.await()
                    return LyricsSearchCandidate("a", title, artist, album, 200, "[00:01.00]Stale track A lyric", null)
                }
                return LyricsSearchCandidate("b", title, artist, album, 200, "[00:01.00]Fresh track B lyric", null)
            }
            override suspend fun searchLyrics(query: String, trackName: String?, artistName: String?) = emptyList<LyricsSearchCandidate>()
        }

        val repo = LyricsRepository(context, db.dao(), fakeProvider)
        val songA = testSong(id = 1L, title = "Slow Track A", isOnline = true, contentUrl = "https://example.com/a")
        val songB = testSong(id = 2L, title = "Fast Track B", isOnline = true, contentUrl = "https://example.com/b")

        var activeToken = 0L
        var activeTrackId = songA.id
        var committedDoc: LyricsDocument? = null

        val tokenA = ++activeToken
        val jobA = async {
            val doc = repo.loadLyricsForSong(songA, allowNetwork = true)
            if (tokenA == activeToken && activeTrackId == songA.id) {
                committedDoc = doc
            }
        }

        // Switch immediately to Track B while Track A is still loading
        val tokenB = ++activeToken
        activeTrackId = songB.id
        val docB = repo.loadLyricsForSong(songB, allowNetwork = true)
        if (tokenB == activeToken && activeTrackId == songB.id) {
            committedDoc = docB
        }

        // Now let slow Track A complete
        slowFirstTrackGate.complete(Unit)
        jobA.await()

        assertNotNull(committedDoc)
        assertEquals("Fast Track B", committedDoc!!.title)
        assertEquals("Fresh track B lyric", committedDoc!!.lines.first().text)
        Unit
    }

    // 9. Missing lyrics handled gracefully in UI
    @Test
    fun missingLyrics_displaysGracefulEmptyStateWithSearchImportAndAddActions() {
        val song = testSong(id = 404L, title = "Instrumental Improv")
        val colors = editorialColorsFor(HomeThemeMode.DYNAMIC_ALBUM_ART, 0xFF1A73E8L, DynamicColorPalette.default())
        var importClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDynamicColorPalette provides DynamicColorPalette.default()
            ) {
                FullScreenLyricsContent(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = false, currentPositionMs = 0L, durationMs = 180_000L),
                    currentPositionMs = 0L,
                    loadState = LyricsLoadState.NotFound("local:404", "No matching lyrics found."),
                    appearance = LyricsAppearanceConfig.DEFAULT,
                    timingState = LyricsTimingState(),
                    availableSources = emptyList(),
                    searchResults = emptyList(),
                    isSearching = false,
                    feedbackMessage = null,
                    colors = colors,
                    palette = DynamicColorPalette.default(),
                    onBack = {},
                    onTogglePlayPause = {},
                    onPlayNext = {},
                    onPlayPrevious = {},
                    onSeekTo = {},
                    onSeekToLyricLine = {},
                    onAdjustTimingDelta = {},
                    onSetTimingOffset = {},
                    onResetTimingOffset = {},
                    onSaveGlobalTimingOffset = {},
                    onUseGlobalTimingOffset = {},
                    onApplyPreset = {},
                    onUpdateAppearance = {},
                    onSelectSource = {},
                    onRefreshLyrics = {},
                    onRequestImportLrc = { importClicked = true },
                    onSearchOnline = {},
                    onApplySearchCandidate = {},
                    onSaveEditedLyrics = {},
                    onSaveLyricsLocally = {},
                    onDismissFeedback = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_empty_search_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_empty_import_button").performScrollTo().performClick()
        assertTrue(importClicked)
    }

    // 10. Malformed LRC files handled safely without crash
    @Test
    fun malformedLrcFiles_parseSafelyWithoutCrashingAndPreserveValidLines() {
        val malformed = """
            [99:99.abc]Invalid seconds and fraction
            [bad:timestamp]
            [00:10.00]Valid first lyric
            [-01:00.00]Negative timestamp garbage
            [00:75.00]Seconds over 59
            [00:20.50]Valid second lyric
            [unclosed bracket line
        """.trimIndent()

        val result = LrcParser.parse(malformed)
        assertEquals(LyricsSyncType.LINE_SYNCED, result.syncType)
        assertEquals(2, result.lines.size)
        assertEquals("Valid first lyric", result.lines[0].text)
        assertEquals(10_000L, result.lines[0].startTimeMs)
        assertEquals("Valid second lyric", result.lines[1].text)
        assertEquals(20_500L, result.lines[1].startTimeMs)

        // Completely corrupted file with no valid timestamps falls back cleanly to plain text
        val corruptedPlain = LrcParser.parse("[xx:yy]Fallback lyric line\nPlain second line")
        assertEquals(LyricsSyncType.UNSYNCED, corruptedPlain.syncType)
        assertEquals(2, corruptedPlain.lines.size)
        assertEquals(-1, LyricsSyncEngine.findActiveLineIndex(corruptedPlain.lines, 5_000L))
    }

    // 11. Mini-player lyrics button & Room v4 -> v5 migration preserving playback/existing data
    @Test
    fun miniPlayerLyricsButton_triggersFullScreenLyricsWithoutInterruptingPlayback() {
        val song = testSong(id = 808L, title = "Afterglow", artist = "Kairo")
        val colors = editorialColorsFor(HomeThemeMode.DYNAMIC_ALBUM_ART, 0xFF1A73E8L, DynamicColorPalette.default())
        var lyricsOpened = false
        var playPauseToggled = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDynamicColorPalette provides DynamicColorPalette.default()
            ) {
                FloatingMiniPlayer(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = true, currentPositionMs = 45_000L, durationMs = 210_000L),
                    colors = colors,
                    onTogglePlayPause = { playPauseToggled = true },
                    onPlayNext = {},
                    onPlayPrevious = {},
                    onClick = {},
                    onOpenLyrics = { lyricsOpened = true },
                    miniPlayerStyle = MiniPlayerStyle.FLOATING_PILL
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mini_lyrics_button").assertIsDisplayed().performClick()
        assertTrue(lyricsOpened)
        assertFalse(playPauseToggled)
    }

    // 12. Room MIGRATION_4_5 & Android 8 (API 26) / Android 16 (API 36) appearance presets
    @Test
    fun roomMigration4To5_createsTrackLyricsTableAndPresetsUpdateAppearanceConfig(): Unit = runBlocking {
        val dbName = "sonora_mig_4_5_${System.nanoTime()}.db"
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, coverArtUri TEXT, createdAt INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO playlists (id, name, coverArtUri, createdAt) VALUES (1, 'Late Night', NULL, 1000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val rawDb = helper.writableDatabase
        SonoraDatabase.MIGRATION_4_5.migrate(rawDb)

        // Verify track_lyrics table exists and preserves playlists table
        rawDb.execSQL(
            "INSERT INTO track_lyrics (trackKey, songId, title, artist, rawLyrics, sourceType, sourceLabel, syncType, timingOffsetMs, hasCustomOffset, isUserEdited, isInstrumental, updatedAt) VALUES ('local:1', 1, 'Song', 'Artist', '[00:01.00]Hi', 'LOCAL_EDITED', 'Edited', 'LINE_SYNCED', 250, 1, 1, 0, 2000)"
        )
        rawDb.query("SELECT timingOffsetMs FROM track_lyrics WHERE trackKey = 'local:1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(250L, c.getLong(0))
        }
        rawDb.query("SELECT name FROM playlists WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Late Night", c.getString(0))
        }
        rawDb.close()
        helper.close()
        context.deleteDatabase(dbName)

        // Verify LyricsPreset configurations
        val minimal = LyricsAppearanceConfig.DEFAULT.withPreset(LyricsPreset.MINIMAL)
        val karaoke = LyricsAppearanceConfig.DEFAULT.withPreset(LyricsPreset.KARAOKE)
        assertFalse(minimal.wordHighlightEnabled)
        assertTrue(karaoke.wordHighlightEnabled)
        assertTrue(karaoke.fontSizeSp > minimal.fontSizeSp)
        assertNull(LrcParser.parse("").lines.firstOrNull())
        Unit
    }
}
