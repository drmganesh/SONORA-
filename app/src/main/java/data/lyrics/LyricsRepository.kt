package data.lyrics

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.example.data.SonoraDao
import com.example.model.Song
import com.example.util.LyricsExtractor
import domain.lyrics.LrcParser
import domain.lyrics.LyricsDocument
import domain.lyrics.LyricsSearchCandidate
import domain.lyrics.LyricsSourceOption
import domain.lyrics.LyricsSourceType
import domain.lyrics.LyricsSyncType
import domain.lyrics.LyricsTimingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates lyrics extraction, online LRCLIB lookup, SAF .lrc import,
 * manual editing, Room persistence, per-track timing offsets, and in-memory LRU caching.
 */
class LyricsRepository(
    private val context: Context,
    private val dao: SonoraDao,
    private val onlineProvider: OnlineLyricsProvider = LrcLibOnlineLyricsClient()
) {
    private val memoryCache = LruCache<String, LyricsDocument>(32)

    fun trackKeyFor(song: Song): String {
        return when {
            song.isOnline && song.contentUrl.isNotBlank() -> "online:${song.id}:${song.contentUrl.hashCode()}"
            song.isOnline -> "online:${song.id}"
            song.dataPath.isNotBlank() -> "local:${song.id}:${song.dataPath.hashCode()}"
            else -> "local:${song.id}"
        }
    }

    /**
     * Loads the per-track timing offset from Room (returns null if no custom track offset saved).
     */
    suspend fun getSavedTrackOffset(song: Song): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val key = trackKeyFor(song)
        val entity = dao.getTrackLyrics(key)
        if (entity != null && entity.hasCustomOffset) {
            entity.timingOffsetMs.coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS) to true
        } else {
            0L to false
        }
    }

    /**
     * Loads parsed lyrics for [song] asynchronously on Dispatchers.IO.
     *
     * Resolution priority when [preferredSource] == null:
     * 1. User-edited / SAF-imported lyrics stored in Room
     * 2. Local sidecar .elrc / .lrc file
     * 3. Embedded local metadata (ID3v2 SYLT/USLT, Vorbis, M4A ©lyr)
     * 4. Cached Room online lyrics
     * 5. Live LRCLIB online provider (when [allowNetwork] is true)
     * 6. Local plain .txt sidecar fallback
     */
    suspend fun loadLyricsForSong(
        song: Song,
        preferredSource: LyricsSourceType? = null,
        forceRefresh: Boolean = false,
        allowNetwork: Boolean = true
    ): LyricsDocument? = withContext(Dispatchers.IO) {
        val trackKey = trackKeyFor(song)

        if (!forceRefresh && preferredSource == null) {
            memoryCache.get(trackKey)?.let { return@withContext it }
        }

        val savedEntity = dao.getTrackLyrics(trackKey)

        // Explicit source selection mode
        if (preferredSource != null) {
            val explicit = resolveSpecificSource(song, trackKey, savedEntity, preferredSource, allowNetwork)
            if (explicit != null) {
                memoryCache.put(trackKey, explicit)
            }
            return@withContext explicit
        }

        // 1. User-edited or imported lyrics in Room always take precedence unless force-refreshed
        if (!forceRefresh && savedEntity != null && savedEntity.rawLyrics.isNotBlank() &&
            (savedEntity.isUserEdited || savedEntity.sourceType == LyricsSourceType.IMPORTED_LRC.name || savedEntity.sourceType == LyricsSourceType.LOCAL_EDITED.name)
        ) {
            val doc = buildDocument(
                trackKey = trackKey,
                song = song,
                rawText = savedEntity.rawLyrics,
                sourceType = runCatching { LyricsSourceType.valueOf(savedEntity.sourceType) }.getOrDefault(LyricsSourceType.LOCAL_EDITED),
                sourceLabel = savedEntity.sourceLabel,
                isInstrumental = savedEntity.isInstrumental
            )
            memoryCache.put(trackKey, doc)
            return@withContext doc
        }

        // 2. Local sidecar .elrc / .lrc
        if (!song.isOnline) {
            LyricsExtractor.extractSidecarLrc(song)?.let { extracted ->
                val doc = buildDocument(trackKey, song, extracted.rawText, extracted.sourceType, extracted.sourceLabel)
                if (doc.hasLyrics) {
                    memoryCache.put(trackKey, doc)
                    return@withContext doc
                }
            }

            // 3. Embedded audio tags (prefer synced embedded lyrics over online; if plain embedded, check online synced if allowed)
            val embedded = LyricsExtractor.extractEmbeddedLyrics(context, song)
            if (embedded != null) {
                val embeddedDoc = buildDocument(trackKey, song, embedded.rawText, embedded.sourceType, embedded.sourceLabel)
                if (embeddedDoc.isSynchronized) {
                    memoryCache.put(trackKey, embeddedDoc)
                    return@withContext embeddedDoc
                }
                // If embedded is unsynced and we have cached or online synced lyrics, prefer synced; otherwise return embedded
                if (!forceRefresh && savedEntity != null && savedEntity.rawLyrics.isNotBlank()) {
                    val cachedDoc = buildDocument(
                        trackKey = trackKey,
                        song = song,
                        rawText = savedEntity.rawLyrics,
                        sourceType = runCatching { LyricsSourceType.valueOf(savedEntity.sourceType) }.getOrDefault(LyricsSourceType.ONLINE_LRCLIB),
                        sourceLabel = savedEntity.sourceLabel,
                        isInstrumental = savedEntity.isInstrumental
                    )
                    if (cachedDoc.isSynchronized) {
                        memoryCache.put(trackKey, cachedDoc)
                        return@withContext cachedDoc
                    }
                }
                if (embeddedDoc.hasLyrics) {
                    memoryCache.put(trackKey, embeddedDoc)
                    return@withContext embeddedDoc
                }
            }
        }

        // 4. Cached Room lyrics (from previous LRCLIB lookup)
        if (!forceRefresh && savedEntity != null && (savedEntity.rawLyrics.isNotBlank() || savedEntity.isInstrumental)) {
            val doc = buildDocument(
                trackKey = trackKey,
                song = song,
                rawText = savedEntity.rawLyrics,
                sourceType = runCatching { LyricsSourceType.valueOf(savedEntity.sourceType) }.getOrDefault(LyricsSourceType.ONLINE_LRCLIB),
                sourceLabel = savedEntity.sourceLabel,
                isInstrumental = savedEntity.isInstrumental
            )
            memoryCache.put(trackKey, doc)
            return@withContext doc
        }

        // 5. Live Online LRCLIB fetch
        if (allowNetwork) {
            val onlineCandidate = onlineProvider.fetchExactLyrics(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs
            )
            if (onlineCandidate != null) {
                val raw = onlineCandidate.preferredRawText
                val doc = buildDocument(
                    trackKey = trackKey,
                    song = song,
                    rawText = raw,
                    sourceType = LyricsSourceType.ONLINE_LRCLIB,
                    sourceLabel = if (onlineCandidate.hasSyncedLyrics) "Online Synced (${onlineCandidate.sourceLabel})" else "Online Plain (${onlineCandidate.sourceLabel})",
                    isInstrumental = onlineCandidate.instrumental
                )
                persistEntityPreservingOffset(trackKey, song, doc, isUserEdited = false)
                memoryCache.put(trackKey, doc)
                return@withContext doc
            }
        }

        // 6. Local plain text sidecar fallback
        if (!song.isOnline) {
            LyricsExtractor.extractSidecarPlainText(song)?.let { plain ->
                val doc = buildDocument(trackKey, song, plain.rawText, plain.sourceType, plain.sourceLabel)
                if (doc.hasLyrics) {
                    memoryCache.put(trackKey, doc)
                    return@withContext doc
                }
            }
        }

        null
    }

    private suspend fun resolveSpecificSource(
        song: Song,
        trackKey: String,
        savedEntity: TrackLyricsEntity?,
        source: LyricsSourceType,
        allowNetwork: Boolean
    ): LyricsDocument? {
        return when (source) {
            LyricsSourceType.LOCAL_SIDECAR_LRC -> {
                LyricsExtractor.extractSidecarLrc(song)?.let {
                    buildDocument(trackKey, song, it.rawText, it.sourceType, it.sourceLabel)
                }
            }
            LyricsSourceType.EMBEDDED_TAG -> {
                LyricsExtractor.extractEmbeddedLyrics(context, song)?.let {
                    buildDocument(trackKey, song, it.rawText, it.sourceType, it.sourceLabel)
                }
            }
            LyricsSourceType.PLAIN_TEXT -> {
                LyricsExtractor.extractSidecarPlainText(song)?.let {
                    buildDocument(trackKey, song, it.rawText, it.sourceType, it.sourceLabel)
                }
            }
            LyricsSourceType.LOCAL_EDITED, LyricsSourceType.IMPORTED_LRC -> {
                savedEntity?.takeIf { it.rawLyrics.isNotBlank() }?.let {
                    buildDocument(
                        trackKey = trackKey,
                        song = song,
                        rawText = it.rawLyrics,
                        sourceType = runCatching { LyricsSourceType.valueOf(it.sourceType) }.getOrDefault(source),
                        sourceLabel = it.sourceLabel,
                        isInstrumental = it.isInstrumental
                    )
                }
            }
            LyricsSourceType.ONLINE_LRCLIB -> {
                if (!allowNetwork) return null
                val candidate = onlineProvider.fetchExactLyrics(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMs
                ) ?: return null
                val doc = buildDocument(
                    trackKey = trackKey,
                    song = song,
                    rawText = candidate.preferredRawText,
                    sourceType = LyricsSourceType.ONLINE_LRCLIB,
                    sourceLabel = if (candidate.hasSyncedLyrics) "Online Synced (${candidate.sourceLabel})" else "Online Plain (${candidate.sourceLabel})",
                    isInstrumental = candidate.instrumental
                )
                persistEntityPreservingOffset(trackKey, song, doc, isUserEdited = false)
                doc
            }
            LyricsSourceType.NONE -> null
        }
    }

    suspend fun searchOnlineLyrics(
        query: String,
        trackName: String? = null,
        artistName: String? = null
    ): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        onlineProvider.searchLyrics(query = query, trackName = trackName, artistName = artistName)
    }

    suspend fun applySearchCandidate(song: Song, candidate: LyricsSearchCandidate): LyricsDocument =
        withContext(Dispatchers.IO) {
            val trackKey = trackKeyFor(song)
            val doc = buildDocument(
                trackKey = trackKey,
                song = song,
                rawText = candidate.preferredRawText,
                sourceType = LyricsSourceType.ONLINE_LRCLIB,
                sourceLabel = if (candidate.hasSyncedLyrics) "Online Synced (${candidate.sourceLabel})" else "Online Plain (${candidate.sourceLabel})",
                isInstrumental = candidate.instrumental
            )
            persistEntityPreservingOffset(trackKey, song, doc, isUserEdited = false)
            memoryCache.put(trackKey, doc)
            doc
        }

    /**
     * Imports a user-selected .lrc / .txt document via Storage Access Framework (SAF) URI.
     */
    suspend fun importLrcFromUri(song: Song, uri: Uri, displayName: String? = null): Result<LyricsDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val maxBytes = 512 * 1024
                    val buffer = ByteArray(maxBytes + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    if (total > maxBytes) {
                        throw IllegalArgumentException("Lyrics file is too large (max 512 KB).")
                    }
                    buffer.copyOf(total)
                } ?: throw IllegalStateException("Unable to read the selected lyrics file.")

                val rawText = decodeUtfBytes(rawBytes).trim()
                if (rawText.isBlank()) {
                    throw IllegalArgumentException("Selected lyrics file is empty.")
                }

                val trackKey = trackKeyFor(song)
                val label = if (!displayName.isNullOrBlank()) {
                    "Imported LRC ($displayName)"
                } else {
                    LyricsSourceType.IMPORTED_LRC.displayLabel
                }
                val doc = buildDocument(
                    trackKey = trackKey,
                    song = song,
                    rawText = rawText,
                    sourceType = LyricsSourceType.IMPORTED_LRC,
                    sourceLabel = label
                )
                persistEntityPreservingOffset(trackKey, song, doc, isUserEdited = true)
                memoryCache.put(trackKey, doc)
                doc
            }
        }

    suspend fun saveEditedLyrics(song: Song, rawText: String): LyricsDocument = withContext(Dispatchers.IO) {
        val trackKey = trackKeyFor(song)
        val cleaned = rawText.trim()
        val doc = buildDocument(
            trackKey = trackKey,
            song = song,
            rawText = cleaned,
            sourceType = LyricsSourceType.LOCAL_EDITED,
            sourceLabel = LyricsSourceType.LOCAL_EDITED.displayLabel
        )
        persistEntityPreservingOffset(trackKey, song, doc, isUserEdited = true)
        memoryCache.put(trackKey, doc)
        doc
    }

    suspend fun saveCurrentLyricsLocally(song: Song, document: LyricsDocument): LyricsDocument =
        withContext(Dispatchers.IO) {
            val trackKey = trackKeyFor(song)
            val savedDoc = document.copy(
                sourceLabel = if (document.sourceLabel.contains("Saved", ignoreCase = true)) {
                    document.sourceLabel
                } else {
                    "${document.sourceLabel} • Saved Locally"
                }
            )
            persistEntityPreservingOffset(trackKey, song, savedDoc, isUserEdited = true)
            memoryCache.put(trackKey, savedDoc)
            savedDoc
        }

    suspend fun saveTrackTimingOffset(song: Song, offsetMs: Long, hasCustomOffset: Boolean = true) =
        withContext(Dispatchers.IO) {
            val trackKey = trackKeyFor(song)
            val clamped = offsetMs.coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
            val existing = dao.getTrackLyrics(trackKey)
            if (existing != null) {
                dao.updateTrackLyricsOffset(
                    trackKey = trackKey,
                    offsetMs = clamped,
                    hasCustomOffset = hasCustomOffset,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                dao.upsertTrackLyrics(
                    TrackLyricsEntity(
                        trackKey = trackKey,
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        rawLyrics = "",
                        sourceType = LyricsSourceType.NONE.name,
                        sourceLabel = LyricsSourceType.NONE.displayLabel,
                        syncType = LyricsSyncType.NONE.name,
                        timingOffsetMs = clamped,
                        hasCustomOffset = hasCustomOffset,
                        isUserEdited = false,
                        isInstrumental = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

    suspend fun inspectAvailableSources(song: Song, activeSource: LyricsSourceType): List<LyricsSourceOption> =
        withContext(Dispatchers.IO) {
            val trackKey = trackKeyFor(song)
            val saved = dao.getTrackLyrics(trackKey)
            val hasEmbedded = !song.isOnline && LyricsExtractor.extractEmbeddedLyrics(context, song) != null
            val hasSidecarLrc = !song.isOnline && LyricsExtractor.extractSidecarLrc(song) != null
            val hasSidecarTxt = !song.isOnline && LyricsExtractor.extractSidecarPlainText(song) != null
            val hasSavedLocal = saved != null && saved.rawLyrics.isNotBlank() &&
                (saved.isUserEdited || saved.sourceType == LyricsSourceType.IMPORTED_LRC.name || saved.sourceType == LyricsSourceType.LOCAL_EDITED.name)

            listOf(
                LyricsSourceOption(
                    sourceType = LyricsSourceType.EMBEDDED_TAG,
                    title = "Embedded Audio Metadata",
                    subtitle = if (hasEmbedded) "Found ID3 / Vorbis / M4A lyrics" else "Not embedded in this audio file",
                    isAvailable = hasEmbedded,
                    isActive = activeSource == LyricsSourceType.EMBEDDED_TAG
                ),
                LyricsSourceOption(
                    sourceType = LyricsSourceType.LOCAL_SIDECAR_LRC,
                    title = "Local Sidecar LRC",
                    subtitle = if (hasSidecarLrc) "Matching .lrc / .elrc file found" else "No sidecar .lrc next to audio",
                    isAvailable = hasSidecarLrc,
                    isActive = activeSource == LyricsSourceType.LOCAL_SIDECAR_LRC
                ),
                LyricsSourceOption(
                    sourceType = LyricsSourceType.ONLINE_LRCLIB,
                    title = "Online Lyrics (LRCLIB)",
                    subtitle = "Open time-synced & plain lyrics database",
                    isAvailable = true,
                    isActive = activeSource == LyricsSourceType.ONLINE_LRCLIB
                ),
                LyricsSourceOption(
                    sourceType = LyricsSourceType.IMPORTED_LRC,
                    title = "Imported / Saved Locally",
                    subtitle = if (hasSavedLocal) "Locally saved lyrics available" else "Import a .lrc file or edit manually",
                    isAvailable = hasSavedLocal,
                    isActive = activeSource == LyricsSourceType.IMPORTED_LRC || activeSource == LyricsSourceType.LOCAL_EDITED
                ),
                LyricsSourceOption(
                    sourceType = LyricsSourceType.PLAIN_TEXT,
                    title = "Local Plain Text (.txt)",
                    subtitle = if (hasSidecarTxt) "Matching .txt lyrics file found" else "No sidecar .txt file found",
                    isAvailable = hasSidecarTxt,
                    isActive = activeSource == LyricsSourceType.PLAIN_TEXT
                )
            )
        }

    fun invalidateMemoryCache(song: Song? = null) {
        if (song == null) {
            memoryCache.evictAll()
        } else {
            memoryCache.remove(trackKeyFor(song))
        }
    }

    private fun buildDocument(
        trackKey: String,
        song: Song,
        rawText: String,
        sourceType: LyricsSourceType,
        sourceLabel: String,
        isInstrumental: Boolean = false
    ): LyricsDocument {
        val parsed = LrcParser.parse(rawText)
        return LyricsDocument(
            trackKey = trackKey,
            title = song.title,
            artist = song.artist,
            rawText = rawText,
            lines = parsed.lines,
            syncType = parsed.syncType,
            sourceType = sourceType,
            sourceLabel = sourceLabel,
            embeddedOffsetMs = parsed.embeddedOffsetMs,
            isInstrumental = isInstrumental
        )
    }

    private suspend fun persistEntityPreservingOffset(
        trackKey: String,
        song: Song,
        doc: LyricsDocument,
        isUserEdited: Boolean
    ) {
        val existing = dao.getTrackLyrics(trackKey)
        dao.upsertTrackLyrics(
            TrackLyricsEntity(
                trackKey = trackKey,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                rawLyrics = doc.rawText,
                sourceType = doc.sourceType.name,
                sourceLabel = doc.sourceLabel,
                syncType = doc.syncType.name,
                timingOffsetMs = existing?.timingOffsetMs ?: 0L,
                hasCustomOffset = existing?.hasCustomOffset ?: false,
                isUserEdited = isUserEdited,
                isInstrumental = doc.isInstrumental,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun decodeUtfBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }
    }
}
