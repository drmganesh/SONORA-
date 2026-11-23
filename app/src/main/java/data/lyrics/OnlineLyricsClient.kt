package data.lyrics

import domain.lyrics.LyricsSearchCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Online lyrics client powered by the open, public LRCLIB API (https://lrclib.net).
 *
 * Strictly validates returned track metadata (title/artist/duration) before auto-matching
 * so SONORA never silently attaches lyrics belonging to a different track.
 */
interface OnlineLyricsProvider {
    suspend fun fetchExactLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): LyricsSearchCandidate?

    suspend fun searchLyrics(
        query: String,
        trackName: String? = null,
        artistName: String? = null
    ): List<LyricsSearchCandidate>
}

class LrcLibOnlineLyricsClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://lrclib.net/api"
) : OnlineLyricsProvider {

    override suspend fun fetchExactLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): LyricsSearchCandidate? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackMetadata(title)
        val cleanArtist = cleanArtistMetadata(artist)
        if (cleanTitle.isBlank()) return@withContext null

        val durationSec = if (durationMs > 0L) (durationMs / 1000L).toInt() else 0

        // 1. Try exact endpoint first when artist & duration are known
        if (cleanArtist.isNotBlank() && durationSec > 0) {
            val exactUrlBuilder = "$baseUrl/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle)
                .addQueryParameter("artist_name", cleanArtist)
                .addQueryParameter("duration", durationSec.toString())
            if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true) && !album.equals("Online Stream", ignoreCase = true)) {
                exactUrlBuilder.addQueryParameter("album_name", album.trim())
            }

            val exactBody = executeGet(exactUrlBuilder.build().toString())
            if (exactBody != null) {
                val parsed = parseCandidateObject(exactBody)
                if (parsed != null && isConfidentMatch(parsed, cleanTitle, cleanArtist, durationSec)) {
                    return@withContext parsed
                }
            }
        }

        // 2. Fall back to search endpoint with strict validation
        val searchCandidates = searchLyrics(
            query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle,
            trackName = cleanTitle,
            artistName = cleanArtist.takeIf { it.isNotBlank() }
        )

        searchCandidates
            .filter { isConfidentMatch(it, cleanTitle, cleanArtist, durationSec) }
            .sortedByDescending { candidateScore(it, cleanTitle, cleanArtist, durationSec) }
            .firstOrNull()
    }

    override suspend fun searchLyrics(
        query: String,
        trackName: String?,
        artistName: String?
    ): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        val trimmedTrack = trackName?.trim().orEmpty()
        val trimmedArtist = artistName?.trim().orEmpty()
        if (trimmedQuery.isBlank() && trimmedTrack.isBlank()) return@withContext emptyList()

        val urlBuilder = "$baseUrl/search".toHttpUrl().newBuilder()
        if (trimmedTrack.isNotBlank()) {
            urlBuilder.addQueryParameter("track_name", trimmedTrack)
            if (trimmedArtist.isNotBlank()) {
                urlBuilder.addQueryParameter("artist_name", trimmedArtist)
            }
        } else {
            urlBuilder.addQueryParameter("q", trimmedQuery)
        }

        val body = executeGet(urlBuilder.build().toString()) ?: return@withContext emptyList()
        parseCandidateArray(body)
    }

    private fun executeGet(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SONORA/1.0 (Android Music Player)")
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCandidateObject(json: String): LyricsSearchCandidate? {
        return try {
            val obj = JSONObject(json)
            fromJsonObject(obj)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCandidateArray(json: String): List<LyricsSearchCandidate> {
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<LyricsSearchCandidate>()
            for (i in 0 until arr.length().coerceAtMost(25)) {
                val obj = arr.optJSONObject(i) ?: continue
                fromJsonObject(obj)?.let { result.add(it) }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fromJsonObject(obj: JSONObject): LyricsSearchCandidate? {
        val synced = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val instrumental = obj.optBoolean("instrumental", false)
        if (synced == null && plain == null && !instrumental) return null

        val id = obj.opt("id")?.toString() ?: "${obj.optString("trackName")}_${obj.optString("artistName")}"
        return LyricsSearchCandidate(
            id = id,
            title = obj.optString("trackName", obj.optString("name", "")).trim(),
            artist = obj.optString("artistName", "").trim(),
            album = obj.optString("albumName", "").trim(),
            durationSec = obj.optDouble("duration", 0.0).toInt().coerceAtLeast(0),
            syncedLyrics = synced,
            plainLyrics = plain,
            instrumental = instrumental,
            sourceLabel = "LRCLIB"
        )
    }

    private fun isConfidentMatch(
        candidate: LyricsSearchCandidate,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationSec: Int
    ): Boolean {
        val candTitle = normalizeForMatch(candidate.title)
        val expTitle = normalizeForMatch(expectedTitle)
        if (candTitle.isBlank() || expTitle.isBlank()) return false

        val titleMatches = candTitle == expTitle ||
            candTitle.contains(expTitle) ||
            expTitle.contains(candTitle)
        if (!titleMatches) return false

        if (expectedArtist.isNotBlank() && !expectedArtist.equals("Unknown Artist", ignoreCase = true)) {
            val candArtist = normalizeForMatch(candidate.artist)
            val expArtist = normalizeForMatch(expectedArtist)
            val artistWords = expArtist.split(" ").filter { it.length >= 2 }
            val artistMatches = candArtist == expArtist ||
                candArtist.contains(expArtist) ||
                expArtist.contains(candArtist) ||
                (artistWords.isNotEmpty() && artistWords.any { candArtist.contains(it) })
            if (!artistMatches) return false
        }

        // Prevent attaching lyrics from a completely different version/song if duration differs wildly
        if (expectedDurationSec > 20 && candidate.durationSec > 20) {
            if (abs(candidate.durationSec - expectedDurationSec) > 12) {
                return false
            }
        }
        return true
    }

    private fun candidateScore(
        candidate: LyricsSearchCandidate,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationSec: Int
    ): Int {
        var score = 0
        if (candidate.hasSyncedLyrics) score += 100
        if (normalizeForMatch(candidate.title) == normalizeForMatch(expectedTitle)) score += 30
        if (normalizeForMatch(candidate.artist) == normalizeForMatch(expectedArtist)) score += 25
        if (expectedDurationSec > 0 && candidate.durationSec > 0) {
            val diff = abs(candidate.durationSec - expectedDurationSec)
            score += (20 - diff).coerceAtLeast(0)
        }
        return score
    }

    companion object {
        private val noiseSuffixRegex = Regex(
            """\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|lyrics?|audio|hd|4k|hq|visualizer|mv|remastered(?:\s*\d{4})?)[\)\]]""",
            RegexOption.IGNORE_CASE
        )

        fun cleanTrackMetadata(raw: String): String {
            return raw.replace(noiseSuffixRegex, "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        fun cleanArtistMetadata(raw: String): String {
            if (raw.equals("Unknown Artist", ignoreCase = true) || raw.equals("<unknown>", ignoreCase = true)) {
                return ""
            }
            return raw.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*(?:ft\.|feat\.|featuring).*""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        private fun normalizeForMatch(text: String): String {
            return text.lowercase()
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(7, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }
}
