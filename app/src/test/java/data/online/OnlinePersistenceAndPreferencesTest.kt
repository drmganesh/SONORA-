package data.online

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.data.OnlineSearchHistoryEntity
import com.example.data.OnlineTrackEntity
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSongCrossRef
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import com.example.data.SonoraPreferences
import domain.online.AudioQualityPreference
import domain.online.OnlineMusicProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlinePersistenceAndPreferencesTest {

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var dao: SonoraDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `OnlineTrackEntity persists canonical contentUrl without storing expiring stream URLs`() = runBlocking {
        val columnFields = OnlineTrackEntity::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("Canonical contentUrl column must exist", "contentUrl" in columnFields)
        assertFalse("Expiring raw streamUrl must never be persisted in Room", "streamUrl" in columnFields)
        assertFalse("Expiring audioUrl must never be persisted in Room", "audioUrl" in columnFields)

        val stableId = OnlineTrackMapper.computeStableId(
            OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            "https://music.youtube.com/watch?v=abc123"
        )
        val entity = OnlineTrackEntity(
            stableId = stableId,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "https://music.youtube.com/watch?v=abc123",
            title = "Midnight City",
            artist = "M83",
            artistUrl = "https://music.youtube.com/channel/UC_example",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243_000L,
            artworkUrl = "https://img.example.com/art.jpg",
            isSaved = false,
            isFavorite = false,
            playCount = 0
        )

        dao.upsertOnlineTrack(entity)
        dao.updateOnlineTrackSaved(stableId, isSaved = true, savedTimestamp = 123456L)
        dao.updateOnlineTrackFavorite(stableId, isFavorite = true)
        dao.incrementOnlinePlayCount(stableId, timestamp = 999999L)

        val saved = dao.getSavedOnlineTracksFlow().first()
        assertEquals(1, saved.size)
        assertEquals(1, saved.first().playCount)
        assertTrue(saved.first().isFavorite)
        assertEquals(999999L, saved.first().lastPlayedTimestamp)

        val recent = dao.getRecentlyPlayedOnlineTracksFlow(10).first()
        assertEquals(1, recent.size)
        assertEquals("Midnight City", recent.first().title)

        // Playlist cross-ref compatibility with online track negative stableId
        val pid = dao.insertPlaylist(PlaylistEntity(name = "Cloud Mix"))
        dao.insertPlaylistSong(PlaylistSongCrossRef(pid, stableId, 0))
        val refs = dao.getPlaylistSongsFlow(pid).first()
        assertEquals(listOf(stableId), refs.map { it.songId })
    }

    @Test
    fun `online search history supports upsert, delete, and clear`() = runBlocking {
        dao.upsertOnlineSearchHistory(OnlineSearchHistoryEntity("synthwave mix", OnlineMusicProvider.KEY_YOUTUBE_MUSIC, 100L))
        dao.upsertOnlineSearchHistory(OnlineSearchHistoryEntity("lofi beats", OnlineMusicProvider.KEY_SOUNDCLOUD, 200L))

        val initial = dao.getOnlineSearchHistoryFlow(10).first()
        assertEquals(listOf("lofi beats", "synthwave mix"), initial.map { it.query })

        dao.deleteOnlineSearchHistory("synthwave mix")
        val afterDelete = dao.getOnlineSearchHistoryFlow(10).first()
        assertEquals(listOf("lofi beats"), afterDelete.map { it.query })

        dao.clearOnlineSearchHistory()
        assertTrue(dao.getOnlineSearchHistoryFlow(10).first().isEmpty())
    }

    @Test
    fun `SonoraPreferences persists online quality and selected provider cleanly`() {
        val prefs = SonoraPreferences(context)
        prefs.onlineQualityPreference = AudioQualityPreference.DATA_SAVER
        prefs.selectedOnlineProviderKey = OnlineMusicProvider.KEY_SOUNDCLOUD

        val reloaded = SonoraPreferences(context)
        assertEquals(AudioQualityPreference.DATA_SAVER, reloaded.onlineQualityPreference)
        assertEquals(OnlineMusicProvider.KEY_SOUNDCLOUD, reloaded.selectedOnlineProviderKey)
    }

    @Test
    fun `MIGRATION_1_2 creates online_tracks and online_search_history on v1 database`() {
        val dbName = "test_migration_1_2.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `song_metadata` (`id` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, `customTitle` TEXT, `customArtist` TEXT, `customAlbum` TEXT, `customGenre` TEXT, `customLyrics` TEXT, PRIMARY KEY(`id`))")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqliteDb = helper.writableDatabase
        SonoraDatabase.MIGRATION_1_2.migrate(sqliteDb)

        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('online_tracks', 'online_search_history')").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
            assertTrue("online_tracks must be created by MIGRATION_1_2", "online_tracks" in tables)
            assertTrue("online_search_history must be created by MIGRATION_1_2", "online_search_history" in tables)
        }
        helper.close()
        context.deleteDatabase(dbName)
    }
}
