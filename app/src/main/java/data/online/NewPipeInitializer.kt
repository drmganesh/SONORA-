package data.online

import android.content.Context
import android.util.Log
import domain.online.OnlineMusicProvider
import domain.online.OnlineSearchFilter
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/**
 * Ensures NewPipeExtractor is initialized exactly once in the application lifecycle,
 * exposes dynamically discovered services, and manages localization/country settings.
 */
object NewPipeInitializer {

    private const val TAG = "NewPipeInitializer"

    @Volatile
    private var initialized = false

    @Volatile
    private var downloaderInstance: NewPipeDownloader? = null

    private val lock = Any()

    fun ensureInitialized(context: Context? = null) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            try {
                val dl = NewPipeDownloader()
                downloaderInstance = dl
                val (localization, country) = resolveInitialLocalization(context)
                NewPipe.init(dl, localization, country)
                initialized = true
                Log.i(TAG, "NewPipeExtractor initialized (lang=${localization.languageCode}, country=${country.countryCode})")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed initializing NewPipeExtractor", t)
            }
        }
    }

    fun getDownloader(): NewPipeDownloader {
        ensureInitialized(null)
        return downloaderInstance ?: synchronized(lock) {
            downloaderInstance ?: NewPipeDownloader().also { downloaderInstance = it }
        }
    }

    internal fun resetForTesting() {
        synchronized(lock) {
            initialized = false
            downloaderInstance = null
        }
    }

    /**
     * Updates preferred localization and content country safely after single-time initialization.
     */
    fun updateLocalization(languageCode: String, countryCode: String) {
        ensureInitialized(null)
        val cleanLang = normalizeLanguage(languageCode)
        val cleanCountry = normalizeCountry(countryCode)
        NewPipe.setPreferredLocalization(Localization(cleanLang, cleanCountry))
        NewPipe.setPreferredContentCountry(ContentCountry(cleanCountry))
        Log.i(TAG, "NewPipe localization updated (lang=$cleanLang, country=$cleanCountry)")
    }

    fun currentCountryCode(): String {
        ensureInitialized(null)
        return runCatching { NewPipe.getPreferredContentCountry()?.countryCode }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
    }

    fun currentLanguageCode(): String {
        ensureInitialized(null)
        return runCatching { NewPipe.getPreferredLocalization()?.languageCode }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: domain.online.OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE
    }

    private fun resolveInitialLocalization(context: Context?): Pair<Localization, ContentCountry> {
        if (context != null) {
            val prefs = data.preferences.OnlineRegionPreferences(context)
            val lang = normalizeLanguage(prefs.getInitialLanguageCode())
            val country = normalizeCountry(prefs.getInitialCountryCode())
            return Localization(lang, country) to ContentCountry(country)
        }
        return Localization(
            domain.online.OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE,
            domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
        ) to ContentCountry(domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE)
    }

    private fun normalizeCountry(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
            trimmed.uppercase(Locale.ROOT)
        } else {
            domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
        }
    }

    private fun normalizeLanguage(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.length in 2..3 && trimmed.all { it.isLetter() }) {
            trimmed.lowercase(Locale.ROOT)
        } else {
            domain.online.OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE
        }
    }

    /**
     * Dynamically discovers supported streaming services from NewPipe's [ServiceList].
     * Also surfaces a Music-focused mode backed by YouTube's official `music_*` search filters.
     */
    fun discoverProviders(): List<OnlineMusicProvider> {
        ensureInitialized(null)
        val result = mutableListOf<OnlineMusicProvider>()

        val ytService = runCatching { ServiceList.YouTube }.getOrNull()
        if (ytService != null) {
            val ytMusicFilters = mapContentFilters(ytService, musicMode = true)
            result.add(
                OnlineMusicProvider(
                    serviceId = ytService.serviceId,
                    key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                    displayName = "YT Music",
                    shortLabel = "Music",
                    baseUrl = ytService.baseUrl,
                    supportedFilters = ytMusicFilters,
                    defaultFilter = OnlineSearchFilter.SONGS,
                    supportsSuggestions = hasSuggestions(ytService),
                    supportsDiscoveryFeed = hasKiosks(ytService),
                    availableKiosks = safeKioskIds(ytService),
                    isMusicFocused = true
                )
            )
            result.add(
                OnlineMusicProvider(
                    serviceId = ytService.serviceId,
                    key = OnlineMusicProvider.KEY_YOUTUBE,
                    displayName = "YouTube",
                    shortLabel = "YouTube",
                    baseUrl = ytService.baseUrl,
                    supportedFilters = mapContentFilters(ytService, musicMode = false),
                    defaultFilter = OnlineSearchFilter.ALL,
                    supportsSuggestions = hasSuggestions(ytService),
                    supportsDiscoveryFeed = hasKiosks(ytService),
                    availableKiosks = safeKioskIds(ytService),
                    isMusicFocused = false
                )
            )
        }

        val allServices = runCatching { ServiceList.all() }.getOrElse { emptyList() }
        for (service in allServices) {
            if (ytService != null && service.serviceId == ytService.serviceId) continue
            val mappedKey = when (service.serviceId) {
                ServiceList.SoundCloud.serviceId -> OnlineMusicProvider.KEY_SOUNDCLOUD
                ServiceList.Bandcamp.serviceId -> OnlineMusicProvider.KEY_BANDCAMP
                ServiceList.PeerTube.serviceId -> OnlineMusicProvider.KEY_PEERTUBE
                ServiceList.MediaCCC.serviceId -> OnlineMusicProvider.KEY_MEDIA_CCC
                else -> "service_${service.serviceId}"
            }
            val display = when (service.serviceId) {
                ServiceList.SoundCloud.serviceId -> "SoundCloud"
                ServiceList.Bandcamp.serviceId -> "Bandcamp"
                ServiceList.PeerTube.serviceId -> "PeerTube"
                ServiceList.MediaCCC.serviceId -> "media.ccc.de"
                else -> service.serviceInfo.name
            }
            val filters = mapContentFilters(service, musicMode = false)
            result.add(
                OnlineMusicProvider(
                    serviceId = service.serviceId,
                    key = mappedKey,
                    displayName = display,
                    shortLabel = display,
                    baseUrl = service.baseUrl,
                    supportedFilters = filters,
                    defaultFilter = filters.firstOrNull() ?: OnlineSearchFilter.ALL,
                    supportsSuggestions = hasSuggestions(service),
                    supportsDiscoveryFeed = hasKiosks(service),
                    availableKiosks = safeKioskIds(service),
                    isMusicFocused = mappedKey == OnlineMusicProvider.KEY_SOUNDCLOUD ||
                        mappedKey == OnlineMusicProvider.KEY_BANDCAMP
                )
            )
        }
        return result
    }

    fun getServiceById(serviceId: Int): StreamingService? {
        ensureInitialized(null)
        return runCatching { NewPipe.getService(serviceId) }.getOrNull()
    }

    fun getServiceForProviderKey(providerKey: String): StreamingService {
        ensureInitialized(null)
        return when (providerKey) {
            OnlineMusicProvider.KEY_YOUTUBE_MUSIC, OnlineMusicProvider.KEY_YOUTUBE -> ServiceList.YouTube
            OnlineMusicProvider.KEY_SOUNDCLOUD -> ServiceList.SoundCloud
            OnlineMusicProvider.KEY_BANDCAMP -> ServiceList.Bandcamp
            OnlineMusicProvider.KEY_PEERTUBE -> ServiceList.PeerTube
            OnlineMusicProvider.KEY_MEDIA_CCC -> ServiceList.MediaCCC
            else -> {
                val id = providerKey.removePrefix("service_").toIntOrNull()
                if (id != null) {
                    runCatching { NewPipe.getService(id) }.getOrElse { ServiceList.YouTube }
                } else {
                    ServiceList.YouTube
                }
            }
        }
    }

    fun resolveNewPipeContentFilters(
        providerKey: String,
        filter: OnlineSearchFilter,
        service: StreamingService
    ): List<String> {
        val available = runCatching { service.searchQHFactory.availableContentFilter.toList() }
            .getOrElse { emptyList() }
        if (available.isEmpty()) return emptyList()

        val desiredCandidates: List<String> = if (providerKey == OnlineMusicProvider.KEY_YOUTUBE_MUSIC) {
            when (filter) {
                OnlineSearchFilter.ALL -> listOf("music_songs", "all")
                OnlineSearchFilter.SONGS -> listOf("music_songs", "videos")
                OnlineSearchFilter.ARTISTS -> listOf("music_artists", "channels")
                OnlineSearchFilter.ALBUMS -> listOf("music_albums", "playlists")
                OnlineSearchFilter.PLAYLISTS -> listOf("music_playlists", "playlists")
                OnlineSearchFilter.VIDEOS -> listOf("music_videos", "videos")
            }
        } else {
            when (filter) {
                OnlineSearchFilter.ALL -> listOf("all")
                OnlineSearchFilter.SONGS -> listOf("tracks", "videos", "music_songs", "all")
                OnlineSearchFilter.ARTISTS -> listOf("users", "artists", "channels", "music_artists")
                OnlineSearchFilter.ALBUMS -> listOf("albums", "music_albums", "playlists")
                OnlineSearchFilter.PLAYLISTS -> listOf("playlists", "music_playlists")
                OnlineSearchFilter.VIDEOS -> listOf("videos", "all")
            }
        }

        val matched = desiredCandidates.firstOrNull { candidate ->
            available.any { it.equals(candidate, ignoreCase = true) }
        }
        return if (matched != null) {
            val exact = available.first { it.equals(matched, ignoreCase = true) }
            listOf(exact)
        } else {
            emptyList()
        }
    }

    private fun mapContentFilters(service: StreamingService, musicMode: Boolean): List<OnlineSearchFilter> {
        val available = runCatching { service.searchQHFactory.availableContentFilter.toList() }
            .getOrElse { emptyList() }
        val filters = mutableListOf<OnlineSearchFilter>()
        if (musicMode) {
            if (available.any { it.equals("music_songs", true) }) filters.add(OnlineSearchFilter.SONGS)
            if (available.any { it.equals("music_artists", true) || it.equals("channels", true) }) filters.add(OnlineSearchFilter.ARTISTS)
            if (available.any { it.equals("music_albums", true) }) filters.add(OnlineSearchFilter.ALBUMS)
            if (available.any { it.equals("music_playlists", true) }) filters.add(OnlineSearchFilter.PLAYLISTS)
            if (available.any { it.equals("music_videos", true) }) filters.add(OnlineSearchFilter.VIDEOS)
            if (filters.isEmpty()) filters.add(OnlineSearchFilter.ALL)
            return filters
        }

        filters.add(OnlineSearchFilter.ALL)
        if (available.any { it.equals("tracks", true) || it.equals("videos", true) || it.equals("music_songs", true) }) {
            filters.add(OnlineSearchFilter.SONGS)
        }
        if (available.any { it.equals("users", true) || it.equals("artists", true) || it.equals("channels", true) }) {
            filters.add(OnlineSearchFilter.ARTISTS)
        }
        if (available.any { it.equals("albums", true) || it.equals("music_albums", true) }) {
            filters.add(OnlineSearchFilter.ALBUMS)
        }
        if (available.any { it.equals("playlists", true) }) {
            filters.add(OnlineSearchFilter.PLAYLISTS)
        }
        if (available.any { it.equals("videos", true) } && service.serviceId == ServiceList.YouTube.serviceId) {
            filters.add(OnlineSearchFilter.VIDEOS)
        }
        return filters.distinct()
    }

    private fun hasSuggestions(service: StreamingService): Boolean =
        runCatching { service.getSuggestionExtractor() != null }.getOrDefault(false)

    private fun hasKiosks(service: StreamingService): Boolean =
        runCatching { !service.getKioskList()?.availableKiosks.isNullOrEmpty() }.getOrDefault(false)

    private fun safeKioskIds(service: StreamingService): List<String> =
        runCatching { service.getKioskList()?.availableKiosks?.toList().orEmpty() }.getOrElse { emptyList() }
}
