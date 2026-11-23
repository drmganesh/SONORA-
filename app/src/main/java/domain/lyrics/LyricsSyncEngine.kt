package domain.lyrics

/**
 * Word-level karaoke highlight progress derived strictly from authentic Enhanced LRC timestamps.
 */
data class WordHighlightState(
    val activeWordIndex: Int = -1,
    val activeWordProgress: Float = 0f,
    val isWordSynced: Boolean = false
) {
    companion object {
        val NONE = WordHighlightState()
    }
}

/**
 * Stateless, O(log N) synchronization engine driven directly by Media3 / AudioEngine playback position.
 * Never relies on independent drift timers.
 */
object LyricsSyncEngine {

    /**
     * Applies user timing offset + embedded LRC offset to the real playback position.
     * Positive offset advances lyrics (lyrics ahead of audio).
     * Negative offset delays lyrics (lyrics behind audio).
     */
    fun effectivePositionMs(
        playbackPositionMs: Long,
        userOffsetMs: Long,
        embeddedOffsetMs: Long = 0L
    ): Long {
        val totalOffset = (userOffsetMs + embeddedOffsetMs)
            .coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
        return (playbackPositionMs.coerceAtLeast(0L) + totalOffset).coerceAtLeast(0L)
    }

    /**
     * Binary search for the active lyric line index at [playbackPositionMs] with [userOffsetMs].
     * Returns -1 if lines are empty, unsynchronized, or playback is before the first lyric line.
     */
    fun findActiveLineIndex(
        lines: List<SyncedLyricLine>,
        playbackPositionMs: Long,
        userOffsetMs: Long = 0L,
        embeddedOffsetMs: Long = 0L
    ): Int {
        if (lines.isEmpty()) return -1
        if (!lines.first().isTimed) return -1

        val effectivePos = effectivePositionMs(playbackPositionMs, userOffsetMs, embeddedOffsetMs)
        if (effectivePos < lines.first().startTimeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val lineStart = lines[mid].startTimeMs
            if (lineStart <= effectivePos) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Computes progressive word highlight state for an Enhanced LRC line.
     * Strictly returns [WordHighlightState.NONE] if the line has no authentic word timestamps.
     */
    fun findWordHighlightState(
        line: SyncedLyricLine,
        playbackPositionMs: Long,
        userOffsetMs: Long = 0L,
        embeddedOffsetMs: Long = 0L
    ): WordHighlightState {
        if (!line.hasWordTiming) return WordHighlightState.NONE
        val words = line.words
        val effectivePos = effectivePositionMs(playbackPositionMs, userOffsetMs, embeddedOffsetMs)

        if (effectivePos < words.first().startTimeMs) {
            return WordHighlightState(activeWordIndex = -1, activeWordProgress = 0f, isWordSynced = true)
        }

        var low = 0
        var high = words.lastIndex
        var matchedIdx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (words[mid].startTimeMs <= effectivePos) {
                matchedIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (matchedIdx < 0) {
            return WordHighlightState(activeWordIndex = -1, activeWordProgress = 0f, isWordSynced = true)
        }

        val word = words[matchedIdx]
        val duration = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
        val progress = ((effectivePos - word.startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        return WordHighlightState(
            activeWordIndex = matchedIdx,
            activeWordProgress = progress,
            isWordSynced = true
        )
    }

    /**
     * Calculates the ExoPlayer seek target in milliseconds when the user taps [line],
     * accounting for active timing offsets so playback lands cleanly on that line.
     */
    fun calculateSeekPositionForLine(
        line: SyncedLyricLine,
        userOffsetMs: Long = 0L,
        embeddedOffsetMs: Long = 0L
    ): Long {
        if (!line.isTimed) return 0L
        val totalOffset = (userOffsetMs + embeddedOffsetMs)
            .coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
        return (line.startTimeMs - totalOffset).coerceAtLeast(0L)
    }
}
