package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicScanner(
    private val context: Context,
    private val dao: SonoraDao
) {

    companion object {
        private const val TAG = "LocalMusicScanner"
    }

    suspend fun scanSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val storageState = try {
            Environment.getExternalStorageState()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read external storage state", t)
            Environment.MEDIA_MOUNTED
        }
        if (storageState != Environment.MEDIA_MOUNTED && storageState != Environment.MEDIA_MOUNTED_READ_ONLY) {
            Log.w(TAG, "External storage unavailable ($storageState); using built-in demo audio")
            return@withContext generateMasterDemoTracks()
        }

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndex(MediaStore.Audio.Media._ID)
                if (idCol < 0) {
                    Log.w(TAG, "MediaStore cursor missing _ID column; falling back")
                    return@use
                }
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val mimeCol = it.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val trackCol = it.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = it.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = if (titleCol >= 0) it.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val rawArtist = if (artistCol >= 0) it.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val rawAlbum = if (albumCol >= 0) it.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val albumId = if (albumIdCol >= 0) it.getLong(albumIdCol) else 0L
                    val durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                    val dataPath = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                    val mimeType = if (mimeCol >= 0) it.getString(mimeCol) ?: "audio/mpeg" else "audio/mpeg"
                    val sizeBytes = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val trackNumber = if (trackCol >= 0) it.getInt(trackCol) else 0
                    val year = if (yearCol >= 0) it.getInt(yearCol) else 0
                    val dateAdded = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else 0L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val savedMetadata = try {
                        dao.getMetadata(id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading Room metadata for track id=$id", e)
                        null
                    }

                    val finalTitle = savedMetadata?.customTitle ?: rawTitle
                    val finalArtist = savedMetadata?.customArtist ?: (if (rawArtist == "<unknown>") "Unknown Artist" else rawArtist)
                    val finalAlbum = savedMetadata?.customAlbum ?: (if (rawAlbum == "<unknown>") "Unknown Album" else rawAlbum)
                    val finalGenre = savedMetadata?.customGenre ?: "Audio Track"
                    val isFav = savedMetadata?.isFavorite ?: false
                    val playCount = savedMetadata?.playCount ?: 0
                    val lastPlayed = savedMetadata?.lastPlayedTimestamp ?: 0L
                    val lyrics = savedMetadata?.customLyrics

                    songsList.add(
                        Song(
                            id = id,
                            title = finalTitle,
                            artist = finalArtist,
                            album = finalAlbum,
                            albumId = albumId,
                            durationMs = durationMs,
                            uri = contentUri,
                            dataPath = dataPath,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            trackNumber = trackNumber,
                            year = year,
                            genre = finalGenre,
                            bitrateKbps = if (mimeType.contains("flac")) 920 else 320,
                            sampleRateHz = if (mimeType.contains("flac")) 96000 else 44100,
                            bitDepth = if (mimeType.contains("flac")) 24 else 16,
                            dateAdded = dateAdded,
                            isFavorite = isFav,
                            playCount = playCount,
                            lastPlayedTimestamp = lastPlayed,
                            lyrics = lyrics,
                            isDemoTrack = false
                        )
                    )
                }
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Audio storage permission not granted yet during media scan", se)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore audio tracks", e)
        }

        // If no physical media found on this device or emulator, synthesize audiophile test master tracks
        if (songsList.isEmpty()) {
            songsList.addAll(generateMasterDemoTracks())
        }

        songsList
    }

    private suspend fun generateMasterDemoTracks(): List<Song> {
        val demoSongs = mutableListOf<Song>()

        val trackConfigs = listOf(
            DemoTrackConfig(
                id = -101L,
                title = "Starlight Odyssey (360° Spatial)",
                artist = "Sonora Sound Lab",
                album = "Acoustic Dimensions",
                year = 2026,
                genre = "Hi-Res Electronic / Synth",
                composer = "A. Vance",
                durationSec = 45,
                baseFreqs = doubleArrayOf(261.63, 329.63, 392.00), // C major chord
                panPattern = 0,
                lyrics = """[00:00.00] Echoes through the stellar night
[00:06.00] Waves of sound in pure delight
[00:14.00] Orbiting the silent core
[00:22.00] Spatial pulse forever more
[00:30.00] Feel the 360 soundstage expand
[00:38.00] Sonics drifting through the band"""
            ),
            DemoTrackConfig(
                id = -102L,
                title = "Deep Velvet Bass Groove",
                artist = "The Obsidian Trio",
                album = "Midnight Sessions Hi-Fi",
                year = 2025,
                genre = "Hi-Res Neo Jazz",
                composer = "Miles C.",
                durationSec = 50,
                baseFreqs = doubleArrayOf(220.00, 277.18, 329.63), // A minor chord
                panPattern = 1,
                lyrics = """[00:00.00] Low frequencies in the dark
[00:08.00] Sub-bass leaving its clean mark
[00:18.00] Ten band equalizer fine tuned
[00:28.00] Under the amber silver moon
[00:38.00] Pristine harmonics, analog warmth
[00:46.00] Sonora audio streaming forth"""
            ),
            DemoTrackConfig(
                id = -103L,
                title = "Ethereal Resonance (DSD Master)",
                artist = "Aura Acoustic Ensemble",
                album = "Audiophile Reference Vol. 1",
                year = 2026,
                genre = "Acoustic / Classical",
                composer = "E. Satie",
                durationSec = 48,
                baseFreqs = doubleArrayOf(349.23, 440.00, 523.25), // F major chord
                panPattern = 0,
                lyrics = """[00:00.00] Floating in crystal reverberation
[00:10.00] Studio master celebration
[00:20.00] 96 kilohertz twenty-four bit flow
[00:30.00] Golden warmth and neon glow
[00:40.00] Spatial acoustics fill the room"""
            ),
            DemoTrackConfig(
                id = -104L,
                title = "Cybernetic Pulse (Bass Test)",
                artist = "K-Wave Dynamics",
                album = "Frequency Response 2026",
                year = 2026,
                genre = "Cyberpunk / Electro",
                composer = "Neural Unit",
                durationSec = 42,
                baseFreqs = doubleArrayOf(196.00, 246.94, 293.66), // G major chord
                panPattern = 1,
                lyrics = """[00:00.00] 30Hz sub-bass response active
[00:08.00] Virtualizer matrix dynamic
[00:16.00] Poweramp-grade digital filters
[00:26.00] Zero clipping, pristine decibels
[00:34.00] Peak stereo wideness unlocked"""
            )
        )

        for (cfg in trackConfigs) {
            try {
                val file = WavSynthesizer.getOrGenerateDemoTrack(
                    context = context,
                    filename = "demo_track_${cfg.id}.wav",
                    durationSeconds = cfg.durationSec,
                    baseFrequencies = cfg.baseFreqs,
                    patternType = cfg.panPattern
                )

                val uri = Uri.fromFile(file)
                val savedMetadata = try {
                    dao.getMetadata(cfg.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading Room metadata for demo track id=${cfg.id}", e)
                    null
                }

                demoSongs.add(
                    Song(
                        id = cfg.id,
                        title = savedMetadata?.customTitle ?: cfg.title,
                        artist = savedMetadata?.customArtist ?: cfg.artist,
                        album = savedMetadata?.customAlbum ?: cfg.album,
                        albumId = cfg.id,
                        durationMs = cfg.durationSec * 1000L,
                        uri = uri,
                        dataPath = file.absolutePath,
                        mimeType = "audio/wav",
                        sizeBytes = file.length(),
                        trackNumber = (trackConfigs.indexOf(cfg) + 1),
                        year = cfg.year,
                        genre = savedMetadata?.customGenre ?: cfg.genre,
                        composer = cfg.composer,
                        bitrateKbps = 1411,
                        sampleRateHz = 44100,
                        bitDepth = 16,
                        dateAdded = System.currentTimeMillis() - (trackConfigs.indexOf(cfg) * 86400000L),
                        isFavorite = savedMetadata?.isFavorite ?: (cfg.id == -101L),
                        playCount = savedMetadata?.playCount ?: (12 - trackConfigs.indexOf(cfg) * 3),
                        lastPlayedTimestamp = savedMetadata?.lastPlayedTimestamp ?: (System.currentTimeMillis() - 3600000L),
                        lyrics = savedMetadata?.customLyrics ?: cfg.lyrics,
                        isDemoTrack = true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed generating demo track ${cfg.id}", e)
            }
        }

        return demoSongs
    }

    private data class DemoTrackConfig(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val year: Int,
        val genre: String,
        val composer: String,
        val durationSec: Int,
        val baseFreqs: DoubleArray,
        val panPattern: Int,
        val lyrics: String
    )
}
