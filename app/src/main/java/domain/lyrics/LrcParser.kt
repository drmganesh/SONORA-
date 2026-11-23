package domain.lyrics

/**
 * Pure Kotlin LRC & Enhanced LRC (word-level) parser.
 *
 * Supported formats:
 * - Standard LRC: [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [mm:ss], [hh:mm:ss.xx]
 * - Multiple timestamps per line: [00:12.00][01:04.50]Chorus line
 * - Metadata headers: [ti:...], [ar:...], [al:...], [offset:+/-ms]
 * - Enhanced LRC word tags: [00:12.00]<00:12.00>Hello <00:12.50>world<00:13.20>
 * - Plain text fallback when no valid timestamps exist
 *
 * Guarantees:
 * - Never crashes on malformed timestamps or oversized payloads.
 * - Never fabricates word timestamps when only line timestamps are present.
 */
object LrcParser {

    private const val MAX_INPUT_CHARS = 512 * 1024
    private const val DEFAULT_FINAL_LINE_DURATION_MS = 5_000L
    private const val DEFAULT_FINAL_WORD_DURATION_MS = 600L

    // Matches [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [hh:mm:ss.xx]
    private val lineTimestampRegex = Regex(
        """\[(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,3}))?\]"""
    )

    // Matches Enhanced LRC word timestamps: <mm:ss.xx>, <mm:ss.xxx>, <mm:ss:xx>, <mm:ss>
    private val wordTimestampRegex = Regex(
        """<(\d{1,3}):(\d{1,2})(?::(\d{1,2}))?(?:[.:](\d{1,3}))?>"""
    )

    // Matches metadata tags like [ti:Song Title], [ar:Artist], [offset:+250]
    private val metadataTagRegex = Regex("""^\[([a-zA-Z]{1,12}):(.*)\]$""")

    // Strips completely broken bracket tags like [xx:yy] only if clearly malformed timestamp attempt
    private val malformedTimestampPrefixRegex = Regex("""^\[[\d:.\-\s]{1,14}[^\]\d]*\]\s*""")

    data class ParseResult(
        val lines: List<SyncedLyricLine>,
        val syncType: LyricsSyncType,
        val embeddedOffsetMs: Long,
        val metadata: Map<String, String>
    )

    fun parse(rawInput: String?): ParseResult {
        if (rawInput.isNullOrBlank()) {
            return ParseResult(
                lines = emptyList(),
                syncType = LyricsSyncType.NONE,
                embeddedOffsetMs = 0L,
                metadata = emptyMap()
            )
        }

        val safeInput = if (rawInput.length > MAX_INPUT_CHARS) {
            rawInput.substring(0, MAX_INPUT_CHARS)
        } else {
            rawInput
        }

        var embeddedOffsetMs = 0L
        val metadata = mutableMapOf<String, String>()
        val rawTimedEntries = mutableListOf<RawTimedLine>()
        val rawPlainEntries = mutableListOf<String>()

        for (rawLine in safeInput.lineSequence()) {
            val trimmed = rawLine.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty()) continue

            // Check metadata tags first (only if line has no valid timestamp matches)
            val lineMatches = lineTimestampRegex.findAll(trimmed).toList()
            if (lineMatches.isEmpty()) {
                val metaMatch = metadataTagRegex.matchEntire(trimmed)
                if (metaMatch != null) {
                    val key = metaMatch.groupValues[1].lowercase()
                    val value = metaMatch.groupValues[2].trim()
                    metadata[key] = value
                    if (key == "offset") {
                        embeddedOffsetMs = value.toLongOrNull()
                            ?.coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
                            ?: 0L
                    }
                    continue
                }

                // Gracefully strip malformed bracketed timestamp tokens if present
                val cleanedPlain = if (trimmed.startsWith("[") && trimmed.contains("]")) {
                    val afterBracket = trimmed.substringAfter("]").trim()
                    if (afterBracket.isNotBlank() && malformedTimestampPrefixRegex.containsMatchIn(trimmed)) {
                        afterBracket
                    } else if (trimmed.endsWith("]") && trimmed.indexOf(']') == trimmed.lastIndex) {
                        // Standalone broken tag like [bad:time] -> skip
                        continue
                    } else {
                        trimmed
                    }
                } else {
                    trimmed
                }

                if (cleanedPlain.isNotBlank()) {
                    rawPlainEntries.add(cleanedPlain)
                }
                continue
            }

            // Collect all valid leading/repeating timestamps on this line
            val leadingTimestamps = mutableListOf<Long>()
            var cursor = 0
            for (match in lineMatches) {
                if (match.range.first == cursor || trimmed.substring(cursor, match.range.first).isBlank()) {
                    val parsedMs = parseTimestampGroups(
                        g1 = match.groupValues[1],
                        g2 = match.groupValues[2],
                        g3 = match.groupValues[3],
                        frac = match.groupValues[4]
                    )
                    if (parsedMs != null) {
                        leadingTimestamps.add(parsedMs)
                        cursor = match.range.last + 1
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            if (leadingTimestamps.isEmpty()) {
                // Invalid numeric bounds (e.g. seconds >= 60 or overflow)
                val fallback = trimmed.replace(lineTimestampRegex, "").trim()
                if (fallback.isNotBlank()) {
                    rawPlainEntries.add(fallback)
                }
                continue
            }

            val body = trimmed.substring(cursor).trim()
            val parsedWords = parseEnhancedWords(body)
            val cleanText = if (parsedWords.isNotEmpty()) {
                parsedWords.joinToString(separator = "") { it.text }.trim()
            } else {
                // Remove any stray malformed angle-bracket timestamps if present
                body.replace(wordTimestampRegex, "").trim()
            }

            for (startMs in leadingTimestamps) {
                rawTimedEntries.add(
                    RawTimedLine(
                        startTimeMs = startMs,
                        text = cleanText,
                        words = parsedWords
                    )
                )
            }
        }

        // If valid timed lines exist, produce chronological synchronized lyrics
        if (rawTimedEntries.isNotEmpty()) {
            val sorted = rawTimedEntries
                .sortedBy { it.startTimeMs }
                // Collapse consecutive empty instrumental markers that share exact timestamp
                .filterIndexed { idx, item ->
                    if (item.text.isNotBlank()) true
                    else idx == 0 || rawTimedEntries[idx - 1].text.isNotBlank()
                }
                .filter { it.text.isNotBlank() }

            if (sorted.isEmpty()) {
                return ParseResult(
                    lines = emptyList(),
                    syncType = LyricsSyncType.NONE,
                    embeddedOffsetMs = embeddedOffsetMs,
                    metadata = metadata
                )
            }

            var hasAnyWordTiming = false
            val finalLines = sorted.mapIndexed { idx, entry ->
                val nextStart = sorted.getOrNull(idx + 1)?.startTimeMs
                val lineEndMs = if (nextStart != null && nextStart > entry.startTimeMs) {
                    nextStart
                } else {
                    entry.startTimeMs + DEFAULT_FINAL_LINE_DURATION_MS
                }

                val finalizedWords = if (entry.words.isNotEmpty()) {
                    hasAnyWordTiming = true
                    finalizeWordEndTimes(entry.words, lineEndMs)
                } else {
                    emptyList()
                }

                SyncedLyricLine(
                    index = idx,
                    startTimeMs = entry.startTimeMs,
                    endTimeMs = lineEndMs,
                    text = entry.text,
                    words = finalizedWords
                )
            }

            val syncType = if (hasAnyWordTiming) LyricsSyncType.WORD_SYNCED else LyricsSyncType.LINE_SYNCED
            return ParseResult(
                lines = finalLines,
                syncType = syncType,
                embeddedOffsetMs = embeddedOffsetMs,
                metadata = metadata
            )
        }

        // Otherwise fallback to unsynchronized plain text lines
        val plainLines = rawPlainEntries.mapIndexed { idx, text ->
            SyncedLyricLine(
                index = idx,
                startTimeMs = -1L,
                endTimeMs = -1L,
                text = text,
                words = emptyList()
            )
        }

        return ParseResult(
            lines = plainLines,
            syncType = if (plainLines.isNotEmpty()) LyricsSyncType.UNSYNCED else LyricsSyncType.NONE,
            embeddedOffsetMs = embeddedOffsetMs,
            metadata = metadata
        )
    }

    /**
     * Parses Enhanced LRC word tags like:
     * `<00:12.00>Hello <00:12.45>darkness <00:13.10>my <00:13.40>old <00:13.80>friend<00:14.50>`
     * Returns empty list if no valid word timestamps are present (never fabricates timing).
     */
    private fun parseEnhancedWords(body: String): List<LyricWord> {
        val matches = wordTimestampRegex.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        val result = mutableListOf<LyricWord>()
        for (i in matches.indices) {
            val currentMatch = matches[i]
            val startMs = parseTimestampGroups(
                g1 = currentMatch.groupValues[1],
                g2 = currentMatch.groupValues[2],
                g3 = currentMatch.groupValues[3],
                frac = currentMatch.groupValues[4]
            ) ?: continue

            val textStart = currentMatch.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            val nextStartMs = if (i + 1 < matches.size) {
                parseTimestampGroups(
                    g1 = matches[i + 1].groupValues[1],
                    g2 = matches[i + 1].groupValues[2],
                    g3 = matches[i + 1].groupValues[3],
                    frac = matches[i + 1].groupValues[4]
                )
            } else {
                null
            }

            if (textStart <= textEnd && textStart <= body.length) {
                val segmentText = body.substring(textStart, textEnd.coerceAtMost(body.length))
                if (segmentText.isNotEmpty()) {
                    val tentativeEnd = nextStartMs?.takeIf { it > startMs } ?: (startMs + DEFAULT_FINAL_WORD_DURATION_MS)
                    result.add(
                        LyricWord(
                            startTimeMs = startMs,
                            endTimeMs = tentativeEnd,
                            text = segmentText
                        )
                    )
                }
            }
        }
        return result
    }

    private fun finalizeWordEndTimes(words: List<LyricWord>, lineEndMs: Long): List<LyricWord> {
        if (words.isEmpty()) return emptyList()
        return words.mapIndexed { idx, word ->
            val nextWordStart = words.getOrNull(idx + 1)?.startTimeMs
            val computedEnd = when {
                nextWordStart != null && nextWordStart > word.startTimeMs -> nextWordStart
                word.endTimeMs > word.startTimeMs && word.endTimeMs <= lineEndMs -> word.endTimeMs
                lineEndMs > word.startTimeMs -> lineEndMs
                else -> word.startTimeMs + DEFAULT_FINAL_WORD_DURATION_MS
            }
            word.copy(endTimeMs = computedEnd)
        }
    }

    private fun parseTimestampGroups(
        g1: String,
        g2: String,
        g3: String,
        frac: String
    ): Long? {
        return try {
            val hours: Long
            val minutes: Long
            val seconds: Long
            if (g3.isNotEmpty()) {
                hours = g1.toLongOrNull() ?: return null
                minutes = g2.toLongOrNull() ?: return null
                seconds = g3.toLongOrNull() ?: return null
                if (minutes !in 0L..59L || seconds !in 0L..59L) return null
            } else {
                hours = 0L
                minutes = g1.toLongOrNull() ?: return null
                seconds = g2.toLongOrNull() ?: return null
                if (minutes < 0L || seconds !in 0L..59L) return null
            }
            val millis = when (frac.length) {
                0 -> 0L
                1 -> (frac.toLongOrNull() ?: return null) * 100L
                2 -> (frac.toLongOrNull() ?: return null) * 10L
                else -> frac.take(3).toLongOrNull() ?: return null
            }
            ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats milliseconds as an LRC timestamp `[mm:ss.xx]`.
     */
    fun formatLrcTimestamp(positionMs: Long): String {
        val safe = positionMs.coerceAtLeast(0L)
        val totalSeconds = safe / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val centis = (safe % 1000L) / 10L
        return String.format(java.util.Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
    }

    private data class RawTimedLine(
        val startTimeMs: Long,
        val text: String,
        val words: List<LyricWord>
    )
}
