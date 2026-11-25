package domain.online

import com.example.model.Artist
import com.example.model.Playlist
import com.example.model.Song
import data.analytics.ListeningEventEntity
import domain.library.FollowedOnlineArtist
import domain.library.OnlinePlaylist
import domain.onboarding.MusicTasteCatalog
import domain.onboarding.MusicTasteProfile
import util.SecureUrlValidator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Pure, local-only personalized music recommendation engine for SONORA.
 *
 * Builds a multi-signal taste profile from:
 * - Liked Songs (local & online)
 * - Followed Artists (highest priority)
 * - User Playlists (local & online, multi-playlist identity)
 * - Listening History (completed sessions, frequency, recency, filtering accidental short plays)
 * - Live User Interactions (Like, Dislike, Early Skip, Play Next, Repeated Listening)
 *
 * Enforces strict India regional eligibility (without misclassifying Indian English music
 * as foreign), candidate validity/deduplication, artist diversity, familiar/discovery balance,
 * and refresh rotation without ever generating fake songs or uploading personal data.
 */
object SonoraPersonalizedRecommendationEngine {

    const val COLD_START_MESSAGE = "Your music journey starts here \uD83C\uDFB5"

    private val PLACEHOLDER_ARTISTS = setOf(
        "", "unknown", "<unknown>", "unknown artist", "various artists",
        "yt music", "youtube", "youtube music", "soundcloud", "bandcamp", "sonora"
    )

    private val PLACEHOLDER_ALBUMS = setOf(
        "", "unknown", "<unknown>", "unknown album", "downloads", "music", "single",
        "yt music", "youtube", "youtube music", "soundcloud", "bandcamp"
    )

    private val UNAVAILABLE_TITLE_MARKERS = listOf(
        "[deleted video]", "[private video]", "deleted video", "private video",
        "video unavailable", "track unavailable"
    )

    private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{M}\\p{N}\\s]+")
    private val MULTISPACE_REGEX = Regex("\\s+")

    /** Regional Indian language detection cues (script + metadata keywords). */
    private val INDIAN_LANGUAGE_MARKERS: Map<String, Set<String>> = mapOf(
        "Hindi" to setOf("hindi", "bollywood", "filmi", "indipop", "dhh", "desi hip hop", "ishq", "pyaar", "dil", "zindagi", "mohabbat"),
        "Odia" to setOf("odia", "oriya", "odisha", "tarang music", "amara muzik odia", "sarthak music", "humane sagar", "asima panda", "ira mohanty", "diptirekha", "mantu chhuria", "akshay mohanty"),
        "Bengali" to setOf("bengali", "bangla", "rabindra sangeet", "svf", "anupam roy", "rupam islam", "fossils", "nachiketa", "iman chakraborty", "somlata"),
        "Punjabi" to setOf("punjabi", "bhangra", "speed records", "white hill music", "diljit dosanjh", "karan aujla", "ap dhillon", "sidhu moose wala", "shubh", "ammy virk", "satinder sartaaj"),
        "Tamil" to setOf("tamil", "kollywood", "sun music", "think music", "anirudh", "ilaiyaraaja", "yuvan shankar raja", "harris jayaraj", "santhosh narayanan", "sid sriram"),
        "Telugu" to setOf("telugu", "tollywood", "aditya music", "devi sri prasad", "thaman", "keeravani", "mani sharma", "mickey j meyer", "anurag kulkarni"),
        "Malayalam" to setOf("malayalam", "mollywood", "muzik247", "manorama music", "sushin shyam", "gopi sundar", "shaan rahman", "vineeth sreenivasan", "yesudas", "thaikkudam bridge"),
        "Kannada" to setOf("kannada", "sandalwood", "anand audio", "lahari music", "arjun janya", "ravi basrur", "ajaneesh loknath", "sanjith hegde", "vijay prakash"),
        "Marathi" to setOf("marathi", "lavani", "everest marathi", "ajay atul", "ajay gogavale", "avadhoot gupte", "bela shende", "rahul deshpande", "sanju rathod"),
        "Assamese" to setOf("assamese", "bihu", "zubeen garg", "papon", "angaraag mahanta", "bhupen hazarika", "shankuraj konwar", "neel akash"),
        "Gujarati" to setOf("gujarati", "garba", "dandiya", "kirtidan gadhvi", "geeta rabari", "kinjal dave", "aditya gadhvi", "jigardan gadhavi", "osman mir")
    )

    private val MOOD_KEYWORDS: Map<String, Set<String>> = mapOf(
        "Romantic" to setOf("romantic", "love", "ishq", "pyaar", "heart", "ballad", "melody", "duet"),
        "Chill" to setOf("chill", "lofi", "lo-fi", "acoustic", "unplugged", "relax", "calm", "soft", "late night", "rain"),
        "Workout" to setOf("workout", "gym", "pump", "beast", "energy", "power", "bass", "running", "hype"),
        "Party" to setOf("party", "dance", "club", "dj", "remix", "bhangra", "garba", "celebration"),
        "Sad" to setOf("sad", "heartbreak", "soulful", "emotional", "breakup", "pain", "tears", "bewafa"),
        "Devotional" to setOf("bhajan", "aarti", "kirtan", "mantra", "chalisa", "devotional", "spiritual", "sufi", "qawwali"),
        "Focus" to setOf("focus", "study", "instrumental", "classical", "carnatic", "hindustani", "raga", "piano")
    )

    fun normalizeKey(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        return MULTISPACE_REGEX.replace(
            NON_ALNUM_REGEX.replace(nfc.lowercase(Locale.ROOT), " "),
            " "
        ).trim()
    }

    fun normalizeTrackUrl(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        return trimmed.substringBefore("#").trimEnd('/').lowercase(Locale.ROOT)
    }

    fun buildTrackIdentityKey(title: String, artist: String): String {
        return "${normalizeKey(title)}|${normalizeKey(artist)}"
    }

    fun isValidArtistName(raw: String?): Boolean {
        val clean = normalizeKey(raw)
        return clean.length >= 2 && clean !in PLACEHOLDER_ARTISTS
    }

    fun isValidAlbumName(raw: String?): Boolean {
        val clean = normalizeKey(raw)
        return clean.length >= 2 && clean !in PLACEHOLDER_ALBUMS
    }

    /**
     * Detects Indian regional language from Unicode Indic scripts or verified metadata cues.
     * Returns null for general/English tracks (never marks English as foreign).
     */
    fun detectIndianLanguage(
        title: String,
        artist: String = "",
        album: String = "",
        genre: String = ""
    ): String? {
        val raw = "$title $artist $album $genre"
        for (ch in raw) {
            when (ch.code) {
                in 0x0B00..0x0B7F -> return "Odia"
                in 0x0980..0x09FF -> return "Bengali"
                in 0x0A00..0x0A7F -> return "Punjabi"
                in 0x0A80..0x0AFF -> return "Gujarati"
                in 0x0B80..0x0BFF -> return "Tamil"
                in 0x0C00..0x0C7F -> return "Telugu"
                in 0x0C80..0x0CFF -> return "Kannada"
                in 0x0D00..0x0D7F -> return "Malayalam"
                in 0x0900..0x097F -> {
                    val norm = normalizeKey(raw)
                    return if (norm.contains("marathi") || norm.contains("lavani")) "Marathi" else "Hindi"
                }
            }
        }
        val norm = normalizeKey(raw)
        if (norm.isBlank()) return null
        for ((lang, markers) in INDIAN_LANGUAGE_MARKERS) {
            if (markers.any { marker -> containsTokenOrPhrase(norm, marker) }) {
                return lang
            }
        }
        return null
    }

    private fun detectMoods(text: String): Set<String> {
        val norm = normalizeKey(text)
        if (norm.isBlank()) return emptySet()
        return MOOD_KEYWORDS.entries
            .filter { (_, keywords) -> keywords.any { kw -> containsTokenOrPhrase(norm, kw) } }
            .map { it.key }
            .toSet()
    }

    private fun containsTokenOrPhrase(haystack: String, needle: String): Boolean {
        if (haystack.isEmpty() || needle.isEmpty()) return false
        val idx = haystack.indexOf(needle)
        if (idx < 0) return false
        val startOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
        val end = idx + needle.length
        val endOk = end == haystack.length || !haystack[end].isLetterOrDigit()
        if (startOk && endOk) return true
        var next = idx + 1
        while (next < haystack.length) {
            val found = haystack.indexOf(needle, next)
            if (found < 0) return false
            val sOk = found == 0 || !haystack[found - 1].isLetterOrDigit()
            val e = found + needle.length
            val eOk = e == haystack.length || !haystack[e].isLetterOrDigit()
            if (sOk && eOk) return true
            next = found + 1
        }
        return false
    }

    /**
     * Checks whether an [OnlineTrack] has valid playable metadata and is not deleted/unavailable.
     */
    fun isCandidateMetadataPlayable(track: OnlineTrack): Boolean {
        if (track.kind != OnlineItemKind.TRACK) return false
        if (track.isLive) return false
        val cleanTitle = track.title.trim()
        if (cleanTitle.length < 2) return false
        val lowerTitle = cleanTitle.lowercase(Locale.ROOT)
        if (UNAVAILABLE_TITLE_MARKERS.any { lowerTitle.contains(it) }) return false
        val url = track.contentUrl.trim()
        if (url.isBlank() || !SecureUrlValidator.isSafeHttpsUrl(url)) return false
        if (track.durationMs > 0L && (track.durationMs < 25_000L || track.durationMs > 900_000L)) {
            // Skip tiny clips (<25s) or >15min full-album/podcast uploads in song recommendations
            return false
        }
        return true
    }

    /**
     * Builds the user's local taste profile from all SONORA activity sources.
     */
    fun buildUserTasteProfile(
        likedLocalSongs: List<Song>,
        likedOnlineSongs: List<Song>,
        followedOnlineArtists: List<FollowedOnlineArtist>,
        favouriteLocalArtists: List<Artist>,
        localPlaylists: List<Playlist>,
        localPlaylistSongs: Map<Long, List<Song>>,
        onlinePlaylists: List<OnlinePlaylist>,
        listeningEvents: List<ListeningEventEntity>,
        recentlyPlayedOnline: List<Song>,
        recentlyPlayedLocal: List<Song>,
        interactions: UserMusicInteractionSignal,
        countryCode: String,
        languageCode: String,
        discoverLanguage: DiscoverMusicLanguage,
        strictIndiaOnly: Boolean = RegionMusicEligibilityFilter.isIndiaRegion(countryCode),
        weights: SonoraRecommendWeights = SonoraRecommendWeights(),
        nowMs: Long = System.currentTimeMillis(),
        musicTasteProfile: MusicTasteProfile = MusicTasteProfile()
    ): SonoraUserTasteProfile {
        val cleanCountry = countryCode.trim().uppercase(Locale.US)
            .ifBlank { OnlineRegionCatalog.DEFAULT_COUNTRY_CODE }
        val cleanLang = languageCode.trim().lowercase(Locale.US)
            .ifBlank { OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE }
        val enforceStrictIndia = strictIndiaOnly && RegionMusicEligibilityFilter.isIndiaRegion(cleanCountry)

        val followedArtists = LinkedHashMap<String, Double>()
        val likedArtists = LinkedHashMap<String, Double>()
        val playlistArtists = LinkedHashMap<String, Double>()
        val historyArtists = LinkedHashMap<String, Double>()
        val recentArtists = LinkedHashMap<String, Double>()
        val displayArtistNames = LinkedHashMap<String, String>()
        val genres = LinkedHashMap<String, Double>()
        val languages = LinkedHashMap<String, Double>()
        val albums = LinkedHashSet<String>()
        val moods = LinkedHashMap<String, Double>()
        val playlistNamesByArtist = LinkedHashMap<String, String>()
        val likedTrackKeys = LinkedHashSet<String>()
        val familiarKeys = LinkedHashSet<String>()

        fun canUseArtistForRegion(artistName: String, titleHint: String = "", albumHint: String = "", genreHint: String = ""): Boolean {
            if (!isValidArtistName(artistName)) return false
            if (!enforceStrictIndia) return true
            return RegionMusicEligibilityFilter.isVerifiedIndianMusic(
                title = titleHint.ifBlank { artistName },
                artist = artistName,
                album = albumHint,
                genre = genreHint
            )
        }

        fun addScore(map: MutableMap<String, Double>, key: String, delta: Double) {
            if (key.isBlank() || delta == 0.0) return
            map[key] = (map[key] ?: 0.0) + delta
        }

        fun recordDisplayArtist(rawArtist: String): String? {
            if (!isValidArtistName(rawArtist)) return null
            val norm = normalizeKey(rawArtist)
            displayArtistNames.putIfAbsent(norm, rawArtist.trim())
            return norm
        }

        fun recordLanguageAndGenre(title: String, artist: String, album: String, genre: String, weight: Double) {
            val cleanGenre = genre.trim()
            if (cleanGenre.length >= 2 &&
                !cleanGenre.equals("unknown", ignoreCase = true) &&
                !cleanGenre.equals("unknown genre", ignoreCase = true)
            ) {
                addScore(genres, normalizeKey(cleanGenre), weight)
            }
            detectIndianLanguage(title, artist, album, genre)?.let { lang ->
                addScore(languages, lang, weight)
            }
            detectMoods("$title $album $genre").forEach { mood ->
                addScore(moods, mood, weight * 0.6)
            }
        }

        // --- Signal A: Liked Songs (Online + Local) ---
        val allLiked = (likedOnlineSongs + likedLocalSongs).distinctBy {
            it.contentUrl.ifBlank { "${it.id}|${it.title}" }
        }
        allLiked.forEach { song ->
            val urlKey = normalizeTrackUrl(song.contentUrl)
            if (urlKey.isNotBlank()) {
                likedTrackKeys.add(urlKey)
                familiarKeys.add(urlKey)
            }
            val titleArtistKey = buildTrackIdentityKey(song.title, song.artist)
            likedTrackKeys.add(titleArtistKey)
            familiarKeys.add(titleArtistKey)

            if (canUseArtistForRegion(song.artist, song.title, song.album, song.genre)) {
                recordDisplayArtist(song.artist)?.let { normArtist ->
                    addScore(likedArtists, normArtist, 2.5 + ln((song.playCount.coerceAtLeast(0) + 1).toDouble()))
                }
            }
            if (isValidAlbumName(song.album)) {
                albums.add(normalizeKey(song.album))
            }
            recordLanguageAndGenre(song.title, song.artist, song.album, song.genre, weight = 2.2)
        }

        // --- Signal B: Followed Artists (Highest priority) ---
        followedOnlineArtists.forEachIndexed { idx, artist ->
            if (canUseArtistForRegion(artist.name)) {
                recordDisplayArtist(artist.name)?.let { normArtist ->
                    val recencyBoost = 1.0 + (1.0 / (1.0 + idx * 0.15))
                    addScore(followedArtists, normArtist, 4.0 * recencyBoost)
                }
            }
            detectIndianLanguage(artist.name, artist.name)?.let { lang ->
                addScore(languages, lang, 2.5)
            }
        }
        favouriteLocalArtists.take(12).forEach { artist ->
            if (canUseArtistForRegion(artist.name)) {
                recordDisplayArtist(artist.name)?.let { normArtist ->
                    addScore(followedArtists, normArtist, 1.8)
                }
            }
        }

        // --- Signal C: User Playlists (Online + Local, multi-playlist identity) ---
        var playlistTrackTotal = 0
        onlinePlaylists.forEach { playlist ->
            detectIndianLanguage(playlist.name)?.let { lang -> addScore(languages, lang, 1.8) }
            detectMoods(playlist.name).forEach { mood -> addScore(moods, mood, 1.5) }
            playlist.tracks.forEach { song ->
                playlistTrackTotal++
                if (canUseArtistForRegion(song.artist, song.title, song.album, song.genre)) {
                    recordDisplayArtist(song.artist)?.let { normArtist ->
                        addScore(playlistArtists, normArtist, 1.8)
                        playlistNamesByArtist.putIfAbsent(normArtist, playlist.name)
                    }
                }
                if (isValidAlbumName(song.album)) albums.add(normalizeKey(song.album))
                recordLanguageAndGenre(song.title, song.artist, song.album, song.genre, weight = 1.5)
            }
        }
        localPlaylists.forEach { playlist ->
            detectIndianLanguage(playlist.name)?.let { lang -> addScore(languages, lang, 1.8) }
            detectMoods(playlist.name).forEach { mood -> addScore(moods, mood, 1.5) }
            localPlaylistSongs[playlist.id].orEmpty().forEach { song ->
                playlistTrackTotal++
                if (canUseArtistForRegion(song.artist, song.title, song.album, song.genre)) {
                    recordDisplayArtist(song.artist)?.let { normArtist ->
                        addScore(playlistArtists, normArtist, 1.6)
                        playlistNamesByArtist.putIfAbsent(normArtist, playlist.name)
                    }
                }
                if (isValidAlbumName(song.album)) albums.add(normalizeKey(song.album))
                recordLanguageAndGenre(song.title, song.artist, song.album, song.genre, weight = 1.4)
            }
        }

        // --- Signal D: Listening History (completed sessions, play frequency, recency, filter short accidental plays) ---
        var meaningfulHistoryCount = 0
        val halfLifeMs = (weights.recentHalfLifeDays * 24.0 * 3600.0 * 1000.0).coerceAtLeast(1.0)
        val listeningBySongId = listeningEvents.groupBy { it.songId }

        listeningBySongId.forEach { (_, events) ->
            // Aggregate total engaged time per sessionToken so split segments still count as a completed session
            val sessionDurations = events.groupBy { it.sessionToken }
                .mapValues { (_, segs) -> segs.sumOf { it.listenedMs.coerceAtLeast(0L) } }
            val meaningfulSessions = sessionDurations.values.filter { it >= weights.minMeaningfulListenMs }
            if (meaningfulSessions.isEmpty()) {
                // Accidental tap or very short listening session (<20s) -> do NOT treat as strong preference
                return@forEach
            }
            meaningfulHistoryCount += meaningfulSessions.size
            val completedCount = meaningfulSessions.count { it >= weights.completedSessionMs }
            val latestEvent = events.maxByOrNull { it.endedAtMs } ?: events.last()
            val ageMs = (nowMs - latestEvent.endedAtMs).coerceAtLeast(0L).toDouble()
            val decay = exp(-ln(2.0) * (ageMs / halfLifeMs))

            val totalListenedMin = (meaningfulSessions.sum() / 60_000.0).coerceAtMost(60.0)
            val frequencyScore = ln(1.0 + meaningfulSessions.size.toDouble()) + completedCount * 0.9 + totalListenedMin * 0.15

            if (canUseArtistForRegion(latestEvent.artist, latestEvent.title, latestEvent.album, latestEvent.genre)) {
                recordDisplayArtist(latestEvent.artist)?.let { normArtist ->
                    addScore(historyArtists, normArtist, frequencyScore)
                    addScore(recentArtists, normArtist, frequencyScore * decay)
                }
            }
            if (meaningfulSessions.size >= 2 || completedCount >= 1) {
                familiarKeys.add(buildTrackIdentityKey(latestEvent.title, latestEvent.artist))
            }
            recordLanguageAndGenre(
                latestEvent.title,
                latestEvent.artist,
                latestEvent.album,
                latestEvent.genre,
                weight = frequencyScore * 0.7
            )
        }

        // Also include recently played online & local songs with playCount >= 1 (weighted conservatively)
        (recentlyPlayedOnline + recentlyPlayedLocal).forEachIndexed { idx, song ->
            if (song.playCount <= 0 && song.lastPlayedTimestamp <= 0L) return@forEachIndexed
            // If we have detailed listeningEvents for this song and ALL of them were short accidental plays, skip it
            val songEvents = listeningBySongId[song.id]
            if (!songEvents.isNullOrEmpty()) {
                val maxSession = songEvents.groupBy { it.sessionToken }
                    .values.maxOfOrNull { segs -> segs.sumOf { it.listenedMs } } ?: 0L
                if (maxSession < weights.minMeaningfulListenMs) return@forEachIndexed
            }
            meaningfulHistoryCount++
            val ageMs = if (song.lastPlayedTimestamp > 0L) {
                (nowMs - song.lastPlayedTimestamp).coerceAtLeast(0L).toDouble()
            } else {
                (idx * 24L * 3600L * 1000L).toDouble()
            }
            val decay = exp(-ln(2.0) * (ageMs / halfLifeMs))
            val playWeight = (1.0 + ln((song.playCount.coerceAtLeast(1) + 1).toDouble()))
            if (canUseArtistForRegion(song.artist, song.title, song.album, song.genre)) {
                recordDisplayArtist(song.artist)?.let { normArtist ->
                    addScore(historyArtists, normArtist, playWeight * 0.85)
                    addScore(recentArtists, normArtist, playWeight * decay)
                }
            }
            if (song.playCount >= 2) {
                val uKey = normalizeTrackUrl(song.contentUrl)
                if (uKey.isNotBlank()) familiarKeys.add(uKey)
                familiarKeys.add(buildTrackIdentityKey(song.title, song.artist))
            }
            recordLanguageAndGenre(song.title, song.artist, song.album, song.genre, weight = playWeight * 0.55)
        }

        // --- Signal E: Live Interaction Feedback (Play Next, Repeated Listening, Skips, Dislikes) ---
        interactions.playNextArtistCounts.forEach { (artistNorm, count) ->
            if (count > 0) addScore(recentArtists, artistNorm, count * 1.1)
        }
        interactions.repeatedArtistCounts.forEach { (artistNorm, count) ->
            if (count > 0) addScore(historyArtists, artistNorm, count * 1.3)
        }

        // Honor explicit discoverLanguage selection as a language signal
        if (discoverLanguage != DiscoverMusicLanguage.ALL) {
            addScore(languages, discoverLanguage.label, 3.0)
        }

        // --- Signal F: Music Taste Onboarding & Profile -> Music Taste Preferences ---
        // Gradually tapers as real listening history accumulates so recommendations adapt naturally over time.
        val onboardingAdaptationFactor = 1.0 / (1.0 + meaningfulHistoryCount * 0.06)
        val onboardingArtists = LinkedHashMap<String, Double>()
        val onboardingLikedTrackUrls = LinkedHashSet<String>()
        val onboardingPassedTrackUrls = LinkedHashSet<String>()
        val onboardingPassedArtistCounts = LinkedHashMap<String, Int>()

        musicTasteProfile.selectedArtists.forEachIndexed { idx, artist ->
            if (isValidArtistName(artist.name)) {
                recordDisplayArtist(artist.name)?.let { normArtist ->
                    val positionWeight = (3.4 - (idx * 0.12).coerceAtMost(1.2)) * onboardingAdaptationFactor
                    addScore(onboardingArtists, normArtist, positionWeight)
                }
                detectIndianLanguage(artist.name, artist.name)?.let { lang ->
                    addScore(languages, lang, 2.2 * onboardingAdaptationFactor)
                }
            }
        }
        musicTasteProfile.selectedGenres.forEach { genre ->
            val normGenre = normalizeKey(genre)
            if (normGenre.isNotBlank()) {
                addScore(genres, normGenre, 2.8 * onboardingAdaptationFactor)
                MusicTasteCatalog.REGIONAL_GENRE_TO_LANGUAGE[normGenre]?.let { mappedLang ->
                    addScore(languages, mappedLang, 2.8 * onboardingAdaptationFactor)
                }
            }
        }
        musicTasteProfile.likedDiscoveryTracks.forEach { track ->
            val urlKey = normalizeTrackUrl(track.contentUrl)
            if (urlKey.isNotBlank()) onboardingLikedTrackUrls.add(urlKey)
            if (isValidArtistName(track.artist)) {
                recordDisplayArtist(track.artist)?.let { normArtist ->
                    addScore(onboardingArtists, normArtist, 2.0 * onboardingAdaptationFactor)
                }
            }
            recordLanguageAndGenre(track.title, track.artist, track.album, "", weight = 1.6 * onboardingAdaptationFactor)
        }
        musicTasteProfile.passedDiscoveryTracks.forEach { track ->
            val urlKey = normalizeTrackUrl(track.contentUrl)
            if (urlKey.isNotBlank()) onboardingPassedTrackUrls.add(urlKey)
            val normArtist = normalizeKey(track.artist)
            if (normArtist.isNotBlank()) {
                onboardingPassedArtistCounts[normArtist] = (onboardingPassedArtistCounts[normArtist] ?: 0) + 1
            }
        }

        val hasPersonalizationSignals = allLiked.isNotEmpty() ||
            followedOnlineArtists.isNotEmpty() ||
            favouriteLocalArtists.isNotEmpty() ||
            playlistTrackTotal > 0 ||
            meaningfulHistoryCount > 0 ||
            interactions.hasAnyPositiveSignals ||
            musicTasteProfile.hasAnyPreferences

        // Combine weighted artist affinities to derive top seed queries
        val allArtistKeys = (
            followedArtists.keys +
                likedArtists.keys +
                playlistArtists.keys +
                historyArtists.keys +
                recentArtists.keys +
                onboardingArtists.keys
            )
        val combinedArtistPriority = allArtistKeys.associateWith { key ->
            val positive = (followedArtists[key] ?: 0.0) * weights.followedArtistWeight +
                (likedArtists[key] ?: 0.0) * weights.likedSongsSimilarityWeight +
                (playlistArtists[key] ?: 0.0) * weights.playlistSimilarityWeight +
                (historyArtists[key] ?: 0.0) * weights.listeningHistoryWeight +
                (recentArtists[key] ?: 0.0) * weights.recentListeningWeight +
                (onboardingArtists[key] ?: 0.0) * weights.onboardingArtistWeight
            val dislikeHits = interactions.dislikedArtistCounts[key] ?: 0
            val passedHits = onboardingPassedArtistCounts[key] ?: 0
            val skipHits = interactions.skippedArtistCounts[key] ?: 0
            positive +
                (dislikeHits * weights.dislikeArtistPenalty) +
                (passedHits * weights.onboardingPassedArtistPenalty) +
                (skipHits * weights.skipPenalty * 0.5)
        }.filterValues { it > 0.25 }

        val topArtistKeys = combinedArtistPriority.entries
            .sortedByDescending { it.value }
            .map { it.key }
        val topDisplayArtists = topArtistKeys.mapNotNull { displayArtistNames[it] }.take(6)

        val topLanguages = languages.entries.sortedByDescending { it.value }.map { it.key }
        val topGenres = genres.entries.sortedByDescending { it.value }.map { it.key }
        val topMoods = moods.entries.sortedByDescending { it.value }.map { it.key }

        val seedQueries = buildSeedQueries(
            hasPersonalizationSignals = hasPersonalizationSignals,
            followedArtists = followedArtists,
            playlistArtists = playlistArtists,
            playlistNamesByArtist = playlistNamesByArtist,
            displayArtistNames = displayArtistNames,
            topArtistKeys = topArtistKeys,
            topLanguages = topLanguages,
            topGenres = topGenres,
            topMoods = topMoods,
            countryCode = cleanCountry,
            languageCode = cleanLang,
            discoverLanguage = discoverLanguage
        )

        val summarySubtitle = when {
            !hasPersonalizationSignals -> COLD_START_MESSAGE
            topDisplayArtists.size >= 2 ->
                "Curated from ${topDisplayArtists[0]}, ${topDisplayArtists[1]} & your taste"
            topDisplayArtists.size == 1 ->
                "Personalized around ${topDisplayArtists[0]} & your listening"
            topLanguages.isNotEmpty() ->
                "Personalized ${topLanguages.first()} & regional picks"
            topGenres.isNotEmpty() ->
                "Tuned for ${topGenres.first().replaceFirstChar { it.uppercase() }} lovers"
            else -> "Tuned to your likes, playlists & listening"
        }

        return SonoraUserTasteProfile(
            hasPersonalizationSignals = hasPersonalizationSignals,
            countryCode = cleanCountry,
            languageCode = cleanLang,
            discoverLanguage = discoverLanguage,
            strictIndiaOnly = enforceStrictIndia,
            followedArtistScores = followedArtists,
            likedArtistScores = likedArtists,
            playlistArtistScores = playlistArtists,
            historyArtistScores = historyArtists,
            recentArtistScores = recentArtists,
            preferredGenres = genres,
            preferredLanguages = languages,
            preferredAlbums = albums,
            preferredMoods = moods,
            playlistNamesByArtist = playlistNamesByArtist,
            likedTrackKeys = likedTrackKeys,
            familiarFavoriteKeys = familiarKeys,
            topDisplayArtists = topDisplayArtists,
            seedQueries = seedQueries,
            summarySubtitle = summarySubtitle,
            onboardingArtistScores = onboardingArtists,
            onboardingLikedTrackUrls = onboardingLikedTrackUrls,
            onboardingPassedTrackUrls = onboardingPassedTrackUrls,
            onboardingPassedArtistCounts = onboardingPassedArtistCounts
        )
    }

    private fun buildSeedQueries(
        hasPersonalizationSignals: Boolean,
        followedArtists: Map<String, Double>,
        playlistArtists: Map<String, Double>,
        playlistNamesByArtist: Map<String, String>,
        displayArtistNames: Map<String, String>,
        topArtistKeys: List<String>,
        topLanguages: List<String>,
        topGenres: List<String>,
        topMoods: List<String>,
        countryCode: String,
        languageCode: String,
        discoverLanguage: DiscoverMusicLanguage
    ): List<SonoraSeedQuery> {
        fun scoped(raw: String): String = RegionMusicEligibilityFilter.scopeRecommendationQuery(
            baseQuery = raw,
            countryCode = countryCode,
            discoverLanguage = discoverLanguage
        )

        val queries = mutableListOf<SonoraSeedQuery>()

        if (!hasPersonalizationSignals) {
            if (RegionMusicEligibilityFilter.isIndiaRegion(countryCode)) {
                val langHint = if (discoverLanguage != DiscoverMusicLanguage.ALL) {
                    discoverLanguage.searchKeyword
                } else {
                    OnlineRecommendationEngine.resolveLocaleMusicHint(countryCode, languageCode, discoverLanguage)
                        .ifBlank { "Indian Hindi Bollywood" }
                }
                queries += SonoraSeedQuery(
                    query = scoped("$langHint popular hit songs"),
                    reasonHint = "Discovery Pick",
                    priority = 3.0,
                    languageTag = discoverLanguage.takeIf { it != DiscoverMusicLanguage.ALL }?.label
                )
                queries += SonoraSeedQuery(
                    query = scoped("Indian indie acoustic melody new songs"),
                    reasonHint = "Fresh Indian Discovery",
                    priority = 2.4
                )
                queries += SonoraSeedQuery(
                    query = scoped("Indian regional hit songs Hindi Punjabi Tamil Telugu Odia"),
                    reasonHint = "Regional Discovery",
                    priority = 2.0
                )
            } else {
                queries += SonoraSeedQuery(
                    query = "top popular hit songs $countryCode",
                    reasonHint = "Discovery Pick",
                    priority = 3.0
                )
                queries += SonoraSeedQuery(
                    query = "fresh new music releases $countryCode",
                    reasonHint = "Fresh Discovery",
                    priority = 2.4
                )
            }
            return queries
        }

        // 1. Highest priority: Followed artist new releases & popular tracks + similar artists
        val topFollowedKey = followedArtists.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .firstOrNull { it in topArtistKeys }
        if (topFollowedKey != null) {
            val artistName = displayArtistNames[topFollowedKey].orEmpty()
            if (artistName.isNotBlank()) {
                queries += SonoraSeedQuery(
                    query = scoped("$artistName latest popular songs"),
                    reasonHint = "Followed Artist",
                    priority = 5.0,
                    artistSeed = artistName
                )
                queries += SonoraSeedQuery(
                    query = scoped("artists similar to $artistName songs"),
                    reasonHint = "Similar to $artistName",
                    priority = 4.2,
                    artistSeed = artistName
                )
            }
        }

        // 2. Top liked / history / playlist artist discoveries
        topArtistKeys.filter { it != topFollowedKey }.take(2).forEachIndexed { idx, key ->
            val artistName = displayArtistNames[key].orEmpty()
            if (artistName.isNotBlank()) {
                val playlistName = playlistNamesByArtist[key]
                val reason = if (!playlistName.isNullOrBlank() && (playlistArtists[key] ?: 0.0) > 0.0) {
                    "Matches $playlistName"
                } else {
                    "Inspired by $artistName"
                }
                queries += SonoraSeedQuery(
                    query = scoped("$artistName best songs mix"),
                    reasonHint = reason,
                    priority = 3.8 - idx * 0.4,
                    artistSeed = artistName
                )
            }
        }

        // 3. Regional language & genre/mood preference seed
        val topLang = topLanguages.firstOrNull()
        val topMood = topMoods.firstOrNull()
        val topGenre = topGenres.firstOrNull()
        if (!topLang.isNullOrBlank()) {
            val moodPart = topMood?.lowercase(Locale.ROOT) ?: "hit"
            queries += SonoraSeedQuery(
                query = scoped("$topLang $moodPart songs"),
                reasonHint = "$topLang Picks",
                priority = 3.2,
                languageTag = topLang
            )
        } else if (!topGenre.isNullOrBlank() || !topMood.isNullOrBlank()) {
            val seedText = listOfNotNull(topMood, topGenre, "songs").joinToString(" ")
            queries += SonoraSeedQuery(
                query = scoped(seedText),
                reasonHint = topMood?.let { "$it Vibe" } ?: "For Your Taste",
                priority = 2.9
            )
        }

        return queries.distinctBy { normalizeKey(it.query) }
    }

    /**
     * Ranks real candidate [OnlineTrack]s using configurable weighted scoring, strict region
     * filtering, deduplication, artist diversity caps, familiar/discovery mixing, and refresh rotation.
     */
    fun rankRecommendations(
        candidates: List<OnlineTrack>,
        profile: SonoraUserTasteProfile,
        interactions: UserMusicInteractionSignal = UserMusicInteractionSignal(),
        unavailableTrackUrls: Set<String> = emptySet(),
        weights: SonoraRecommendWeights = SonoraRecommendWeights(),
        refreshGeneration: Int = 0
    ): List<RecommendedSongItem> {
        if (candidates.isEmpty()) return emptyList()

        val normalizedUnavailable = unavailableTrackUrls.map { normalizeTrackUrl(it) }.toSet()
        val normalizedDislikedUrls = (
            interactions.dislikedTrackUrls.map { normalizeTrackUrl(it) } +
                profile.onboardingPassedTrackUrls
            ).toSet()
        val recentlyShownSet = interactions.recentlyShownTrackUrls.map { normalizeTrackUrl(it) }.toSet()

        // 1. Validate playable metadata & exclude unavailable/explicitly disliked/passed tracks
        val validCandidates = candidates.filter { track ->
            if (!isCandidateMetadataPlayable(track)) return@filter false
            val normUrl = normalizeTrackUrl(track.contentUrl)
            if (normUrl in normalizedUnavailable) return@filter false
            if (track.stableId in interactions.dislikedTrackIds || normUrl in normalizedDislikedUrls) {
                return@filter false
            }
            true
        }

        // 2. Deduplicate by stableId, normalized canonical URL, and normalized title|artist
        val seenUrls = HashSet<String>()
        val seenIds = HashSet<Long>()
        val seenTitleArtist = HashSet<String>()
        val deduped = mutableListOf<OnlineTrack>()
        for (track in validCandidates) {
            val normUrl = normalizeTrackUrl(track.contentUrl)
            val titleArtistKey = buildTrackIdentityKey(track.title, track.artist)
            if (normUrl in seenUrls || track.stableId in seenIds || titleArtistKey in seenTitleArtist) {
                continue
            }
            seenUrls.add(normUrl)
            seenIds.add(track.stableId)
            seenTitleArtist.add(titleArtistKey)
            deduped.add(track)
        }

        // 3. Enforce strict India region eligibility (never mix foreign/unknown-origin tracks in strict India mode)
        val regionEligible = if (profile.strictIndiaOnly) {
            RegionMusicEligibilityFilter.filterTracksForRegion(deduped, profile.countryCode)
        } else {
            deduped
        }
        if (regionEligible.isEmpty()) return emptyList()

        // 4. Multi-factor weighted scoring
        val maxFollowed = profile.followedArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxLiked = profile.likedArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxPlaylist = profile.playlistArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxHistory = profile.historyArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxRecent = profile.recentArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxLang = profile.preferredLanguages.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxGenre = profile.preferredGenres.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxOnboardingArtist = profile.onboardingArtistScores.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        val scored = regionEligible.map { track ->
            val normArtist = normalizeKey(track.artist)
            val normAlbum = normalizeKey(track.album)
            val normUrl = normalizeTrackUrl(track.contentUrl)
            val titleArtistKey = buildTrackIdentityKey(track.title, track.artist)
            val detectedLang = detectIndianLanguage(track.title, track.artist, track.album)

            val isAlreadyLiked = track.isFavorite ||
                normUrl in profile.likedTrackKeys ||
                titleArtistKey in profile.likedTrackKeys
            val isFamiliar = isAlreadyLiked ||
                normUrl in profile.familiarFavoriteKeys ||
                titleArtistKey in profile.familiarFavoriteKeys

            val followedScore = ((profile.followedArtistScores[normArtist] ?: 0.0) / maxFollowed) *
                weights.followedArtistWeight
            val likedArtistSim = ((profile.likedArtistScores[normArtist] ?: 0.0) / maxLiked) *
                weights.likedSongsSimilarityWeight
            val albumSim = if (normAlbum.isNotBlank() && normAlbum in profile.preferredAlbums) {
                0.75 * weights.likedSongsSimilarityWeight
            } else {
                0.0
            }
            val playlistSim = ((profile.playlistArtistScores[normArtist] ?: 0.0) / maxPlaylist) *
                weights.playlistSimilarityWeight
            val historyScore = ((profile.historyArtistScores[normArtist] ?: 0.0) / maxHistory) *
                weights.listeningHistoryWeight
            val recentScore = ((profile.recentArtistScores[normArtist] ?: 0.0) / maxRecent) *
                weights.recentListeningWeight

            val langScore = if (detectedLang != null && detectedLang in profile.preferredLanguages) {
                ((profile.preferredLanguages[detectedLang] ?: 0.0) / maxLang) * weights.genreAndLanguageWeight
            } else if (profile.discoverLanguage != DiscoverMusicLanguage.ALL &&
                detectedLang.equals(profile.discoverLanguage.label, ignoreCase = true)
            ) {
                weights.genreAndLanguageWeight
            } else {
                0.0
            }

            val trackText = normalizeKey("${track.title} ${track.album} ${track.artist}")
            val genreMatch = profile.preferredGenres.entries
                .firstOrNull { (g, _) -> containsTokenOrPhrase(trackText, g) }
                ?.let { (_, v) -> (v / maxGenre) * (weights.genreAndLanguageWeight * 0.7) }
                ?: 0.0
            val moodMatch = detectMoods(trackText)
                .mapNotNull { profile.preferredMoods[it] }
                .maxOrNull()
                ?.let { (it / 3.0).coerceAtMost(1.0) * (weights.genreAndLanguageWeight * 0.45) }
                ?: 0.0

            // Freshness & availability quality score
            var freshnessQuality = 0.0
            if (!track.artworkUrl.isNullOrBlank()) freshnessQuality += 0.35
            if (track.durationMs in 90_000L..420_000L) freshnessQuality += 0.35
            if (track.viewCount > 10_000L) {
                freshnessQuality += (ln(track.viewCount.toDouble()) / 25.0).coerceAtMost(0.55)
            }
            if (track.title.contains("2025") || track.title.contains("2026") ||
                track.title.contains("new", ignoreCase = true)
            ) {
                freshnessQuality += 0.25
            }
            val freshnessScore = freshnessQuality * weights.freshnessAndAvailabilityWeight

            // Interaction adjustments (Play Next, Repeated Listening, Early Skip, Disliked Artist)
            var interactionScore = 0.0
            val playNextArtistHits = interactions.playNextArtistCounts[normArtist] ?: 0
            if (playNextArtistHits > 0) {
                interactionScore += (playNextArtistHits.coerceAtMost(4) * 0.45) * weights.positiveInteractionBoost
            }
            val repeatedArtistHits = interactions.repeatedArtistCounts[normArtist] ?: 0
            if (repeatedArtistHits > 0) {
                interactionScore += (repeatedArtistHits.coerceAtMost(4) * 0.4) * weights.positiveInteractionBoost
            }
            val trackSkips = interactions.skippedTrackCounts[track.stableId] ?: 0
            val artistSkips = interactions.skippedArtistCounts[normArtist] ?: 0
            if (trackSkips > 0) interactionScore += trackSkips.coerceAtMost(4) * weights.skipPenalty
            if (artistSkips > 0) interactionScore += artistSkips.coerceAtMost(4) * (weights.skipPenalty * 0.45)

            val dislikedArtistHits = interactions.dislikedArtistCounts[normArtist] ?: 0
            if (dislikedArtistHits > 0) {
                interactionScore += dislikedArtistHits.coerceAtMost(3) * weights.dislikeArtistPenalty
            }

            // Avoid repeatedly recommending already-liked songs
            val alreadyLikedAdj = if (isAlreadyLiked) weights.alreadyLikedPenalty else 0.0
            // Avoid repeatedly showing the same recommendations across refreshes
            val repeatShownAdj = if (normUrl in recentlyShownSet) weights.previouslyShownPenalty else 0.0

            // Deterministic refresh rotation tie-breaker so tapping Refresh surfaces fresh tracks
            val rotationJitter = if (refreshGeneration > 0) {
                val hash = abs((normUrl + "#" + refreshGeneration).hashCode() % 1000) / 1000.0
                (hash - 0.5) * 1.15
            } else {
                0.0
            }

            val onboardingArtistScore = ((profile.onboardingArtistScores[normArtist] ?: 0.0) / maxOnboardingArtist) *
                weights.onboardingArtistWeight
            val onboardingLikedBoost = if (normUrl in profile.onboardingLikedTrackUrls) {
                weights.onboardingLikedTrackBoost
            } else {
                0.0
            }
            val onboardingPassedHits = profile.onboardingPassedArtistCounts[normArtist] ?: 0
            if (onboardingPassedHits > 0) {
                interactionScore += onboardingPassedHits.coerceAtMost(3) * weights.onboardingPassedArtistPenalty
            }

            val totalScore = followedScore + likedArtistSim + albumSim + playlistSim +
                historyScore + recentScore + langScore + genreMatch + moodMatch +
                onboardingArtistScore + onboardingLikedBoost +
                freshnessScore + interactionScore + alreadyLikedAdj + repeatShownAdj + rotationJitter

            val reasonLabel = resolveReasonLabel(
                profile = profile,
                normArtist = normArtist,
                displayArtist = track.artist,
                followedScore = followedScore,
                likedScore = likedArtistSim + albumSim + onboardingArtistScore,
                playlistScore = playlistSim,
                historyScore = historyScore + recentScore,
                detectedLang = detectedLang,
                isFamiliar = isFamiliar
            )

            RecommendedSongItem(
                track = track,
                score = totalScore,
                reasonLabel = reasonLabel,
                isFamiliarFavorite = isFamiliar,
                detectedLanguage = detectedLang
            )
        }.sortedByDescending { it.score }

        // 5. Enforce artist diversity & mix familiar favorites with fresh discoveries
        val target = weights.targetCount.coerceAtLeast(1)
        val maxFamiliar = (target * weights.familiarFavoriteRatio).toInt().coerceAtLeast(1)
        val maxPerArtist = weights.maxTracksPerArtist.coerceAtLeast(1)

        val selected = mutableListOf<RecommendedSongItem>()
        val artistCounts = HashMap<String, Int>()
        var familiarCount = 0

        fun tryAdd(item: RecommendedSongItem, allowOverFamiliar: Boolean): Boolean {
            if (selected.size >= target) return false
            val artistKey = normalizeKey(item.track.artist).ifBlank { "unknown_${item.track.stableId}" }
            val currentArtistCount = artistCounts[artistKey] ?: 0
            if (currentArtistCount >= maxPerArtist) return false
            if (item.isFamiliarFavorite && !allowOverFamiliar && familiarCount >= maxFamiliar) return false
            selected.add(item)
            artistCounts[artistKey] = currentArtistCount + 1
            if (item.isFamiliarFavorite) familiarCount++
            return true
        }

        // First pass: strictly honor artist diversity AND discovery/familiar ratio
        for (item in scored) {
            if (selected.size >= target) break
            tryAdd(item, allowOverFamiliar = false)
        }

        // Second pass: if pool was small, allow remaining diverse familiar tracks to fill remaining slots
        if (selected.size < target) {
            val selectedIds = selected.map { it.track.stableId }.toHashSet()
            for (item in scored) {
                if (selected.size >= target) break
                if (item.track.stableId in selectedIds) continue
                if (tryAdd(item, allowOverFamiliar = true)) {
                    selectedIds.add(item.track.stableId)
                }
            }
        }

        return selected
    }

    private fun resolveReasonLabel(
        profile: SonoraUserTasteProfile,
        normArtist: String,
        displayArtist: String,
        followedScore: Double,
        likedScore: Double,
        playlistScore: Double,
        historyScore: Double,
        detectedLang: String?,
        isFamiliar: Boolean
    ): String {
        if (!profile.hasPersonalizationSignals) {
            return if (!detectedLang.isNullOrBlank()) "$detectedLang Discovery" else "Discovery Pick"
        }
        val playlistName = profile.playlistNamesByArtist[normArtist]
        return when {
            followedScore >= 1.0 -> "Followed Artist"
            likedScore >= 0.9 && !isFamiliar -> "Similar to Liked"
            playlistScore >= 0.8 && !playlistName.isNullOrBlank() -> "Fits $playlistName"
            playlistScore >= 0.8 -> "Playlist Match"
            historyScore >= 0.9 && isFamiliar -> "Favorite Replay"
            historyScore >= 0.9 -> "Based on History"
            !detectedLang.isNullOrBlank() && detectedLang in profile.preferredLanguages -> "$detectedLang Pick"
            !detectedLang.isNullOrBlank() -> "$detectedLang Discovery"
            displayArtist.isNotBlank() -> "Picked for You"
            else -> "Fresh Discovery"
        }
    }
}
