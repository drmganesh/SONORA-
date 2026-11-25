package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.SonoraPersonalizedRecommendationEngine
import domain.online.UserMusicInteractionSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "SonoraRecCachePrefs"
private const val MAX_STORED_ENTRIES = 120
private const val MAX_RECENT_SHOWN = 64

private val Context.sonoraRecommendDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_personalized_recommendations",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(TAG, "Corrupted recommendation DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

data class CachedSonoraRecommendations(
    val countryCode: String,
    val providerKey: String,
    val discoverLanguageId: String,
    val profileSummary: String,
    val isColdStartDiscovery: Boolean,
    val cachedAtMs: Long,
    val items: List<RecommendedSongItem>
)

/**
 * 100% local-only DataStore persistence for:
 * 1. User recommendation interaction feedback (Dislike, Early Skip, Play Next, Repeated Listening,
 *    and Recently Shown track URLs).
 * 2. Offline-resilient cached recommendation metadata (canonical content URLs & display metadata
 *    only — NEVER temporary streaming URLs, NEVER uploaded to any remote server).
 */
class SonoraRecommendationCachePreferences(private val context: Context) {

    companion object {
        private val KEY_INTERACTIONS_JSON = stringPreferencesKey("sonora_rec_interactions_json")
        private val KEY_CACHED_RECS_JSON = stringPreferencesKey("sonora_rec_cached_items_json")
    }

    private val safeData: Flow<Preferences> = context.sonoraRecommendDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(TAG, "IO error reading recommendation DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val interactionSignalFlow: Flow<UserMusicInteractionSignal> = safeData.map { prefs ->
        decodeInteractions(prefs[KEY_INTERACTIONS_JSON])
    }

    suspend fun getInteractionSignalOnce(): UserMusicInteractionSignal =
        decodeInteractions(safeData.first()[KEY_INTERACTIONS_JSON])

    suspend fun getCachedRecommendationsOnce(): CachedSonoraRecommendations? =
        decodeCachedRecommendations(safeData.first()[KEY_CACHED_RECS_JSON])

    suspend fun recordDislike(trackId: Long, contentUrl: String, artist: String) {
        updateInteractions { current ->
            val normUrl = SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(contentUrl)
            val normArtist = SonoraPersonalizedRecommendationEngine.normalizeKey(artist)
            val nextIds = (current.dislikedTrackIds + trackId).toList().takeLast(MAX_STORED_ENTRIES).toSet()
            val nextUrls = if (normUrl.isNotBlank()) {
                (current.dislikedTrackUrls + normUrl).toList().takeLast(MAX_STORED_ENTRIES).toSet()
            } else {
                current.dislikedTrackUrls
            }
            val nextArtists = current.dislikedArtistCounts.toMutableMap()
            if (normArtist.isNotBlank()) {
                nextArtists[normArtist] = (nextArtists[normArtist] ?: 0) + 1
            }
            current.copy(
                dislikedTrackIds = nextIds,
                dislikedTrackUrls = nextUrls,
                dislikedArtistCounts = trimCounterMap(nextArtists),
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun recordEarlySkip(trackId: Long, artist: String) {
        updateInteractions { current ->
            val normArtist = SonoraPersonalizedRecommendationEngine.normalizeKey(artist)
            val nextTrackSkips = current.skippedTrackCounts.toMutableMap()
            nextTrackSkips[trackId] = (nextTrackSkips[trackId] ?: 0) + 1
            val nextArtistSkips = current.skippedArtistCounts.toMutableMap()
            if (normArtist.isNotBlank()) {
                nextArtistSkips[normArtist] = (nextArtistSkips[normArtist] ?: 0) + 1
            }
            current.copy(
                skippedTrackCounts = trimLongCounterMap(nextTrackSkips),
                skippedArtistCounts = trimCounterMap(nextArtistSkips),
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun recordPlayNext(trackId: Long, artist: String) {
        updateInteractions { current ->
            val normArtist = SonoraPersonalizedRecommendationEngine.normalizeKey(artist)
            val nextIds = (current.playNextTrackIds + trackId).toList().takeLast(MAX_STORED_ENTRIES).toSet()
            val nextArtists = current.playNextArtistCounts.toMutableMap()
            if (normArtist.isNotBlank()) {
                nextArtists[normArtist] = (nextArtists[normArtist] ?: 0) + 1
            }
            current.copy(
                playNextTrackIds = nextIds,
                playNextArtistCounts = trimCounterMap(nextArtists),
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun recordRepeatedListen(trackId: Long, artist: String) {
        updateInteractions { current ->
            val normArtist = SonoraPersonalizedRecommendationEngine.normalizeKey(artist)
            val nextTracks = current.repeatedTrackCounts.toMutableMap()
            nextTracks[trackId] = (nextTracks[trackId] ?: 0) + 1
            val nextArtists = current.repeatedArtistCounts.toMutableMap()
            if (normArtist.isNotBlank()) {
                nextArtists[normArtist] = (nextArtists[normArtist] ?: 0) + 1
            }
            current.copy(
                repeatedTrackCounts = trimLongCounterMap(nextTracks),
                repeatedArtistCounts = trimCounterMap(nextArtists),
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun recordShownTracks(contentUrls: List<String>) {
        if (contentUrls.isEmpty()) return
        updateInteractions { current ->
            val normalizedNew = contentUrls
                .map { SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(it) }
                .filter { it.isNotBlank() }
            val merged = (current.recentlyShownTrackUrls + normalizedNew)
                .distinct()
                .takeLast(MAX_RECENT_SHOWN)
            current.copy(
                recentlyShownTrackUrls = merged
            )
        }
    }

    suspend fun saveCachedRecommendations(cache: CachedSonoraRecommendations) {
        try {
            val encoded = encodeCachedRecommendations(cache)
            context.sonoraRecommendDataStore.edit { prefs ->
                prefs[KEY_CACHED_RECS_JSON] = encoded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving cached recommendations", e)
        }
    }

    suspend fun clearAllForHistoryReset() {
        try {
            context.sonoraRecommendDataStore.edit { prefs ->
                prefs.remove(KEY_INTERACTIONS_JSON)
                prefs.remove(KEY_CACHED_RECS_JSON)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing recommendation cache", e)
        }
    }

    private suspend fun updateInteractions(transform: (UserMusicInteractionSignal) -> UserMusicInteractionSignal) {
        try {
            context.sonoraRecommendDataStore.edit { prefs ->
                val current = decodeInteractions(prefs[KEY_INTERACTIONS_JSON])
                val updated = transform(current)
                prefs[KEY_INTERACTIONS_JSON] = encodeInteractions(updated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating recommendation interaction signals", e)
        }
    }

    private fun trimCounterMap(map: Map<String, Int>): Map<String, Int> {
        if (map.size <= MAX_STORED_ENTRIES) return map
        return map.entries.sortedByDescending { it.value }.take(MAX_STORED_ENTRIES).associate { it.key to it.value }
    }

    private fun trimLongCounterMap(map: Map<Long, Int>): Map<Long, Int> {
        if (map.size <= MAX_STORED_ENTRIES) return map
        return map.entries.sortedByDescending { it.value }.take(MAX_STORED_ENTRIES).associate { it.key to it.value }
    }

    private fun encodeInteractions(signal: UserMusicInteractionSignal): String {
        val root = JSONObject()
        root.put("dislikedIds", JSONArray(signal.dislikedTrackIds.toList()))
        root.put("dislikedUrls", JSONArray(signal.dislikedTrackUrls.toList()))
        root.put("dislikedArtists", JSONObject(signal.dislikedArtistCounts))
        root.put("skippedTracks", JSONObject(signal.skippedTrackCounts.mapKeys { it.key.toString() }))
        root.put("skippedArtists", JSONObject(signal.skippedArtistCounts))
        root.put("playNextIds", JSONArray(signal.playNextTrackIds.toList()))
        root.put("playNextArtists", JSONObject(signal.playNextArtistCounts))
        root.put("repeatedTracks", JSONObject(signal.repeatedTrackCounts.mapKeys { it.key.toString() }))
        root.put("repeatedArtists", JSONObject(signal.repeatedArtistCounts))
        root.put("recentShown", JSONArray(signal.recentlyShownTrackUrls))
        root.put("updatedAt", signal.lastUpdatedAtMs)
        return root.toString()
    }

    private fun decodeInteractions(raw: String?): UserMusicInteractionSignal {
        if (raw.isNullOrBlank()) return UserMusicInteractionSignal()
        return runCatching {
            val root = JSONObject(raw)
            UserMusicInteractionSignal(
                dislikedTrackIds = root.optJSONArray("dislikedIds").toLongSet(),
                dislikedTrackUrls = root.optJSONArray("dislikedUrls").toStringSet(),
                dislikedArtistCounts = root.optJSONObject("dislikedArtists").toStringIntMap(),
                skippedTrackCounts = root.optJSONObject("skippedTracks").toLongIntMap(),
                skippedArtistCounts = root.optJSONObject("skippedArtists").toStringIntMap(),
                playNextTrackIds = root.optJSONArray("playNextIds").toLongSet(),
                playNextArtistCounts = root.optJSONObject("playNextArtists").toStringIntMap(),
                repeatedTrackCounts = root.optJSONObject("repeatedTracks").toLongIntMap(),
                repeatedArtistCounts = root.optJSONObject("repeatedArtists").toStringIntMap(),
                recentlyShownTrackUrls = root.optJSONArray("recentShown").toStringList(),
                lastUpdatedAtMs = root.optLong("updatedAt", 0L)
            )
        }.getOrDefault(UserMusicInteractionSignal())
    }

    private fun encodeCachedRecommendations(cache: CachedSonoraRecommendations): String {
        val root = JSONObject()
        root.put("countryCode", cache.countryCode)
        root.put("providerKey", cache.providerKey)
        root.put("discoverLanguageId", cache.discoverLanguageId)
        root.put("profileSummary", cache.profileSummary)
        root.put("isColdStart", cache.isColdStartDiscovery)
        root.put("cachedAtMs", cache.cachedAtMs)
        val arr = JSONArray()
        cache.items.take(24).forEach { item ->
            val t = item.track
            val obj = JSONObject()
            obj.put("stableId", t.stableId)
            obj.put("serviceId", t.serviceId)
            obj.put("providerKey", t.providerKey)
            obj.put("providerName", t.providerName)
            obj.put("contentUrl", t.contentUrl)
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("artistUrl", t.artistUrl ?: "")
            obj.put("album", t.album)
            obj.put("durationMs", t.durationMs)
            obj.put("artworkUrl", t.artworkUrl ?: "")
            obj.put("viewCount", t.viewCount)
            obj.put("score", item.score)
            obj.put("reasonLabel", item.reasonLabel)
            obj.put("isFamiliar", item.isFamiliarFavorite)
            obj.put("detectedLanguage", item.detectedLanguage ?: "")
            arr.put(obj)
        }
        root.put("items", arr)
        return root.toString()
    }

    private fun decodeCachedRecommendations(raw: String?): CachedSonoraRecommendations? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("items") ?: return null
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val contentUrl = obj.optString("contentUrl", "").trim()
                    val title = obj.optString("title", "").trim()
                    if (contentUrl.isBlank() || title.isBlank()) continue
                    val track = OnlineTrack(
                        stableId = obj.optLong("stableId", 0L),
                        serviceId = obj.optInt("serviceId", 0),
                        providerKey = obj.optString("providerKey", ""),
                        providerName = obj.optString("providerName", "Online"),
                        contentUrl = contentUrl,
                        title = title,
                        artist = obj.optString("artist", ""),
                        artistUrl = obj.optString("artistUrl", "").takeIf { it.isNotBlank() },
                        album = obj.optString("album", ""),
                        durationMs = obj.optLong("durationMs", 0L),
                        artworkUrl = obj.optString("artworkUrl", "").takeIf { it.isNotBlank() },
                        viewCount = obj.optLong("viewCount", -1L),
                        kind = OnlineItemKind.TRACK
                    )
                    add(
                        RecommendedSongItem(
                            track = track,
                            score = obj.optDouble("score", 0.0),
                            reasonLabel = obj.optString("reasonLabel", "Picked for You"),
                            isFamiliarFavorite = obj.optBoolean("isFamiliar", false),
                            detectedLanguage = obj.optString("detectedLanguage", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            if (items.isEmpty()) return null
            CachedSonoraRecommendations(
                countryCode = root.optString("countryCode", "IN"),
                providerKey = root.optString("providerKey", ""),
                discoverLanguageId = root.optString("discoverLanguageId", "all"),
                profileSummary = root.optString("profileSummary", ""),
                isColdStartDiscovery = root.optBoolean("isColdStart", false),
                cachedAtMs = root.optLong("cachedAtMs", 0L),
                items = items
            )
        }.getOrNull()
    }

    private fun JSONArray?.toLongSet(): Set<Long> {
        if (this == null) return emptySet()
        val out = LinkedHashSet<Long>(length())
        for (i in 0 until length()) out.add(optLong(i))
        return out
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        val out = LinkedHashSet<String>(length())
        for (i in 0 until length()) {
            val v = optString(i, "")
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val v = optString(i, "")
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    private fun JSONObject?.toStringIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = optInt(k, 0)
        }
        return out
    }

    private fun JSONObject?.toLongIntMap(): Map<Long, Int> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<Long, Int>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            k.toLongOrNull()?.let { id -> out[id] = optInt(k, 0) }
        }
        return out
    }
}
