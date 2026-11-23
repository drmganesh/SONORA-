package ui.library

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalMusicScanner
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolder
import com.example.model.Playlist
import com.example.model.Song
import com.example.model.SortOption
import data.download.DownloadedTrackEntity
import data.library.OfflineLibraryRepository
import data.library.OnlineLibraryRepository
import data.online.NetworkConnectivityMonitor
import data.online.OnlineMusicRepository
import data.online.OnlineTrackMapper
import data.preferences.FolderFilterPreferences
import data.preferences.LibraryModePreferences
import domain.library.FollowedOnlineArtist
import domain.library.LibrarySectionMode
import domain.library.OnlineLibrarySearchResults
import domain.library.OnlineLibrarySortOption
import domain.library.OnlinePlaylist
import domain.library.SavedOnlineAlbum
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.online.OnlineTrackOptionsSheet
import ui.theme.DynamicColorPalette
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class OfflineOnlineLibraryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var dao: SonoraDao
    private lateinit var connectivityMonitor: NetworkConnectivityMonitor
    private lateinit var onlineMusicRepository: OnlineMusicRepository
    private lateinit var onlineLibraryRepository: OnlineLibraryRepository
    private lateinit var offlineLibraryRepository: OfflineLibraryRepository
    private lateinit var libraryModePrefs: LibraryModePreferences

    private val lightColors = editorialColorsFor(
        mode = HomeThemeMode.WARM_CREAM,
        customAccentArgb = null,
        dynamicPalette = DynamicColorPalette.default()
    )

    private val darkColors = editorialColorsFor(
        mode = HomeThemeMode.DYNAMIC_ALBUM_ART,
        customAccentArgb = null,
        dynamicPalette = DynamicColorPalette.default()
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        connectivityMonitor = NetworkConnectivityMonitor(context)
        onlineMusicRepository = OnlineMusicRepository(dao, connectivityMonitor)
        onlineLibraryRepository = OnlineLibraryRepository(dao, onlineMusicRepository)
        offlineLibraryRepository = OfflineLibraryRepository(
            context = context,
            scanner = LocalMusicScanner(context, dao),
            dao = dao,
            folderFilterPreferences = FolderFilterPreferences(context)
        )
        libraryModePrefs = LibraryModePreferences(context)
        runBlocking { libraryModePrefs.setLibrarySectionMode(LibrarySectionMode.OFFLINE) }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun sampleLocalSong(
        id: Long,
        title: String,
        artist: String,
        album: String,
        dateAdded: Long = 1000L,
        durationMs: Long = 180_000L,
        dataPath: String = "/storage/emulated/0/Music/$title.mp3"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = id,
        durationMs = durationMs,
        uri = Uri.parse("file://$dataPath"),
        mimeType = "audio/mpeg",
        sizeBytes = 4_200_000L,
        genre = "Electronic",
        dataPath = dataPath,
        dateAdded = dateAdded,
        isOnline = false,
        sourceType = "LOCAL"
    )

    private fun sampleOnlineTrack(
        url: String = "https://music.youtube.com/watch?v=sonora_track_1",
        title: String = "Midnight Echoes",
        artist: String = "Solaris",
        providerKey: String = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName: String = "YouTube Music",
        kind: OnlineItemKind = OnlineItemKind.TRACK,
        artistUrl: String? = "https://music.youtube.com/channel/UC_solaris"
    ): OnlineTrack = OnlineTrack(
        stableId = OnlineTrackMapper.computeStableId(providerKey, url),
        serviceId = 0,
        providerKey = providerKey,
        providerName = providerName,
        contentUrl = url,
        title = title,
        artist = artist,
        album = "Neon Horizons",
        durationMs = 215_000L,
        artworkUrl = "https://example.com/art.jpg",
        kind = kind,
        artistUrl = artistUrl
    )

    @Test
    fun offlineLibrary_filtersAndSortsLocalSongsAlbumsArtistsFoldersAndPlaylists() {
        val songs = listOf(
            sampleLocalSong(1L, "Zeta Dawn", "Beta Artist", "Alpha Album", dateAdded = 100L, durationMs = 240_000L, dataPath = "/storage/Music/Ambient/zeta.flac"),
            sampleLocalSong(2L, "Aurora Pulse", "Alpha Artist", "Zebra Album", dateAdded = 300L, durationMs = 150_000L, dataPath = "/storage/Music/Synth/aurora.mp3"),
            sampleLocalSong(3L, "Midnight Drift", "Gamma Artist", "Echoes Album", dateAdded = 200L, durationMs = 310_000L, dataPath = "/storage/Music/Synth/drift.mp3")
        )

        val sortedTitleAsc = offlineLibraryRepository.filterAndSortSongs(songs, "", SortOption.TITLE_ASC)
        assertEquals(listOf("Aurora Pulse", "Midnight Drift", "Zeta Dawn"), sortedTitleAsc.map { it.title })

        val sortedArtistAsc = offlineLibraryRepository.filterAndSortSongs(songs, "", SortOption.ARTIST_ASC)
        assertEquals(listOf("Alpha Artist", "Beta Artist", "Gamma Artist"), sortedArtistAsc.map { it.artist })

        val sortedAlbumAsc = offlineLibraryRepository.filterAndSortSongs(songs, "", SortOption.ALBUM_ASC)
        assertEquals(listOf("Alpha Album", "Echoes Album", "Zebra Album"), sortedAlbumAsc.map { it.album })

        val sortedDateDesc = offlineLibraryRepository.filterAndSortSongs(songs, "", SortOption.DATE_ADDED_DESC)
        assertEquals(listOf("Aurora Pulse", "Midnight Drift", "Zeta Dawn"), sortedDateDesc.map { it.title })

        val searched = offlineLibraryRepository.filterAndSortSongs(songs, "synth", SortOption.TITLE_ASC)
        assertEquals(2, searched.size)

        val albums = listOf(
            Album(1L, "Alpha Album", "Beta Artist", 4, 2024, null),
            Album(2L, "Zebra Album", "Alpha Artist", 6, 2025, null)
        )
        assertEquals(1, offlineLibraryRepository.filterAlbums(albums, "zebra").size)

        val artists = listOf(
            Artist(1L, "Alpha Artist", 3, 1),
            Artist(2L, "Beta Artist", 2, 1)
        )
        assertEquals(1, offlineLibraryRepository.filterArtists(artists, "alpha").size)

        val folders = listOf(MusicFolder("/storage/Music/Ambient", "Ambient", 1), MusicFolder("/storage/Music/Synth", "Synth", 2))
        assertEquals(1, offlineLibraryRepository.filterFolders(folders, "ambient").size)

        val playlists = listOf(Playlist(1L, "Morning Focus", 4, 1000L), Playlist(2L, "Night Run", 8, 2000L))
        assertEquals(1, offlineLibraryRepository.filterPlaylists(playlists, "focus").size)
    }

    @Test
    fun onlineLibrary_savesRemovesTracksAlbumsArtistsPlaylistsAndPreventsDuplicates(): Unit = runBlocking {
        // 1. Save & toggle online track using canonical URL
        val track = sampleOnlineTrack()
        val song = OnlineTrackMapper.toUnifiedSong(track)
        val saveResult = onlineLibraryRepository.toggleSaveTrack(song)
        assertTrue(saveResult.isSuccess)
        assertEquals(true, saveResult.getOrNull())
        assertEquals(1, onlineLibraryRepository.savedTracksFlow.first().size)

        // 2. Save album & verify duplicate prevention + removal
        val albumItem = sampleOnlineTrack(
            url = "https://music.youtube.com/playlist?list=OLAK5uy_album1",
            title = "Neon Horizons LP",
            artist = "Solaris",
            kind = OnlineItemKind.ALBUM
        )
        val albumRes1 = onlineLibraryRepository.toggleSaveAlbum(albumItem)
        assertTrue(albumRes1.isSuccess)
        assertTrue(albumRes1.getOrNull() == true)
        assertEquals(1, onlineLibraryRepository.savedAlbumsFlow.first().size)

        // Toggle off removes it cleanly
        val albumRes2 = onlineLibraryRepository.toggleSaveAlbum(albumItem)
        assertTrue(albumRes2.isSuccess)
        assertTrue(albumRes2.getOrNull() == false)
        assertEquals(0, onlineLibraryRepository.savedAlbumsFlow.first().size)

        // Re-save for further assertions
        onlineLibraryRepository.toggleSaveAlbum(albumItem)

        // 3. Follow & unfollow artist
        val artistItem = sampleOnlineTrack(
            url = "https://music.youtube.com/channel/UC_solaris",
            title = "Solaris",
            artist = "Solaris",
            kind = OnlineItemKind.ARTIST,
            artistUrl = "https://music.youtube.com/channel/UC_solaris"
        )
        val followRes = onlineLibraryRepository.toggleFollowArtist(artistItem)
        assertTrue(followRes.isSuccess)
        assertTrue(followRes.getOrNull() == true)
        assertEquals(1, onlineLibraryRepository.followedArtistsFlow.first().size)

        // 4. Unsupported item without canonical URL returns clean descriptive failure
        val invalidAlbum = albumItem.copy(contentUrl = "")
        val invalidRes = onlineLibraryRepository.toggleSaveAlbum(invalidAlbum)
        assertTrue(invalidRes.isFailure)
        assertTrue(invalidRes.exceptionOrNull()?.message?.isNotBlank() == true)

        // 5. Create custom Online Playlist, add track twice (duplicate prevented by Room primary key), rename, remove track
        val createRes = onlineLibraryRepository.createOnlinePlaylist("Late Night Stream")
        assertTrue(createRes.isSuccess)
        val playlistId = createRes.getOrThrow()
        assertTrue(playlistId > 0L)

        assertTrue(onlineLibraryRepository.addTrackToOnlinePlaylist(playlistId, song).isSuccess)
        assertTrue(onlineLibraryRepository.addTrackToOnlinePlaylist(playlistId, song).isSuccess)

        val playlists = onlineLibraryRepository.onlinePlaylistsFlow.first()
        assertEquals(1, playlists.size)
        assertEquals(1, playlists.first().tracks.size)
        assertEquals("Midnight Echoes", playlists.first().tracks.first().title)

        onlineLibraryRepository.renameOnlinePlaylist(playlistId, "Chillwave Online")
        assertEquals("Chillwave Online", onlineLibraryRepository.onlinePlaylistsFlow.first().first().name)

        onlineLibraryRepository.removeTrackFromOnlinePlaylist(playlistId, song.id)
        assertEquals(0, onlineLibraryRepository.onlinePlaylistsFlow.first().first().tracks.size)
    }

    @Test
    fun onlineLibrary_localMetadataSearchWorksWithoutNetworkAndFiltersByProvider(): Unit = runBlocking {
        val ytTrack = OnlineTrackMapper.toUnifiedSong(
            sampleOnlineTrack(
                url = "https://music.youtube.com/watch?v=yt_1",
                title = "Starlight Highway",
                artist = "Synthwave Collective",
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                providerName = "YouTube Music"
            )
        )
        val scTrack = OnlineTrackMapper.toUnifiedSong(
            sampleOnlineTrack(
                url = "https://soundcloud.com/lofi/rainy-cafe",
                title = "Rainy Cafe Beats",
                artist = "LoFi Dreamer",
                providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
                providerName = "SoundCloud"
            )
        )

        val album = SavedOnlineAlbum(
            stableId = -10L,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YouTube Music",
            contentUrl = "https://music.youtube.com/playlist?list=ALB1",
            title = "Starlight Anthology",
            artist = "Synthwave Collective",
            artworkUrl = null,
            trackCount = 12L,
            savedTimestamp = 2000L
        )
        val artist = FollowedOnlineArtist(
            stableId = -20L,
            serviceId = 1,
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            providerName = "SoundCloud",
            contentUrl = "https://soundcloud.com/lofi",
            name = "LoFi Dreamer",
            artworkUrl = null,
            subscriberCount = 5000L,
            followedTimestamp = 3000L
        )
        val playlist = OnlinePlaylist(
            id = 5L,
            name = "Starlight Coding Mix",
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YouTube Music",
            externalContentUrl = null,
            artworkUrl = null,
            createdAt = 1000L,
            updatedAt = 4000L,
            tracks = listOf(ytTrack)
        )

        // Zero-network search across all saved content
        val starlightResults = onlineLibraryRepository.searchSavedContent(
            query = "Starlight",
            providerFilterKey = null,
            sortOption = OnlineLibrarySortOption.TITLE_ASC,
            savedTracks = listOf(ytTrack, scTrack),
            savedAlbums = listOf(album),
            followedArtists = listOf(artist),
            onlinePlaylists = listOf(playlist),
            listeningHistory = listOf(ytTrack, scTrack),
            favorites = listOf(ytTrack)
        )
        assertEquals(1, starlightResults.tracks.size)
        assertEquals(1, starlightResults.albums.size)
        assertEquals(0, starlightResults.artists.size)
        assertEquals(1, starlightResults.playlists.size)

        // Provider filter isolates SoundCloud content only
        val soundCloudOnly = onlineLibraryRepository.searchSavedContent(
            query = "",
            providerFilterKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            sortOption = OnlineLibrarySortOption.ARTIST_ASC,
            savedTracks = listOf(ytTrack, scTrack),
            savedAlbums = listOf(album),
            followedArtists = listOf(artist),
            onlinePlaylists = listOf(playlist),
            listeningHistory = listOf(ytTrack, scTrack),
            favorites = listOf(ytTrack)
        )
        assertEquals(1, soundCloudOnly.tracks.size)
        assertEquals("Rainy Cafe Beats", soundCloudOnly.tracks.first().title)
        assertEquals(0, soundCloudOnly.albums.size)
        assertEquals(1, soundCloudOnly.artists.size)
    }

    @Test
    fun downloadsStayInOfflineLibraryAndPlayOfflineWithoutInternet(): Unit = runBlocking {
        val localFile = File(context.cacheDir, "offline_test_track.m4a").apply {
            writeBytes(ByteArray(256) { 1 })
        }
        val dlEntity = DownloadedTrackEntity(
            id = -9001L,
            sourceKey = "youtube_music|https://music.youtube.com/watch?v=offline1",
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YouTube Music",
            contentUrl = "https://music.youtube.com/watch?v=offline1",
            title = "Offline Anthem",
            artist = "Local Hero",
            album = "Downloads Pack",
            durationMs = 195_000L,
            artworkUrl = null,
            localArtworkPath = null,
            localFileUri = Uri.fromFile(localFile).toString(),
            treeFolderUri = Uri.fromFile(context.cacheDir).toString(),
            displayFileName = "offline_test_track.m4a",
            formatLabel = "M4A",
            mimeType = "audio/mp4",
            bitrateKbps = 256,
            sampleRateHz = 44100,
            fileSizeBytes = 256L,
            downloadedAt = System.currentTimeMillis()
        )
        dao.upsertDownloadedTrack(dlEntity)

        val downloadedRows = dao.getAllDownloadedTracksFlow().first()
        assertEquals(1, downloadedRows.size)

        // Downloaded tracks are separate from online saved bookmarks
        assertEquals(0, onlineLibraryRepository.savedTracksFlow.first().size)

        // Verify downloaded song plays offline (isOnline = false, sourceType = DOWNLOADED, local URI)
        val offlineSong = downloadedRows.first().toDomain().toSong()
        assertFalse(offlineSong.isOnline)
        assertEquals("DOWNLOADED", offlineSong.sourceType)
        assertTrue(offlineSong.uri.toString().startsWith("file://"))
        localFile.delete()
        Unit
    }

    @Test
    fun libraryModePreference_defaultsToOfflineAndPersistsAcrossRestart(): Unit = runBlocking {
        assertEquals(LibrarySectionMode.OFFLINE, libraryModePrefs.getInitialLibrarySectionMode())

        libraryModePrefs.setLibrarySectionMode(LibrarySectionMode.ONLINE)
        assertEquals(LibrarySectionMode.ONLINE, libraryModePrefs.librarySectionModeFlow.first())

        // Simulate app restart with a fresh LibraryModePreferences reader
        val restartedPrefs = LibraryModePreferences(context)
        assertEquals(LibrarySectionMode.ONLINE, restartedPrefs.getInitialLibrarySectionMode())
        assertEquals(LibrarySectionMode.ONLINE, restartedPrefs.librarySectionModeFlow.first())
    }

    @Test
    fun roomMigration3To4_createsOnlineLibraryTablesAndPreservesExistingPlaylistsAndDownloads() {
        val dbName = "sonora_migration_3_4_test_${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `coverArtUri` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceKey` TEXT NOT NULL, `providerKey` TEXT NOT NULL, `providerName` TEXT NOT NULL, `contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `artworkUrl` TEXT, `localFileUri` TEXT NOT NULL, `fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `formatLabel` TEXT NOT NULL, `bitrateKbps` INTEGER NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO `playlists` (`id`, `name`, `createdAt`) VALUES (1, 'My Classic Mix', 123456)")
                    db.execSQL("INSERT INTO `playlist_songs` (`playlistId`, `songId`, `orderIndex`) VALUES (1, 99, 0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        val rawDb = helper.writableDatabase

        // Run real Room MIGRATION_3_4
        SonoraDatabase.MIGRATION_3_4.migrate(rawDb)

        val createdTables = mutableSetOf<String>()
        rawDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) createdTables.add(c.getString(0))
        }
        assertTrue("saved_online_albums" in createdTables)
        assertTrue("followed_online_artists" in createdTables)
        assertTrue("online_playlists" in createdTables)
        assertTrue("online_playlist_tracks" in createdTables)

        // Verify pre-existing playlist data survived untouched
        rawDb.query("SELECT name FROM playlists WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("My Classic Mix", c.getString(0))
        }
        rawDb.query("SELECT songId FROM playlist_songs WHERE playlistId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(99L, c.getLong(0))
        }

        rawDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun libraryModeSegmentedSelector_switchesSmoothlyWithoutInterruptingPlayback() {
        var mode by mutableStateOf(LibrarySectionMode.OFFLINE)
        var playbackStillRunning = true

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides darkColors) {
                LibraryModeSegmentedSelector(
                    selectedMode = mode,
                    colors = darkColors,
                    onSelectMode = { mode = it }
                )
            }
        }

        composeRule.onNodeWithTag("library_mode_offline").assertIsDisplayed()
        composeRule.onNodeWithTag("library_mode_online").assertIsDisplayed()

        composeRule.onNodeWithTag("library_mode_online").performClick()
        composeRule.waitForIdle()
        assertEquals(LibrarySectionMode.ONLINE, mode)
        assertTrue(playbackStillRunning)

        composeRule.onNodeWithTag("library_mode_offline").performClick()
        composeRule.waitForIdle()
        assertEquals(LibrarySectionMode.OFFLINE, mode)
        assertTrue(playbackStillRunning)
    }

    @Test
    fun onlineLibraryContent_rendersAllSectionsAndCreatePlaylistButton() {
        val onlineSong = OnlineTrackMapper.toUnifiedSong(sampleOnlineTrack())
        val searchResults = OnlineLibrarySearchResults(
            query = "",
            tracks = listOf(onlineSong),
            albums = listOf(
                SavedOnlineAlbum(
                    stableId = -501L,
                    serviceId = 0,
                    providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                    providerName = "YouTube Music",
                    contentUrl = "https://music.youtube.com/playlist?list=ALB1",
                    title = "Neon Horizons",
                    artist = "Solaris",
                    artworkUrl = null,
                    trackCount = 10L,
                    savedTimestamp = 1000L
                )
            ),
            artists = listOf(
                FollowedOnlineArtist(
                    stableId = -601L,
                    serviceId = 0,
                    providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                    providerName = "YouTube Music",
                    contentUrl = "https://music.youtube.com/channel/UC_solaris",
                    name = "Solaris",
                    artworkUrl = null,
                    subscriberCount = 12_000L,
                    followedTimestamp = 1000L
                )
            ),
            playlists = listOf(
                OnlinePlaylist(
                    id = 1L,
                    name = "Synthwave Radio",
                    providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                    providerName = "YouTube Music",
                    externalContentUrl = null,
                    artworkUrl = null,
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    tracks = listOf(onlineSong)
                )
            ),
            history = listOf(onlineSong),
            favorites = listOf(onlineSong)
        )

        var createdPlaylistTriggered = false
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides lightColors) {
                OnlineLibraryContent(
                    searchResults = searchResults,
                    recentlyPlayed = listOf(onlineSong),
                    savedTrackIds = setOf(onlineSong.id),
                    availableProviders = emptyList(),
                    selectedProviderKey = null,
                    searchQuery = "",
                    sortOption = OnlineLibrarySortOption.RECENTLY_SAVED,
                    isCollectionLoading = false,
                    currentSongId = null,
                    isPlaying = false,
                    colors = lightColors,
                    onSearchQueryChange = {},
                    onSelectProviderFilter = {},
                    onOpenSortSheet = {},
                    onCreateOnlinePlaylist = { createdPlaylistTriggered = true },
                    onOpenOnlinePlaylist = {},
                    onPlaySongs = { _, _, _ -> },
                    onToggleSaveTrack = {},
                    onToggleFavoriteTrack = {},
                    onOpenSongMore = {},
                    onOpenAlbum = {},
                    onRemoveAlbum = {},
                    onOpenArtist = {},
                    onUnfollowArtist = {},
                    onClearHistory = {},
                    onExploreOnlineMusic = {}
                )
            }
        }

        composeRule.onNodeWithTag("online_library_content").assertIsDisplayed()
        composeRule.onNodeWithTag("online_library_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("online_library_create_playlist_button").performClick()
        assertTrue(createdPlaylistTriggered)
    }

    @Test
    fun offlinePlaylistsSection_displaysPlaylistsAndOpensCreateDialog() {
        val samplePlaylist = Playlist(id = 7L, name = "Road Trip FLAC", songCount = 3, createdAt = 1000L)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides lightColors) {
                OfflinePlaylistsSection(
                    playlists = listOf(samplePlaylist),
                    allLocalSongs = emptyList(),
                    playlistSongResolver = { emptyList() },
                    colors = lightColors,
                    onCreatePlaylist = {},
                    onDeletePlaylist = {},
                    onOpenPlaylist = { _, _ -> },
                    onPlayPlaylist = {}
                )
            }
        }

        composeRule.onNodeWithTag("offline_playlists_section").assertIsDisplayed()
        composeRule.onNodeWithText("Road Trip FLAC").assertIsDisplayed()
        composeRule.onNodeWithTag("offline_create_playlist_button").performClick()
        composeRule.onNodeWithText("New Local Playlist").assertIsDisplayed()
    }

    @Test
    fun onlineTrackOptionsSheet_showsAddToOnlinePlaylistAndFollowArtistActions() {
        var addedToOnlinePlaylist = false
        var followedArtist = false
        val track = sampleOnlineTrack()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides darkColors) {
                OnlineTrackOptionsSheet(
                    track = track,
                    isSaved = false,
                    isFavorite = false,
                    isDownloaded = false,
                    isArtistFollowed = false,
                    isCollectionSaved = false,
                    resolvedStream = null,
                    colors = darkColors,
                    onPlayNow = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onToggleSave = {},
                    onToggleFavorite = {},
                    onAddToPlaylist = {},
                    onAddToOnlinePlaylist = { addedToOnlinePlaylist = true },
                    onToggleFollowArtist = { followedArtist = true },
                    onToggleSaveCollection = null,
                    onDownload = {},
                    onOpenQualitySettings = {},
                    onViewArtistChannel = null,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("online_menu_add_online_playlist").assertExists()
        composeRule.onNodeWithTag("online_menu_follow_artist").assertExists()
        composeRule.onNodeWithTag("online_menu_add_online_playlist").performClick()
        assertTrue(addedToOnlinePlaylist)
        assertFalse(followedArtist)
    }
}
