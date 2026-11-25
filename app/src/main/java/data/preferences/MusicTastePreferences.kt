package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.SonoraApplication
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTasteOnboardingState
import domain.onboarding.MusicTasteProfile
import domain.onboarding.MusicTasteSwipeDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.musicTasteDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_music_taste",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Local-only DataStore persistence for SONORA's Personalized Music Taste Onboarding
 * and Profile -> Music Taste preferences.
 *
 * Privacy & Update Safety:
 * - Strictly device-local (no account, no login, no cloud sync).
 * - Never triggers onboarding after an app update for pre-existing installations.
 * - Resetting Music Taste clears ONLY music taste preferences and never touches the
 *   user's music library, playlists, downloads, or playback history.
 */
class MusicTastePreferences(
    context: Context,
    private val dataStoreOverride: DataStore<Preferences>? = null,
    private val existingInstallationDetector: (() -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val store: DataStore<Preferences>
        get() = dataStoreOverride ?: appContext.musicTasteDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "MusicTastePreferences"
        private const val MAX_STORED_ARTISTS = 40
        private const val MAX_STORED_DISCOVERY_TRACKS = 60

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("music_taste_onboarding_completed")
        private val KEY_SELECTED_ARTISTS_JSON = stringPreferencesKey("music_taste_selected_artists_json")
        private val KEY_SELECTED_GENRES_JSON = stringPreferencesKey("music_taste_selected_genres_json")
        private val KEY_LIKED_TRACKS_JSON = stringPreferencesKey("music_taste_liked_tracks_json")
        private val KEY_PASSED_TRACKS_JSON = stringPreferencesKey("music_taste_passed_tracks_json")
        private val KEY_ADDED_LIKED_URLS_JSON = stringPreferencesKey("music_taste_added_liked_urls_json")
        private val KEY_LAST_UPDATED_MS = longPreferencesKey("music_taste_last_updated_ms")

        fun detectPreExistingInstallForMusicTaste(context: Context): Boolean {
            if (SonoraApplication.hadPreExistingInstallationDataOnProcessStart) return true
            return runCatching {
                val datastoreDir = File(context.filesDir, "datastore")
                val profileStoreFile = File(datastoreDir, "sonora_user_profile.preferences_pb")
                profileStoreFile.exists() && profileStoreFile.length() > 0L
            }.getOrDefault(false)
        }
    }

    private val _musicTasteProfile = MutableStateFlow(MusicTasteProfile())
    val musicTasteProfile: StateFlow<MusicTasteProfile> = _musicTasteProfile.asStateFlow()

    private val _onboardingState = MutableStateFlow(MusicTasteOnboardingState.LOADING)
    val onboardingState: StateFlow<MusicTasteOnboardingState> = _onboardingState.asStateFlow()

    init {
        scope.launch {
            ensureInitialOnboardingStateResolved()
            observeStore()
        }
    }

    /**
     * Resolves whether this launch is a genuine fresh first-time install (REQUIRED) or an
     * existing installation / app update (COMPLETED) before emitting the initial state.
     */
    suspend fun ensureInitialOnboardingStateResolved(): MusicTasteOnboardingState = withContext(Dispatchers.IO) {
        val prefs = runCatching { store.data.first() }.getOrElse { emptyPreferences() }
        val explicitCompleted = prefs[KEY_ONBOARDING_COMPLETED]
        if (explicitCompleted != null) {
            val profile = decodeProfile(prefs)
            _musicTasteProfile.value = profile
            val state = if (explicitCompleted) {
                MusicTasteOnboardingState.COMPLETED
            } else {
                MusicTasteOnboardingState.REQUIRED
            }
            _onboardingState.value = state
            return@withContext state
        }

        val isExistingInstall = existingInstallationDetector?.invoke()
            ?: detectPreExistingInstallForMusicTaste(appContext)
        val hasSavedTaste = !prefs[KEY_SELECTED_ARTISTS_JSON].isNullOrBlank() ||
            !prefs[KEY_SELECTED_GENRES_JSON].isNullOrBlank() ||
            !prefs[KEY_LIKED_TRACKS_JSON].isNullOrBlank()

        val shouldMarkCompleted = isExistingInstall || hasSavedTaste
        runCatching {
            store.edit { mutable ->
                if (mutable[KEY_ONBOARDING_COMPLETED] == null) {
                    mutable[KEY_ONBOARDING_COMPLETED] = shouldMarkCompleted
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed persisting initial music taste onboarding state", err)
        }

        val state = if (shouldMarkCompleted) {
            MusicTasteOnboardingState.COMPLETED
        } else {
            MusicTasteOnboardingState.REQUIRED
        }
        _musicTasteProfile.value = decodeProfile(prefs).copy(onboardingCompleted = shouldMarkCompleted)
        _onboardingState.value = state
        state
    }

    private suspend fun observeStore() {
        store.data
            .catch { err ->
                Log.w(TAG, "Error observing music taste DataStore", err)
                emit(emptyPreferences())
            }
            .collect { prefs ->
                val profile = decodeProfile(prefs)
                _musicTasteProfile.value = profile
                val completed = prefs[KEY_ONBOARDING_COMPLETED]
                if (completed != null) {
                    _onboardingState.value = if (completed) {
                        MusicTasteOnboardingState.COMPLETED
                    } else {
                        MusicTasteOnboardingState.REQUIRED
                    }
                }
            }
    }

    suspend fun getProfileOnce(): MusicTasteProfile = withContext(Dispatchers.IO) {
        val prefs = runCatching { store.data.first() }.getOrElse { emptyPreferences() }
        decodeProfile(prefs)
    }

    suspend fun toggleArtist(artist: MusicTasteArtist): MusicTasteProfile = withContext(Dispatchers.IO) {
        val key = artist.normalizedKey
        if (key.isBlank()) return@withContext _musicTasteProfile.value
        updateProfile { current ->
            val exists = current.selectedArtists.any { it.normalizedKey == key }
            val updated = if (exists) {
                current.selectedArtists.filterNot { it.normalizedKey == key }
            } else {
                (current.selectedArtists + artist)
                    .distinctBy { it.normalizedKey }
                    .take(MAX_STORED_ARTISTS)
            }
            current.copy(selectedArtists = updated, lastUpdatedAtMs = System.currentTimeMillis())
        }
    }

    suspend fun setSelectedArtists(artists: List<MusicTasteArtist>): MusicTasteProfile = withContext(Dispatchers.IO) {
        val deduped = artists
            .filter { it.normalizedKey.isNotBlank() }
            .distinctBy { it.normalizedKey }
            .take(MAX_STORED_ARTISTS)
        updateProfile { current ->
            current.copy(selectedArtists = deduped, lastUpdatedAtMs = System.currentTimeMillis())
        }
    }

    suspend fun clearSelectedArtists(): MusicTasteProfile = setSelectedArtists(emptyList())

    suspend fun toggleGenre(genre: String): MusicTasteProfile = withContext(Dispatchers.IO) {
        val clean = genre.trim()
        if (clean.isBlank()) return@withContext _musicTasteProfile.value
        updateProfile { current ->
            val exists = current.selectedGenres.any { it.equals(clean, ignoreCase = true) }
            val updated = if (exists) {
                current.selectedGenres.filterNot { it.equals(clean, ignoreCase = true) }
            } else {
                current.selectedGenres + clean
            }
            current.copy(selectedGenres = updated, lastUpdatedAtMs = System.currentTimeMillis())
        }
    }

    suspend fun setSelectedGenres(genres: List<String>): MusicTasteProfile = withContext(Dispatchers.IO) {
        val deduped = genres.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        updateProfile { current ->
            current.copy(selectedGenres = deduped, lastUpdatedAtMs = System.currentTimeMillis())
        }
    }

    suspend fun clearSelectedGenres(): MusicTasteProfile = setSelectedGenres(emptyList())

    suspend fun recordDiscoveryDecision(
        track: MusicTasteDiscoveryTrack,
        decision: MusicTasteSwipeDecision
    ): MusicTasteProfile = withContext(Dispatchers.IO) {
        val normUrl = track.normalizedUrl
        if (normUrl.isBlank()) return@withContext _musicTasteProfile.value
        updateProfile { current ->
            val withoutTrackLiked = current.likedDiscoveryTracks.filterNot { it.normalizedUrl == normUrl }
            val withoutTrackPassed = current.passedDiscoveryTracks.filterNot { it.normalizedUrl == normUrl }
            when (decision) {
                MusicTasteSwipeDecision.LIKE -> current.copy(
                    likedDiscoveryTracks = (listOf(track) + withoutTrackLiked).take(MAX_STORED_DISCOVERY_TRACKS),
                    passedDiscoveryTracks = withoutTrackPassed,
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
                MusicTasteSwipeDecision.ADD_TO_LIKED_SONGS -> current.copy(
                    likedDiscoveryTracks = (listOf(track) + withoutTrackLiked).take(MAX_STORED_DISCOVERY_TRACKS),
                    passedDiscoveryTracks = withoutTrackPassed,
                    addedToLikedSongUrls = current.addedToLikedSongUrls + normUrl,
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
                MusicTasteSwipeDecision.PASS -> current.copy(
                    likedDiscoveryTracks = withoutTrackLiked,
                    passedDiscoveryTracks = (listOf(track) + withoutTrackPassed).take(MAX_STORED_DISCOVERY_TRACKS),
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    suspend fun removeLikedDiscoveryTrack(contentUrl: String): MusicTasteProfile = withContext(Dispatchers.IO) {
        val normUrl = contentUrl.trim().substringBefore("#").trimEnd('/').lowercase()
        updateProfile { current ->
            current.copy(
                likedDiscoveryTracks = current.likedDiscoveryTracks.filterNot { it.normalizedUrl == normUrl },
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun removePassedDiscoveryTrack(contentUrl: String): MusicTasteProfile = withContext(Dispatchers.IO) {
        val normUrl = contentUrl.trim().substringBefore("#").trimEnd('/').lowercase()
        updateProfile { current ->
            current.copy(
                passedDiscoveryTracks = current.passedDiscoveryTracks.filterNot { it.normalizedUrl == normUrl },
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun clearDiscoveryDecisions(): MusicTasteProfile = withContext(Dispatchers.IO) {
        updateProfile { current ->
            current.copy(
                likedDiscoveryTracks = emptyList(),
                passedDiscoveryTracks = emptyList(),
                addedToLikedSongUrls = emptySet(),
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun completeOnboarding(): MusicTasteProfile = withContext(Dispatchers.IO) {
        val updated = updateProfile { current ->
            current.copy(
                onboardingCompleted = true,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
        _onboardingState.value = MusicTasteOnboardingState.COMPLETED
        updated
    }

    suspend fun skipOnboarding(): MusicTasteProfile = completeOnboarding()

    /**
     * Resets ONLY Music Taste preferences (favorite artists, genres, liked/disliked discovery tracks).
     * Does NOT reset onboarding completion status (so onboarding never auto-pops on next launch),
     * and never touches the user's music library, playlists, downloads, or playback history.
     */
    suspend fun resetMusicTaste(): MusicTasteProfile = withContext(Dispatchers.IO) {
        val reset = MusicTasteProfile(
            selectedArtists = emptyList(),
            selectedGenres = emptyList(),
            likedDiscoveryTracks = emptyList(),
            passedDiscoveryTracks = emptyList(),
            addedToLikedSongUrls = emptySet(),
            onboardingCompleted = true,
            lastUpdatedAtMs = System.currentTimeMillis()
        )
        persistProfile(reset)
        _musicTasteProfile.value = reset
        _onboardingState.value = MusicTasteOnboardingState.COMPLETED
        reset
    }

    private suspend fun updateProfile(transform: (MusicTasteProfile) -> MusicTasteProfile): MusicTasteProfile {
        val current = getProfileOnce()
        val updated = transform(current)
        persistProfile(updated)
        _musicTasteProfile.value = updated
        return updated
    }

    private suspend fun persistProfile(profile: MusicTasteProfile) {
        store.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = profile.onboardingCompleted
            prefs[KEY_SELECTED_ARTISTS_JSON] = encodeArtists(profile.selectedArtists)
            prefs[KEY_SELECTED_GENRES_JSON] = encodeStringList(profile.selectedGenres)
            prefs[KEY_LIKED_TRACKS_JSON] = encodeTracks(profile.likedDiscoveryTracks)
            prefs[KEY_PASSED_TRACKS_JSON] = encodeTracks(profile.passedDiscoveryTracks)
            prefs[KEY_ADDED_LIKED_URLS_JSON] = encodeStringList(profile.addedToLikedSongUrls.toList())
            prefs[KEY_LAST_UPDATED_MS] = profile.lastUpdatedAtMs
        }
    }

    private fun decodeProfile(prefs: Preferences): MusicTasteProfile {
        return MusicTasteProfile(
            selectedArtists = decodeArtists(prefs[KEY_SELECTED_ARTISTS_JSON]),
            selectedGenres = decodeStringList(prefs[KEY_SELECTED_GENRES_JSON]),
            likedDiscoveryTracks = decodeTracks(prefs[KEY_LIKED_TRACKS_JSON]),
            passedDiscoveryTracks = decodeTracks(prefs[KEY_PASSED_TRACKS_JSON]),
            addedToLikedSongUrls = decodeStringList(prefs[KEY_ADDED_LIKED_URLS_JSON]).toSet(),
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            lastUpdatedAtMs = prefs[KEY_LAST_UPDATED_MS] ?: 0L
        )
    }

    private fun encodeArtists(artists: List<MusicTasteArtist>): String {
        val arr = JSONArray()
        artists.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("imageUrl", a.imageUrl ?: "")
                    put("channelUrl", a.channelUrl ?: "")
                    put("providerKey", a.providerKey)
                    put("serviceId", a.serviceId)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeArtists(raw: String?): List<MusicTasteArtist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        MusicTasteArtist(
                            id = o.optString("id").ifBlank { MusicTasteArtist.normalizeArtistName(name) },
                            name = name,
                            imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                            channelUrl = o.optString("channelUrl").takeIf { it.isNotBlank() },
                            providerKey = o.optString("providerKey").ifBlank { "youtube_music" },
                            serviceId = o.optInt("serviceId", 0)
                        )
                    )
                }
            }.distinctBy { it.normalizedKey }
        }.getOrDefault(emptyList())
    }

    private fun encodeTracks(tracks: List<MusicTasteDiscoveryTrack>): String {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("stableId", t.stableId)
                    put("serviceId", t.serviceId)
                    put("providerKey", t.providerKey)
                    put("providerName", t.providerName)
                    put("contentUrl", t.contentUrl)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("artworkUrl", t.artworkUrl ?: "")
                    put("durationMs", t.durationMs)
                    put("reasonLabel", t.reasonLabel)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeTracks(raw: String?): List<MusicTasteDiscoveryTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("contentUrl").trim()
                    val title = o.optString("title").trim()
                    if (url.isBlank() || title.isBlank()) continue
                    add(
                        MusicTasteDiscoveryTrack(
                            stableId = o.optLong("stableId", url.hashCode().toLong()),
                            serviceId = o.optInt("serviceId", 0),
                            providerKey = o.optString("providerKey").ifBlank { "youtube_music" },
                            providerName = o.optString("providerName").ifBlank { "YouTube Music" },
                            contentUrl = url,
                            title = title,
                            artist = o.optString("artist").trim(),
                            album = o.optString("album").trim(),
                            artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() },
                            durationMs = o.optLong("durationMs", 0L),
                            reasonLabel = o.optString("reasonLabel").ifBlank { "For Your Taste" }
                        )
                    )
                }
            }.distinctBy { it.normalizedUrl }
        }.getOrDefault(emptyList())
    }

    private fun encodeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }
}
