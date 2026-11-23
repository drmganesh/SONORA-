package domain.analytics

import com.example.model.Song
import data.analytics.ListeningEventEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class AnalyticsTimeRange(val label: String, val days: Int?) {
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    ALL_TIME("All Time", null)
}

data class RankedStatItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val songId: Long? = null,
    val playCount: Int,
    val listenedMs: Long,
    val isOnline: Boolean = false
)

data class ListeningChartPoint(
    val key: String,
    val label: String,
    val listenedMs: Long,
    val playCount: Int
)

data class ListeningTimelineItem(
    val id: Long,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val isOnline: Boolean,
    val listenedMs: Long,
    val endedAtMs: Long
)

data class ListeningAnalyticsDashboardState(
    val range: AnalyticsTimeRange = AnalyticsTimeRange.LAST_30_DAYS,
    val totalListeningMs: Long = 0L,
    val totalSongsPlayed: Int = 0,
    val uniqueTracksPlayed: Int = 0,
    val mostPlayedSong: RankedStatItem? = null,
    val mostPlayedArtist: RankedStatItem? = null,
    val mostPlayedAlbum: RankedStatItem? = null,
    val mostPlayedGenre: RankedStatItem? = null,
    val offlineListeningMs: Long = 0L,
    val onlineListeningMs: Long = 0L,
    val dailyActivity: List<ListeningChartPoint> = emptyList(),
    val weeklyActivity: List<ListeningChartPoint> = emptyList(),
    val monthlyActivity: List<ListeningChartPoint> = emptyList(),
    val topArtists: List<RankedStatItem> = emptyList(),
    val topTracks: List<RankedStatItem> = emptyList(),
    val topAlbums: List<RankedStatItem> = emptyList(),
    val timeline: List<ListeningTimelineItem> = emptyList()
) {
    val isEmpty: Boolean
        get() = totalListeningMs <= 0L && totalSongsPlayed == 0
}

/**
 * Pure analytics calculator that derives all dashboard KPIs, rankings, charts,
 * and offline/online breakdowns from local [ListeningEventEntity] rows.
 */
object ListeningAnalyticsCalculator {

    private const val DAY_MS = 86_400_000L

    fun dayEpochFor(timestampMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / DAY_MS
    }

    fun calculate(
        allEvents: List<ListeningEventEntity>,
        range: AnalyticsTimeRange,
        nowMs: Long = System.currentTimeMillis()
    ): ListeningAnalyticsDashboardState {
        val cutoffMs = range.days?.let { days ->
            val startOfToday = startOfDayMs(nowMs)
            startOfToday - (days - 1).coerceAtLeast(0) * DAY_MS
        } ?: Long.MIN_VALUE

        val filtered = allEvents.filter { it.listenedMs > 0L && it.endedAtMs >= cutoffMs }
        if (filtered.isEmpty()) {
            return ListeningAnalyticsDashboardState(
                range = range,
                dailyActivity = buildDailyBuckets(emptyList(), range, nowMs),
                weeklyActivity = buildWeeklyBuckets(emptyList(), nowMs),
                monthlyActivity = buildMonthlyBuckets(emptyList(), nowMs)
            )
        }

        val totalListeningMs = filtered.sumOf { it.listenedMs }
        val offlineListeningMs = filtered.filter { !it.isOnline }.sumOf { it.listenedMs }
        val onlineListeningMs = filtered.filter { it.isOnline }.sumOf { it.listenedMs }

        // Each distinct play sessionToken counts once as a song play (prevents double-counting checkpoints).
        val sessionsByToken = filtered.groupBy { it.sessionToken }
        val totalSongsPlayed = sessionsByToken.size
        val uniqueTracksPlayed = filtered.map { it.songId }.toSet().size

        val topTracks = filtered
            .groupBy { it.songId }
            .map { (songId, events) ->
                val sample = events.maxBy { it.endedAtMs }
                val plays = events.map { it.sessionToken }.toSet().size
                RankedStatItem(
                    id = "track_$songId",
                    title = sample.title.ifBlank { "Unknown Track" },
                    subtitle = sample.artist.ifBlank { "Unknown Artist" },
                    artworkUrl = sample.artworkUrl,
                    songId = songId,
                    playCount = plays,
                    listenedMs = events.sumOf { it.listenedMs },
                    isOnline = sample.isOnline
                )
            }
            .sortedWith(compareByDescending<RankedStatItem> { it.listenedMs }.thenByDescending { it.playCount })
            .take(10)

        val topArtists = filtered
            .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown Artist", ignoreCase = true) }
            .groupBy { it.artist.trim().lowercase(Locale.ROOT) }
            .map { (key, events) ->
                val sample = events.maxBy { it.endedAtMs }
                val plays = events.map { it.sessionToken }.toSet().size
                val uniqueCount = events.map { it.songId }.toSet().size
                RankedStatItem(
                    id = "artist_$key",
                    title = sample.artist.trim(),
                    subtitle = "$uniqueCount ${if (uniqueCount == 1) "track" else "tracks"}",
                    artworkUrl = sample.artworkUrl,
                    songId = sample.songId,
                    playCount = plays,
                    listenedMs = events.sumOf { it.listenedMs },
                    isOnline = sample.isOnline
                )
            }
            .sortedWith(compareByDescending<RankedStatItem> { it.listenedMs }.thenByDescending { it.playCount })
            .take(10)

        val topAlbums = filtered
            .filter { it.album.isNotBlank() && !it.album.equals("Unknown Album", ignoreCase = true) }
            .groupBy { "${it.album.trim().lowercase(Locale.ROOT)}|${it.artist.trim().lowercase(Locale.ROOT)}" }
            .map { (key, events) ->
                val sample = events.maxBy { it.endedAtMs }
                val plays = events.map { it.sessionToken }.toSet().size
                RankedStatItem(
                    id = "album_$key",
                    title = sample.album.trim(),
                    subtitle = sample.artist.ifBlank { "Unknown Artist" },
                    artworkUrl = sample.artworkUrl,
                    songId = sample.songId,
                    playCount = plays,
                    listenedMs = events.sumOf { it.listenedMs },
                    isOnline = sample.isOnline
                )
            }
            .sortedWith(compareByDescending<RankedStatItem> { it.listenedMs }.thenByDescending { it.playCount })
            .take(10)

        val topGenres = filtered
            .filter {
                val g = it.genre.trim()
                g.isNotBlank() &&
                    !g.equals("Unknown", ignoreCase = true) &&
                    !g.equals("Unknown Genre", ignoreCase = true)
            }
            .groupBy { it.genre.trim().lowercase(Locale.ROOT) }
            .map { (key, events) ->
                val sample = events.maxBy { it.endedAtMs }
                val plays = events.map { it.sessionToken }.toSet().size
                RankedStatItem(
                    id = "genre_$key",
                    title = sample.genre.trim(),
                    subtitle = "$plays ${if (plays == 1) "play" else "plays"}",
                    playCount = plays,
                    listenedMs = events.sumOf { it.listenedMs }
                )
            }
            .sortedWith(compareByDescending<RankedStatItem> { it.listenedMs }.thenByDescending { it.playCount })

        val timeline = sessionsByToken.values
            .map { sessionEvents ->
                val latest = sessionEvents.maxBy { it.endedAtMs }
                ListeningTimelineItem(
                    id = latest.id,
                    songId = latest.songId,
                    title = latest.title.ifBlank { "Unknown Track" },
                    artist = latest.artist.ifBlank { "Unknown Artist" },
                    album = latest.album,
                    artworkUrl = latest.artworkUrl,
                    isOnline = latest.isOnline,
                    listenedMs = sessionEvents.sumOf { it.listenedMs },
                    endedAtMs = latest.endedAtMs
                )
            }
            .sortedByDescending { it.endedAtMs }
            .take(40)

        return ListeningAnalyticsDashboardState(
            range = range,
            totalListeningMs = totalListeningMs,
            totalSongsPlayed = totalSongsPlayed,
            uniqueTracksPlayed = uniqueTracksPlayed,
            mostPlayedSong = topTracks.firstOrNull(),
            mostPlayedArtist = topArtists.firstOrNull(),
            mostPlayedAlbum = topAlbums.firstOrNull(),
            mostPlayedGenre = topGenres.firstOrNull(),
            offlineListeningMs = offlineListeningMs,
            onlineListeningMs = onlineListeningMs,
            dailyActivity = buildDailyBuckets(filtered, range, nowMs),
            weeklyActivity = buildWeeklyBuckets(filtered, nowMs),
            monthlyActivity = buildMonthlyBuckets(filtered, nowMs),
            topArtists = topArtists,
            topTracks = topTracks,
            topAlbums = topAlbums,
            timeline = timeline
        )
    }

    private fun startOfDayMs(timestampMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun buildDailyBuckets(
        events: List<ListeningEventEntity>,
        range: AnalyticsTimeRange,
        nowMs: Long
    ): List<ListeningChartPoint> {
        val bucketCount = when (range) {
            AnalyticsTimeRange.LAST_7_DAYS -> 7
            AnalyticsTimeRange.LAST_30_DAYS -> 14
            AnalyticsTimeRange.LAST_90_DAYS -> 14
            AnalyticsTimeRange.ALL_TIME -> 14
        }
        val todayStart = startOfDayMs(nowMs)
        val dayFmt = SimpleDateFormat(if (bucketCount <= 7) "EEE" else "d MMM", Locale.getDefault())
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val eventsByDay = events.groupBy { keyFmt.format(Date(it.endedAtMs)) }
        return (bucketCount - 1 downTo 0).map { offset ->
            val dayMs = todayStart - offset * DAY_MS
            val date = Date(dayMs)
            val key = keyFmt.format(date)
            val dayEvents = eventsByDay[key].orEmpty()
            ListeningChartPoint(
                key = key,
                label = dayFmt.format(date),
                listenedMs = dayEvents.sumOf { it.listenedMs },
                playCount = dayEvents.map { it.sessionToken }.toSet().size
            )
        }
    }

    private fun buildWeeklyBuckets(
        events: List<ListeningEventEntity>,
        nowMs: Long
    ): List<ListeningChartPoint> {
        val weekCount = 8
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMs(nowMs)
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val currentWeekStart = cal.timeInMillis
        val labelFmt = SimpleDateFormat("d MMM", Locale.getDefault())

        return (weekCount - 1 downTo 0).map { weeksAgo ->
            val start = currentWeekStart - weeksAgo * 7L * DAY_MS
            val end = start + 7L * DAY_MS
            val bucketEvents = events.filter { it.endedAtMs in start until end }
            ListeningChartPoint(
                key = "week_$start",
                label = labelFmt.format(Date(start)),
                listenedMs = bucketEvents.sumOf { it.listenedMs },
                playCount = bucketEvents.map { it.sessionToken }.toSet().size
            )
        }
    }

    private fun buildMonthlyBuckets(
        events: List<ListeningEventEntity>,
        nowMs: Long
    ): List<ListeningChartPoint> {
        val monthCount = 6
        val labelFmt = SimpleDateFormat("MMM", Locale.getDefault())
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val eventsByMonth = events.groupBy { keyFmt.format(Date(it.endedAtMs)) }

        return (monthCount - 1 downTo 0).map { monthsAgo ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -monthsAgo)
            }
            val date = cal.time
            val key = keyFmt.format(date)
            val bucketEvents = eventsByMonth[key].orEmpty()
            ListeningChartPoint(
                key = key,
                label = labelFmt.format(date),
                listenedMs = bucketEvents.sumOf { it.listenedMs },
                playCount = bucketEvents.map { it.sessionToken }.toSet().size
            )
        }
    }

    fun formatListeningDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            totalMinutes > 0 -> "${totalMinutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

/**
 * Monotonic, deduplicated active-playback time tracker.
 * - Counts ONLY elapsed playback time while [isActivelyPlaying] is true.
 * - Never counts paused time.
 * - Never double-counts across UI recompositions, screen switches, or service reconnects.
 */
class ListeningTimeTracker(
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private var activeSong: Song? = null
    private var activeSessionToken: String = ""
    private var isActivelyPlaying: Boolean = false
    private var segmentStartMs: Long = 0L
    private var hasEmittedSessionStart: Boolean = false

    fun onTrackStarted(song: Song): ListeningEventEntity? = synchronized(lock) {
        val flushed = flushLocked(markPaused = true)
        activeSong = song
        activeSessionToken = "${song.id}_${UUID.randomUUID().toString().take(8)}"
        isActivelyPlaying = true
        segmentStartMs = clockMs()
        hasEmittedSessionStart = false
        flushed
    }

    fun onPlaybackResumed(song: Song?): ListeningEventEntity? = synchronized(lock) {
        if (song == null) return@synchronized null
        val cur = activeSong
        if (cur == null || cur.id != song.id) {
            return@synchronized onTrackStarted(song)
        }
        if (!isActivelyPlaying) {
            isActivelyPlaying = true
            segmentStartMs = clockMs()
        }
        null
    }

    fun onPlaybackPausedOrStopped(): ListeningEventEntity? = synchronized(lock) {
        flushLocked(markPaused = true)
    }

    /**
     * Periodically checkpoints active listening time without resetting the play session token,
     * so crashes or task removal preserve listening progress without double-counting plays.
     */
    fun checkpointIfPlaying(minElapsedMs: Long = 10_000L): ListeningEventEntity? = synchronized(lock) {
        if (!isActivelyPlaying || activeSong == null || segmentStartMs <= 0L) return@synchronized null
        val now = clockMs()
        val elapsed = (now - segmentStartMs).coerceAtLeast(0L)
        if (elapsed < minElapsedMs) return@synchronized null
        flushLocked(markPaused = false)
    }

    fun reset() = synchronized(lock) {
        activeSong = null
        activeSessionToken = ""
        isActivelyPlaying = false
        segmentStartMs = 0L
        hasEmittedSessionStart = false
    }

    private fun flushLocked(markPaused: Boolean): ListeningEventEntity? {
        val song = activeSong
        val wasPlaying = isActivelyPlaying
        val start = segmentStartMs
        if (markPaused) {
            isActivelyPlaying = false
        }
        if (!wasPlaying || song == null || start <= 0L) return null
        val now = clockMs()
        val elapsed = (now - start).coerceIn(0L, 6 * 3_600_000L)
        segmentStartMs = if (markPaused) 0L else now
        if (elapsed < 1_000L) return null

        val isStart = !hasEmittedSessionStart
        hasEmittedSessionStart = true
        return ListeningEventEntity(
            sessionToken = activeSessionToken,
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre,
            isOnline = song.isOnline,
            providerKey = song.providerKey,
            artworkUrl = song.artworkUrl,
            listenedMs = elapsed,
            startedAtMs = start,
            endedAtMs = now,
            dayEpoch = ListeningAnalyticsCalculator.dayEpochFor(now),
            isPlayStart = isStart
        )
    }
}
