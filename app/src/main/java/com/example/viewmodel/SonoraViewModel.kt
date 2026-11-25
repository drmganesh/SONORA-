package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.audiofx.PresetReverb
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SonoraAudioEngine
import com.example.data.LocalMusicScanner
import com.example.data.PlaylistEntity
import com.example.data.PlaylistSongCrossRef
import com.example.data.SongMetadataEntity
import com.example.data.SonoraDatabase
import com.example.data.SonoraPreferences
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.PlayerStyle
import data.preferences.PlayerStylePreferences
import ui.player.seekbar.SeekbarStyle
import data.preferences.SeekbarPreferences
import com.example.model.DynamicThemeMode
import data.preferences.ThemePreferences
import data.cache.AlbumColorCache
import domain.color.AlbumArtColorExtractor
import domain.color.DynamicThemeGenerator
import ui.theme.DynamicColorPalette
import com.example.util.AlbumArtHelper
import kotlinx.coroutines.withContext
import com.example.model.OnlinePlaybackStage
import com.example.model.PlaybackInfo
import com.example.model.Playlist
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.model.SortOption
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import com.example.model.ThemeMode
import com.example.model.VisualizerStyle
import com.example.service.SonoraMusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import data.online.NetworkConnectivityMonitor
import data.online.NewPipeInitializer
import data.online.OnlineMusicRepository
import data.online.OnlineTrackMapper
import domain.online.AudioQualityPreference
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineCollectionDetails
import domain.online.OnlineErrorKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationEngine
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineSearchPageResult
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.defaultDiscoverSectionsState
import domain.home.HomeMusicMode
import domain.home.LocalLibraryStats
import domain.online.OnlineItemKind
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import playback.UnifiedPlaybackController
import java.io.File

sealed interface OnlineSearchUiState {
    data object Idle : OnlineSearchUiState
    data class Loading(val query: String) : OnlineSearchUiState
    data class Success(
        val page: OnlineSearchPageResult,
        val items: List<OnlineTrack>,
        val isLoadingMore: Boolean = false
    ) : OnlineSearchUiState
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val isRetryable: Boolean = true
    ) : OnlineSearchUiState
}

sealed interface OnlineCollectionUiState {
    data object Idle : OnlineCollectionUiState
    data class Loading(val title: String) : OnlineCollectionUiState
    data class Success(
        val details: OnlineCollectionDetails,
        val sourceItem: OnlineTrack? = null
    ) : OnlineCollectionUiState
    data class Error(val message: String, val item: OnlineTrack) : OnlineCollectionUiState
}

sealed interface DownloadFlowUiState {
    data object Idle : DownloadFlowUiState
    data class AskFolderLocation(
        val pendingTrack: OnlineTrack?,
        val suggestedInitialUri: android.net.Uri? = null,
        val reasonMessage: String? = null
    ) : DownloadFlowUiState
    data class QualitySelector(
        val data: domain.download.DownloadQualitySheetData
    ) : DownloadFlowUiState
}

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SonoraViewModel"
    }

    val preferences = SonoraPreferences(application)

    private val db = SonoraDatabase.getDatabase(application)
    private val dao = db.dao()
    private val scanner = LocalMusicScanner(application, dao)
    private val connectivityMonitor = NetworkConnectivityMonitor(application)
    val onlineRepository = OnlineMusicRepository(dao, connectivityMonitor)
    val lyricsPreferences = data.preferences.LyricsPreferences(application)
    val lyricsRepository = data.lyrics.LyricsRepository(application, dao)
    val videoAccountPreferences = data.preferences.VideoAccountPreferences(application)
    val experiencePreferences = data.preferences.ExperiencePreferences(application)
    val videoExperienceSettings: StateFlow<data.preferences.VideoExperienceSettings> =
        experiencePreferences.videoExperienceFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            data.preferences.VideoExperienceSettings()
        )
    val musicExperienceSettings: StateFlow<data.preferences.MusicExperienceSettings> =
        experiencePreferences.musicExperienceFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            data.preferences.MusicExperienceSettings()
        )
    val musicQueueRepository = data.queue.MusicQueueRepository(dao)
    val onlineVideoRepository = data.video.OnlineVideoRepository(
        dao = dao,
        dataSource = data.video.OnlineVideoDataSource(connectivityMonitor)
    )
    val videoController: viewmodel.OnlineVideoController by lazy {
        viewmodel.OnlineVideoController(
            context = application,
            scope = viewModelScope,
            repository = onlineVideoRepository,
            accountPreferences = videoAccountPreferences,
            getCountryCode = { _onlineContentCountry.value },
            getLanguageCode = { _onlineContentLanguage.value },
            onPauseMusicForVideo = { pausePlayback() },
            onActiveVideoArtworkChanged = { _ ->
                if (_homeMusicMode.value == HomeMusicMode.ONLINE_VIDEO) {
                    updateDynamicThemeForCurrentMode()
                }
            },
            onPlayChannelAudioInBackground = { videos ->
                val tracks = videos.map { video ->
                    OnlineTrack(
                        stableId = OnlineTrackMapper.computeStableId("youtube", video.contentUrl),
                        serviceId = 0,
                        providerKey = "youtube",
                        providerName = "YouTube",
                        contentUrl = video.contentUrl,
                        title = video.title,
                        artist = video.channelName,
                        artistUrl = video.channelUrl,
                        durationMs = video.durationSeconds * 1000L,
                        artworkUrl = video.thumbnailUrl,
                        viewCount = video.viewCount ?: -1L,
                        isLive = video.isLive
                    )
                }
                if (tracks.isNotEmpty()) {
                    playOnlineTrack(tracks.first(), tracks)
                }
            },
            experiencePreferences = experiencePreferences,
            isMeteredNetwork = { connectivityMonitor.isMeteredConnection.value },
            dao = dao
        )
    }

    private var musicService: SonoraMusicService? = null
    private var unifiedPlaybackController: UnifiedPlaybackController? = null
    private var isBound = false

    val folderFilterPreferences = data.preferences.FolderFilterPreferences(application)
    val folderFilterSettings: StateFlow<domain.model.FolderFilterSettings>
        get() = folderFilterPreferences.settings

    private val _rawScannedSongs = MutableStateFlow<List<Song>>(emptyList())
    val rawScannedSongs: StateFlow<List<Song>> = _rawScannedSongs.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _queuedItems = MutableStateFlow<List<data.queue.QueuedMusicItem>>(emptyList())
    val queuedItems: StateFlow<List<data.queue.QueuedMusicItem>> = _queuedItems.asStateFlow()

    private val _queueIndex = MutableStateFlow(preferences.lastQueueIndex)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    val playbackLifecyclePreferences = data.preferences.PlaybackLifecyclePreferences(application)
    val backgroundPlaybackSettings: StateFlow<data.preferences.BackgroundPlaybackSettings> =
        playbackLifecyclePreferences.backgroundSettingsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, data.preferences.BackgroundPlaybackSettings())
    val sessionManagementSettings: StateFlow<data.preferences.SessionManagementSettings> =
        playbackLifecyclePreferences.sessionSettingsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, data.preferences.SessionManagementSettings())

    val listeningAnalyticsRepository = data.analytics.ListeningAnalyticsRepository(dao)
    private val listeningTimeTracker = domain.analytics.ListeningTimeTracker()

    private val _analyticsTimeRange = MutableStateFlow(domain.analytics.AnalyticsTimeRange.LAST_30_DAYS)
    val analyticsTimeRange: StateFlow<domain.analytics.AnalyticsTimeRange> = _analyticsTimeRange.asStateFlow()

    val listeningAnalyticsDashboard: StateFlow<domain.analytics.ListeningAnalyticsDashboardState> =
        combine(listeningAnalyticsRepository.allEventsFlow, _analyticsTimeRange) { events, range ->
            domain.analytics.ListeningAnalyticsCalculator.calculate(events, range)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            domain.analytics.ListeningAnalyticsDashboardState()
        )

    private val _sessionRestoreNotice = MutableStateFlow<String?>(null)
    val sessionRestoreNotice: StateFlow<String?> = _sessionRestoreNotice.asStateFlow()

    private var activePlaybackContextType: String = "QUEUE"
    private var activePlaybackContextLabel: String = ""

    // --- Advanced Music Discovery & Current-Song Radio State ---
    private val _selectedMoodActivity = MutableStateFlow(data.online.MusicMoodActivity.FOCUS)
    val selectedMoodActivity: StateFlow<data.online.MusicMoodActivity> = _selectedMoodActivity.asStateFlow()

    private val _moodDiscoveryTracks = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val moodDiscoveryTracks: StateFlow<List<OnlineTrack>> = _moodDiscoveryTracks.asStateFlow()

    private val _isLoadingMoodDiscovery = MutableStateFlow(false)
    val isLoadingMoodDiscovery: StateFlow<Boolean> = _isLoadingMoodDiscovery.asStateFlow()

    private val _selectedIndiaCategory = MutableStateFlow(data.online.IndiaMusicCategory.TRENDING_INDIA)
    val selectedIndiaCategory: StateFlow<data.online.IndiaMusicCategory> = _selectedIndiaCategory.asStateFlow()

    private val _indiaDiscoveryTracks = MutableStateFlow<List<OnlineTrack>>(emptyList())
    val indiaDiscoveryTracks: StateFlow<List<OnlineTrack>> = _indiaDiscoveryTracks.asStateFlow()

    private val _isLoadingIndiaDiscovery = MutableStateFlow(false)
    val isLoadingIndiaDiscovery: StateFlow<Boolean> = _isLoadingIndiaDiscovery.asStateFlow()

    private val _currentSongRecommendations = MutableStateFlow<List<Song>>(emptyList())
    val currentSongRecommendations: StateFlow<List<Song>> = _currentSongRecommendations.asStateFlow()

    private val _isLoadingCurrentSongRecommendations = MutableStateFlow(false)
    val isLoadingCurrentSongRecommendations: StateFlow<Boolean> = _isLoadingCurrentSongRecommendations.asStateFlow()

    private val _songRadioSession = MutableStateFlow(data.online.SongRadioSession())
    val songRadioSession: StateFlow<data.online.SongRadioSession> = _songRadioSession.asStateFlow()

    private val _pendingVideoDownload =
        MutableStateFlow<Pair<domain.video.OnlineVideoItem, domain.video.ResolvedVideoDetails>?>(null)
    val pendingVideoDownload: StateFlow<Pair<domain.video.OnlineVideoItem, domain.video.ResolvedVideoDetails>?> =
        _pendingVideoDownload.asStateFlow()

    private var pendingVideoDownloadAfterFolderPick: Pair<domain.video.OnlineVideoItem, domain.video.ResolvedVideoDetails>? = null
    private var queuePersistJob: Job? = null
    private var moodDiscoveryJob: Job? = null
    private var indiaDiscoveryJob: Job? = null
    private var currentSongRecsJob: Job? = null
    private var radioTopUpJob: Job? = null
    private var lastQueuePositionPersistMs: Long = 0L

    private val _playbackInfo = MutableStateFlow(
        PlaybackInfo(
            repeatMode = preferences.repeatMode,
            shuffleMode = preferences.shuffleMode
        )
    )
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption: MutableStateFlow<SortOption> = MutableStateFlow(preferences.sortOption)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // --- Online Music Streaming State (NewPipe Extractor + Media3) ---
    val isNetworkOnline: StateFlow<Boolean> = connectivityMonitor.isOnline
    val isMeteredNetwork: StateFlow<Boolean> = connectivityMonitor.isMeteredConnection

    private val _onlineProviders = MutableStateFlow<List<OnlineMusicProvider>>(emptyList())
    val onlineProviders: StateFlow<List<OnlineMusicProvider>> = _onlineProviders.asStateFlow()

    private val _selectedOnlineProvider = MutableStateFlow<OnlineMusicProvider?>(null)
    val selectedOnlineProvider: StateFlow<OnlineMusicProvider?> = _selectedOnlineProvider.asStateFlow()

    private val _selectedOnlineFilter = MutableStateFlow(OnlineSearchFilter.SONGS)
    val selectedOnlineFilter: StateFlow<OnlineSearchFilter> = _selectedOnlineFilter.asStateFlow()

    private val _onlineSearchQuery = MutableStateFlow("")
    val onlineSearchQuery: StateFlow<String> = _onlineSearchQuery.asStateFlow()

    private val _onlineSearchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val onlineSearchSuggestions: StateFlow<List<String>> = _onlineSearchSuggestions.asStateFlow()

    private val _onlineSearchState = MutableStateFlow<OnlineSearchUiState>(OnlineSearchUiState.Idle)
    val onlineSearchState: StateFlow<OnlineSearchUiState> = _onlineSearchState.asStateFlow()

    private val _discoverSectionsState =
        MutableStateFlow<Map<DiscoverSectionType, DiscoverSectionState>>(defaultDiscoverSectionsState())
    val discoverSectionsState: StateFlow<Map<DiscoverSectionType, DiscoverSectionState>> =
        _discoverSectionsState.asStateFlow()

    private val _discoverRefreshCount = MutableStateFlow(0L)
    val discoverRefreshCount: StateFlow<Long> = _discoverRefreshCount.asStateFlow()

    // --- Personalized "Sonora Recommend" Section (Local-Only Personalization + Real NewPipe Candidates) ---
    val sonoraRecommendCachePrefs = data.preferences.SonoraRecommendationCachePreferences(application)
    private val sonoraRecommendationCoordinator by lazy {
        data.online.SonoraRecommendationCoordinator.fromRepository(
            cachePreferences = sonoraRecommendCachePrefs,
            onlineRepository = onlineRepository
        )
    }
    private val _sonoraRecommendState =
        MutableStateFlow(domain.online.SonoraRecommendUiState(isLoading = true))
    val sonoraRecommendState: StateFlow<domain.online.SonoraRecommendUiState> =
        _sonoraRecommendState.asStateFlow()
    private var sonoraRecommendJob: Job? = null
    private var sonoraRecommendDebounceJob: Job? = null
    private var sonoraRecommendGeneration: Int = 0

    private val _onlineCollectionState = MutableStateFlow<OnlineCollectionUiState>(OnlineCollectionUiState.Idle)
    val onlineCollectionState: StateFlow<OnlineCollectionUiState> = _onlineCollectionState.asStateFlow()

    private val onlineRegionPrefs = data.preferences.OnlineRegionPreferences(application)
    private val homeModePrefs = data.preferences.HomeModePreferences(application)
    private val libraryModePrefs = data.preferences.LibraryModePreferences(application)
    private val ambientEffectsPrefs = data.preferences.AmbientEffectsPreferences(application)

    val snowfallSettings: StateFlow<data.preferences.SnowfallSettings> =
        ambientEffectsPrefs.snowfallSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = data.preferences.SnowfallSettings.DEFAULT
        )

    fun updateSnowfallSettings(transform: (data.preferences.SnowfallSettings) -> data.preferences.SnowfallSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            ambientEffectsPrefs.updateSettings(transform)
        }
    }

    fun resetSnowfallSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            ambientEffectsPrefs.resetToDefaults()
        }
    }

    // --- Weather-Synced Ambient Effects Engine (5 Fixed Odisha Locations, Open-Meteo Public API, Zero GPS) ---
    val weatherRepository = data.weather.WeatherRepository(ambientEffectsPrefs)

    val weatherAmbientSettings: StateFlow<data.preferences.WeatherAmbientSettings> =
        ambientEffectsPrefs.weatherAmbientSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = data.preferences.WeatherAmbientSettings.DEFAULT
        )

    val weatherFetchState: StateFlow<domain.weather.WeatherFetchState> =
        weatherRepository.selectedWeatherState

    val locationWeatherSummaries: StateFlow<Map<String, domain.weather.WeatherSnapshot>> =
        weatherRepository.cachedSnapshotsByLocation

    fun selectWeatherLocation(location: domain.weather.OdishaWeatherLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            ambientEffectsPrefs.updateWeatherSettings { it.copy(selectedLocation = location) }
            weatherRepository.selectAndRefreshLocation(location = location, force = false)
        }
    }

    fun updateWeatherAmbientSettings(
        transform: (data.preferences.WeatherAmbientSettings) -> data.preferences.WeatherAmbientSettings
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val previousSettings = weatherAmbientSettings.value
            val updated = transform(previousSettings)
            ambientEffectsPrefs.updateWeatherSettings(transform)
            if (updated.mode == domain.weather.WeatherEffectMode.AUTOMATIC &&
                (previousSettings.mode != domain.weather.WeatherEffectMode.AUTOMATIC ||
                    weatherFetchState.value.latestSnapshot == null)
            ) {
                weatherRepository.refreshWeather(updated.selectedLocation, force = false)
            }
        }
    }

    fun resetWeatherAmbientSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            ambientEffectsPrefs.resetWeatherSettingsToDefaults()
            weatherRepository.selectAndRefreshLocation(
                location = domain.weather.OdishaWeatherLocation.DEFAULT,
                force = false
            )
        }
    }

    fun refreshSelectedWeather(force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            weatherRepository.refreshWeather(
                location = weatherAmbientSettings.value.selectedLocation,
                force = force
            )
        }
    }

    fun prefetchAllOdishaLocationWeather() {
        viewModelScope.launch(Dispatchers.IO) {
            weatherRepository.refreshLocationSummariesIfStale(
                activeLocation = weatherAmbientSettings.value.selectedLocation
            )
        }
    }

    // --- Pan-India Hindu Festival Atmosphere Engine (Coexists with Weather, Zero Personal Permissions) ---
    val festivalAtmospherePrefs = data.preferences.FestivalAtmospherePreferences(application)
    val festivalRepository = data.festival.FestivalCalendarRepository(
        dao = db.festivalCalendarDao(),
        isNetworkAvailable = { isNetworkOnline.value }
    )

    val festivalSyncUiState: StateFlow<data.festival.FestivalSyncUiState> =
        festivalRepository.syncUiState

    val festivalAtmosphereSettings: StateFlow<data.preferences.FestivalAtmosphereSettings> =
        festivalAtmospherePrefs.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = data.preferences.FestivalAtmosphereSettings.DEFAULT
        )

    val festivalSyncStatus: StateFlow<data.festival.FestivalSyncStatus> =
        festivalRepository.syncStatusFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = data.festival.FestivalSyncStatus()
        )

    private val _festivalReferenceDate = MutableStateFlow(
        java.time.LocalDate.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
    )

    val activeFestivalResolution: StateFlow<domain.festival.ActiveFestivalResolution> =
        combine(
            festivalRepository.cachedRecordsFlow,
            festivalRepository.syncStatusFlow,
            festivalAtmosphereSettings,
            _festivalReferenceDate
        ) { records, syncStatus, settings, date ->
            domain.festival.FestivalDateResolver.resolveForDate(
                records = records,
                date = date,
                regionMode = settings.regionMode,
                selectedState = settings.selectedState,
                includeRegionalInAllIndia = settings.includeRegionalInAllIndia,
                observanceRule = settings.observanceRule,
                nowEpochMs = System.currentTimeMillis(),
                lastUpdatedEpochMs = syncStatus.lastSyncedEpochMs,
                isFromOfflineCache = syncStatus.isOfflineFallback
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = domain.festival.ActiveFestivalResolution.EMPTY
        )

    val upcomingCachedFestivals: StateFlow<List<domain.festival.FestivalRecord>> =
        combine(
            festivalRepository.cachedRecordsFlow,
            festivalAtmosphereSettings,
            _festivalReferenceDate
        ) { records, settings, date ->
            data.festival.FestivalCalendarRepository.filterUpcomingFestivals(
                records = records,
                fromDate = date,
                regionMode = settings.regionMode,
                selectedState = settings.selectedState,
                includeRegionalInAllIndia = settings.includeRegionalInAllIndia
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateFestivalAtmosphereSettings(
        transform: (data.preferences.FestivalAtmosphereSettings) -> data.preferences.FestivalAtmosphereSettings
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            festivalAtmospherePrefs.updateSettings(transform)
        }
    }

    fun resetFestivalAtmosphereSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            festivalAtmospherePrefs.resetToDefaults()
        }
    }

    /**
     * Called when SONORA opens or returns to foreground (`MainActivity.onStart`).
     * Uses the single [data.festival.FestivalCalendarSyncCoordinator] so:
     * - If not yet synced successfully on the current `Asia/Kolkata` calendar day, runs automatic daily sync.
     * - If already synced today, reuses cached Room data without downloading again.
     * - Never runs repeatedly during Compose recompositions or in-app screen navigation.
     * - Never interrupts audio playback.
     */
    fun onAppForegroundedForFestivalSync() {
        viewModelScope.launch(Dispatchers.IO) {
            // Never contend with the cold-start intro animation for CPU, Disk, or Network
            startupDeferredWorkGate.await()
            runCatching {
                _festivalReferenceDate.value = java.time.LocalDate.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
                val autoEnabled = runCatching {
                    festivalAtmospherePrefs.settingsFlow.first().automaticDailySyncEnabled
                }.getOrDefault(true)
                festivalRepository.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = autoEnabled)
            }.onFailure { e ->
                Log.w(TAG, "Automatic daily festival sync skipped: ${e.message}")
            }
        }
    }

    fun refreshFestivalCalendarNow(force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _festivalReferenceDate.value = java.time.LocalDate.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
            if (force) {
                festivalRepository.syncNowManually()
            } else {
                val autoEnabled = runCatching {
                    festivalAtmospherePrefs.settingsFlow.first().automaticDailySyncEnabled
                }.getOrDefault(true)
                festivalRepository.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = autoEnabled)
            }
        }
    }

    // --- Advanced Cache Management (Media3 SimpleCache + DataStore) ---
    val cacheManager: data.cache.SonoraMediaCacheManager =
        data.cache.SonoraMediaCacheManager.getInstance(application)

    val cacheSettings: StateFlow<data.cache.CacheSettings> = cacheManager.settings
    val cacheStorageSnapshot: StateFlow<data.cache.CacheStorageSnapshot> = cacheManager.storageSnapshot

    fun setCacheMusicEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.setCacheMusicEnabled(enabled)
        }
    }

    fun setCacheVideosEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.setCacheVideosEnabled(enabled)
        }
    }

    fun setPreloadUpcomingEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.setPreloadUpcomingEnabled(enabled)
            if (!enabled) {
                cacheManager.cancelBackgroundPreloadsForForegroundPlayback()
            }
        }
    }

    fun setMusicCacheLimitBytes(limitBytes: Long, clearExistingWhenZero: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.setMusicCacheLimitBytes(limitBytes, clearExistingWhenZero)
        }
    }

    fun clearMusicCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.clearMusicCache()
        }
    }

    fun clearVideoCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.clearVideoAndShortsCache()
        }
    }

    fun refreshCacheStorageSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.refreshStorageSnapshot()
        }
    }

    // Dedicated Library Repositories (Offline / Online / Downloads)
    val offlineLibraryRepository = data.library.OfflineLibraryRepository(
        context = application,
        scanner = scanner,
        dao = dao,
        folderFilterPreferences = folderFilterPreferences
    )
    val onlineLibraryRepository = data.library.OnlineLibraryRepository(
        dao = dao,
        onlineMusicRepository = onlineRepository
    )

    @Volatile
    var hasCompletedLaunchAnimation: Boolean = false

    private val startupDeferredWorkGate = CompletableDeferred<Unit>()

    val isStartupDeferredWorkReleased: Boolean
        get() = startupDeferredWorkGate.isCompleted

    /**
     * Releases deferred non-essential startup tasks (online discovery network fetches,
     * weather/festival calendar synchronization, download reconciliation, and full recommendation
     * scoring) once the intro animation finishes or is skipped so startup frames stay smooth.
     */
    fun onStartupIntroCompleted() {
        startupDeferredWorkGate.complete(Unit)
    }

    @Volatile
    private var userUpdatedHomeMode = false
    private val _homeMusicMode = MutableStateFlow(homeModePrefs.getInitialHomeMusicMode())
    val homeMusicMode: StateFlow<HomeMusicMode> = _homeMusicMode.asStateFlow()

    @Volatile
    private var userUpdatedLibraryMode = false
    private val _librarySectionMode = MutableStateFlow(libraryModePrefs.getInitialLibrarySectionMode())
    val librarySectionMode: StateFlow<domain.library.LibrarySectionMode> = _librarySectionMode.asStateFlow()

    private val _onlineLibrarySearchQuery = MutableStateFlow("")
    val onlineLibrarySearchQuery: StateFlow<String> = _onlineLibrarySearchQuery.asStateFlow()

    private val _onlineLibrarySortOption = MutableStateFlow(domain.library.OnlineLibrarySortOption.RECENTLY_SAVED)
    val onlineLibrarySortOption: StateFlow<domain.library.OnlineLibrarySortOption> = _onlineLibrarySortOption.asStateFlow()

    private val _onlineLibraryProviderFilter = MutableStateFlow<String?>(null)
    val onlineLibraryProviderFilter: StateFlow<String?> = _onlineLibraryProviderFilter.asStateFlow()

    private val _libraryFeedbackMessage = MutableStateFlow<String?>(null)
    val libraryFeedbackMessage: StateFlow<String?> = _libraryFeedbackMessage.asStateFlow()

    private val _onlineContentCountry = MutableStateFlow(onlineRegionPrefs.getInitialCountryCode())
    val onlineContentCountry: StateFlow<String> = _onlineContentCountry.asStateFlow()

    private val _onlineContentLanguage = MutableStateFlow(onlineRegionPrefs.getInitialLanguageCode())
    val onlineContentLanguage: StateFlow<String> = _onlineContentLanguage.asStateFlow()

    private val _discoverMusicLanguage = MutableStateFlow(onlineRegionPrefs.getInitialDiscoverLanguage())
    val discoverMusicLanguage: StateFlow<DiscoverMusicLanguage> = _discoverMusicLanguage.asStateFlow()

    private val _onlineQualityPreference = MutableStateFlow(preferences.onlineQualityPreference)
    val onlineQualityPreference: StateFlow<AudioQualityPreference> = _onlineQualityPreference.asStateFlow()

    private val _currentResolvedStream = MutableStateFlow<ResolvedOnlineStreamResult?>(null)
    val currentResolvedStream: StateFlow<ResolvedOnlineStreamResult?> = _currentResolvedStream.asStateFlow()

    val onlineSavedTracks: StateFlow<List<Song>> = onlineLibraryRepository.savedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineFavoriteTracks: StateFlow<List<Song>> = onlineLibraryRepository.favoriteTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedOnlineTracks: StateFlow<List<Song>> = onlineLibraryRepository.recentlyPlayedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineListeningHistory: StateFlow<List<Song>> = onlineLibraryRepository.listeningHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedOnlineAlbums: StateFlow<List<domain.library.SavedOnlineAlbum>> = onlineLibraryRepository.savedAlbumsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedOnlineArtists: StateFlow<List<domain.library.FollowedOnlineArtist>> = onlineLibraryRepository.followedArtistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlinePlaylists: StateFlow<List<domain.library.OnlinePlaylist>> = onlineLibraryRepository.onlinePlaylistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedOnlineAlbumIds: StateFlow<Set<Long>> = savedOnlineAlbums
        .map { list -> list.map { it.stableId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val followedOnlineArtistIds: StateFlow<Set<Long>> = followedOnlineArtists
        .map { list -> list.map { it.stableId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val savedOnlinePlaylistUrls: StateFlow<Set<String>> = onlinePlaylists
        .map { list -> list.mapNotNull { it.externalContentUrl?.takeIf { u -> u.isNotBlank() } }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allOnlineTracks: StateFlow<List<Song>> = onlineRepository.allOnlineTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineSearchHistory: StateFlow<List<String>> = onlineRepository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Music Download Manager State (SAF + WorkManager + Room + DataStore) ---
    val downloadCoordinator = data.download.DownloadManagerCoordinator.getInstance(application)
    val downloadRepository = data.library.DownloadRepository(downloadCoordinator, dao)

    val downloadTasks: StateFlow<List<domain.download.DownloadTask>> =
        downloadRepository.downloadTasksFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloadTasksCount: StateFlow<Int> =
        downloadRepository.activeDownloadTasksFlow
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedTracks: StateFlow<List<domain.download.DownloadedTrack>> =
        downloadRepository.downloadedTracksFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedMusicTracks: StateFlow<List<domain.download.DownloadedTrack>> =
        downloadCoordinator.downloadedMusicFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedVideos: StateFlow<List<domain.download.DownloadedTrack>> =
        downloadCoordinator.downloadedVideosFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongs: StateFlow<List<Song>> =
        downloadCoordinator.downloadedMusicFlow
            .map { list -> list.filter { !it.isMissingFile }.map { it.toSong() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val madeForYouCards: StateFlow<List<data.online.PersonalizedMusicMixCard>> =
        combine(
            recentlyPlayedOnlineTracks,
            onlineFavoriteTracks,
            _onlineContentCountry,
            _discoverMusicLanguage
        ) { recent, favs, country, lang ->
            data.online.MusicDiscoveryAndRadioEngine.buildMadeForYouCards(
                recentSongs = recent,
                favoriteSongs = favs,
                countryCode = country,
                discoverLanguage = lang
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val basedOnListeningCards: StateFlow<List<data.online.PersonalizedMusicMixCard>> =
        combine(
            recentlyPlayedOnlineTracks,
            onlineFavoriteTracks,
            _onlineContentCountry
        ) { recent, favs, country ->
            data.online.MusicDiscoveryAndRadioEngine.buildBasedOnListeningCards(
                recentSongs = recent,
                favoriteSongs = favs,
                countryCode = country
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSourceKeys: StateFlow<Set<String>> =
        downloadCoordinator.downloadedTracksFlow
            .map { list -> list.filter { !it.isMissingFile }.map { it.sourceKey }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val activeDownloadStateBySourceKey: StateFlow<Map<String, domain.download.DownloadState>> =
        downloadCoordinator.allTasksFlow
            .map { tasks ->
                tasks.filter { !it.state.isTerminal || it.state == domain.download.DownloadState.COMPLETED }
                    .associate { it.sourceKey to it.state }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val downloadStorageSummary: StateFlow<domain.download.DownloadStorageSummary> =
        downloadCoordinator.storageSummaryFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                domain.download.DownloadStorageSummary()
            )

    val downloadLocationConfig: StateFlow<data.preferences.DownloadLocationConfig> =
        downloadCoordinator.preferences.locationConfigFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                data.preferences.DownloadLocationConfig()
            )

    private val _downloadFlowState = MutableStateFlow<DownloadFlowUiState>(DownloadFlowUiState.Idle)
    val downloadFlowState: StateFlow<DownloadFlowUiState> = _downloadFlowState.asStateFlow()

    private val _downloadFeedbackMessage = MutableStateFlow<String?>(null)
    val downloadFeedbackMessage: StateFlow<String?> = _downloadFeedbackMessage.asStateFlow()

    private val _downloadSortOption = MutableStateFlow(domain.download.DownloadSortOption.RECENTLY_DOWNLOADED)
    val downloadSortOption: StateFlow<domain.download.DownloadSortOption> = _downloadSortOption.asStateFlow()

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private var downloadFormatResolveJob: Job? = null

    private val _onlineRecommendationProfile = MutableStateFlow(
        OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = emptyList(),
            countryCode = _onlineContentCountry.value,
            languageCode = _onlineContentLanguage.value,
            discoverLanguage = _discoverMusicLanguage.value
        )
    )
    val onlineRecommendationProfile: StateFlow<OnlineRecommendationProfile> =
        _onlineRecommendationProfile.asStateFlow()

    private var onlineSearchJob: Job? = null
    private var onlineSuggestionJob: Job? = null
    private val discoverSectionJobs = mutableMapOf<DiscoverSectionType, Job>()
    private val discoverLoadMoreJobs = mutableMapOf<DiscoverSectionType, Job>()
    private var discoverGeneration: Long = 0L
    private var activeDiscoverProviderKey: String? = null
    private var lastRecommendationSeedQueried: String? = null
    private var activeSearchSignature: String? = null
    private var onlineCollectionJob: Job? = null

    // Audio FX State (Restored from preferences)

    private val _bassBoost = MutableStateFlow(preferences.bassBoost) // 0..1000
    val bassBoost: StateFlow<Int> = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow(preferences.virtualizer) // 0..1000
    val virtualizer: StateFlow<Int> = _virtualizer.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(preferences.reverbPreset)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(preferences.loudnessGainMb) // 0..1000 millibels
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    // Adaptive 15-Band Equalizer Engine & States
    val adaptiveEqualizer = com.example.equalizer.AdaptiveEqualizerEngine.getInstance(application)
    val equalizerSettings: StateFlow<com.example.equalizer.AdaptiveEqualizerSettings> = adaptiveEqualizer.settings
    val equalizerResponseCurve: StateFlow<FloatArray> = adaptiveEqualizer.frequencyResponseCurve
    val isHardwareEqSupported: StateFlow<Boolean> = adaptiveEqualizer.isHardwareEqSupported

    val customEqPresets: StateFlow<List<com.example.data.CustomEqPresetEntity>> =
        dao.getCustomEqPresetsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEqualizerMasterEnabled(enabled: Boolean) = adaptiveEqualizer.setMasterEnabled(enabled)
    fun setEqualizerMode(mode: com.example.equalizer.EqualizerMode) = adaptiveEqualizer.setMode(mode)
    fun setEqualizerDynamicPad(tilt: Float, contour: Float) = adaptiveEqualizer.setDynamicPad(tilt, contour)
    fun setEqualizerMacroProfile(profile: com.example.equalizer.MacroProfile) = adaptiveEqualizer.setMacroProfile(profile)
    fun setEqualizerBandGain(index: Int, gainDb: Float) = adaptiveEqualizer.setManualBandGain(index, gainDb)
    fun setEqualizerVolumeBoost(boostDb: Float) = adaptiveEqualizer.setVolumeBoost(boostDb)
    fun selectEqualizerSoundProfile(profileId: String) = adaptiveEqualizer.selectSoundProfile(profileId)
    fun resetEqualizerToNeutral() = adaptiveEqualizer.resetToNeutral()
    fun applyStudioEqShelf(isBass: Boolean, levelDb: Float) = adaptiveEqualizer.applyStudioShelf(isBass, levelDb)

    fun saveCustomEqPreset(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val gains = equalizerSettings.value.getEffectiveGains()
        val json = org.json.JSONArray().apply {
            gains.forEach { put(it.toDouble()) }
        }.toString()
        val entity = com.example.data.CustomEqPresetEntity(
            name = clean,
            bandLevelsJson = json,
            bassBoost = _bassBoost.value,
            virtualizer = _virtualizer.value,
            reverbPreset = _reverbPreset.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertCustomEqPreset(entity)
        }
    }

    fun applyCustomEqPreset(preset: com.example.data.CustomEqPresetEntity) {
        val parsedGains = try {
            val arr = org.json.JSONArray(preset.bandLevelsJson)
            FloatArray(15) { i -> arr.optDouble(i, 0.0).toFloat() }
        } catch (_: Exception) {
            preset.bandLevelsJson.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
        }
        adaptiveEqualizer.applyCustomPresetGains(parsedGains, "user_${preset.name}")
        setBassBoost(preset.bassBoost)
        setVirtualizer(preset.virtualizer)
        setReverb(preset.reverbPreset)
    }

    fun deleteCustomEqPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCustomEqPreset(name)
        }
    }

    // 360° Spatial Audio & Audio Effects Studio State
    private val _spatialConfig = MutableStateFlow(preferences.spatialConfig)
    val spatialConfig: StateFlow<SpatialAudioConfig> = _spatialConfig.asStateFlow()

    private val _globalEffectsBypass = MutableStateFlow(preferences.globalEffectsBypass)
    val globalEffectsBypass: StateFlow<Boolean> = _globalEffectsBypass.asStateFlow()

    private val _orbit8dEnabled = MutableStateFlow(preferences.orbit8dEnabled)
    val orbit8dEnabled: StateFlow<Boolean> = _orbit8dEnabled.asStateFlow()

    private val _orbit8dSpeed = MutableStateFlow(preferences.orbit8dSpeed)
    val orbit8dSpeed: StateFlow<Float> = _orbit8dSpeed.asStateFlow()

    private val _orbit8dDepth = MutableStateFlow(preferences.orbit8dDepth)
    val orbit8dDepth: StateFlow<Float> = _orbit8dDepth.asStateFlow()

    private val _orbitAzimuthRad = MutableStateFlow(0f)
    val orbitAzimuthRad: StateFlow<Float> = _orbitAzimuthRad.asStateFlow()

    private val _isBassBoostSupported = MutableStateFlow(true)
    val isBassBoostSupported: StateFlow<Boolean> = _isBassBoostSupported.asStateFlow()

    private val _isVirtualizerSupported = MutableStateFlow(true)
    val isVirtualizerSupported: StateFlow<Boolean> = _isVirtualizerSupported.asStateFlow()

    private val _isReverbSupported = MutableStateFlow(true)
    val isReverbSupported: StateFlow<Boolean> = _isReverbSupported.asStateFlow()

    private val _isLoudnessSupported = MutableStateFlow(true)
    val isLoudnessSupported: StateFlow<Boolean> = _isLoudnessSupported.asStateFlow()

    private val _dspDiagnosticMessage = MutableStateFlow<String?>(null)
    val dspDiagnosticMessage: StateFlow<String?> = _dspDiagnosticMessage.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(preferences.playbackSpeed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Real-time Visualizer Flows
    private val _visualizerFrequencies = MutableStateFlow(FloatArray(32) { 0f })
    val visualizerFrequencies: StateFlow<FloatArray> = _visualizerFrequencies.asStateFlow()

    private val _waveformSamples = MutableStateFlow(FloatArray(64) { 0f })
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples.asStateFlow()

    private val _visualizerStyle = MutableStateFlow(VisualizerStyle.SPECTRUM_BARS)
    val visualizerStyle: StateFlow<VisualizerStyle> = _visualizerStyle.asStateFlow()

    // Theme & Audio Settings (Restored from preferences)
    private val _themeMode = MutableStateFlow(preferences.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(preferences.crossfadeSeconds)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _gaplessEnabled = MutableStateFlow(preferences.gaplessEnabled)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    // Gesture Settings (Restored from preferences)
    private val _gesturesEnabled = MutableStateFlow(preferences.gesturesEnabled)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    private val _swipeArtworkToSkip = MutableStateFlow(preferences.swipeArtworkToSkip)
    val swipeArtworkToSkip: StateFlow<Boolean> = _swipeArtworkToSkip.asStateFlow()

    private val _doubleTapToSeek = MutableStateFlow(preferences.doubleTapToSeek)
    val doubleTapToSeek: StateFlow<Boolean> = _doubleTapToSeek.asStateFlow()

    private val _swipeDownToCollapse = MutableStateFlow(preferences.swipeDownToCollapse)
    val swipeDownToCollapse: StateFlow<Boolean> = _swipeDownToCollapse.asStateFlow()

    // Automix & Song Matching State (Restored from preferences)
    private val _automixEnabled = MutableStateFlow(preferences.automixEnabled)
    val automixEnabled: StateFlow<Boolean> = _automixEnabled.asStateFlow()

    private var userUpdatedHarmonicMatching = false
    private val _matchNextSongEnabled = MutableStateFlow(preferences.getExplicitMatchNextSongPreference() ?: false)
    val matchNextSongEnabled: StateFlow<Boolean> = _matchNextSongEnabled.asStateFlow()

    // Player Style System (30 Curated Styles + 10 Preserved Legacy Styles, 20 Mini-Player Styles)
    private val playerStylePrefs = PlayerStylePreferences(application)
    private val _playerStyle = MutableStateFlow(preferences.playerStyle)
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _landscapePlayerStyle = MutableStateFlow(preferences.landscapePlayerStyle)
    val landscapePlayerStyle: StateFlow<PlayerStyle> = _landscapePlayerStyle.asStateFlow()

    private val _miniPlayerStyle = MutableStateFlow(preferences.miniPlayerStyle)
    val miniPlayerStyle: StateFlow<com.example.model.MiniPlayerStyle> = _miniPlayerStyle.asStateFlow()

    private val _favoritePlayerStyles = MutableStateFlow(preferences.favoritePlayerStyleIds)
    val favoritePlayerStyles: StateFlow<Set<String>> = _favoritePlayerStyles.asStateFlow()

    private val _favoriteMiniPlayerStyles = MutableStateFlow(preferences.favoriteMiniPlayerStyleIds)
    val favoriteMiniPlayerStyles: StateFlow<Set<String>> = _favoriteMiniPlayerStyles.asStateFlow()

    private val _lockScreenStyle = MutableStateFlow(preferences.lockScreenStyle)
    val lockScreenStyle: StateFlow<com.example.model.LockScreenStyle> = _lockScreenStyle.asStateFlow()

    private val _playerAppearance = MutableStateFlow(preferences.playerAppearanceConfig)
    val playerAppearance: StateFlow<com.example.model.PlayerAppearanceConfig> = _playerAppearance.asStateFlow()

    private val _playerGestureConfig = MutableStateFlow(preferences.playerGestureConfig)
    val playerGestureConfig: StateFlow<com.example.model.PlayerGestureConfig> = _playerGestureConfig.asStateFlow()

    fun setPlayerStyle(style: PlayerStyle) {
        _playerStyle.value = style
        preferences.playerStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setPlayerStyle(style)
        }
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = _playbackInfo.value.isPlaying,
            explicitlyStopped = false
        )
    }

    fun setLandscapePlayerStyle(style: PlayerStyle) {
        _landscapePlayerStyle.value = style
        preferences.landscapePlayerStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setLandscapePlayerStyle(style)
        }
    }

    fun cyclePlayerStyle() {
        val list = PlayerStyle.entries
        val nextIndex = (list.indexOf(_playerStyle.value) + 1) % list.size
        setPlayerStyle(list[nextIndex])
    }

    fun setMiniPlayerStyle(style: com.example.model.MiniPlayerStyle) {
        _miniPlayerStyle.value = style
        preferences.miniPlayerStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setMiniPlayerStyle(style)
        }
    }

    fun toggleFavoritePlayerStyle(style: PlayerStyle) {
        val updated = _favoritePlayerStyles.value.toMutableSet().apply {
            if (!add(style.id)) remove(style.id)
        }.toSet()
        _favoritePlayerStyles.value = updated
        preferences.favoritePlayerStyleIds = updated
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setFavoritePlayerStyles(updated)
        }
    }

    fun toggleFavoriteMiniPlayerStyle(style: com.example.model.MiniPlayerStyle) {
        val updated = _favoriteMiniPlayerStyles.value.toMutableSet().apply {
            if (!add(style.id)) remove(style.id)
        }.toSet()
        _favoriteMiniPlayerStyles.value = updated
        preferences.favoriteMiniPlayerStyleIds = updated
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setFavoriteMiniPlayerStyles(updated)
        }
    }

    fun setLockScreenStyle(style: com.example.model.LockScreenStyle) {
        _lockScreenStyle.value = style
        preferences.lockScreenStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setLockScreenStyle(style)
        }
        syncNotification()
    }

    fun updatePlayerAppearance(transform: (com.example.model.PlayerAppearanceConfig) -> com.example.model.PlayerAppearanceConfig) {
        val updated = transform(_playerAppearance.value)
        _playerAppearance.value = updated
        preferences.playerAppearanceConfig = updated
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setAppearanceConfig(updated)
        }
    }

    fun resetPlayerAppearanceToDefault() {
        val def = com.example.model.PlayerAppearanceConfig.DEFAULT
        _playerAppearance.value = def
        preferences.playerAppearanceConfig = def
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setAppearanceConfig(def)
        }
    }

    fun resetAllPlayerCustomizationToDefault() {
        setPlayerStyle(PlayerStyle.DEFAULT)
        setLandscapePlayerStyle(PlayerStyle.DEFAULT_LANDSCAPE)
        setMiniPlayerStyle(com.example.model.MiniPlayerStyle.DEFAULT)
        resetPlayerAppearanceToDefault()
        setSeekbarStyle(ui.player.seekbar.SeekbarStyle.DEFAULT)
        setVisualizerStyle(com.example.model.VisualizerStyle.SPECTRUM_BARS)
    }

    fun updatePlayerGestureConfig(transform: (com.example.model.PlayerGestureConfig) -> com.example.model.PlayerGestureConfig) {
        val updated = transform(_playerGestureConfig.value)
        _playerGestureConfig.value = updated
        _gesturesEnabled.value = updated.gesturesEnabled
        _swipeArtworkToSkip.value = updated.swipeArtworkEnabled
        _doubleTapToSeek.value = updated.doubleTapEnabled
        _swipeDownToCollapse.value = updated.swipeDownToMinimizeEnabled
        preferences.playerGestureConfig = updated
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setGestureConfig(updated)
        }
        val mappedSens = domain.motion.GestureSensitivityLevel.fromPlayerConfig(
            sensitivity = updated.swipeSensitivity,
            thresholdDp = updated.swipeThresholdDp
        )
        val currentMotion = _motionConfig.value
        if (currentMotion.hapticFeedbackEnabled != updated.hapticFeedbackEnabled ||
            currentMotion.gestureSensitivity != mappedSens
        ) {
            val syncedMotion = currentMotion.copy(
                hapticFeedbackEnabled = updated.hapticFeedbackEnabled,
                gestureSensitivity = mappedSens
            )
            _motionConfig.value = syncedMotion
            viewModelScope.launch(Dispatchers.IO) {
                motionPrefs.saveConfig(syncedMotion)
            }
        }
    }

    fun resetPlayerGestureConfigToDefault() {
        val def = com.example.model.PlayerGestureConfig.DEFAULT
        updatePlayerGestureConfig { def }
    }

    // Seekbar Style System (9 Premium Styles, default = BLOCKS)
    private val seekbarPrefs = SeekbarPreferences(application)
    private val _seekbarStyle = MutableStateFlow(preferences.seekbarStyle)
    val seekbarStyle: StateFlow<SeekbarStyle> = _seekbarStyle.asStateFlow()

    fun setSeekbarStyle(style: SeekbarStyle) {
        _seekbarStyle.value = style
        preferences.seekbarStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            seekbarPrefs.setSeekbarStyle(style)
        }
    }

    fun resetSeekbarStyleToDefault() {
        setSeekbarStyle(SeekbarStyle.DEFAULT)
    }

    // Dynamic Album Art-Based Theme Engine
    private val themePrefs = ThemePreferences(application)
    private val _dynamicColorsEnabled = MutableStateFlow(preferences.dynamicColorsEnabled)
    val dynamicColorsEnabled: StateFlow<Boolean> = _dynamicColorsEnabled.asStateFlow()

    private val _dynamicThemeMode = MutableStateFlow(preferences.dynamicThemeMode)
    val dynamicThemeMode: StateFlow<DynamicThemeMode> = _dynamicThemeMode.asStateFlow()

    private val _dynamicAppearanceMode = MutableStateFlow(preferences.dynamicAppearanceMode)
    val dynamicAppearanceMode: StateFlow<com.example.model.DynamicAppearanceMode> = _dynamicAppearanceMode.asStateFlow()

    private val _dynamicColorIntensity = MutableStateFlow(preferences.dynamicColorIntensity)
    val dynamicColorIntensity: StateFlow<com.example.model.DynamicColorIntensity> = _dynamicColorIntensity.asStateFlow()

    private val _dynamicGradientEnabled = MutableStateFlow(preferences.dynamicGradientEnabled)
    val dynamicGradientEnabled: StateFlow<Boolean> = _dynamicGradientEnabled.asStateFlow()

    private var currentSystemIsDark: Boolean = true

    private val _homeThemeMode = MutableStateFlow(preferences.homeThemeMode)
    val homeThemeMode: StateFlow<ui.theme.HomeThemeMode> = _homeThemeMode.asStateFlow()

    private val _homeCustomAccentArgb = MutableStateFlow<Long?>(preferences.homeCustomAccentArgb.takeIf { it != -1L })
    val homeCustomAccentArgb: StateFlow<Long?> = _homeCustomAccentArgb.asStateFlow()

    private val _dynamicPalette = MutableStateFlow(
        DynamicThemeGenerator.generate(
            rawPalette = domain.color.AlbumArtPalette.default(),
            mode = preferences.dynamicThemeMode,
            enabled = preferences.dynamicColorsEnabled,
            appearanceMode = preferences.dynamicAppearanceMode,
            intensity = preferences.dynamicColorIntensity,
            gradientEnabled = preferences.dynamicGradientEnabled,
            customAccentColor = preferences.homeCustomAccentArgb.takeIf { it != -1L }?.let { androidx.compose.ui.graphics.Color(it) },
            systemIsDark = true
        )
    )
    val dynamicPalette: StateFlow<DynamicColorPalette> = _dynamicPalette.asStateFlow()

    private var paletteExtractionJob: Job? = null

    fun setDynamicColorsEnabled(enabled: Boolean) {
        _dynamicColorsEnabled.value = enabled
        preferences.dynamicColorsEnabled = enabled
        if (enabled && _homeThemeMode.value != ui.theme.HomeThemeMode.DYNAMIC_ALBUM_ART) {
            setHomeThemeMode(ui.theme.HomeThemeMode.DYNAMIC_ALBUM_ART)
        }
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicColorsEnabled(enabled)
        }
        updateDynamicThemeForCurrentMode()
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        _dynamicThemeMode.value = mode
        preferences.dynamicThemeMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicThemeMode(mode)
        }
        updateDynamicThemeForCurrentMode()
    }

    fun setDynamicAppearanceMode(mode: com.example.model.DynamicAppearanceMode) {
        _dynamicAppearanceMode.value = mode
        preferences.dynamicAppearanceMode = mode
        if (_homeThemeMode.value != ui.theme.HomeThemeMode.DYNAMIC_ALBUM_ART && _dynamicColorsEnabled.value) {
            setHomeThemeMode(ui.theme.HomeThemeMode.DYNAMIC_ALBUM_ART)
        }
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicAppearanceMode(mode)
        }
        updateDynamicThemeForCurrentMode()
    }

    fun setDynamicColorIntensity(intensity: com.example.model.DynamicColorIntensity) {
        _dynamicColorIntensity.value = intensity
        preferences.dynamicColorIntensity = intensity
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicColorIntensity(intensity)
        }
        updateDynamicThemeForCurrentMode()
    }

    fun setDynamicGradientEnabled(enabled: Boolean) {
        _dynamicGradientEnabled.value = enabled
        preferences.dynamicGradientEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicGradientEnabled(enabled)
        }
        updateDynamicThemeForCurrentMode()
    }

    fun updateSystemDarkTheme(isDark: Boolean) {
        if (currentSystemIsDark != isDark) {
            currentSystemIsDark = isDark
            updateDynamicThemeForCurrentMode()
        }
    }

    private val _reduceAnimations = MutableStateFlow(preferences.reduceAnimations)
    val reduceAnimations: StateFlow<Boolean> = _reduceAnimations.asStateFlow()

    private val motionPrefs = data.preferences.MotionPreferences(application)
    private val _motionConfig = MutableStateFlow(motionPrefs.getInitialConfig())
    val motionConfig: StateFlow<domain.motion.GlobalMotionConfig> = _motionConfig.asStateFlow()

    private val _trackTransitionDirection = MutableStateFlow(0)
    val trackTransitionDirection: StateFlow<Int> = _trackTransitionDirection.asStateFlow()

    private val homeAnimationPrefs = data.preferences.HomeAnimationPreferences(application)
    private val _homeAnimationLevel = MutableStateFlow(homeAnimationPrefs.getInitialSettings().animationLevel)
    val homeAnimationLevel: StateFlow<ui.home.animations.HomeAnimationLevel> = _homeAnimationLevel.asStateFlow()

    private val _dailyMixAnimationStyle = MutableStateFlow(homeAnimationPrefs.getInitialSettings().dailyMixStyle)
    val dailyMixAnimationStyle: StateFlow<ui.home.animations.DailyMixAnimationStyle> = _dailyMixAnimationStyle.asStateFlow()

    // Dedicated User Profile state & DataStore persistence (strictly separate from song artwork)
    private val userProfilePrefs = data.preferences.UserProfilePreferences(application)
    val userProfile: StateFlow<com.example.model.UserProfile> = userProfilePrefs.userProfile
    val profileOnboardingState: StateFlow<data.preferences.ProfileOnboardingState> = userProfilePrefs.onboardingState

    suspend fun completeFirstLaunchProfile(
        displayName: String,
        pickedAvatarUri: android.net.Uri?
    ): Result<com.example.model.UserProfile> {
        return userProfilePrefs.completeFirstLaunchProfile(displayName, pickedAvatarUri)
    }

    suspend fun updateUserProfileNameOnly(displayName: String): Result<String> {
        return userProfilePrefs.updateDisplayName(displayName)
    }

    fun updateUserProfileDetails(displayName: String, username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfilePrefs.saveProfileDetails(displayName, username)
        }
    }

    fun updateUserProfilePhoto(pickedUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfilePrefs.persistPickedAvatar(pickedUri)
        }
    }

    fun removeUserProfilePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            userProfilePrefs.removeAvatar()
        }
    }

    fun resetPersonalProfileData() {
        viewModelScope.launch(Dispatchers.IO) {
            userProfilePrefs.resetPersonalProfileData()
        }
    }

    private val _recentSearches = MutableStateFlow(preferences.recentSearches)
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _audioFocusEnabled = MutableStateFlow(preferences.audioFocusEnabled)
    val audioFocusEnabled: StateFlow<Boolean> = _audioFocusEnabled.asStateFlow()

    private val _resumeOnLaunch = MutableStateFlow(preferences.resumeOnLaunch)
    val resumeOnLaunch: StateFlow<Boolean> = _resumeOnLaunch.asStateFlow()

    private val _headsetAutoPlayPause = MutableStateFlow(preferences.headsetAutoPlayPause)
    val headsetAutoPlayPause: StateFlow<Boolean> = _headsetAutoPlayPause.asStateFlow()

    private val _showLockScreenControls = MutableStateFlow(preferences.showLockScreenControls)
    val showLockScreenControls: StateFlow<Boolean> = _showLockScreenControls.asStateFlow()

    private val _showNotificationFormatBadge = MutableStateFlow(preferences.showNotificationFormatBadge)
    val showNotificationFormatBadge: StateFlow<Boolean> = _showNotificationFormatBadge.asStateFlow()

    fun setHomeThemeMode(mode: ui.theme.HomeThemeMode) {
        _homeThemeMode.value = mode
        preferences.homeThemeMode = mode
    }

    fun setHomeMusicMode(mode: HomeMusicMode) {
        userUpdatedHomeMode = true
        homeModePrefs.setCachedHomeMusicMode(mode)
        val changed = _homeMusicMode.value != mode
        _homeMusicMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            homeModePrefs.setHomeMusicMode(_homeMusicMode.value)
        }
        if (!changed) return
        if (mode == HomeMusicMode.ONLINE) {
            val hasLoadedSection = _discoverSectionsState.value.values.any { it is DiscoverSectionState.Success }
            val anyJobRunning = discoverSectionJobs.values.any { it.isActive }
            if (!hasLoadedSection && !anyJobRunning && _selectedOnlineProvider.value != null) {
                refreshOnlineDiscovery(forceRefresh = false)
            } else if (_sonoraRecommendState.value.items.isEmpty() && sonoraRecommendJob?.isActive != true) {
                loadSonoraRecommendations(forceRefresh = false)
            }
        } else if (mode == HomeMusicMode.ONLINE_VIDEO) {
            videoController.ensureHomeDiscoveryLoaded()
        }
        updateDynamicThemeForCurrentMode()
    }

    fun setHomeCustomAccentArgb(argb: Long?) {
        _homeCustomAccentArgb.value = argb
        preferences.homeCustomAccentArgb = argb ?: -1L
        updateDynamicThemeForCurrentMode()
    }

    fun setReduceAnimations(reduce: Boolean) {
        _reduceAnimations.value = reduce
        preferences.reduceAnimations = reduce
        val current = _motionConfig.value
        val targetIntensity = if (reduce) {
            domain.motion.MotionIntensity.OFF
        } else if (current.intensity == domain.motion.MotionIntensity.OFF) {
            domain.motion.MotionIntensity.BALANCED
        } else {
            current.intensity
        }
        if (current.intensity != targetIntensity) {
            val updated = current.copy(intensity = targetIntensity)
            _motionConfig.value = updated
            viewModelScope.launch(Dispatchers.IO) {
                motionPrefs.saveConfig(updated)
            }
        }
    }

    fun updateMotionConfig(
        transform: (domain.motion.GlobalMotionConfig) -> domain.motion.GlobalMotionConfig
    ) {
        val previous = _motionConfig.value
        val updated = transform(previous)
        if (updated == previous) return
        _motionConfig.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            motionPrefs.saveConfig(updated)
        }

        val shouldReduce = updated.intensity == domain.motion.MotionIntensity.OFF
        if (_reduceAnimations.value != shouldReduce) {
            _reduceAnimations.value = shouldReduce
            preferences.reduceAnimations = shouldReduce
        }

        if (previous.intensity != updated.intensity) {
            val syncedHomeLevel = when (updated.intensity) {
                domain.motion.MotionIntensity.OFF -> ui.home.animations.HomeAnimationLevel.OFF
                domain.motion.MotionIntensity.SUBTLE -> ui.home.animations.HomeAnimationLevel.SUBTLE
                domain.motion.MotionIntensity.BALANCED -> ui.home.animations.HomeAnimationLevel.BALANCED
                domain.motion.MotionIntensity.EXPRESSIVE -> ui.home.animations.HomeAnimationLevel.EXPRESSIVE
            }
            setHomeAnimationLevel(syncedHomeLevel)
        }

        val currentGestures = _playerGestureConfig.value
        val nextGestures = currentGestures.copy(
            swipeSensitivity = if (previous.gestureSensitivity != updated.gestureSensitivity) {
                updated.gestureSensitivity.playerSensitivity
            } else {
                currentGestures.swipeSensitivity
            },
            swipeThresholdDp = if (previous.gestureSensitivity != updated.gestureSensitivity) {
                updated.gestureSensitivity.thresholdDp
            } else {
                currentGestures.swipeThresholdDp
            },
            hapticFeedbackEnabled = updated.hapticFeedbackEnabled
        )
        if (nextGestures != currentGestures) {
            _playerGestureConfig.value = nextGestures
            preferences.playerGestureConfig = nextGestures
            viewModelScope.launch(Dispatchers.IO) {
                playerStylePrefs.setGestureConfig(nextGestures)
            }
        }
    }

    fun setMotionIntensity(intensity: domain.motion.MotionIntensity) =
        updateMotionConfig { it.copy(intensity = intensity) }

    fun setScreenTransitionStyle(style: domain.motion.ScreenTransitionStyle) =
        updateMotionConfig { it.copy(transitionStyle = style) }

    fun setGestureSensitivityLevel(level: domain.motion.GestureSensitivityLevel) =
        updateMotionConfig { it.copy(gestureSensitivity = level) }

    fun resetMotionConfigToDefaults() {
        updateMotionConfig { domain.motion.GlobalMotionConfig.DEFAULT }
    }

    fun setHomeAnimationLevel(level: ui.home.animations.HomeAnimationLevel) {
        _homeAnimationLevel.value = level
        viewModelScope.launch(Dispatchers.IO) {
            homeAnimationPrefs.setHomeAnimationLevel(level)
        }
    }

    fun setDailyMixAnimationStyle(style: ui.home.animations.DailyMixAnimationStyle) {
        _dailyMixAnimationStyle.value = style
        viewModelScope.launch(Dispatchers.IO) {
            homeAnimationPrefs.setDailyMixAnimationStyle(style)
        }
    }

    fun applyHomeAnimationSettings(
        level: ui.home.animations.HomeAnimationLevel,
        style: ui.home.animations.DailyMixAnimationStyle
    ) {
        _homeAnimationLevel.value = level
        _dailyMixAnimationStyle.value = style
        viewModelScope.launch(Dispatchers.IO) {
            homeAnimationPrefs.saveSettings(level, style)
        }
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val updated = (listOf(clean) + _recentSearches.value.filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        _recentSearches.value = updated
        preferences.recentSearches = updated
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it == query }
        _recentSearches.value = updated
        preferences.recentSearches = updated
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        preferences.recentSearches = emptyList()
    }

    fun setAudioFocusEnabled(enabled: Boolean) {
        _audioFocusEnabled.value = enabled
        preferences.audioFocusEnabled = enabled
        musicService?.updateAudioPolicySettings(enabled, _headsetAutoPlayPause.value)
    }

    fun setResumeOnLaunch(enabled: Boolean) {
        _resumeOnLaunch.value = enabled
        preferences.resumeOnLaunch = enabled
    }

    fun setHeadsetAutoPlayPause(enabled: Boolean) {
        _headsetAutoPlayPause.value = enabled
        preferences.headsetAutoPlayPause = enabled
        musicService?.updateAudioPolicySettings(_audioFocusEnabled.value, enabled)
    }

    fun setShowLockScreenControls(enabled: Boolean) {
        _showLockScreenControls.value = enabled
        preferences.showLockScreenControls = enabled
    }

    fun setShowNotificationFormatBadge(enabled: Boolean) {
        _showNotificationFormatBadge.value = enabled
        preferences.showNotificationFormatBadge = enabled
    }

    fun resetDynamicThemeToDefault() {
        setHomeThemeMode(ui.theme.HomeThemeMode.DEFAULT)
        _dynamicColorsEnabled.value = true
        preferences.dynamicColorsEnabled = true
        _dynamicThemeMode.value = DynamicThemeMode.DEFAULT
        preferences.dynamicThemeMode = DynamicThemeMode.DEFAULT
        _dynamicAppearanceMode.value = com.example.model.DynamicAppearanceMode.DEFAULT
        preferences.dynamicAppearanceMode = com.example.model.DynamicAppearanceMode.DEFAULT
        _dynamicColorIntensity.value = com.example.model.DynamicColorIntensity.DEFAULT
        preferences.dynamicColorIntensity = com.example.model.DynamicColorIntensity.DEFAULT
        _dynamicGradientEnabled.value = true
        preferences.dynamicGradientEnabled = true
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.resetToDefault()
        }
        updateDynamicThemeForCurrentMode()
    }

    fun resolveThemeSongForMode(
        mode: HomeMusicMode = _homeMusicMode.value,
        activeSong: Song? = _currentSong.value
    ): Song? {
        return when (mode) {
            HomeMusicMode.OFFLINE -> {
                activeSong?.takeIf { !it.isOnline }
                    ?: recentlyPlayedSongs.value.firstOrNull { !it.isOnline }
                    ?: yourMixSongs.value.firstOrNull { !it.isOnline }
                    ?: _allSongs.value.firstOrNull { !it.isOnline }
            }
            HomeMusicMode.ONLINE -> {
                activeSong?.takeIf { it.isOnline }
                    ?: recentlyPlayedOnlineTracks.value.firstOrNull()
                    ?: run {
                        val sections = _discoverSectionsState.value
                        val candidates = listOf(
                            DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                            DiscoverSectionType.TRENDING_MUSIC,
                            DiscoverSectionType.POPULAR_SONGS
                        ).flatMap { type ->
                            (sections[type] as? DiscoverSectionState.Success)?.items.orEmpty()
                        }
                        val chosen = candidates.firstOrNull {
                            it.kind == OnlineItemKind.TRACK && !it.artworkUrl.isNullOrBlank()
                        } ?: candidates.firstOrNull { !it.artworkUrl.isNullOrBlank() }
                        chosen?.let { OnlineTrackMapper.toUnifiedSong(it) }
                    }
            }
            HomeMusicMode.ONLINE_VIDEO -> {
                val activeVideo = videoController.playerState.value.activeVideo
                    ?: videoController.videoHistory.value.firstOrNull()
                    ?: videoController.homeSections.value.values
                        .filterIsInstance<domain.video.VideoSectionState.Success>()
                        .flatMap { it.items }
                        .firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                activeVideo?.let { v ->
                    Song(
                        id = v.stableId,
                        title = v.title,
                        artist = v.channelName,
                        album = v.providerName,
                        albumId = v.stableId,
                        durationMs = v.durationMs,
                        uri = android.net.Uri.EMPTY,
                        dataPath = v.contentUrl,
                        mimeType = "video/mp4",
                        sizeBytes = 0L,
                        isOnline = true,
                        sourceType = "ONLINE_VIDEO",
                        providerKey = v.providerKey,
                        providerName = v.providerName,
                        contentUrl = v.contentUrl,
                        artworkUrl = v.thumbnailUrl
                    )
                }
            }
        }
    }

    fun updateDynamicThemeForCurrentMode() {
        updateDynamicThemeForSong(resolveThemeSongForMode())
    }

    fun updateDynamicThemeForSong(song: Song?) {
        paletteExtractionJob?.cancel()
        paletteExtractionJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val enabled = _dynamicColorsEnabled.value
                val mode = _dynamicThemeMode.value
                val appMode = _dynamicAppearanceMode.value
                val intensity = _dynamicColorIntensity.value
                val gradEnabled = _dynamicGradientEnabled.value
                val customAccent = _homeCustomAccentArgb.value?.let { androidx.compose.ui.graphics.Color(it) }
                val sysDark = currentSystemIsDark

                if (!enabled || mode == DynamicThemeMode.STATIC_THEME || song == null) {
                    _dynamicPalette.value = DynamicThemeGenerator.generate(
                        rawPalette = domain.color.AlbumArtPalette.default(),
                        mode = mode,
                        enabled = enabled,
                        appearanceMode = appMode,
                        intensity = intensity,
                        gradientEnabled = gradEnabled,
                        customAccentColor = customAccent,
                        systemIsDark = sysDark
                    )
                    return@launch
                }

                val artKey = "song_${song.id}_${song.albumId}"
                val cachedPalette = AlbumColorCache.get(artKey)
                if (cachedPalette != null) {
                    _dynamicPalette.value = DynamicThemeGenerator.generate(
                        rawPalette = cachedPalette,
                        mode = mode,
                        enabled = enabled,
                        appearanceMode = appMode,
                        intensity = intensity,
                        gradientEnabled = gradEnabled,
                        customAccentColor = customAccent,
                        systemIsDark = sysDark
                    )
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    AlbumArtHelper.loadAlbumArt(getApplication(), song)
                }

                if (!isActive) return@launch

                val rawPalette = AlbumArtColorExtractor.extractPalette(bitmap, artKey)
                if (!isActive) return@launch

                _dynamicPalette.value = DynamicThemeGenerator.generate(
                    rawPalette = rawPalette,
                    mode = mode,
                    enabled = enabled,
                    appearanceMode = appMode,
                    intensity = intensity,
                    gradientEnabled = gradEnabled,
                    customAccentColor = customAccent,
                    systemIsDark = sysDark
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.e(TAG, "Failed updating dynamic theme for song=${song?.id}", e)
                _dynamicPalette.value = DynamicThemeGenerator.generate(
                    rawPalette = domain.color.AlbumArtPalette.default(),
                    mode = _dynamicThemeMode.value,
                    enabled = _dynamicColorsEnabled.value,
                    appearanceMode = _dynamicAppearanceMode.value,
                    intensity = _dynamicColorIntensity.value,
                    gradientEnabled = _dynamicGradientEnabled.value,
                    customAccentColor = _homeCustomAccentArgb.value?.let { androidx.compose.ui.graphics.Color(it) },
                    systemIsDark = currentSystemIsDark
                )
            }
        }
    }

    // Harmonic / Genre Next Matched Song Indicator
    private val _nextMatchedSong = MutableStateFlow<Song?>(null)
    val nextMatchedSong: StateFlow<Song?> = _nextMatchedSong.asStateFlow()

    // Daily Listening Analytics State
    private val _dailyAnalytics = MutableStateFlow(
        com.example.model.DailyAnalytics(
            dateKey = "today",
            displayDate = "Today",
            listenedSeconds = 0,
            tracksPlayed = 0,
            topArtist = "Exploring",
            topGenre = "Hi-Fi Studio",
            weeklyDayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            weeklyDayMinutes = listOf(15, 30, 20, 45, 60, 50, 40),
            totalWeeklyMinutes = 260,
            peakHourLabel = "Evening"
        )
    )
    val dailyAnalytics: StateFlow<com.example.model.DailyAnalytics> = _dailyAnalytics.asStateFlow()

    // Daily Mixes (Custom Personalized Playlists Generated Daily)
    private val _dailyMixes = MutableStateFlow<List<com.example.model.DailyMix>>(emptyList())
    val dailyMixes: StateFlow<List<com.example.model.DailyMix>> = _dailyMixes.asStateFlow()



    // Professional 360° Spatial Audio & TWS Presets
    val spatialPresets = listOf(
        com.example.model.SpatialPreset(
            name = "TWS Ultra-Wide 360°",
            description = "Optimized dual-earbud panoramic soundfield with enhanced stereo crossfeed and zero cancellation",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.85f,
            soundstageWidth = 0.95f,
            stereoWidening = 0.95f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "TWS Binaural Panoramic",
            description = "Spacious binaural acoustic imaging engineered for wireless earbuds with centered vocals",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.80f,
            soundstageWidth = 0.90f,
            stereoWidening = 0.90f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Studio Control Room",
            description = "Nearfield balanced binaural reference with crystal acoustic imaging",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.70f,
            soundstageWidth = 0.75f,
            stereoWidening = 0.80f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Holographic 360° Sphere",
            description = "Maximum 360-degree soundfield immersion with orbital acoustic pan",
            mode = SpatialMode.CINEMA_360,
            intensity = 0.95f,
            soundstageWidth = 1.0f,
            stereoWidening = 0.95f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Live Concert Arena",
            description = "Expansive outdoor stadium soundstage with large acoustic reflections",
            mode = SpatialMode.SPEAKERS,
            intensity = 0.85f,
            soundstageWidth = 0.90f,
            stereoWidening = 0.85f,
            reverbPreset = PresetReverb.PRESET_LARGEHALL,
            listenerX = 0f,
            listenerY = 0.15f
        ),
        com.example.model.SpatialPreset(
            name = "Intimate Jazz Lounge",
            description = "Warm club acoustics with subtle room reflections and front-row presence",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.60f,
            soundstageWidth = 0.65f,
            stereoWidening = 0.70f,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Binaural IEM Master",
            description = "Precision cross-feed matrix engineered specifically for in-ear monitors",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.80f,
            soundstageWidth = 0.85f,
            stereoWidening = 0.90f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Car Audio Cockpit",
            description = "Acoustic driver-seat compensation with tailored channel panning",
            mode = SpatialMode.SPEAKERS,
            intensity = 0.75f,
            soundstageWidth = 0.70f,
            stereoWidening = 0.65f,
            listenerX = -0.25f,
            listenerY = 0.10f
        ),
        com.example.model.SpatialPreset(
            name = "Open-Back Audiophile",
            description = "Airy, natural soundstage replicating flagship open-back headphones",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.70f,
            soundstageWidth = 0.90f,
            stereoWidening = 0.85f,
            listenerX = 0f,
            listenerY = 0f
        ),
        com.example.model.SpatialPreset(
            name = "Cinema Surround 7.1",
            description = "Ultra-wide cinema theater soundfield with deep surround presence",
            mode = SpatialMode.CINEMA_360,
            intensity = 0.90f,
            soundstageWidth = 0.95f,
            stereoWidening = 0.90f,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            listenerX = 0f,
            listenerY = -0.10f
        ),
        com.example.model.SpatialPreset(
            name = "Midnight Ambient Chill",
            description = "Dreamy panoramic stereo field with gentle relaxation acoustics",
            mode = SpatialMode.HEADPHONES,
            intensity = 0.55f,
            soundstageWidth = 0.90f,
            stereoWidening = 0.95f,
            listenerX = 0f,
            listenerY = 0f
        )
    )

    private val _selectedSpatialPresetIndex = MutableStateFlow(preferences.selectedSpatialPresetIndex)
    val selectedSpatialPresetIndex: StateFlow<Int> = _selectedSpatialPresetIndex.asStateFlow()

    // TWS & Bluetooth Audio Device Connection Status
    private val _isTwsConnected = MutableStateFlow(false)
    val isTwsConnected: StateFlow<Boolean> = _isTwsConnected.asStateFlow()

    private val _connectedAudioDeviceName = MutableStateFlow("Phone Speaker")
    val connectedAudioDeviceName: StateFlow<String> = _connectedAudioDeviceName.asStateFlow()

    // Dynamic Synchronized Lyrics State (Embedded, Local LRC/ELRC, Online LRCLIB, Imported, Edited)
    private val _currentTrackLyrics = MutableStateFlow<String?>(null)
    val currentTrackLyrics: StateFlow<String?> = _currentTrackLyrics.asStateFlow()

    private val _lyricsLoadState = MutableStateFlow<domain.lyrics.LyricsLoadState>(domain.lyrics.LyricsLoadState.Idle)
    val lyricsLoadState: StateFlow<domain.lyrics.LyricsLoadState> = _lyricsLoadState.asStateFlow()

    val lyricsAppearance: StateFlow<domain.lyrics.LyricsAppearanceConfig> =
        lyricsPreferences.appearanceFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = domain.lyrics.LyricsAppearanceConfig.DEFAULT
        )

    private val _lyricsTimingState = MutableStateFlow(domain.lyrics.LyricsTimingState())
    val lyricsTimingState: StateFlow<domain.lyrics.LyricsTimingState> = _lyricsTimingState.asStateFlow()

    private val _lyricsAvailableSources = MutableStateFlow<List<domain.lyrics.LyricsSourceOption>>(emptyList())
    val lyricsAvailableSources: StateFlow<List<domain.lyrics.LyricsSourceOption>> = _lyricsAvailableSources.asStateFlow()

    private val _lyricsSearchResults = MutableStateFlow<List<domain.lyrics.LyricsSearchCandidate>>(emptyList())
    val lyricsSearchResults: StateFlow<List<domain.lyrics.LyricsSearchCandidate>> = _lyricsSearchResults.asStateFlow()

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private val _lyricsFeedbackMessage = MutableStateFlow<String?>(null)
    val lyricsFeedbackMessage: StateFlow<String?> = _lyricsFeedbackMessage.asStateFlow()

    private val _lyricsPositionMs = MutableStateFlow(0L)
    val lyricsPositionMs: StateFlow<Long> = _lyricsPositionMs.asStateFlow()

    private var isFullScreenLyricsOpen: Boolean = false
    private var lyricsLoadJob: Job? = null
    private var lyricsSearchJob: Job? = null
    private var lyricsPositionJob: Job? = null
    private var activeLyricsRequestToken: Long = 0L

    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var serviceCollectorsJob: Job? = null
    private var originalQueueBeforeShuffle: List<Song>? = null
    private val dateKeyFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    private val servicePlaybackCallback = object : SonoraMusicService.ServicePlaybackCallback {
        override fun onRemotePlay() = resumePlayback()
        override fun onRemotePause() = pausePlayback()
        override fun onRemoteNext() = playNext()
        override fun onRemotePrev() = playPrevious()
        override fun onRemoteShuffle() = toggleShuffle()
        override fun onRemoteRepeat() = cycleRepeatMode()
        override fun onRemoteSeek(positionMs: Long) = seekTo(positionMs)
        override fun onRemoteStop() = handleRemoteStopFromService()
        override fun onTransientFocusPause(positionMs: Long) = handleTransientFocusPauseFromService(positionMs)
        override fun onTaskRemovedStop(positionMs: Long, wasPlaying: Boolean) = handleTaskRemovedFromService(positionMs, wasPlaying)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SonoraMusicService.SonoraBinder
            if (binder == null) {
                Log.w(TAG, "onServiceConnected ignored null or incompatible binder: $service")
                return
            }
            val connectedService = try {
                binder.service
            } catch (e: Throwable) {
                Log.e(TAG, "Failed retrieving SonoraMusicService from binder", e)
                return
            }
            musicService = connectedService
            isBound = true

            connectedService.updateAudioPolicySettings(_audioFocusEnabled.value, _headsetAutoPlayPause.value)
            val bgSettings = backgroundPlaybackSettings.value
            val sessSettings = sessionManagementSettings.value
            connectedService.updateLifecycleSettings(
                backgroundPlayback = bgSettings.backgroundPlaybackEnabled,
                resumeAfterPhoneCall = bgSettings.resumeAfterPhoneCall,
                stopOnTaskRemoved = sessSettings.stopPlaybackOnTaskRemoved
            )
            connectedService.setPlaybackCallback(servicePlaybackCallback)
            if (_queue.value.isNotEmpty()) {
                connectedService.updateQueueSnapshot(_queue.value, _queueIndex.value)
            } else if (connectedService.currentQueueSnapshot.isNotEmpty()) {
                _queue.value = connectedService.currentQueueSnapshot
                _queueIndex.value = connectedService.currentQueueIndexSnapshot
            }
            connectedService.currentSongSnapshot?.let { activeSvcSong ->
                if (_currentSong.value?.id != activeSvcSong.id || (connectedService.audioEngine.isPrepared || connectedService.audioEngine.isPlaying)) {
                    _currentSong.value = activeSvcSong
                    loadLyricsForTrack(activeSvcSong)
                }
            }

            serviceCollectorsJob?.cancel()
            val collectorJob = kotlinx.coroutines.SupervisorJob()
            serviceCollectorsJob = collectorJob
            val collectorScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + collectorJob)

            // Bind audio visualizer & bands flows from AudioEngine
            val engine = connectedService.audioEngine
            engine?.let { eng ->
                unifiedPlaybackController = UnifiedPlaybackController(eng, onlineRepository)

                // Apply restored settings to audio engine
                eng.setBassBoost(_bassBoost.value)
                eng.setVirtualizer(_virtualizer.value)
                eng.setReverbPreset(_reverbPreset.value)
                eng.setLoudnessEnhancer(_loudnessGainMb.value)
                eng.updateSpatialAudioConfig(_spatialConfig.value)
                eng.setGlobalEffectsBypass(_globalEffectsBypass.value)
                eng.update8dOrbitConfig(_orbit8dEnabled.value, _orbit8dSpeed.value, _orbit8dDepth.value)
                eng.setPlaybackSpeed(_playbackSpeed.value)

                if (eng.isPrepared || eng.isPlaying) {
                    val activeSong = connectedService.currentSongSnapshot ?: _currentSong.value
                    val engStage = eng.playbackStage.value
                    _playbackInfo.value = _playbackInfo.value.copy(
                        isPlaying = eng.isPlaying,
                        isBuffering = eng.isBuffering.value && engStage != OnlinePlaybackStage.PLAYING,
                        playbackStage = engStage,
                        statusMessage = eng.statusMessage.value.ifBlank { engStage.defaultMessage },
                        bufferedPositionMs = eng.bufferedPositionMs,
                        currentPositionMs = eng.currentPosition,
                        durationMs = eng.duration.coerceAtLeast(activeSong?.durationMs ?: 0L),
                        playbackSpeed = _playbackSpeed.value
                    )
                    _lyricsPositionMs.value = eng.currentPosition
                    if (eng.isPlaying) {
                        startProgressTracker()
                        scheduleNextTrackPreload()
                    }
                }

                collectorScope.launch {
                    eng.isBuffering.collect { buffering ->
                        val cur = _currentSong.value
                        val resolvingNow = cur != null && unifiedPlaybackController?.isCurrentlyResolving(cur) == true
                        if (!resolvingNow) {
                            val stage = eng.playbackStage.value
                            val activeLoading = buffering &&
                                stage != OnlinePlaybackStage.PLAYING &&
                                stage != OnlinePlaybackStage.FAILED
                            _playbackInfo.value = _playbackInfo.value.copy(
                                isBuffering = activeLoading,
                                bufferedPositionMs = eng.bufferedPositionMs
                            )
                        }
                    }
                }
                collectorScope.launch {
                    eng.playbackStage.collect { stage ->
                        val cur = _currentSong.value ?: return@collect
                        val resolvingNow = unifiedPlaybackController?.isCurrentlyResolving(cur) == true
                        if (!resolvingNow && cur.isOnline) {
                            val activeLoading = stage == OnlinePlaybackStage.RESOLVING_STREAM ||
                                stage == OnlinePlaybackStage.PREPARING_PLAYER ||
                                stage == OnlinePlaybackStage.BUFFERING_AUDIO
                            val msg = eng.statusMessage.value.ifBlank { stage.defaultMessage }
                            _playbackInfo.value = _playbackInfo.value.copy(
                                playbackStage = stage,
                                statusMessage = msg,
                                isBuffering = activeLoading,
                                bufferedPositionMs = eng.bufferedPositionMs
                            )
                        }
                    }
                }
                unifiedPlaybackController?.let { controller ->
                    collectorScope.launch {
                        controller.currentResolvedStream.collect { resolved ->
                            _currentResolvedStream.value = resolved
                        }
                    }
                }
                collectorScope.launch {
                    eng.visualizerFrequencies.collect { _visualizerFrequencies.value = it }
                }
                collectorScope.launch {
                    eng.waveformSamples.collect { _waveformSamples.value = it }
                }
                collectorScope.launch {
                    eng.isTwsConnected.collect { _isTwsConnected.value = it }
                }
                collectorScope.launch {
                    eng.connectedAudioDeviceName.collect {
                        _connectedAudioDeviceName.value = it
                        if (_currentSong.value != null) {
                            syncNotification()
                        }
                    }
                }
                collectorScope.launch {
                    eng.orbitAzimuthRad.collect { _orbitAzimuthRad.value = it }
                }
                collectorScope.launch {
                    eng.isBassBoostSupported.collect { _isBassBoostSupported.value = it }
                }
                collectorScope.launch {
                    eng.isVirtualizerSupported.collect { _isVirtualizerSupported.value = it }
                }
                collectorScope.launch {
                    eng.isReverbSupported.collect { _isReverbSupported.value = it }
                }
                collectorScope.launch {
                    eng.isLoudnessSupported.collect { _isLoudnessSupported.value = it }
                }
                collectorScope.launch {
                    eng.dspDiagnosticMessage.collect { _dspDiagnosticMessage.value = it }
                }
            }

            pendingPlayRequest?.let { req ->
                pendingPlayRequest = null
                playSong(
                    song = req.song,
                    newQueue = req.queue,
                    startIndex = req.index,
                    manualStreamId = req.manualStreamId,
                    resumePositionMs = req.resumePositionMs
                )
            } ?: run {
                if (pendingTogglePlayOnConnect) {
                    pendingTogglePlayOnConnect = false
                    togglePlayPause()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceCollectorsJob?.cancel()
            serviceCollectorsJob = null
            unifiedPlaybackController?.cancelPendingResolution()
            unifiedPlaybackController = null
            musicService?.let { svc ->
                if (svc.playbackCallback === servicePlaybackCallback) {
                    svc.setPlaybackCallback(null)
                }
            }
            musicService = null
            isBound = false
        }
    }

    private data class PendingPlayRequest(
        val song: Song,
        val queue: List<Song>?,
        val index: Int,
        val manualStreamId: String? = null,
        val resumePositionMs: Long = 0L
    )

    private var pendingPlayRequest: PendingPlayRequest? = null
    private var pendingTogglePlayOnConnect: Boolean = false

    // --- Filtered and Organized Library Categories (Declared before init so init observers see initialized StateFlows) ---

    val filteredSongs: StateFlow<List<Song>> = combine(
        _allSongs, _searchQuery, _sortOption
    ) { songs, query, sort ->
        val filtered = if (query.isBlank()) songs else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.genre.contains(query, ignoreCase = true)
            }
        }

        when (sort) {
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> filtered.sortedBy { it.album.lowercase() }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            SortOption.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = _allSongs.combine(_searchQuery) { songs, query ->
        songs.groupBy { it.album }
            .map { (albumTitle, songList) ->
                val first = songList.first()
                Album(
                    id = first.albumId,
                    title = albumTitle,
                    artist = first.artist,
                    songCount = songList.size,
                    year = first.year,
                    artworkUri = first.uri
                )
            }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = _allSongs.combine(_searchQuery) { songs, query ->
        songs.groupBy { it.artist }
            .entries
            .toList()
            .mapIndexed { idx, entry ->
                Artist(
                    id = idx.toLong(),
                    name = entry.key,
                    songCount = entry.value.size,
                    albumCount = entry.value.map { it.album }.distinct().size
                )
            }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<Genre>> = _allSongs.combine(_searchQuery) { songs, query ->
        songs.groupBy { it.genre }
            .map { (genreName, songList) ->
                Genre(name = genreName, songCount = songList.size)
            }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<MusicFolder>> = _allSongs.combine(_searchQuery) { songs, query ->
        songs.groupBy { File(it.dataPath).parent ?: "/storage" }
            .map { (dirPath, songList) ->
                val dirName = File(dirPath).name.ifBlank { "Root Storage" }
                MusicFolder(path = dirPath, name = dirName, songCount = songList.size)
            }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = _allSongs.combine(_searchQuery) { songs, query ->
        songs.filter { it.isFavorite }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = _allSongs.combine(_searchQuery) { songs, _ ->
        songs.sortedByDescending { it.playCount }.take(15)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = _allSongs.combine(_searchQuery) { songs, _ ->
        songs.filter { it.lastPlayedTimestamp > 0 }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(15)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = _allSongs.combine(_searchQuery) { songs, _ ->
        songs.sortedByDescending { it.dateAdded }.take(15)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yourMixSongs: StateFlow<List<Song>> = _allSongs.combine(_playbackInfo) { songs, _ ->
        domain.home.HomeDiscoveryEngine.buildYourMix(songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yourMixContributorsSummary: StateFlow<String> = yourMixSongs.combine(_allSongs) { mix, all ->
        domain.home.HomeDiscoveryEngine.buildContributorsSummary(mix.ifEmpty { all })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val favouriteAlbums: StateFlow<List<Album>> = combine(_allSongs, albums) { songs, albumList ->
        domain.home.HomeDiscoveryEngine.rankFavouriteAlbums(songs, albumList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favouriteArtists: StateFlow<List<Artist>> = combine(_allSongs, artists) { songs, artistList ->
        domain.home.HomeDiscoveryEngine.rankFavouriteArtists(songs, artistList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localLibraryStats: StateFlow<LocalLibraryStats> = combine(
        _allSongs,
        albums,
        artists,
        favoriteSongs
    ) { songs, albumList, artistList, favs ->
        domain.home.HomeDiscoveryEngine.computeLocalLibraryStats(
            songs = songs,
            albums = albumList,
            artists = artistList,
            favorites = favs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalLibraryStats())

    init {
        // Safety fallback so non-UI/test ViewModel instantiations still release deferred work automatically
        viewModelScope.launch {
            delay(2_200L)
            startupDeferredWorkGate.complete(Unit)
        }
        initOnlineProvidersAsync(application)
        bindMusicService()
        loadLocalSongs()
        observePlaylists()
        observeFolderFiltering()
        restorePersistedUnlimitedQueue()
        viewModelScope.launch {
            experiencePreferences.musicExperienceFlow
                .catch { e -> Log.w(TAG, "Music experience DataStore error", e) }
                .collect { settings ->
                    val prevMode = data.artwork.AlbumArtworkQualityManager.currentQualityMode
                    data.artwork.AlbumArtworkQualityManager.currentQualityMode = settings.albumArtworkQualityMode
                    if (prevMode != settings.albumArtworkQualityMode) {
                        AlbumArtHelper.clearOnlineArtworkCache()
                    }
                }
        }
        viewModelScope.launch {
            experiencePreferences.harmonicTrackMatchingFlow {
                preferences.getExplicitMatchNextSongPreference()
            }
                .catch { e -> Log.w(TAG, "Harmonic matching DataStore error", e) }
                .take(1)
                .collect { savedEnabled ->
                    if (!userUpdatedHarmonicMatching) {
                        _matchNextSongEnabled.value = savedEnabled
                        if (!savedEnabled) {
                            _nextMatchedSong.value = null
                        }
                    }
                }
        }
        viewModelScope.launch {
            connectivityMonitor.isMeteredConnection.collect { metered ->
                data.artwork.AlbumArtworkQualityManager.isMeteredOrMobileNetwork = metered
            }
        }
        viewModelScope.launch {
            homeAnimationPrefs.settingsFlow
                .catch { e -> Log.w(TAG, "Home animation DataStore stream error", e) }
                .collect { saved ->
                    _homeAnimationLevel.value = saved.animationLevel
                    _dailyMixAnimationStyle.value = saved.dailyMixStyle
                }
        }
        viewModelScope.launch {
            motionPrefs.motionConfigFlow
                .catch { e -> Log.w(TAG, "Motion DataStore stream error", e) }
                .take(1)
                .collect { saved ->
                    _motionConfig.value = saved
                }
        }
        viewModelScope.launch {
            homeModePrefs.homeMusicModeFlow
                .catch { e -> Log.w(TAG, "Home mode DataStore stream error", e) }
                .take(1)
                .collect { savedMode ->
                    if (!userUpdatedHomeMode && _homeMusicMode.value != savedMode) {
                        _homeMusicMode.value = savedMode
                    }
                }
        }
        viewModelScope.launch {
            libraryModePrefs.librarySectionModeFlow
                .catch { e -> Log.w(TAG, "Library mode DataStore stream error", e) }
                .take(1)
                .collect { savedMode ->
                    if (!userUpdatedLibraryMode && _librarySectionMode.value != savedMode) {
                        _librarySectionMode.value = savedMode
                    }
                }
        }
        viewModelScope.launch {
            combine(
                _currentSong,
                _homeMusicMode,
                _allSongs,
                _discoverSectionsState
            ) { song, mode, _, _ ->
                resolveThemeSongForMode(mode = mode, activeSong = song)
            }
                .distinctUntilChanged { a, b ->
                    a?.id == b?.id && a?.albumId == b?.albumId && a?.artworkUrl == b?.artworkUrl
                }
                .collect { themeSong ->
                    updateDynamicThemeForSong(themeSong)
                }
        }
        viewModelScope.launch {
            onlineRegionPrefs.contentCountryFlow
                .catch { e -> Log.w(TAG, "Online country DataStore stream error", e) }
                .collect { savedCountry ->
                    if (_onlineContentCountry.value != savedCountry) {
                        _onlineContentCountry.value = savedCountry
                        NewPipeInitializer.updateLocalization(_onlineContentLanguage.value, savedCountry)
                    }
                }
        }
        viewModelScope.launch {
            onlineRegionPrefs.contentLanguageFlow
                .catch { e -> Log.w(TAG, "Online language DataStore stream error", e) }
                .collect { savedLang ->
                    if (_onlineContentLanguage.value != savedLang) {
                        _onlineContentLanguage.value = savedLang
                        NewPipeInitializer.updateLocalization(savedLang, _onlineContentCountry.value)
                    }
                }
        }
        viewModelScope.launch {
            onlineRegionPrefs.discoverLanguageFlow
                .catch { e -> Log.w(TAG, "Discover language DataStore stream error", e) }
                .collect { savedFilter ->
                    _discoverMusicLanguage.value = savedFilter
                }
        }
        viewModelScope.launch {
            onlineRegionPrefs.selectedProviderKeyFlow
                .catch { e -> Log.w(TAG, "Online provider DataStore stream error", e) }
                .collect { savedKey ->
                    val currentKey = _selectedOnlineProvider.value?.key
                    if (currentKey != null && !currentKey.equals(savedKey, ignoreCase = true)) {
                        val target = _onlineProviders.value.firstOrNull { it.key == savedKey }
                        if (target != null) {
                            _selectedOnlineProvider.value = target
                            preferences.selectedOnlineProviderKey = target.key
                            if (_selectedOnlineFilter.value !in target.supportedFilters) {
                                _selectedOnlineFilter.value = target.defaultFilter
                            }
                        }
                    }
                }
        }
        observeOnlineRecommendationProfile()
        viewModelScope.launch {
            combine(backgroundPlaybackSettings, sessionManagementSettings) { bg, sess -> bg to sess }
                .collect { (bg, sess) ->
                    musicService?.updateLifecycleSettings(
                        backgroundPlayback = bg.backgroundPlaybackEnabled,
                        resumeAfterPhoneCall = bg.resumeAfterPhoneCall,
                        stopOnTaskRemoved = sess.stopPlaybackOnTaskRemoved
                    )
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            startupDeferredWorkGate.await()
            runCatching {
                downloadCoordinator.reconcileInterruptedTasks(autoResume = true)
            }.onFailure { e ->
                Log.w(TAG, "Download reconciliation skipped: ${e.message}")
            }
        }
        // Defer Odisha weather hydration, initial network refresh, and 20-min periodic loop until after intro
        viewModelScope.launch(Dispatchers.IO) {
            startupDeferredWorkGate.await()
            val initialSettings = runCatching {
                ambientEffectsPrefs.weatherAmbientSettingsFlow.first()
            }.getOrDefault(data.preferences.WeatherAmbientSettings.DEFAULT)

            weatherRepository.hydrateCacheFromDisk(initialSettings.selectedLocation)
            if (initialSettings.mode == domain.weather.WeatherEffectMode.AUTOMATIC && isNetworkOnline.value) {
                weatherRepository.refreshWeather(initialSettings.selectedLocation, force = false)
            }

            while (true) {
                delay(data.weather.WeatherRepository.PERIODIC_REFRESH_INTERVAL_MS)
                val currentSettings = weatherAmbientSettings.value
                if (currentSettings.mode == domain.weather.WeatherEffectMode.AUTOMATIC && isNetworkOnline.value) {
                    weatherRepository.refreshWeather(currentSettings.selectedLocation, force = true)
                }
                _festivalReferenceDate.value = java.time.LocalDate.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
            }
        }
        // Defer Pan-India Hindu Festival Room seeding, WorkManager scheduling, and daily sync until after intro
        viewModelScope.launch(Dispatchers.IO) {
            startupDeferredWorkGate.await()
            runCatching {
                festivalRepository.ensureInitialCacheSeeded()
                festivalRepository.schedulePeriodicRefresh(getApplication())
                val autoEnabled = runCatching {
                    festivalAtmospherePrefs.settingsFlow.first().automaticDailySyncEnabled
                }.getOrDefault(true)
                festivalRepository.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = autoEnabled)
            }.onFailure { e ->
                Log.w(TAG, "Festival calendar initialization deferred: ${e.message}")
            }
        }
    }

    private fun observeFolderFiltering() {
        viewModelScope.launch {
            combine(_rawScannedSongs, folderFilterPreferences.settingsFlow) { raw, settings ->
                domain.filter.MusicFolderFilter.filterSongs(raw, settings)
            }.catch { e ->
                Log.e(TAG, "Folder filtering stream failed", e)
            }.collect { filtered ->
                _allSongs.value = filtered
            }
        }
    }

    private fun bindMusicService() {
        try {
            val intent = Intent(getApplication(), SonoraMusicService::class.java)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind SonoraMusicService", e)
        }
    }

    private fun ensureServiceStarted() {
        val app = getApplication<Application>()
        val intent = Intent(app, SonoraMusicService::class.java)
        try {
            app.startService(intent)
        } catch (ise: IllegalStateException) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
                _currentSong.value?.let { syncNotification(it, _playbackInfo.value.isPlaying) }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed starting foreground SonoraMusicService on API ${android.os.Build.VERSION.SDK_INT}", e)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not elevate SonoraMusicService with startService: ${e.message}")
        }
    }

    fun loadLocalSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = scanner.scanSongs()
                _rawScannedSongs.value = songs
                val filteredInitial = domain.filter.MusicFolderFilter.filterSongs(songs, folderFilterPreferences.settings.value)
                _allSongs.value = filteredInitial
                if (_queue.value.isEmpty()) {
                    val savedOnline = try {
                        onlineRepository.allOnlineTracksFlow.first()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    val savedDownloads = try {
                        downloadCoordinator.downloadedTracksFlow.first().map { it.toSong() }
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    val combinedPool = filteredInitial + savedDownloads + savedOnline
                    if (combinedPool.isNotEmpty()) {
                        val byId = combinedPool.associateBy { it.id }
                        val savedIds = preferences.lastQueueSongIds
                        val restoredQueue = savedIds.mapNotNull { byId[it] }
                            .ifEmpty { filteredInitial.ifEmpty { savedDownloads.ifEmpty { savedOnline } } }
                        if (restoredQueue.isNotEmpty()) {
                            val lastId = preferences.lastPlayedSongId
                            val savedIdx = preferences.lastQueueIndex
                            val targetIndex = when {
                                lastId != -1L -> restoredQueue.indexOfFirst { it.id == lastId }
                                    .takeIf { it >= 0 }
                                    ?: savedIdx.coerceIn(0, restoredQueue.lastIndex)
                                else -> savedIdx.coerceIn(0, restoredQueue.lastIndex)
                            }
                            val targetSong = restoredQueue[targetIndex]

                            _queue.value = restoredQueue
                            _queueIndex.value = targetIndex
                            _currentSong.value = targetSong
                            loadLyricsForTrack(targetSong)
                        }
                    }
                }
                generateDailyMixes(filteredInitial)
                updateDailyAnalytics()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.e(TAG, "Failed loading local songs", e)
            }
        }
    }

    fun setWhitelistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            folderFilterPreferences.setWhitelistEnabled(enabled)
        }
    }

    fun setWhitelistedFolder(folder: domain.model.FolderInfo) {
        viewModelScope.launch {
            folderFilterPreferences.setWhitelistedFolder(folder)
        }
    }

    fun addWhitelistedFolder(folder: domain.model.FolderInfo) {
        viewModelScope.launch {
            folderFilterPreferences.addWhitelistedFolder(folder)
        }
    }

    fun removeWhitelistedFolder(pathOrUri: String) {
        viewModelScope.launch {
            folderFilterPreferences.removeWhitelistedFolder(pathOrUri)
        }
    }

    fun addBlacklistedFolder(folder: domain.model.FolderInfo) {
        viewModelScope.launch {
            folderFilterPreferences.addBlacklistedFolder(folder)
        }
    }

    fun addBlacklistedFolders(folders: List<domain.model.FolderInfo>) {
        viewModelScope.launch {
            folderFilterPreferences.addBlacklistedFolders(folders)
        }
    }

    fun removeBlacklistedFolder(pathOrUri: String) {
        viewModelScope.launch {
            folderFilterPreferences.removeBlacklistedFolder(pathOrUri)
        }
    }

    fun clearBlacklist() {
        viewModelScope.launch {
            folderFilterPreferences.clearBlacklist()
        }
    }

    fun getAvailableDeviceFolders(): List<domain.model.FolderInfo> {
        return data.scanner.FolderScannerHelper.extractDistinctFoldersFromSongs(_rawScannedSongs.value)
    }

    private val _playlistSongsMap = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())
    val playlistSongsMap: StateFlow<Map<Long, List<Song>>> = _playlistSongsMap.asStateFlow()

    private fun observePlaylists() {
        viewModelScope.launch {
            combine(
                dao.getAllPlaylistsFlow(),
                dao.getAllPlaylistSongCrossRefsFlow(),
                _allSongs,
                onlineRepository.allOnlineTracksFlow,
                downloadedSongs
            ) { entities, crossRefs, localSongs, onlineSongs, offlineDownloads ->
                val byId = (localSongs + onlineSongs + offlineDownloads).associateBy { it.id }
                val grouped = crossRefs.filter { it.songId in byId }.groupBy { it.playlistId }
                val songsMap = mutableMapOf<Long, List<Song>>()
                val enriched = entities.map { entity ->
                    val refs = grouped[entity.id].orEmpty().sortedBy { it.orderIndex }
                    val resolvedSongs = refs.mapNotNull { byId[it.songId] }
                    songsMap[entity.id] = resolvedSongs
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songCount = resolvedSongs.size,
                        createdAt = entity.createdAt,
                        coverArtUri = entity.coverArtUri
                    )
                }
                enriched to songsMap.toMap()
            }.catch { e ->
                Log.e(TAG, "Playlist observation failed", e)
            }.collect { (enriched, songsMap) ->
                _playlists.value = enriched
                _playlistSongsMap.value = songsMap
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeOnlineRecommendationProfile() {
        val listeningSignalsFlow = combine(
            recentlyPlayedSongs,
            recentlyPlayedOnlineTracks,
            favouriteArtists,
            _allSongs,
            allOnlineTracks
        ) { recLocal, recOnline, favArtists, allLocal, allOnline ->
            RecommendationListeningSignals(
                recentlyPlayedLocal = recLocal,
                recentlyPlayedOnline = recOnline,
                favouriteArtists = favArtists,
                allSongs = allLocal,
                onlineTracks = allOnline
            )
        }
        val preferenceSignalsFlow = combine(
            onlineSearchHistory,
            _dailyMixes,
            _onlineContentCountry,
            _onlineContentLanguage,
            _discoverMusicLanguage
        ) { searches, mixes, country, lang, discLang ->
            RecommendationPreferenceSignals(
                searchHistory = searches,
                dailyMixes = mixes,
                countryCode = country,
                languageCode = lang,
                discoverLanguage = discLang
            )
        }

        viewModelScope.launch {
            // Defer full recommendation profile scoring until intro finishes and compute off-main-thread
            startupDeferredWorkGate.await()
            combine(listeningSignalsFlow, preferenceSignalsFlow) { listening, prefs ->
                OnlineRecommendationEngine.buildDailyRecommendationProfile(
                    recentlyPlayedLocal = listening.recentlyPlayedLocal,
                    recentlyPlayedOnline = listening.recentlyPlayedOnline,
                    favouriteArtists = listening.favouriteArtists,
                    allSongs = listening.allSongs,
                    onlineTracks = listening.onlineTracks,
                    onlineSearchHistory = prefs.searchHistory,
                    dailyMixes = prefs.dailyMixes,
                    countryCode = prefs.countryCode,
                    languageCode = prefs.languageCode,
                    discoverLanguage = prefs.discoverLanguage
                )
            }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .debounce(450L)
                .catch { e -> Log.w(TAG, "Recommendation profile observation error", e) }
                .collect { profile ->
                    _onlineRecommendationProfile.value = profile
                    val provider = _selectedOnlineProvider.value
                    val previousSeed = lastRecommendationSeedQueried
                    if (provider != null && previousSeed != null && previousSeed != profile.seedQuery) {
                        lastRecommendationSeedQueried = profile.seedQuery
                        discoverSectionJobs[DiscoverSectionType.MUSIC_RECOMMENDATIONS]?.cancel()
                        discoverLoadMoreJobs[DiscoverSectionType.MUSIC_RECOMMENDATIONS]?.cancel()
                        _discoverSectionsState.update { current ->
                            current.toMutableMap().apply {
                                put(DiscoverSectionType.MUSIC_RECOMMENDATIONS, DiscoverSectionState.Loading)
                            }
                        }
                        launchDiscoverSection(
                            section = DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                            provider = provider,
                            generation = discoverGeneration,
                            recentArtistSeed = profile.primaryArtistSeed,
                            recommendationProfile = profile,
                            forceRefresh = false
                        )
                    }
                }
        }
    }

    private data class RecommendationListeningSignals(
        val recentlyPlayedLocal: List<Song>,
        val recentlyPlayedOnline: List<Song>,
        val favouriteArtists: List<Artist>,
        val allSongs: List<Song>,
        val onlineTracks: List<Song>
    )

    private data class RecommendationPreferenceSignals(
        val searchHistory: List<String>,
        val dailyMixes: List<com.example.model.DailyMix>,
        val countryCode: String,
        val languageCode: String,
        val discoverLanguage: DiscoverMusicLanguage
    )

    fun playCollection(songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val targetQueue = if (shuffle) songs.shuffled() else songs
        if (shuffle && _playbackInfo.value.shuffleMode == ShuffleMode.OFF) {
            _playbackInfo.value = _playbackInfo.value.copy(shuffleMode = ShuffleMode.ON)
            preferences.shuffleMode = ShuffleMode.ON
        }
        playSong(targetQueue.first(), targetQueue, 0)
    }

    fun getSongsForPlaylistFlow(playlistId: Long): kotlinx.coroutines.flow.Flow<List<Song>> {
        return combine(
            dao.getPlaylistSongsFlow(playlistId),
            _allSongs,
            onlineRepository.allOnlineTracksFlow,
            downloadedSongs
        ) { refs, localSongs, onlineSongs, offlineDownloads ->
            val byId = (localSongs + onlineSongs + offlineDownloads).associateBy { it.id }
            refs.mapNotNull { byId[it.songId] }
        }
    }

    // --- Playback Controls ---

    fun playSong(
        song: Song,
        newQueue: List<Song>? = null,
        startIndex: Int = -1,
        manualStreamId: String? = null,
        resumePositionMs: Long = 0L,
        transitionDirection: Int = 0
    ) {
        videoController.pauseForAudioHandoff()
        _trackTransitionDirection.value = transitionDirection
        crossfadeToken++
        isCrossfadingNext = false
        val persistedOnlineById = allOnlineTracks.value.associateBy { it.id }
        val hydratedSong = if (song.isOnline) {
            persistedOnlineById[song.id]?.let { saved ->
                song.copy(
                    isFavorite = saved.isFavorite || song.isFavorite,
                    playCount = maxOf(saved.playCount, song.playCount)
                )
            } ?: song
        } else song

        val baseQueue = (newQueue ?: _queue.value).map { item ->
            if (item.isOnline) {
                persistedOnlineById[item.id]?.let { saved ->
                    item.copy(isFavorite = saved.isFavorite || item.isFavorite)
                } ?: item
            } else item
        }
        val q = if (baseQueue.any { it.id == hydratedSong.id }) {
            baseQueue.map { if (it.id == hydratedSong.id) hydratedSong else it }
        } else {
            listOf(hydratedSong)
        }
        val idx = if (startIndex in q.indices) startIndex else q.indexOfFirst { it.id == hydratedSong.id }.coerceAtLeast(0)
        if (newQueue != null && _playbackInfo.value.shuffleMode == ShuffleMode.OFF) {
            originalQueueBeforeShuffle = q
        }

        _queue.value = q
        syncQueuedItemsFromQueue(q)
        _queueIndex.value = idx
        _currentSong.value = hydratedSong
        if (_songRadioSession.value.isActive) {
            activePlaybackContextType = "RADIO"
            activePlaybackContextLabel = _songRadioSession.value.seedLabel
        }
        persistUnlimitedQueueAsync()
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = true,
            explicitlyStopped = false,
            overridePositionMs = resumePositionMs.coerceAtLeast(0L)
        )
        if (_songRadioSession.value.isActive) {
            _songRadioSession.update { it.copy(playedSongIds = it.playedSongIds + hydratedSong.id) }
            maybeTopUpSongRadioQueue()
        }
        if (musicExperienceSettings.value.musicRecommendationsEnabled) {
            loadRecommendationsForCurrentSong(forceRefresh = false)
        }
        val initialStage = if (hydratedSong.isOnline) {
            OnlinePlaybackStage.RESOLVING_STREAM
        } else {
            OnlinePlaybackStage.IDLE
        }
        _playbackInfo.value = _playbackInfo.value.copy(
            playbackError = null,
            playbackErrorKind = null,
            isBuffering = hydratedSong.isOnline,
            playbackStage = initialStage,
            statusMessage = initialStage.defaultMessage,
            currentPositionMs = resumePositionMs.coerceAtLeast(0L)
        )

        // Save last played song and queue to preferences
        preferences.lastPlayedSongId = hydratedSong.id
        preferences.lastQueueIndex = idx
        preferences.lastQueueSongIds = q.map { it.id }

        // Find and pre-match the best next track for smooth automix
        findNextMatchedSong(hydratedSong, q, idx)

        // Load synchronized lyrics asynchronously and cancel any obsolete request
        _lyricsPositionMs.value = resumePositionMs.coerceAtLeast(0L)
        loadLyricsForTrack(hydratedSong)

        val engine = musicService?.audioEngine
        val controller = unifiedPlaybackController
        if (engine == null || controller == null) {
            pendingPlayRequest = PendingPlayRequest(hydratedSong, q, idx, manualStreamId, resumePositionMs)
            if (!isBound) bindMusicService()
            return
        }
        if (hydratedSong.isOnline &&
            controller.isCurrentlyResolving(hydratedSong, manualStreamId) &&
            _playbackInfo.value.isBuffering
        ) {
            return
        }
        pendingPlayRequest = null
        pendingTogglePlayOnConnect = false

        ensureServiceStarted()
        musicService?.updateQueueSnapshot(q, idx)
        if (musicService?.requestAudioFocusIfNeeded() == false) {
            _playbackInfo.value = _playbackInfo.value.copy(
                isPlaying = false,
                isBuffering = false,
                playbackStage = OnlinePlaybackStage.IDLE,
                statusMessage = ""
            )
            return
        }
        syncNotification(hydratedSong, true)

        controller.playSong(
            song = hydratedSong,
            qualityPreference = _onlineQualityPreference.value,
            manualStreamId = manualStreamId,
            resumePositionMs = resumePositionMs,
            onResolvingStarted = {
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = false,
                    isBuffering = true,
                    playbackStage = OnlinePlaybackStage.RESOLVING_STREAM,
                    statusMessage = OnlinePlaybackStage.RESOLVING_STREAM.defaultMessage,
                    playbackError = null,
                    playbackErrorKind = null
                )
            },
            onStageChanged = { stage, msg ->
                val activeLoading = stage == OnlinePlaybackStage.RESOLVING_STREAM ||
                    stage == OnlinePlaybackStage.PREPARING_PLAYER ||
                    stage == OnlinePlaybackStage.BUFFERING_AUDIO
                _playbackInfo.value = _playbackInfo.value.copy(
                    playbackStage = stage,
                    statusMessage = msg,
                    isBuffering = activeLoading
                )
            },
            onPrepared = { updatedSong, _, durMs ->
                _currentSong.value = updatedSong
                _queue.value = _queue.value.map { if (it.id == updatedSong.id) updatedSong else it }
                musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = true,
                    isBuffering = false,
                    playbackStage = OnlinePlaybackStage.PLAYING,
                    statusMessage = OnlinePlaybackStage.PLAYING.defaultMessage,
                    bufferedPositionMs = engine.bufferedPositionMs,
                    durationMs = durMs.coerceAtLeast(updatedSong.durationMs),
                    playbackError = null,
                    playbackErrorKind = null
                )
                musicService?.notifyUserStartedPlayback()
                recordListeningEventAsync(listeningTimeTracker.onTrackStarted(updatedSong))
                startProgressTracker()
                syncNotification(updatedSong, true)
                incrementPlayCount(updatedSong)
                scheduleNextTrackPreload()
                persistPlaybackSessionSnapshotAsync(
                    wasPlaying = true,
                    explicitlyStopped = false,
                    overridePositionMs = engine.currentPosition
                )

                // If crossfade is enabled, smoothly fade in
                if (_crossfadeSeconds.value > 0) {
                    engine.crossfadeIn((_crossfadeSeconds.value * 1000L).coerceAtMost(3000L))
                }
            },
            onCompletion = {
                handleTrackCompletion()
            },
            onError = { errMsg, errKind ->
                isCrossfadingNext = false
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    playbackStage = OnlinePlaybackStage.FAILED,
                    statusMessage = errMsg,
                    playbackError = errMsg,
                    playbackErrorKind = errKind
                )
                _dspDiagnosticMessage.value = errMsg
                syncNotification(_currentSong.value, false)
            },
            onAutoTransitionToNext = { promotedResolved ->
                val curQueue = _queue.value
                val curIdx = _queueIndex.value
                val nextTarget = computeNextPlayableSong(curQueue, curIdx, _playbackInfo.value.repeatMode)
                if (nextTarget != null &&
                    nextTarget.contentUrl.ifBlank { nextTarget.uri.toString() } == promotedResolved.contentUrl
                ) {
                    val newIdx = curQueue.indexOfFirst { it.id == nextTarget.id }.coerceAtLeast(0)
                    val enrichedNext = nextTarget.copy(
                        title = promotedResolved.title.ifBlank { nextTarget.title },
                        artist = promotedResolved.artist.ifBlank { nextTarget.artist },
                        durationMs = if (promotedResolved.durationMs > 0L) promotedResolved.durationMs else nextTarget.durationMs,
                        artworkUrl = promotedResolved.artworkUrl ?: nextTarget.artworkUrl,
                        uploaderUrl = promotedResolved.artistUrl ?: nextTarget.uploaderUrl,
                        mimeType = promotedResolved.selectedStream.mimeType,
                        bitrateKbps = promotedResolved.selectedStream.averageBitrateKbps,
                        streamCodec = promotedResolved.selectedStream.codec,
                        streamQualityLabel = promotedResolved.selectedStream.qualityLabel,
                        isBitrateKnown = promotedResolved.selectedStream.averageBitrateKbps > 0,
                        isSampleRateKnown = false
                    )
                    crossfadeToken++
                    isCrossfadingNext = false
                    _trackTransitionDirection.value = 1
                    _queueIndex.value = newIdx
                    _currentSong.value = enrichedNext
                    _queue.value = curQueue.map { if (it.id == enrichedNext.id) enrichedNext else it }
                    preferences.lastPlayedSongId = enrichedNext.id
                    preferences.lastQueueIndex = newIdx
                    findNextMatchedSong(enrichedNext, _queue.value, newIdx)
                    musicService?.updateQueueSnapshot(_queue.value, newIdx)
                    _playbackInfo.value = _playbackInfo.value.copy(
                        isPlaying = true,
                        isBuffering = engine.isBuffering.value && engine.playbackStage.value != OnlinePlaybackStage.PLAYING,
                        playbackStage = engine.playbackStage.value,
                        statusMessage = engine.statusMessage.value.ifBlank { OnlinePlaybackStage.PLAYING.defaultMessage },
                        bufferedPositionMs = engine.bufferedPositionMs,
                        currentPositionMs = 0L,
                        durationMs = engine.duration.coerceAtLeast(enrichedNext.durationMs),
                        playbackError = null,
                        playbackErrorKind = null
                    )
                    _lyricsPositionMs.value = 0L
                    loadLyricsForTrack(enrichedNext)
                    recordListeningEventAsync(listeningTimeTracker.onTrackStarted(enrichedNext))
                    startProgressTracker()
                    syncNotification(enrichedNext, true)
                    incrementPlayCount(enrichedNext)
                    scheduleNextTrackPreload()
                    persistPlaybackSessionSnapshotAsync(
                        wasPlaying = true,
                        explicitlyStopped = false,
                        overridePositionMs = 0L
                    )
                }
            }
        )
    }

    fun clearPlaybackError() {
        _playbackInfo.value = _playbackInfo.value.copy(
            playbackError = null,
            playbackErrorKind = null
        )
    }

    fun switchToSoundCloudFallback() {
        clearPlaybackError()
        val target = _onlineProviders.value.firstOrNull { it.key == OnlineMusicProvider.KEY_SOUNDCLOUD } ?: return
        selectOnlineProvider(target.key)
        val cur = _currentSong.value
        val candidateQuery = cur?.takeIf { it.isOnline && it.title.isNotBlank() }?.title.orEmpty()
        if (candidateQuery.isNotBlank() && _onlineSearchQuery.value.isBlank()) {
            _onlineSearchQuery.value = candidateQuery
            executeOnlineSearch(candidateQuery)
        }
    }

    fun retryCurrentPlayback() {
        val current = _currentSong.value ?: return
        val lastPos = _playbackInfo.value.currentPositionMs.coerceAtLeast(0L)
        if (current.isOnline) {
            onlineRepository.invalidateStreamCache(current.contentUrl.ifBlank { current.uri.toString() })
        }
        playSong(current, _queue.value, _queueIndex.value, resumePositionMs = lastPos)
    }

    fun resumePlayback() {
        val engine = musicService?.audioEngine
        if (engine == null) {
            pendingTogglePlayOnConnect = true
            if (!isBound) bindMusicService()
            return
        }
        if (engine.isPlaying) return
        val cur = _currentSong.value
        if (cur != null) {
            if (cur.isOnline &&
                (_playbackInfo.value.isBuffering || unifiedPlaybackController?.isCurrentlyResolving(cur) == true)
            ) {
                return
            }
            if (engine.isPrepared) {
                videoController.pauseForAudioHandoff()
                ensureServiceStarted()
                if (musicService?.requestAudioFocusIfNeeded() == false) return
                musicService?.notifyUserStartedPlayback()
                engine.play()
                val resumedPos = engine.currentPosition
                _playbackInfo.value = _playbackInfo.value.copy(isPlaying = true, currentPositionMs = resumedPos)
                _lyricsPositionMs.value = resumedPos
                recordListeningEventAsync(listeningTimeTracker.onPlaybackResumed(cur))
                startProgressTracker()
                if (isFullScreenLyricsOpen) startHighPrecisionLyricsTracker()
                syncNotification(cur, true)
                persistPlaybackSessionSnapshotAsync(
                    wasPlaying = true,
                    explicitlyStopped = false,
                    overridePositionMs = resumedPos
                )
            } else {
                val restoredPos = _playbackInfo.value.currentPositionMs.coerceAtLeast(0L)
                if (cur.isOnline) {
                    onlineRepository.invalidateStreamCache(cur.contentUrl.ifBlank { cur.uri.toString() })
                }
                playSong(cur, _queue.value, _queueIndex.value, resumePositionMs = restoredPos)
            }
        } else if (_allSongs.value.isNotEmpty()) {
            playSong(_allSongs.value.first())
        }
    }

    fun pausePlayback() {
        isCrossfadingNext = false
        val engine = musicService?.audioEngine
        if (engine != null && engine.isPlaying) {
            engine.pause()
        }
        val pausedPos = engine?.currentPosition ?: _playbackInfo.value.currentPositionMs
        musicService?.notifyUserManualPause()
        musicService?.abandonAudioFocus()
        recordListeningEventAsync(listeningTimeTracker.onPlaybackPausedOrStopped())
        _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false, currentPositionMs = pausedPos)
        _lyricsPositionMs.value = pausedPos
        lyricsPositionJob?.cancel()
        persistCurrentQueuePositionNow(pausedPos)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = false,
            explicitlyStopped = false,
            overridePositionMs = pausedPos
        )
        syncNotification(_currentSong.value, false)
    }

    /**
     * Explicit Stop Playback action (from Now Playing player menu).
     * Stops playback, marks session explicitly stopped so Auto Resume will not restart it,
     * removes notification & deactivates MediaSession, and preserves the queue intact.
     */
    fun stopPlayback() {
        isCrossfadingNext = false
        val engine = musicService?.audioEngine
        val stoppedPos = engine?.currentPosition ?: _playbackInfo.value.currentPositionMs
        recordListeningEventAsync(listeningTimeTracker.onPlaybackPausedOrStopped())
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentPositionMs = stoppedPos
        )
        _lyricsPositionMs.value = stoppedPos
        lyricsPositionJob?.cancel()
        progressTrackingJob?.cancel()
        persistCurrentQueuePositionNow(stoppedPos)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = false,
            explicitlyStopped = true,
            overridePositionMs = stoppedPos
        )
        musicService?.handleExplicitStop()
    }

    private fun handleRemoteStopFromService() {
        isCrossfadingNext = false
        val stoppedPos = musicService?.audioEngine?.currentPosition ?: _playbackInfo.value.currentPositionMs
        recordListeningEventAsync(listeningTimeTracker.onPlaybackPausedOrStopped())
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentPositionMs = stoppedPos
        )
        _lyricsPositionMs.value = stoppedPos
        lyricsPositionJob?.cancel()
        progressTrackingJob?.cancel()
        persistCurrentQueuePositionNow(stoppedPos)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = false,
            explicitlyStopped = true,
            overridePositionMs = stoppedPos
        )
    }

    private fun handleTransientFocusPauseFromService(positionMs: Long) {
        isCrossfadingNext = false
        recordListeningEventAsync(listeningTimeTracker.onPlaybackPausedOrStopped())
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = false,
            currentPositionMs = positionMs
        )
        _lyricsPositionMs.value = positionMs
        lyricsPositionJob?.cancel()
        persistCurrentQueuePositionNow(positionMs)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = true,
            explicitlyStopped = false,
            overridePositionMs = positionMs
        )
        syncNotification(_currentSong.value, false)
    }

    private fun handleTaskRemovedFromService(positionMs: Long, wasPlaying: Boolean) {
        isCrossfadingNext = false
        recordListeningEventAsync(listeningTimeTracker.onPlaybackPausedOrStopped())
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentPositionMs = positionMs
        )
        _lyricsPositionMs.value = positionMs
        lyricsPositionJob?.cancel()
        progressTrackingJob?.cancel()
        persistCurrentQueuePositionNow(positionMs)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = wasPlaying,
            explicitlyStopped = false,
            overridePositionMs = positionMs
        )
    }

    /**
     * Called from MainActivity.onStop(). Persists exact playback position & listening progress.
     * Keeps foreground music playing uninterrupted when [BackgroundPlaybackSettings.backgroundPlaybackEnabled] is On.
     */
    fun onAppBackgrounded(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) return
        val currentlyPlaying = _playbackInfo.value.isPlaying
        val currentPos = musicService?.audioEngine?.currentPosition ?: _playbackInfo.value.currentPositionMs
        recordListeningEventAsync(listeningTimeTracker.checkpointIfPlaying(minElapsedMs = 1_000L))
        persistCurrentQueuePositionNow(currentPos)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = currentlyPlaying,
            explicitlyStopped = false,
            overridePositionMs = currentPos
        )
        if (!backgroundPlaybackSettings.value.backgroundPlaybackEnabled && currentlyPlaying) {
            pausePlayback()
        }
    }

    fun togglePlayPause() {
        val engine = musicService?.audioEngine
        if (engine == null) {
            pendingTogglePlayOnConnect = true
            if (!isBound) bindMusicService()
            return
        }
        if (engine.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    /**
     * Determines the optimal next track using Automix:
     * Evaluates harmonic compatibility (same artist, matching genre, or key/energy tempo flow).
     */
    private fun findNextMatchedSong(current: Song, currentQueue: List<Song>, currentIndex: Int) {
        if (!_matchNextSongEnabled.value) {
            // Harmonic Track Matching is OFF: do not run track analysis or matching jobs.
            _nextMatchedSong.value = null
            return
        }
        if (currentQueue.size <= 1) {
            _nextMatchedSong.value = null
            return
        }

        val candidates = currentQueue.filter { it.id != current.id }
        if (candidates.isEmpty()) return

        // 1. Same artist track with close duration/tempo
        val sameArtist = candidates.filter { it.artist.equals(current.artist, ignoreCase = true) }
        if (sameArtist.isNotEmpty()) {
            _nextMatchedSong.value = sameArtist.first()
            return
        }

        // 2. Same genre track
        val sameGenre = candidates.filter { it.genre.equals(current.genre, ignoreCase = true) }
        if (sameGenre.isNotEmpty()) {
            _nextMatchedSong.value = sameGenre.first()
            return
        }

        // 3. Fallback to natural next track in queue
        val nextIdx = (currentIndex + 1) % currentQueue.size
        _nextMatchedSong.value = currentQueue[nextIdx]
    }

    private var isCrossfadingNext = false
    private var crossfadeToken = 0L

    private fun computeNextPlayableSong(
        q: List<Song> = _queue.value,
        curIdx: Int = _queueIndex.value,
        repeatMode: RepeatMode = _playbackInfo.value.repeatMode
    ): Song? {
        if (q.isEmpty()) return null
        if (repeatMode == RepeatMode.ONE) return null
        val matched = _nextMatchedSong.value
        val curId = _currentSong.value?.id
        if (_automixEnabled.value &&
            _matchNextSongEnabled.value &&
            matched != null &&
            matched.id != curId &&
            q.any { it.id == matched.id }
        ) {
            return matched
        }
        val nextIdx = curIdx + 1
        return when {
            nextIdx < q.size -> q[nextIdx]
            repeatMode == RepeatMode.ALL -> q[0]
            else -> null
        }
    }

    private fun scheduleNextTrackPreload() {
        val controller = unifiedPlaybackController ?: return
        val nextSong = computeNextPlayableSong()
        if (nextSong == null || !nextSong.isOnline) {
            controller.clearPreloadedNextTrack()
            return
        }
        val preloadEnabled = cacheSettings.value.preloadUpcomingEnabled
        val crossfadeEnabled = _crossfadeSeconds.value > 0
        // "Crossfade may still prepare the next song when enabled."
        if (!preloadEnabled && !crossfadeEnabled) {
            controller.clearPreloadedNextTrack()
            return
        }
        val stageInPlayer = preloadEnabled && !crossfadeEnabled && _playbackInfo.value.repeatMode != RepeatMode.ONE
        controller.preloadNextTrack(
            song = nextSong,
            qualityPreference = _onlineQualityPreference.value,
            stageIntoPlayer = stageInPlayer,
            preloadAudioWindow = preloadEnabled
        )
    }

    fun playNext(withCrossfade: Boolean = true) {
        val q = _queue.value
        if (q.isEmpty()) return

        // Record early skip (< 35% of track) for adaptive Song Radio & Sonora Recommend personalization
        val currentPlaying = _currentSong.value
        val curDur = _playbackInfo.value.durationMs
        val curPos = _playbackInfo.value.currentPositionMs
        if (currentPlaying != null && curDur > 10_000L && curPos < (curDur * 0.35f).toLong()) {
            if (_songRadioSession.value.isActive) {
                _songRadioSession.update { data.online.MusicDiscoveryAndRadioEngine.recordRadioSkip(it, currentPlaying) }
            }
            viewModelScope.launch(Dispatchers.IO) {
                sonoraRecommendCachePrefs.recordEarlySkip(currentPlaying.id, currentPlaying.artist)
            }
        }

        val matched = _nextMatchedSong.value
        val targetSong = if (_automixEnabled.value && _matchNextSongEnabled.value && matched != null && q.any { it.id == matched.id }) {
            matched
        } else {
            var nextIdx = _queueIndex.value + 1
            if (nextIdx >= q.size) {
                if (_playbackInfo.value.repeatMode == RepeatMode.ALL) {
                    nextIdx = 0
                } else {
                    crossfadeToken++
                    isCrossfadingNext = false
                    return
                }
            }
            q[nextIdx]
        }

        val targetIdx = q.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
        val fadeSec = _crossfadeSeconds.value

        if (withCrossfade && fadeSec > 0 && musicService?.audioEngine?.isPlaying == true && !isCrossfadingNext) {
            val myFadeToken = ++crossfadeToken
            isCrossfadingNext = true
            musicService?.audioEngine?.crossfadeOut(fadeSec * 1000L) {
                if (myFadeToken != crossfadeToken) return@crossfadeOut
                isCrossfadingNext = false
                playSong(targetSong, q, targetIdx, transitionDirection = 1)
            }
        } else {
            crossfadeToken++
            isCrossfadingNext = false
            playSong(targetSong, q, targetIdx, transitionDirection = 1)
        }
    }

    fun playPrevious() {
        crossfadeToken++
        isCrossfadingNext = false
        val engine = musicService?.audioEngine
        if (engine != null && engine.currentPosition > 3000) {
            seekTo(0)
            return
        }
        val q = _queue.value
        if (q.isEmpty()) return
        var prevIdx = _queueIndex.value - 1
        if (prevIdx < 0) {
            prevIdx = if (_playbackInfo.value.repeatMode == RepeatMode.ALL) q.size - 1 else 0
        }
        playSong(q[prevIdx], q, prevIdx, transitionDirection = -1)
    }

    fun seekTo(positionMs: Long) {
        val currentInfo = _playbackInfo.value
        if (!currentInfo.canUserSeek) return
        val maxDur = currentInfo.durationMs.coerceAtLeast(0L)
        val safePos = if (maxDur > 0L) positionMs.coerceIn(0L, maxDur) else positionMs.coerceAtLeast(0L)
        musicService?.audioEngine?.seekTo(safePos)
        _playbackInfo.value = currentInfo.copy(currentPositionMs = safePos)
        _lyricsPositionMs.value = safePos
        musicService?.notificationManager?.updatePlaybackState(
            isPlaying = currentInfo.isPlaying,
            positionMs = safePos,
            durationMs = currentInfo.durationMs,
            isShuffle = currentInfo.shuffleMode == ShuffleMode.ON,
            repeatMode = currentInfo.repeatMode
        )
    }

    fun toggleShuffle() {
        val newMode = if (_playbackInfo.value.shuffleMode == ShuffleMode.OFF) ShuffleMode.ON else ShuffleMode.OFF
        _playbackInfo.value = _playbackInfo.value.copy(shuffleMode = newMode)
        preferences.shuffleMode = newMode
        if (newMode == ShuffleMode.ON) {
            val cur = _currentSong.value
            val currentQ = _queue.value
            if (currentQ.isNotEmpty()) {
                originalQueueBeforeShuffle = currentQ
            }
            val shuffled = currentQ.shuffled()
            val finalQueue = if (cur != null) listOf(cur) + shuffled.filter { it.id != cur.id } else shuffled
            _queue.value = finalQueue
            _queueIndex.value = 0
        } else {
            val restored = originalQueueBeforeShuffle?.takeIf { it.isNotEmpty() } ?: _allSongs.value
            _queue.value = restored
            _queueIndex.value = restored.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(0)
        }
        _currentSong.value?.let { findNextMatchedSong(it, _queue.value, _queueIndex.value) }
        musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
        syncNotification()
        scheduleNextTrackPreload()
        persistUnlimitedQueueAsync()
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = _playbackInfo.value.isPlaying,
            explicitlyStopped = false
        )
    }

    fun cycleRepeatMode() {
        val newMode = when (_playbackInfo.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackInfo.value = _playbackInfo.value.copy(repeatMode = newMode)
        preferences.repeatMode = newMode
        syncNotification()
        scheduleNextTrackPreload()
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = _playbackInfo.value.isPlaying,
            explicitlyStopped = false
        )
    }

    fun syncNotification(song: Song? = _currentSong.value, isPlaying: Boolean = _playbackInfo.value.isPlaying) {
        val cur = song ?: _currentSong.value
        val isShuffle = _playbackInfo.value.shuffleMode == ShuffleMode.ON
        val repeat = _playbackInfo.value.repeatMode
        val dev = _connectedAudioDeviceName.value
        musicService?.updateForegroundNotification(
            song = cur,
            isPlaying = isPlaying,
            isShuffle = isShuffle,
            repeatMode = repeat,
            deviceName = dev
        )
    }

    private fun handleTrackCompletion() {
        val completedSong = _currentSong.value
        if (_songRadioSession.value.isActive && completedSong != null) {
            _songRadioSession.update {
                data.online.MusicDiscoveryAndRadioEngine.recordRadioCompletion(it, completedSong)
            }
            maybeTopUpSongRadioQueue()
        }
        when (_playbackInfo.value.repeatMode) {
            RepeatMode.ONE -> {
                completedSong?.let { playSong(it) }
            }
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                if (_queueIndex.value < _queue.value.size - 1) {
                    playNext()
                } else if (musicExperienceSettings.value.autoSongRadioEnabled && completedSong != null) {
                    startSongRadio(completedSong, autoPlayFirstAdded = true)
                } else {
                    _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false, currentPositionMs = 0)
                    syncNotification(_currentSong.value, false)
                }
            }
        }
    }

    private var secondsCounter = 0

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                delay(250)
                val engine = musicService?.audioEngine
                if (engine != null && engine.isPlaying) {
                    val pos = engine.currentPosition
                    val dur = engine.duration.coerceAtLeast(_currentSong.value?.durationMs ?: 0L)
                    val isLive = engine.isCurrentTrackLive
                    val isSeekable = engine.isCurrentTrackSeekable && !isLive && dur > 0L
                    _playbackInfo.value = _playbackInfo.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        isBuffering = engine.isBuffering.value,
                        bufferedPositionMs = engine.bufferedPositionMs,
                        isSeekable = isSeekable,
                        isLiveStream = isLive
                    )
                    if (!isFullScreenLyricsOpen) {
                        _lyricsPositionMs.value = pos
                    }

                    // Accumulate analytics listening time (strictly while actively playing, never while paused)
                    secondsCounter++
                    if (secondsCounter >= 4) { // every 1 second (4 * 250ms)
                        secondsCounter = 0
                        recordListeningSecond()
                        recordListeningEventAsync(listeningTimeTracker.checkpointIfPlaying(minElapsedMs = 10_000L))
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastQueuePositionPersistMs >= 8_000L) {
                            lastQueuePositionPersistMs = nowMs
                            persistCurrentQueuePositionNow(pos)
                            persistPlaybackSessionSnapshotAsync(
                                wasPlaying = true,
                                explicitlyStopped = false,
                                overridePositionMs = pos
                            )
                        }
                    }

                    // Automix crossfade trigger: when remaining duration reaches crossfade window
                    val crossfadeSec = _crossfadeSeconds.value
                    if (crossfadeSec > 0 && _automixEnabled.value && dur > 10000 && !isCrossfadingNext) {
                        val remainingMs = dur - pos
                        if (remainingMs in 100..(crossfadeSec * 1000L)) {
                            playNext(withCrossfade = true)
                        }
                    }
                }
            }
        }
    }

    private fun recordListeningSecond() {
        val todayKey = synchronized(dateKeyFormatter) { dateKeyFormatter.format(java.util.Date()) }
        preferences.addListeningTime(1, todayKey)
        // Refresh live analytics state
        val todaySec = preferences.getListeningTime(todayKey)
        val cur = _dailyAnalytics.value
        _dailyAnalytics.value = cur.copy(
            dateKey = todayKey,
            listenedSeconds = todaySec
        )
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFav = if (song.isOnline) {
                onlineRepository.toggleFavoriteOnlineTrack(song)
            } else {
                val toggled = !song.isFavorite
                dao.upsertFavorite(song.id, toggled)
                toggled
            }
            _allSongs.value = _allSongs.value.map {
                if (it.id == song.id) it.copy(isFavorite = newFav) else it
            }
            _queue.value = _queue.value.map {
                if (it.id == song.id) it.copy(isFavorite = newFav) else it
            }
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = _currentSong.value?.copy(isFavorite = newFav)
            }
            if (newFav && _songRadioSession.value.isActive) {
                _songRadioSession.update {
                    data.online.MusicDiscoveryAndRadioEngine.recordRadioLike(it, song)
                }
            }
            scheduleSonoraRecommendationsRefreshDebounced()
        }
    }

    private fun incrementPlayCount(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            if (song.isOnline) {
                onlineRepository.recordOnlinePlay(song)
            } else {
                val now = System.currentTimeMillis()
                dao.upsertIncrementPlayCount(song.id, now)
                _allSongs.value = _allSongs.value.map {
                    if (it.id == song.id) it.copy(playCount = it.playCount + 1, lastPlayedTimestamp = now) else it
                }
            }
            if (song.playCount >= 1) {
                sonoraRecommendCachePrefs.recordRepeatedListen(song.id, song.artist)
            }
        }
    }

    // --- Unlimited Room-Backed Queue Management ---

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.value.indices || toIndex !in _queue.value.indices) return
        val items = _queuedItems.value.toMutableList()
        val list = _queue.value.toMutableList()
        val activeItemId = items.getOrNull(_queueIndex.value)?.queueItemId
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        if (fromIndex in items.indices && toIndex in 0..items.size) {
            val wrapped = items.removeAt(fromIndex)
            items.add(toIndex.coerceIn(0, items.size), wrapped)
            _queuedItems.value = items
        } else {
            syncQueuedItemsFromQueue(list)
        }
        _queue.value = list
        val cur = _currentSong.value
        val newIdx = activeItemId?.let { id -> _queuedItems.value.indexOfFirst { it.queueItemId == id } }
            ?.takeIf { it >= 0 }
            ?: list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
        _queueIndex.value = newIdx
        cur?.let { findNextMatchedSong(it, list, newIdx) }
        musicService?.updateQueueSnapshot(list, newIdx)
        persistUnlimitedQueueAsync()
        scheduleNextTrackPreload()
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val items = _queuedItems.value.toMutableList()
        val list = _queue.value.toMutableList()
        val activeItemId = items.getOrNull(_queueIndex.value)?.queueItemId
        list.removeAt(index)
        if (index in items.indices) {
            items.removeAt(index)
            _queuedItems.value = items
        } else {
            syncQueuedItemsFromQueue(list)
        }
        _queue.value = list
        val cur = _currentSong.value
        val newIdx = activeItemId?.let { id -> _queuedItems.value.indexOfFirst { it.queueItemId == id } }
            ?.takeIf { it >= 0 }
            ?: list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
        _queueIndex.value = newIdx.coerceAtMost((list.size - 1).coerceAtLeast(0))
        cur?.let { findNextMatchedSong(it, list, _queueIndex.value) }
        musicService?.updateQueueSnapshot(list, _queueIndex.value)
        persistUnlimitedQueueAsync()
        scheduleNextTrackPreload()
    }

    fun clearQueue() {
        val cur = _currentSong.value
        val newList = if (cur != null) listOf(cur) else emptyList()
        _queue.value = newList
        _queuedItems.value = musicQueueRepository.wrapSongs(newList)
        _queueIndex.value = 0
        musicService?.updateQueueSnapshot(_queue.value, 0)
        persistUnlimitedQueueAsync()
        unifiedPlaybackController?.clearPreloadedNextTrack()
    }

    fun saveQueueAsPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = dao.insertPlaylist(PlaylistEntity(name = name))
            _queue.value.forEachIndexed { idx, s ->
                if (s.isOnline && dao.getOnlineTrackById(s.id) == null) {
                    val track = OnlineTrackMapper.toOnlineTrack(s)
                    dao.upsertOnlineTrack(OnlineTrackMapper.mapOnlineTrackToEntity(track))
                }
                dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, s.id, idx))
            }
        }
    }

    fun addToQueue(song: Song) {
        addAllToQueue(listOf(song))
    }

    fun addAllToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val wrapped = musicQueueRepository.wrapSongs(songs)
        val updatedItems = _queuedItems.value + wrapped
        val updatedList = _queue.value + songs
        _queuedItems.value = updatedItems
        _queue.value = updatedList
        _currentSong.value?.let { findNextMatchedSong(it, updatedList, _queueIndex.value) }
        musicService?.updateQueueSnapshot(updatedList, _queueIndex.value)
        persistUnlimitedQueueAsync()
        scheduleNextTrackPreload()
    }

    fun playNextInQueue(song: Song) {
        playAllNextInQueue(listOf(song))
        viewModelScope.launch(Dispatchers.IO) {
            sonoraRecommendCachePrefs.recordPlayNext(song.id, song.artist)
        }
    }

    fun playAllNextInQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val q = _queue.value.toMutableList()
        val items = _queuedItems.value.toMutableList()
        val insertPos = (_queueIndex.value + 1).coerceIn(0, q.size)
        val wrapped = musicQueueRepository.wrapSongs(songs)
        q.addAll(insertPos, songs)
        items.addAll(insertPos.coerceAtMost(items.size), wrapped)
        _queue.value = q
        _queuedItems.value = items
        _currentSong.value?.let { findNextMatchedSong(it, q, _queueIndex.value) }
        musicService?.updateQueueSnapshot(q, _queueIndex.value)
        persistUnlimitedQueueAsync()
        scheduleNextTrackPreload()
    }

    fun replaceQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, songs.lastIndex)
        playSong(songs[safeIdx], songs, safeIdx)
    }

    fun shuffleRemainingQueue() {
        val q = _queue.value
        val items = _queuedItems.value
        if (q.size <= 2) return
        val curIdx = _queueIndex.value.coerceIn(0, q.lastIndex)
        if (items.size == q.size) {
            val playedAndCurrent = items.subList(0, curIdx + 1)
            val upNextShuffled = items.subList(curIdx + 1, items.size).shuffled()
            val merged = playedAndCurrent + upNextShuffled
            _queuedItems.value = merged
            _queue.value = merged.map { it.song }
        } else {
            val playedAndCurrent = q.subList(0, curIdx + 1)
            val upNextShuffled = q.subList(curIdx + 1, q.size).shuffled()
            _queue.value = playedAndCurrent + upNextShuffled
            syncQueuedItemsFromQueue(_queue.value)
        }
        _currentSong.value?.let { findNextMatchedSong(it, _queue.value, _queueIndex.value) }
        musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
        persistUnlimitedQueueAsync()
        scheduleNextTrackPreload()
    }

    private fun syncQueuedItemsFromQueue(songs: List<Song>) {
        val current = _queuedItems.value
        if (current.size == songs.size && current.indices.all { current[it].song.id == songs[it].id }) {
            _queuedItems.value = current.mapIndexed { i, item -> item.copy(song = songs[i]) }
        } else {
            _queuedItems.value = musicQueueRepository.wrapSongs(songs)
        }
    }

    private fun restorePersistedUnlimitedQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                musicQueueRepository.initializeIdGenerator()
                val sessionSettings = playbackLifecyclePreferences.sessionSettingsFlow.first()
                val bgSettings = playbackLifecyclePreferences.backgroundSettingsFlow.first()
                if (!sessionSettings.restorePreviousQueue) return@runCatching

                val restored = musicQueueRepository.restoreQueue()
                val snapshot = listeningAnalyticsRepository.getPlaybackSession()
                if (restored.items.isNotEmpty() && _queue.value.isEmpty() && _currentSong.value == null) {
                    val songs = restored.items.map { it.song }
                    val safeIdx = (snapshot?.queueIndex ?: restored.currentIndex).coerceIn(0, songs.lastIndex)
                    val targetSong = songs[safeIdx]
                    val restoredPositionMs = if (sessionSettings.restorePlaybackPosition) {
                        (snapshot?.positionMs ?: restored.playbackPositionMs).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    val restoredRepeat = snapshot?.repeatMode?.let { stored ->
                        RepeatMode.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
                    } ?: _playbackInfo.value.repeatMode
                    val restoredShuffle = if (snapshot != null) {
                        if (snapshot.isShuffle) ShuffleMode.ON else ShuffleMode.OFF
                    } else {
                        _playbackInfo.value.shuffleMode
                    }
                    val restoredSpeed = (snapshot?.playbackSpeed ?: _playbackSpeed.value).coerceIn(0.5f, 2.0f)

                    if (snapshot != null && snapshot.lastPlayerStyle.isNotBlank()) {
                        com.example.model.PlayerStyle.entries
                            .firstOrNull { it.name.equals(snapshot.lastPlayerStyle, ignoreCase = true) }
                            ?.let { restoredStyle -> _playerStyle.value = restoredStyle }
                    }
                    if (snapshot != null && snapshot.contextType.isNotBlank()) {
                        activePlaybackContextType = snapshot.contextType
                        activePlaybackContextLabel = snapshot.contextLabel
                    }

                    _queuedItems.value = restored.items
                    _queue.value = songs
                    _queueIndex.value = safeIdx
                    _currentSong.value = targetSong
                    _playbackSpeed.value = restoredSpeed
                    _playbackInfo.value = _playbackInfo.value.copy(
                        isPlaying = false,
                        currentPositionMs = restoredPositionMs,
                        durationMs = targetSong.durationMs,
                        repeatMode = restoredRepeat,
                        shuffleMode = restoredShuffle,
                        playbackSpeed = restoredSpeed
                    )
                    _lyricsPositionMs.value = restoredPositionMs

                    val shouldAutoResume = bgSettings.autoResumeOnAppLaunch &&
                        snapshot != null &&
                        !snapshot.explicitlyStopped &&
                        snapshot.wasPlayingWhenSaved

                    if (shouldAutoResume) {
                        val isTrackAvailable = isRestoredTrackAvailable(targetSong)
                        if (!isTrackAvailable) {
                            _sessionRestoreNotice.value = "\"${targetSong.title}\" is no longer available. Your queue was preserved."
                        } else {
                            if (targetSong.isOnline) {
                                // Expired online stream URLs are invalidated so a fresh stream resolves cleanly.
                                onlineRepository.invalidateStreamCache(targetSong.contentUrl.ifBlank { targetSong.uri.toString() })
                            }
                            withContext(Dispatchers.Main) {
                                playSong(
                                    song = targetSong,
                                    newQueue = songs,
                                    startIndex = safeIdx,
                                    resumePositionMs = restoredPositionMs
                                )
                            }
                        }
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Unable to restore persisted music session", e)
            }
        }
    }

    private fun isRestoredTrackAvailable(song: Song): Boolean {
        if (song.isOnline) {
            return song.contentUrl.isNotBlank() || song.uri.toString().isNotBlank()
        }
        val path = song.dataPath
        if (path.isNotBlank()) {
            val file = java.io.File(path)
            if (file.exists() && file.length() > 0L) return true
        }
        if (song.uri != android.net.Uri.EMPTY) {
            val canOpen = runCatching {
                getApplication<Application>().contentResolver.openAssetFileDescriptor(song.uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            if (canOpen) return true
        }
        return false
    }

    fun dismissSessionRestoreNotice() {
        _sessionRestoreNotice.value = null
    }

    private fun recordListeningEventAsync(event: data.analytics.ListeningEventEntity?) {
        if (event == null || event.listenedMs < 1_000L) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                listeningAnalyticsRepository.recordEvent(event)
            }.onFailure { e ->
                Log.w(TAG, "Failed recording listening analytics event: ${e.message}")
            }
        }
    }

    fun setAnalyticsTimeRange(range: domain.analytics.AnalyticsTimeRange) {
        _analyticsTimeRange.value = range
    }

    fun clearListeningHistory() {
        listeningTimeTracker.reset()
        preferences.recentSearches = emptyList()
        _recentSearches.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                listeningAnalyticsRepository.clearListeningHistory()
                dao.clearLocalPlayHistory()
                dao.clearOnlinePlayHistory()
                dao.clearAllVideoHistory()
                dao.pruneUnreferencedVideos()
                dao.clearOnlineSearchHistory()
                dao.clearVideoSearchHistory()
                sonoraRecommendCachePrefs.clearAllForHistoryReset()
            }.onFailure { e ->
                Log.w(TAG, "Failed clearing listening history: ${e.message}")
            }
            scheduleSonoraRecommendationsRefreshDebounced()
        }
    }

    private fun persistPlaybackSessionSnapshotAsync(
        wasPlaying: Boolean,
        explicitlyStopped: Boolean,
        overridePositionMs: Long? = null
    ) {
        val song = _currentSong.value ?: return
        val info = _playbackInfo.value
        val snapshot = data.analytics.PlaybackSessionSnapshotEntity(
            id = 1,
            lastTrackId = song.id,
            isOnline = song.isOnline,
            positionMs = (overridePositionMs ?: info.currentPositionMs).coerceAtLeast(0L),
            queueIndex = _queueIndex.value.coerceAtLeast(0),
            repeatMode = info.repeatMode.name,
            isShuffle = info.shuffleMode == ShuffleMode.ON,
            playbackSpeed = _playbackSpeed.value,
            volume = 1.0f,
            contextType = activePlaybackContextType,
            contextLabel = activePlaybackContextLabel,
            lastPlayerStyle = _playerStyle.value.name,
            wasPlayingWhenSaved = wasPlaying,
            explicitlyStopped = explicitlyStopped,
            savedAtMs = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                listeningAnalyticsRepository.savePlaybackSession(snapshot)
            }.onFailure { e ->
                Log.w(TAG, "Failed saving playback session snapshot: ${e.message}")
            }
        }
    }

    private fun persistUnlimitedQueueAsync() {
        if (!musicExperienceSettings.value.unlimitedQueueEnabled) return
        val snapshotItems = _queuedItems.value
        val snapshotIdx = _queueIndex.value
        val snapshotPos = _playbackInfo.value.currentPositionMs
        queuePersistJob?.cancel()
        queuePersistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            runCatching {
                musicQueueRepository.persistQueue(
                    items = snapshotItems,
                    currentIndex = snapshotIdx,
                    playbackPositionMs = snapshotPos
                )
            }.onFailure { e -> Log.w(TAG, "Failed to persist unlimited queue", e) }
        }
    }

    private fun persistCurrentQueuePositionNow(positionMs: Long) {
        val items = _queuedItems.value
        val idx = _queueIndex.value
        val activeId = items.getOrNull(idx)?.queueItemId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                musicQueueRepository.persistPlaybackPosition(activeId, idx, positionMs)
            }
        }
    }

    // --- Background Playback & Session Management Settings ---

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setBackgroundPlaybackEnabled(enabled) }
    }

    fun setResumeAfterPhoneCall(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setResumeAfterPhoneCall(enabled) }
    }

    fun setAutoResumeOnAppLaunch(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setAutoResumeOnAppLaunch(enabled) }
    }

    fun setRestorePreviousQueue(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setRestorePreviousQueue(enabled) }
    }

    fun setRestorePlaybackPosition(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setRestorePlaybackPosition(enabled) }
    }

    fun setStopPlaybackOnTaskRemoved(enabled: Boolean) {
        viewModelScope.launch { playbackLifecyclePreferences.setStopPlaybackOnTaskRemoved(enabled) }
    }

    // --- Experience Settings Setters (Part 7) ---

    fun setAutoPipEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setAutoPipEnabled(enabled) }
    }

    fun setInAppSwipeMiniPlayerEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setInAppSwipeMiniPlayerEnabled(enabled) }
    }

    fun setPinchToZoomEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setPinchToZoomEnabled(enabled) }
    }

    fun setAutoVideoQualityEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setAutoVideoQualityEnabled(enabled) }
    }

    fun setPreferHighestQualityOnWifi(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setPreferHighestQualityOnWifi(enabled) }
    }

    fun setMobileDataSaverEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setMobileDataSaverEnabled(enabled) }
    }

    fun setAllowHighQualityOnMobileData(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setAllowHighQualityOnMobileData(enabled) }
    }

    fun setPreferredWifiQualityHeight(heightPx: Int) {
        viewModelScope.launch { experiencePreferences.setPreferredWifiQualityHeight(heightPx) }
    }

    fun setMusicRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setMusicRecommendationsEnabled(enabled) }
    }

    fun setAutomaticSongRadioEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setAutoSongRadioEnabled(enabled) }
    }

    fun setUnlimitedQueueEnabled(enabled: Boolean) {
        viewModelScope.launch { experiencePreferences.setUnlimitedQueueEnabled(enabled) }
    }

    fun setAlbumArtworkQualityMode(mode: data.preferences.AlbumArtworkQualityMode) {
        viewModelScope.launch {
            experiencePreferences.setAlbumArtworkQualityMode(mode)
            data.artwork.AlbumArtworkQualityManager.currentQualityMode = mode
            AlbumArtHelper.clearOnlineArtworkCache()
        }
    }

    // --- Advanced Music Discovery (Moods, India Regional, Mixes) & Continuous Song Radio ---

    fun loadMoodDiscovery(mood: data.online.MusicMoodActivity) {
        _selectedMoodActivity.value = mood
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        val country = _onlineContentCountry.value
        val discoverLang = _discoverMusicLanguage.value
        moodDiscoveryJob?.cancel()
        moodDiscoveryJob = viewModelScope.launch {
            _isLoadingMoodDiscovery.value = true
            val langPrefix = if (discoverLang != DiscoverMusicLanguage.ALL) {
                "${discoverLang.label} "
            } else {
                ""
            }
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = "$langPrefix${mood.querySuffix}",
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = country,
                    discoverLanguage = discoverLang
                )
            ) {
                is OnlineResult.Success -> _moodDiscoveryTracks.value = res.value.take(20)
                is OnlineResult.Error -> _moodDiscoveryTracks.value = emptyList()
            }
            _isLoadingMoodDiscovery.value = false
        }
    }

    fun loadIndiaMusicCategory(category: data.online.IndiaMusicCategory) {
        _selectedIndiaCategory.value = category
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        indiaDiscoveryJob?.cancel()
        indiaDiscoveryJob = viewModelScope.launch {
            _isLoadingIndiaDiscovery.value = true
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = category.query,
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = domain.online.RegionMusicEligibilityFilter.REGION_INDIA,
                    discoverLanguage = _discoverMusicLanguage.value
                )
            ) {
                is OnlineResult.Success -> _indiaDiscoveryTracks.value = res.value.take(20)
                is OnlineResult.Error -> _indiaDiscoveryTracks.value = emptyList()
            }
            _isLoadingIndiaDiscovery.value = false
        }
    }

    fun ensureAdvancedMusicDiscoveryLoaded() {
        if (_moodDiscoveryTracks.value.isEmpty() && !_isLoadingMoodDiscovery.value) {
            loadMoodDiscovery(_selectedMoodActivity.value)
        }
        if (domain.online.RegionMusicEligibilityFilter.isIndiaRegion(_onlineContentCountry.value) &&
            _indiaDiscoveryTracks.value.isEmpty() &&
            !_isLoadingIndiaDiscovery.value
        ) {
            loadIndiaMusicCategory(_selectedIndiaCategory.value)
        }
    }

    fun playPersonalizedMixCard(card: data.online.PersonalizedMusicMixCard) {
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull()
        val country = _onlineContentCountry.value
        val eligiblePreviews = domain.online.RegionMusicEligibilityFilter.filterSongsForRegion(
            songs = card.previewSongs,
            countryCode = country
        )
        if (provider == null) {
            if (eligiblePreviews.isNotEmpty()) {
                playCollection(eligiblePreviews, shuffle = false)
            }
            return
        }
        viewModelScope.launch {
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = card.query,
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = country,
                    discoverLanguage = _discoverMusicLanguage.value
                )
            ) {
                is OnlineResult.Success -> {
                    val tracks = res.value
                    if (tracks.isNotEmpty()) {
                        val songs = tracks.map { OnlineTrackMapper.toUnifiedSong(it) }
                        if (card.id == "sonora_personalized_radio") {
                            _songRadioSession.value = data.online.SongRadioSession(
                                isActive = true,
                                seedSong = songs.first(),
                                playedSongIds = setOf(songs.first().id),
                                currentQueryHint = card.query
                            )
                        }
                        playSong(songs.first(), songs, 0)
                    } else if (eligiblePreviews.isNotEmpty()) {
                        playCollection(eligiblePreviews, shuffle = false)
                    } else {
                        _libraryFeedbackMessage.value =
                            domain.online.RegionMusicEligibilityFilter.emptyRegionRecommendationsMessage(
                                sectionTitle = card.title,
                                countryCode = country,
                                providerName = provider.displayName
                            )
                    }
                }
                is OnlineResult.Error -> {
                    if (eligiblePreviews.isNotEmpty()) {
                        playCollection(eligiblePreviews, shuffle = false)
                    } else {
                        _libraryFeedbackMessage.value = res.message
                    }
                }
            }
        }
    }

    fun loadRecommendationsForCurrentSong(forceRefresh: Boolean = false) {
        val seed = _currentSong.value ?: return
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        val country = _onlineContentCountry.value
        if (!forceRefresh && _currentSongRecommendations.value.isNotEmpty() && _songRadioSession.value.seedSong?.id == seed.id) {
            return
        }
        currentSongRecsJob?.cancel()
        currentSongRecsJob = viewModelScope.launch {
            _isLoadingCurrentSongRecommendations.value = true
            val query = data.online.MusicDiscoveryAndRadioEngine.buildRelatedSongsQuery(
                seedSong = seed,
                session = _songRadioSession.value,
                countryCode = country,
                discoverLanguage = _discoverMusicLanguage.value
            )
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = query,
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = country,
                    discoverLanguage = _discoverMusicLanguage.value
                )
            ) {
                is OnlineResult.Success -> {
                    val candidateSongs = res.value.map { OnlineTrackMapper.toUnifiedSong(it) }
                    val ranked = data.online.MusicDiscoveryAndRadioEngine.rankRadioCandidates(
                        candidates = candidateSongs,
                        seedSong = seed,
                        session = _songRadioSession.value,
                        existingQueueIds = setOf(seed.id),
                        limit = 15,
                        countryCode = country
                    )
                    _currentSongRecommendations.value = ranked
                }
                is OnlineResult.Error -> {
                    // Fallback to region-eligible local library similar artist/genre tracks if offline (never backfill foreign tracks in India mode)
                    val localFallback = domain.online.RegionMusicEligibilityFilter.filterSongsForRegion(
                        songs = _allSongs.value.filter {
                            it.id != seed.id && (
                                it.artist.equals(seed.artist, ignoreCase = true) ||
                                    (seed.genre.isNotBlank() && it.genre.equals(seed.genre, ignoreCase = true))
                                )
                        },
                        countryCode = country
                    ).take(12)
                    _currentSongRecommendations.value = localFallback
                }
            }
            _isLoadingCurrentSongRecommendations.value = false
        }
    }

    fun startSongRadio(seedSong: Song, autoPlayFirstAdded: Boolean = false) {
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull()
        val country = _onlineContentCountry.value
        val existingPlayed = _songRadioSession.value.playedSongIds + seedSong.id
        val query = data.online.MusicDiscoveryAndRadioEngine.buildRelatedSongsQuery(
            seedSong = seedSong,
            session = _songRadioSession.value.copy(isActive = true, seedSong = seedSong),
            countryCode = country,
            discoverLanguage = _discoverMusicLanguage.value
        )
        _songRadioSession.value = data.online.SongRadioSession(
            isActive = true,
            seedSong = seedSong,
            playedSongIds = existingPlayed,
            currentQueryHint = query
        )
        _libraryFeedbackMessage.value = "Started SONORA Radio for \"${seedSong.title}\""

        if (provider == null) return
        radioTopUpJob?.cancel()
        radioTopUpJob = viewModelScope.launch {
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = query,
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = country,
                    discoverLanguage = _discoverMusicLanguage.value
                )
            ) {
                is OnlineResult.Success -> {
                    val candidateSongs = res.value.map { OnlineTrackMapper.toUnifiedSong(it) }
                    val ranked = data.online.MusicDiscoveryAndRadioEngine.rankRadioCandidates(
                        candidates = candidateSongs,
                        seedSong = seedSong,
                        session = _songRadioSession.value,
                        existingQueueIds = _queue.value.map { it.id }.toSet(),
                        limit = 15,
                        countryCode = country
                    )
                    _currentSongRecommendations.value = ranked
                    if (ranked.isNotEmpty()) {
                        if (autoPlayFirstAdded) {
                            addAllToQueue(ranked)
                            playNext(withCrossfade = false)
                        } else if (_currentSong.value?.id == seedSong.id) {
                            addAllToQueue(ranked)
                        } else {
                            playSong(seedSong, listOf(seedSong) + ranked, 0)
                        }
                    } else {
                        _libraryFeedbackMessage.value =
                            domain.online.RegionMusicEligibilityFilter.emptyRegionRecommendationsMessage(
                                sectionTitle = "song radio recommendations",
                                countryCode = country,
                                providerName = provider.displayName
                            )
                    }
                }
                is OnlineResult.Error -> {
                    _libraryFeedbackMessage.value = res.message
                }
            }
        }
    }

    fun stopSongRadio() {
        radioTopUpJob?.cancel()
        _songRadioSession.value = data.online.SongRadioSession(isActive = false)
        _libraryFeedbackMessage.value = "Stopped Song Radio"
    }

    private fun maybeTopUpSongRadioQueue() {
        val session = _songRadioSession.value
        if (!session.isActive) return
        val remaining = _queue.value.size - 1 - _queueIndex.value
        if (remaining > 3 || radioTopUpJob?.isActive == true) return
        val seed = _currentSong.value ?: session.seedSong ?: return
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        val country = _onlineContentCountry.value
        radioTopUpJob = viewModelScope.launch {
            val query = data.online.MusicDiscoveryAndRadioEngine.buildRelatedSongsQuery(
                seedSong = seed,
                session = session,
                countryCode = country,
                discoverLanguage = _discoverMusicLanguage.value
            )
            when (
                val res = onlineRepository.searchRegionRecommendations(
                    query = query,
                    provider = provider,
                    filter = OnlineSearchFilter.SONGS,
                    countryCode = country,
                    discoverLanguage = _discoverMusicLanguage.value
                )
            ) {
                is OnlineResult.Success -> {
                    val candidateSongs = res.value.map { OnlineTrackMapper.toUnifiedSong(it) }
                    val nextBatch = data.online.MusicDiscoveryAndRadioEngine.rankRadioCandidates(
                        candidates = candidateSongs,
                        seedSong = seed,
                        session = _songRadioSession.value,
                        existingQueueIds = _queue.value.map { it.id }.toSet(),
                        limit = 10,
                        countryCode = country
                    )
                    if (nextBatch.isNotEmpty()) {
                        addAllToQueue(nextBatch)
                    }
                }
                is OnlineResult.Error -> Unit
            }
        }
    }

    // --- Audio Effects & Spatial Controls ---

    fun setBassBoost(value: Int) {
        _bassBoost.value = value
        preferences.bassBoost = value
        musicService?.audioEngine?.setBassBoost(value)
    }

    fun setVirtualizer(value: Int) {
        _virtualizer.value = value
        preferences.virtualizer = value
        musicService?.audioEngine?.setVirtualizer(value)
    }

    fun setReverb(preset: Short) {
        _reverbPreset.value = preset
        preferences.reverbPreset = preset
        musicService?.audioEngine?.setReverbPreset(preset)
    }

    fun setLoudness(gainMb: Int) {
        _loudnessGainMb.value = gainMb
        preferences.loudnessGainMb = gainMb
        musicService?.audioEngine?.setLoudnessEnhancer(gainMb)
    }

    fun updateSpatialConfig(config: SpatialAudioConfig) {
        _spatialConfig.value = config
        preferences.spatialConfig = config
        musicService?.audioEngine?.updateSpatialAudioConfig(config)
    }

    fun setSpatialProfile(profile: com.example.model.SpatialProfile) {
        val updated = _spatialConfig.value.copy(spatialProfile = profile, isEnabled = true)
        updateSpatialConfig(updated)
    }

    fun setSpatialIntensity(intensity: com.example.model.SpatialIntensity) {
        val updated = _spatialConfig.value.copy(spatialIntensity = intensity)
        updateSpatialConfig(updated)
    }

    fun selectSpatialPreset(index: Int) {
        if (index !in spatialPresets.indices) return
        _selectedSpatialPresetIndex.value = index
        preferences.selectedSpatialPresetIndex = index
        val p = spatialPresets[index]
        val updatedConfig = _spatialConfig.value.copy(
            isEnabled = true,
            mode = p.mode,
            intensity = p.intensity,
            soundstageWidth = p.soundstageWidth,
            stereoWidening = p.stereoWidening,
            listenerX = p.listenerX,
            listenerY = p.listenerY,
            spatialProfile = p.profile
        )
        updateSpatialConfig(updatedConfig)
        if (p.reverbPreset != PresetReverb.PRESET_NONE) {
            setReverb(p.reverbPreset)
        }
    }

    fun toggleTwsWideStereo(enabled: Boolean) {
        val updated = _spatialConfig.value.copy(twsWideStereoEnabled = enabled)
        updateSpatialConfig(updated)
    }

    fun setTwsStereoBoost(boost: Float) {
        val updated = _spatialConfig.value.copy(twsStereoBoost = boost.coerceIn(0.5f, 1.5f))
        updateSpatialConfig(updated)
    }

    fun forceBalanceDualEarbuds() {
        val updated = _spatialConfig.value.copy(
            channelBalance = 0.0f,
            listenerX = 0.0f
        )
        updateSpatialConfig(updated)
        musicService?.audioEngine?.forceSymmetricalDualEarbudBalance()
    }

    fun setGlobalEffectsBypass(bypass: Boolean) {
        _globalEffectsBypass.value = bypass
        preferences.globalEffectsBypass = bypass
        musicService?.audioEngine?.setGlobalEffectsBypass(bypass)
    }

    fun setOrbit8dConfig(enabled: Boolean, speed: Float = _orbit8dSpeed.value, depth: Float = _orbit8dDepth.value) {
        val clampedSpeed = speed.coerceIn(0.1f, 2.0f)
        val clampedDepth = depth.coerceIn(0.1f, 1.0f)
        _orbit8dEnabled.value = enabled
        _orbit8dSpeed.value = clampedSpeed
        _orbit8dDepth.value = clampedDepth
        preferences.orbit8dEnabled = enabled
        preferences.orbit8dSpeed = clampedSpeed
        preferences.orbit8dDepth = clampedDepth
        musicService?.audioEngine?.update8dOrbitConfig(enabled, clampedSpeed, clampedDepth)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        preferences.playbackSpeed = clamped
        _playbackInfo.value = _playbackInfo.value.copy(playbackSpeed = clamped)
        musicService?.audioEngine?.setPlaybackSpeed(clamped)
        persistPlaybackSessionSnapshotAsync(
            wasPlaying = _playbackInfo.value.isPlaying,
            explicitlyStopped = false
        )
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _visualizerStyle.value = style
    }

    fun cycleVisualizerStyle() {
        val styles = VisualizerStyle.values()
        val nextIdx = (styles.indexOf(_visualizerStyle.value) + 1) % styles.size
        _visualizerStyle.value = styles[nextIdx]
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences.themeMode = mode
    }

    fun setCrossfadeSeconds(sec: Int) {
        _crossfadeSeconds.value = sec
        preferences.crossfadeSeconds = sec
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        preferences.gaplessEnabled = enabled
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: com.example.model.SortOption) {
        _sortOption.value = option
        preferences.sortOption = option
    }

    // --- Gesture Settings Controls ---

    fun toggleGesturesEnabled(enabled: Boolean) {
        updatePlayerGestureConfig { it.copy(gesturesEnabled = enabled) }
    }

    fun toggleSwipeArtworkToSkip(enabled: Boolean) {
        updatePlayerGestureConfig { it.copy(swipeArtworkEnabled = enabled) }
    }

    fun toggleDoubleTapToSeek(enabled: Boolean) {
        updatePlayerGestureConfig { it.copy(doubleTapEnabled = enabled) }
    }

    fun toggleSwipeDownToCollapse(enabled: Boolean) {
        updatePlayerGestureConfig { it.copy(swipeDownToMinimizeEnabled = enabled) }
    }

    // --- Automix & Song Matching Controls ---

    fun toggleAutomix(enabled: Boolean) {
        _automixEnabled.value = enabled
        preferences.automixEnabled = enabled
    }

    fun toggleMatchNextSong(enabled: Boolean) {
        userUpdatedHarmonicMatching = true
        _matchNextSongEnabled.value = enabled
        preferences.matchNextSongEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            experiencePreferences.setHarmonicTrackMatchingEnabled(enabled)
        }
        if (enabled) {
            _currentSong.value?.let { cur ->
                findNextMatchedSong(cur, _queue.value, _queueIndex.value)
            }
        } else {
            _nextMatchedSong.value = null
        }
    }

    // --- Daily Mix & Analytics Algorithms ---

    fun generateDailyMixes(songs: List<Song>) {
        _dailyMixes.value = domain.home.HomeDiscoveryEngine.buildDailyMixes(songs)
    }

    fun updateDailyAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayKey = format.format(java.util.Date())
            val todaySec = preferences.getListeningTime(todayKey)

            val songs = _allSongs.value
            val playedCount = songs.sumOf { it.playCount }

            val topArtist = songs.groupBy { it.artist }
                .maxByOrNull { entry -> entry.value.sumOf { it.playCount } }
                ?.key ?: "Studio Master"

            val topGenre = songs.groupBy { it.genre }
                .maxByOrNull { entry -> entry.value.sumOf { it.playCount } }
                ?.key ?: "Hi-Fi Studio"

            val weeklyStats = preferences.getWeeklyListeningStats()
            val labels = mutableListOf<String>()
            val minutes = mutableListOf<Int>()
            val dayNameFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.US)

            weeklyStats.forEach { (dateStr, sec) ->
                try {
                    val d = format.parse(dateStr)
                    labels.add(if (d != null) dayNameFormat.format(d) else "Day")
                } catch (e: Exception) {
                    labels.add("Day")
                }
                minutes.add((sec / 60).coerceAtLeast(0))
            }

            val totalWeeklyMins = minutes.sum().coerceAtLeast(todaySec / 60)

            _dailyAnalytics.value = com.example.model.DailyAnalytics(
                dateKey = todayKey,
                displayDate = "Today",
                listenedSeconds = todaySec,
                tracksPlayed = playedCount,
                topArtist = topArtist,
                topGenre = topGenre,
                weeklyDayLabels = labels,
                weeklyDayMinutes = minutes,
                totalWeeklyMinutes = totalWeeklyMins,
                peakHourLabel = "Evening Hi-Fi Session"
            )
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackInfo.value = _playbackInfo.value.copy(sleepTimerMinutesRemaining = null)
            return
        }

        _playbackInfo.value = _playbackInfo.value.copy(sleepTimerMinutesRemaining = minutes)
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60000L)
                remaining--
                _playbackInfo.value = _playbackInfo.value.copy(sleepTimerMinutesRemaining = if (remaining > 0) remaining else null)
            }
            // Timer expired -> pause playback cleanly
            pausePlayback()
            _playbackInfo.value = _playbackInfo.value.copy(sleepTimerMinutesRemaining = null)
        }
    }

    fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        lyrics: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getMetadata(songId)
            dao.upsertMetadata(
                SongMetadataEntity(
                    id = songId,
                    isFavorite = existing?.isFavorite ?: false,
                    playCount = existing?.playCount ?: 0,
                    lastPlayedTimestamp = existing?.lastPlayedTimestamp ?: 0L,
                    customTitle = newTitle,
                    customArtist = newArtist,
                    customAlbum = newAlbum,
                    customGenre = newGenre,
                    customLyrics = lyrics
                )
            )
            // Update in-memory songs and active queue
            _allSongs.value = _allSongs.value.map {
                if (it.id == songId) {
                    it.copy(
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        genre = newGenre,
                        lyrics = lyrics
                    )
                } else it
            }
            _queue.value = _queue.value.map {
                if (it.id == songId) {
                    it.copy(
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        genre = newGenre,
                        lyrics = lyrics
                    )
                } else it
            }
            if (_currentSong.value?.id == songId) {
                _currentSong.value = _currentSong.value?.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = newGenre,
                    lyrics = lyrics
                )
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.renamePlaylist(playlistId, newName)
        }
    }

    fun updatePlaylistDetails(playlistId: Long, newName: String, coverArtUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updatePlaylistDetails(playlistId, newName, coverArtUri)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidate = (_queue.value + listOfNotNull(_currentSong.value) + allOnlineTracks.value)
                .firstOrNull { it.id == songId && it.isOnline }
            if (candidate != null && dao.getOnlineTrackById(songId) == null) {
                val track = OnlineTrackMapper.toOnlineTrack(candidate)
                dao.upsertOnlineTrack(OnlineTrackMapper.mapOnlineTrackToEntity(track))
            }
            dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, 0))
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val knownOnline = (_queue.value + listOfNotNull(_currentSong.value) + allOnlineTracks.value)
                .filter { it.isOnline }
                .associateBy { it.id }
            val distinctIds = songIds.distinct()
            distinctIds.forEach { id ->
                val online = knownOnline[id]
                if (online != null && dao.getOnlineTrackById(id) == null) {
                    val track = OnlineTrackMapper.toOnlineTrack(online)
                    dao.upsertOnlineTrack(OnlineTrackMapper.mapOnlineTrackToEntity(track))
                }
            }
            val baseTime = (System.currentTimeMillis() % 100000).toInt()
            val refs = distinctIds.mapIndexed { index, id ->
                PlaylistSongCrossRef(playlistId, id, baseTime + index)
            }
            dao.insertPlaylistSongs(refs)
        }
    }

    fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.replacePlaylistOrder(playlistId, orderedSongIds)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // --- Online Music Discovery, Search & Stream Quality Controls (NewPipe Extractor) ---

    private fun initOnlineProvidersAsync(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            NewPipeInitializer.ensureInitialized(application)
            val providers = onlineRepository.getAvailableProviders()
            val savedKey = onlineRegionPrefs.getInitialSelectedProviderKey()
            val selected = providers.firstOrNull { it.key == savedKey }
                ?: providers.firstOrNull { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }
                ?: providers.firstOrNull()
            withContext(Dispatchers.Main.immediate) {
                _onlineProviders.value = providers
                if (_selectedOnlineProvider.value == null && selected != null) {
                    _selectedOnlineProvider.value = selected
                    _selectedOnlineFilter.value = selected.defaultFilter
                }
            }
            // Wait until the startup intro hands off to Home before kicking off online discovery network traffic
            startupDeferredWorkGate.await()
            withContext(Dispatchers.Main.immediate) {
                if (_selectedOnlineProvider.value != null && _discoverRefreshCount.value == 0L) {
                    refreshOnlineDiscovery(forceRefresh = false)
                }
            }
        }
    }

    fun selectOnlineProvider(providerKey: String) {
        val target = _onlineProviders.value.firstOrNull { it.key == providerKey } ?: return
        if (_selectedOnlineProvider.value?.key == target.key) return
        clearPlaybackError()
        _selectedOnlineProvider.value = target
        preferences.selectedOnlineProviderKey = target.key
        viewModelScope.launch(Dispatchers.IO) {
            onlineRegionPrefs.setSelectedProviderKey(target.key)
        }
        if (_selectedOnlineFilter.value !in target.supportedFilters) {
            _selectedOnlineFilter.value = target.defaultFilter
        }
        _onlineSearchSuggestions.value = emptyList()
        onlineRepository.clearDiscoverCache()
        refreshOnlineDiscovery(forceRefresh = true)
        val q = _onlineSearchQuery.value.trim()
        if (q.isNotBlank()) {
            executeOnlineSearch(q)
        } else {
            _onlineSearchState.value = OnlineSearchUiState.Idle
        }
    }

    fun selectOnlineFilter(filter: OnlineSearchFilter) {
        val provider = _selectedOnlineProvider.value ?: return
        val resolvedFilter = if (filter in provider.supportedFilters) filter else provider.defaultFilter
        if (_selectedOnlineFilter.value == resolvedFilter) return
        _selectedOnlineFilter.value = resolvedFilter
        val q = _onlineSearchQuery.value.trim()
        if (q.isNotBlank()) {
            executeOnlineSearch(q)
        }
    }

    fun updateOnlineSearchQuery(query: String) {
        _onlineSearchQuery.value = query
        onlineSuggestionJob?.cancel()
        val provider = _selectedOnlineProvider.value
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _onlineSearchSuggestions.value = emptyList()
            _onlineSearchState.value = OnlineSearchUiState.Idle
            return
        }
        if (provider == null || !provider.supportsSuggestions) {
            _onlineSearchSuggestions.value = emptyList()
            return
        }
        onlineSuggestionJob = viewModelScope.launch {
            delay(300L)
            val suggestions = onlineRepository.getSearchSuggestions(
                query = trimmed,
                provider = provider,
                countryCode = _onlineContentCountry.value
            )
            _onlineSearchSuggestions.value = suggestions
        }
    }

    fun executeOnlineSearch(
        query: String = _onlineSearchQuery.value,
        forceRefresh: Boolean = false
    ) {
        val trimmed = query.trim()
        _onlineSearchQuery.value = query
        onlineSuggestionJob?.cancel()
        _onlineSearchSuggestions.value = emptyList()
        if (trimmed.isBlank()) {
            activeSearchSignature = null
            onlineSearchJob?.cancel()
            _onlineSearchState.value = OnlineSearchUiState.Idle
            return
        }
        val provider = _selectedOnlineProvider.value ?: return
        val filter = _selectedOnlineFilter.value
        val signature = "${provider.key}|${filter.name}|$trimmed"
        if (!forceRefresh && onlineSearchJob?.isActive == true && activeSearchSignature == signature) {
            return
        }
        activeSearchSignature = signature
        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _onlineSearchState.value = OnlineSearchUiState.Loading(trimmed)
            onlineRepository.saveSearchQuery(trimmed, provider.key)
            when (val res = onlineRepository.search(trimmed, provider, filter)) {
                is OnlineResult.Success -> {
                    _onlineSearchState.value = OnlineSearchUiState.Success(
                        page = res.value,
                        items = res.value.items,
                        isLoadingMore = false
                    )
                }
                is OnlineResult.Error -> {
                    _onlineSearchState.value = OnlineSearchUiState.Error(
                        kind = res.kind,
                        message = res.message,
                        isRetryable = res.isRetryable
                    )
                }
            }
        }
    }

    fun loadMoreOnlineSearchResults() {
        val currentState = _onlineSearchState.value as? OnlineSearchUiState.Success ?: return
        val token = currentState.page.nextPageToken
        if (currentState.isLoadingMore || !currentState.page.hasNextPage || token.isNullOrBlank()) return

        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _onlineSearchState.value = currentState.copy(isLoadingMore = true)
            when (val res = onlineRepository.loadMoreSearchResults(token)) {
                is OnlineResult.Success -> {
                    val combined = (currentState.items + res.value.items).distinctBy { it.contentUrl }
                    _onlineSearchState.value = OnlineSearchUiState.Success(
                        page = res.value,
                        items = combined,
                        isLoadingMore = false
                    )
                }
                is OnlineResult.Error -> {
                    _onlineSearchState.value = currentState.copy(isLoadingMore = false)
                    _dspDiagnosticMessage.value = res.message
                }
            }
        }
    }

    fun removeOnlineSearchHistory(query: String) {
        viewModelScope.launch {
            onlineRepository.removeSearchQuery(query)
        }
    }

    fun clearOnlineSearchHistory() {
        viewModelScope.launch {
            onlineRepository.clearSearchHistory()
        }
    }

    fun refreshOnlineDiscovery(forceRefresh: Boolean = true) {
        val provider = _selectedOnlineProvider.value ?: return
        if (!forceRefresh &&
            activeDiscoverProviderKey == provider.key &&
            discoverSectionJobs.values.any { it.isActive }
        ) {
            return
        }
        activeDiscoverProviderKey = provider.key
        discoverGeneration += 1L
        _discoverRefreshCount.value = discoverGeneration
        val generation = discoverGeneration

        discoverSectionJobs.values.forEach { it.cancel() }
        discoverSectionJobs.clear()
        discoverLoadMoreJobs.values.forEach { it.cancel() }
        discoverLoadMoreJobs.clear()
        _discoverSectionsState.value = defaultDiscoverSectionsState()

        val currentProfile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = recentlyPlayedSongs.value,
            recentlyPlayedOnline = recentlyPlayedOnlineTracks.value,
            favouriteArtists = favouriteArtists.value,
            allSongs = _allSongs.value,
            onlineTracks = allOnlineTracks.value,
            onlineSearchHistory = onlineSearchHistory.value,
            dailyMixes = _dailyMixes.value,
            countryCode = _onlineContentCountry.value,
            languageCode = _onlineContentLanguage.value,
            discoverLanguage = _discoverMusicLanguage.value
        )
        _onlineRecommendationProfile.value = currentProfile
        lastRecommendationSeedQueried = currentProfile.seedQuery

        val recentArtistSeed = currentProfile.primaryArtistSeed
            ?: recentlyPlayedOnlineTracks.value.firstOrNull()?.artist
        for (section in DiscoverSectionType.entries) {
            launchDiscoverSection(
                section = section,
                provider = provider,
                generation = generation,
                recentArtistSeed = recentArtistSeed,
                recommendationProfile = if (section == DiscoverSectionType.MUSIC_RECOMMENDATIONS) currentProfile else null,
                forceRefresh = forceRefresh
            )
        }
        loadSonoraRecommendations(forceRefresh = forceRefresh)
    }

    fun retryDiscoverSection(section: DiscoverSectionType) {
        val provider = _selectedOnlineProvider.value ?: return
        // Prevent duplicate concurrent requests for the same section while already loading
        if (discoverSectionJobs[section]?.isActive == true &&
            _discoverSectionsState.value[section] is DiscoverSectionState.Loading
        ) {
            return
        }
        discoverSectionJobs[section]?.cancel()
        discoverLoadMoreJobs[section]?.cancel()
        val generation = discoverGeneration
        _discoverSectionsState.update { current ->
            current.toMutableMap().apply { put(section, DiscoverSectionState.Loading) }
        }
        val currentProfile = _onlineRecommendationProfile.value
        val recentArtistSeed = currentProfile.primaryArtistSeed
            ?: recentlyPlayedOnlineTracks.value.firstOrNull()?.artist
        launchDiscoverSection(
            section = section,
            provider = provider,
            generation = generation,
            recentArtistSeed = recentArtistSeed,
            recommendationProfile = if (section == DiscoverSectionType.MUSIC_RECOMMENDATIONS) currentProfile else null,
            forceRefresh = true
        )
    }

    fun loadMoreDiscoverSection(section: DiscoverSectionType) {
        val provider = _selectedOnlineProvider.value ?: return
        val currentState = _discoverSectionsState.value[section] as? DiscoverSectionState.Success ?: return
        val token = currentState.nextPageToken
        if (currentState.isLoadingMore || !currentState.hasMore || token.isNullOrBlank()) return
        if (discoverLoadMoreJobs[section]?.isActive == true) return

        val generation = discoverGeneration
        _discoverSectionsState.update { current ->
            val latest = current[section] as? DiscoverSectionState.Success ?: return@update current
            current.toMutableMap().apply { put(section, latest.copy(isLoadingMore = true)) }
        }

        val activeCountry = _onlineContentCountry.value
        discoverLoadMoreJobs[section] = viewModelScope.launch {
            when (val res = onlineRepository.loadMoreDiscoverSection(section, provider, token, activeCountry)) {
                is OnlineResult.Success -> {
                    if (generation != discoverGeneration || _selectedOnlineProvider.value?.key != provider.key) {
                        return@launch
                    }
                    val (moreItems, nextToken) = res.value
                    _discoverSectionsState.update { current ->
                        val latest = current[section] as? DiscoverSectionState.Success ?: return@update current
                        val combined = (latest.items + moreItems).distinctBy { it.contentUrl }
                        current.toMutableMap().apply {
                            put(
                                section,
                                latest.copy(
                                    items = combined,
                                    nextPageToken = nextToken,
                                    hasMore = !nextToken.isNullOrBlank(),
                                    isLoadingMore = false
                                )
                            )
                        }
                    }
                }
                is OnlineResult.Error -> {
                    if (generation != discoverGeneration) return@launch
                    _discoverSectionsState.update { current ->
                        val latest = current[section] as? DiscoverSectionState.Success ?: return@update current
                        current.toMutableMap().apply { put(section, latest.copy(isLoadingMore = false)) }
                    }
                    _dspDiagnosticMessage.value = res.message
                }
            }
        }
    }

    fun setOnlineContentCountry(countryCode: String) {
        viewModelScope.launch {
            val clean = onlineRegionPrefs.setContentCountry(countryCode)
            if (_onlineContentCountry.value != clean) {
                _onlineContentCountry.value = clean
                NewPipeInitializer.updateLocalization(_onlineContentLanguage.value, clean)
                onlineRepository.clearDiscoverCache()
                // Clear cached recommendation lists so switching regions never leaves stale foreign tracks
                moodDiscoveryJob?.cancel()
                indiaDiscoveryJob?.cancel()
                currentSongRecsJob?.cancel()
                radioTopUpJob?.cancel()
                _moodDiscoveryTracks.value = emptyList()
                _currentSongRecommendations.value = emptyList()
                _onlineSearchSuggestions.value = emptyList()
                if (_songRadioSession.value.isActive) {
                    _songRadioSession.value = data.online.SongRadioSession(isActive = false)
                }
                refreshOnlineDiscovery(forceRefresh = true)
                if (musicExperienceSettings.value.musicRecommendationsEnabled) {
                    loadMoodDiscovery(_selectedMoodActivity.value)
                    if (domain.online.RegionMusicEligibilityFilter.isIndiaRegion(clean)) {
                        loadIndiaMusicCategory(_selectedIndiaCategory.value)
                    }
                }
                if (_currentSong.value != null) {
                    loadRecommendationsForCurrentSong(forceRefresh = true)
                }
                videoController.onRegionOrLanguageChanged()
                val q = _onlineSearchQuery.value.trim()
                if (q.isNotBlank()) executeOnlineSearch(q, forceRefresh = true)
            }
        }
    }

    fun setOnlineContentLanguage(languageCode: String) {
        viewModelScope.launch {
            val clean = onlineRegionPrefs.setContentLanguage(languageCode)
            if (_onlineContentLanguage.value != clean) {
                _onlineContentLanguage.value = clean
                NewPipeInitializer.updateLocalization(clean, _onlineContentCountry.value)
                onlineRepository.clearDiscoverCache()
                refreshOnlineDiscovery(forceRefresh = true)
                videoController.onRegionOrLanguageChanged()
                val q = _onlineSearchQuery.value.trim()
                if (q.isNotBlank()) executeOnlineSearch(q, forceRefresh = true)
            }
        }
    }

    fun setDiscoverMusicLanguage(language: DiscoverMusicLanguage) {
        if (_discoverMusicLanguage.value == language) return
        _discoverMusicLanguage.value = language
        viewModelScope.launch {
            onlineRegionPrefs.setDiscoverLanguage(language)
        }
        _moodDiscoveryTracks.value = emptyList()
        refreshOnlineDiscovery(forceRefresh = true)
        if (musicExperienceSettings.value.musicRecommendationsEnabled) {
            loadMoodDiscovery(_selectedMoodActivity.value)
        }
    }

    private fun launchDiscoverSection(
        section: DiscoverSectionType,
        provider: OnlineMusicProvider,
        generation: Long,
        recentArtistSeed: String?,
        recommendationProfile: OnlineRecommendationProfile? = null,
        forceRefresh: Boolean
    ) {
        val countryCode = _onlineContentCountry.value
        val languageCode = _onlineContentLanguage.value
        val discoverLanguage = _discoverMusicLanguage.value
        val job = viewModelScope.launch {
            val resultState = onlineRepository.fetchDiscoverSection(
                sectionType = section,
                provider = provider,
                countryCode = countryCode,
                languageCode = languageCode,
                discoverLanguage = discoverLanguage,
                recentArtistSeed = recentArtistSeed,
                recommendationProfile = recommendationProfile,
                forceRefresh = forceRefresh
            )
            if (generation == discoverGeneration && _selectedOnlineProvider.value?.key == provider.key) {
                _discoverSectionsState.update { current ->
                    current.toMutableMap().apply { put(section, resultState) }
                }
                if (resultState is DiscoverSectionState.Success &&
                    _sonoraRecommendState.value.items.isEmpty() &&
                    sonoraRecommendJob?.isActive != true
                ) {
                    loadSonoraRecommendations(forceRefresh = false)
                }
            }
        }
        discoverSectionJobs[section] = job
    }

    // --- Sonora Recommend Public Controls, Availability Check & Artist/Album Navigation ---

    fun refreshSonoraRecommendations() {
        sonoraRecommendGeneration += 1
        loadSonoraRecommendations(forceRefresh = true)
    }

    fun loadSonoraRecommendations(forceRefresh: Boolean = false) {
        val provider = _selectedOnlineProvider.value
            ?: _onlineProviders.value.firstOrNull()
            ?: return
        sonoraRecommendJob?.cancel()
        val generation = sonoraRecommendGeneration
        val currentItems = _sonoraRecommendState.value.items
        _sonoraRecommendState.update {
            it.copy(
                isLoading = currentItems.isEmpty(),
                isRefreshing = currentItems.isNotEmpty() && forceRefresh,
                errorMessage = null
            )
        }
        sonoraRecommendJob = viewModelScope.launch {
            val events = runCatching { listeningAnalyticsRepository.getAllEventsOnce() }.getOrDefault(emptyList())
            val allLocal = _allSongs.value
            val allOnline = allOnlineTracks.value
            val combinedSongsById = buildMap<Long, Song> {
                allLocal.forEach { put(it.id, it) }
                allOnline.forEach { put(it.id, it) }
            }
            val playlistCrossRefs = runCatching {
                withContext(Dispatchers.IO) { dao.getAllPlaylistSongCrossRefsFlow().first() }
            }.getOrDefault(emptyList())
            val localPlaylistSongs = playlistCrossRefs
                .groupBy { it.playlistId }
                .mapValues { (_, refs) ->
                    refs.sortedBy { it.orderIndex }.mapNotNull { combinedSongsById[it.songId] }
                }
            val snapshot = data.online.LocalPersonalizationSnapshot(
                likedLocalSongs = allLocal.filter { it.isFavorite },
                likedOnlineSongs = allOnline.filter { it.isFavorite },
                followedOnlineArtists = followedOnlineArtists.value,
                favouriteLocalArtists = favouriteArtists.value,
                localPlaylists = _playlists.value,
                localPlaylistSongs = localPlaylistSongs,
                onlinePlaylists = onlinePlaylists.value,
                listeningEvents = events,
                recentlyPlayedOnline = recentlyPlayedOnlineTracks.value,
                recentlyPlayedLocal = recentlyPlayedSongs.value,
                countryCode = _onlineContentCountry.value,
                languageCode = _onlineContentLanguage.value,
                discoverLanguage = _discoverMusicLanguage.value,
                strictIndiaOnly = domain.online.RegionMusicEligibilityFilter.isIndiaRegion(_onlineContentCountry.value)
            )
            val resultState = sonoraRecommendationCoordinator.loadRecommendations(
                snapshot = snapshot,
                provider = provider,
                existingDiscoverSections = _discoverSectionsState.value,
                isNetworkOnline = connectivityMonitor.isOnline.value,
                forceRefresh = forceRefresh,
                refreshGeneration = generation
            )
            if (generation == sonoraRecommendGeneration) {
                _sonoraRecommendState.value = resultState
            }
        }
    }

    private fun scheduleSonoraRecommendationsRefreshDebounced() {
        sonoraRecommendDebounceJob?.cancel()
        sonoraRecommendDebounceJob = viewModelScope.launch {
            delay(1_200L)
            if (_homeMusicMode.value == HomeMusicMode.ONLINE || _sonoraRecommendState.value.items.isNotEmpty()) {
                loadSonoraRecommendations(forceRefresh = true)
            }
        }
    }

    fun dislikeRecommendedTrack(track: OnlineTrack) {
        val normUrl = domain.online.SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(track.contentUrl)
        _sonoraRecommendState.update { current ->
            current.copy(
                items = current.items.filterNot { item ->
                    item.track.stableId == track.stableId ||
                        domain.online.SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(item.track.contentUrl) == normUrl
                }
            )
        }
        _libraryFeedbackMessage.value = "We'll recommend fewer tracks like \"${track.title}\"."
        viewModelScope.launch(Dispatchers.IO) {
            sonoraRecommendCachePrefs.recordDislike(track.stableId, track.contentUrl, track.artist)
            if (_sonoraRecommendState.value.items.size < 6) {
                loadSonoraRecommendations(forceRefresh = true)
            }
        }
    }

    /**
     * Verifies candidate playability and stream availability (with expired-URL re-resolution)
     * before handing off to SONORA's unified Media3 playback pipeline. Gracefully removes
     * deleted or unavailable tracks without crashing.
     */
    fun playRecommendedTrack(track: OnlineTrack, pool: List<OnlineTrack>, onPlaybackStarted: () -> Unit = {}) {
        if (!domain.online.SonoraPersonalizedRecommendationEngine.isCandidateMetadataPlayable(track)) {
            markRecommendedTrackUnavailable(track, "This track is no longer available.")
            return
        }
        if (!connectivityMonitor.isOnline.value) {
            _libraryFeedbackMessage.value = "You're offline. Connect to stream \"${track.title}\"."
            return
        }
        viewModelScope.launch {
            val song = OnlineTrackMapper.toUnifiedSong(track)
            var resolved = onlineRepository.resolvePlayableStream(
                song = song,
                qualityPreference = _onlineQualityPreference.value,
                forceRefresh = false
            )
            if (resolved is OnlineResult.Error && resolved.kind == OnlineErrorKind.STREAM_EXPIRED) {
                onlineRepository.invalidateStreamCache(song.contentUrl)
                resolved = onlineRepository.resolvePlayableStream(
                    song = song,
                    qualityPreference = _onlineQualityPreference.value,
                    forceRefresh = true
                )
            }
            when (resolved) {
                is OnlineResult.Success -> {
                    playOnlineTrack(track, pool)
                    onPlaybackStarted()
                }
                is OnlineResult.Error -> {
                    val permanentlyUnavailable = resolved.kind == OnlineErrorKind.NO_PLAYABLE_AUDIO_STREAM ||
                        resolved.kind == OnlineErrorKind.UNSUPPORTED ||
                        resolved.kind == OnlineErrorKind.AGE_OR_GEO_RESTRICTED
                    if (permanentlyUnavailable) {
                        markRecommendedTrackUnavailable(track, resolved.message)
                    } else {
                        // Transient error (timeout / rate limit): still allow playback pipeline retry UI
                        playOnlineTrack(track, pool)
                        onPlaybackStarted()
                    }
                }
            }
        }
    }

    private fun markRecommendedTrackUnavailable(track: OnlineTrack, reason: String) {
        sonoraRecommendationCoordinator.markTrackUnavailable(track.contentUrl)
        val normUrl = domain.online.SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(track.contentUrl)
        _sonoraRecommendState.update { current ->
            current.copy(
                items = current.items.filterNot { item ->
                    item.track.stableId == track.stableId ||
                        domain.online.SonoraPersonalizedRecommendationEngine.normalizeTrackUrl(item.track.contentUrl) == normUrl
                }
            )
        }
        _libraryFeedbackMessage.value = "\"${track.title}\" is unavailable and was removed from Sonora Recommend ($reason)"
    }

    fun openRecommendedArtist(track: OnlineTrack) {
        val directArtist = ui.online.buildArtistExploreTrack(track)
        if (directArtist != null) {
            openOnlineCollection(directArtist)
            return
        }
        val artistName = track.artist.trim()
        if (artistName.isBlank()) return
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        onlineCollectionJob?.cancel()
        onlineCollectionJob = viewModelScope.launch {
            _onlineCollectionState.value = OnlineCollectionUiState.Loading(artistName)
            when (val res = onlineRepository.search(artistName, provider, OnlineSearchFilter.SONGS)) {
                is OnlineResult.Success -> {
                    val tracks = res.value.items.filter { it.kind == OnlineItemKind.TRACK }
                    _onlineCollectionState.value = OnlineCollectionUiState.Success(
                        details = OnlineCollectionDetails(
                            title = artistName,
                            subtitle = "Artist • ${provider.displayName}",
                            providerName = provider.displayName,
                            contentUrl = track.artistUrl ?: track.contentUrl,
                            artworkUrl = track.artworkUrl,
                            tracks = tracks
                        ),
                        sourceItem = track.copy(title = artistName, kind = OnlineItemKind.ARTIST)
                    )
                }
                is OnlineResult.Error -> {
                    _onlineCollectionState.value = OnlineCollectionUiState.Error(res.message, track)
                }
            }
        }
    }

    fun openRecommendedAlbum(track: OnlineTrack) {
        val albumName = track.album.trim()
        if (!domain.online.SonoraPersonalizedRecommendationEngine.isValidAlbumName(albumName)) return
        val provider = _selectedOnlineProvider.value ?: _onlineProviders.value.firstOrNull() ?: return
        onlineCollectionJob?.cancel()
        onlineCollectionJob = viewModelScope.launch {
            _onlineCollectionState.value = OnlineCollectionUiState.Loading(albumName)
            val query = "$albumName ${track.artist}".trim()
            val albumSearch = onlineRepository.search(query, provider, OnlineSearchFilter.ALBUMS)
            val matchingAlbum = (albumSearch as? OnlineResult.Success)?.value?.items
                ?.firstOrNull { it.kind == OnlineItemKind.ALBUM || it.kind == OnlineItemKind.PLAYLIST }
            if (matchingAlbum != null) {
                when (val details = onlineRepository.fetchCollectionDetails(matchingAlbum)) {
                    is OnlineResult.Success -> {
                        _onlineCollectionState.value = OnlineCollectionUiState.Success(
                            details = details.value,
                            sourceItem = matchingAlbum
                        )
                        return@launch
                    }
                    is OnlineResult.Error -> Unit
                }
            }
            when (val songSearch = onlineRepository.search(query, provider, OnlineSearchFilter.SONGS)) {
                is OnlineResult.Success -> {
                    val tracks = songSearch.value.items.filter { it.kind == OnlineItemKind.TRACK }
                    _onlineCollectionState.value = OnlineCollectionUiState.Success(
                        details = OnlineCollectionDetails(
                            title = albumName,
                            subtitle = "${track.artist} • ${provider.displayName}",
                            providerName = provider.displayName,
                            contentUrl = track.contentUrl,
                            artworkUrl = track.artworkUrl,
                            tracks = tracks
                        ),
                        sourceItem = track.copy(title = albumName, kind = OnlineItemKind.ALBUM)
                    )
                }
                is OnlineResult.Error -> {
                    _onlineCollectionState.value = OnlineCollectionUiState.Error(songSearch.message, track)
                }
            }
        }
    }

    fun openOnlineCollection(item: OnlineTrack) {
        onlineCollectionJob?.cancel()
        onlineCollectionJob = viewModelScope.launch {
            _onlineCollectionState.value = OnlineCollectionUiState.Loading(item.title)
            when (val res = onlineRepository.fetchCollectionDetails(item)) {
                is OnlineResult.Success -> {
                    _onlineCollectionState.value = OnlineCollectionUiState.Success(
                        details = res.value,
                        sourceItem = item
                    )
                }
                is OnlineResult.Error -> {
                    _onlineCollectionState.value = OnlineCollectionUiState.Error(res.message, item)
                }
            }
        }
    }

    fun dismissOnlineCollection() {
        onlineCollectionJob?.cancel()
        _onlineCollectionState.value = OnlineCollectionUiState.Idle
    }

    fun playOnlineTrack(track: OnlineTrack, collection: List<OnlineTrack> = emptyList()) {
        val savedById = allOnlineTracks.value.associateBy { it.id }
        val playablePool = collection.filter { it.kind == domain.online.OnlineItemKind.TRACK }
            .ifEmpty { listOf(track) }
        val songQueue = playablePool.map { item ->
            val baseSong = OnlineTrackMapper.toUnifiedSong(item)
            savedById[baseSong.id]?.let { saved ->
                baseSong.copy(
                    isFavorite = saved.isFavorite || baseSong.isFavorite,
                    playCount = maxOf(saved.playCount, baseSong.playCount)
                )
            } ?: baseSong
        }
        val targetSong = songQueue.firstOrNull { it.contentUrl == track.contentUrl }
            ?: OnlineTrackMapper.toUnifiedSong(track)
        val idx = songQueue.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
        playSong(targetSong, songQueue, idx)
    }

    fun enqueueOnlineTrackNext(track: OnlineTrack) {
        playNextInQueue(OnlineTrackMapper.toUnifiedSong(track))
    }

    fun addOnlineTrackToQueue(track: OnlineTrack) {
        addToQueue(OnlineTrackMapper.toUnifiedSong(track))
    }

    fun toggleSaveOnlineTrack(song: Song) {
        viewModelScope.launch {
            onlineLibraryRepository.toggleSaveTrack(song)
                .onSuccess { saved ->
                    _libraryFeedbackMessage.value = if (saved) {
                        "Saved \"${song.title}\" to Online Library."
                    } else {
                        "Removed \"${song.title}\" from Online Library."
                    }
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "Unable to save this track."
                }
        }
    }

    fun toggleSaveOnlineTrack(track: OnlineTrack) {
        toggleSaveOnlineTrack(OnlineTrackMapper.toUnifiedSong(track))
    }

    fun removeSavedOnlineTrack(stableId: Long, title: String = "") {
        viewModelScope.launch {
            onlineLibraryRepository.removeSavedTrack(stableId)
            if (title.isNotBlank()) {
                _libraryFeedbackMessage.value = "Removed \"$title\" from Saved Online Songs."
            }
        }
    }

    fun setLibrarySectionMode(mode: domain.library.LibrarySectionMode) {
        userUpdatedLibraryMode = true
        _librarySectionMode.value = mode
        libraryModePrefs.setCachedLibrarySectionMode(mode)
        viewModelScope.launch(Dispatchers.IO) {
            libraryModePrefs.setLibrarySectionMode(mode)
        }
    }

    fun setOnlineLibrarySearchQuery(query: String) {
        _onlineLibrarySearchQuery.value = query
    }

    fun setOnlineLibrarySortOption(option: domain.library.OnlineLibrarySortOption) {
        _onlineLibrarySortOption.value = option
    }

    fun setOnlineLibraryProviderFilter(providerKey: String?) {
        _onlineLibraryProviderFilter.value = providerKey
    }

    fun clearLibraryFeedback() {
        _libraryFeedbackMessage.value = null
    }

    fun toggleSaveOnlineAlbum(item: OnlineTrack, details: OnlineCollectionDetails? = null) {
        viewModelScope.launch {
            onlineLibraryRepository.toggleSaveAlbum(item, details)
                .onSuccess { saved ->
                    val name = details?.title?.takeIf { it.isNotBlank() } ?: item.title
                    _libraryFeedbackMessage.value = if (saved) {
                        "Saved album \"$name\" to Online Library."
                    } else {
                        "Removed album \"$name\" from Online Library."
                    }
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "This provider does not allow saving this album."
                }
        }
    }

    fun removeSavedOnlineAlbum(stableId: Long, title: String = "") {
        viewModelScope.launch {
            onlineLibraryRepository.removeSavedAlbum(stableId)
            if (title.isNotBlank()) {
                _libraryFeedbackMessage.value = "Removed album \"$title\" from Online Library."
            }
        }
    }

    fun toggleFollowOnlineArtist(item: OnlineTrack, details: OnlineCollectionDetails? = null) {
        viewModelScope.launch {
            onlineLibraryRepository.toggleFollowArtist(item, details)
                .onSuccess { followed ->
                    val name = if (item.kind == OnlineItemKind.ARTIST) {
                        details?.title?.takeIf { it.isNotBlank() } ?: item.title
                    } else {
                        item.artist.ifBlank { item.title }
                    }
                    _libraryFeedbackMessage.value = if (followed) {
                        "Following $name in Online Library."
                    } else {
                        "Unfollowed $name."
                    }
                    scheduleSonoraRecommendationsRefreshDebounced()
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "This provider does not expose an artist channel to follow."
                }
        }
    }

    fun unfollowOnlineArtist(stableId: Long, name: String = "") {
        viewModelScope.launch {
            onlineLibraryRepository.unfollowArtist(stableId)
            if (name.isNotBlank()) {
                _libraryFeedbackMessage.value = "Unfollowed $name."
            }
        }
    }

    fun createOnlinePlaylist(name: String, initialTrack: Song? = null) {
        viewModelScope.launch {
            onlineLibraryRepository.createOnlinePlaylist(name)
                .onSuccess { newId ->
                    if (initialTrack != null) {
                        onlineLibraryRepository.addTrackToOnlinePlaylist(newId, initialTrack)
                    }
                    _libraryFeedbackMessage.value = "Created online playlist \"${name.trim()}\"."
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "Could not create online playlist."
                }
        }
    }

    fun renameOnlinePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            onlineLibraryRepository.renameOnlinePlaylist(playlistId, newName)
        }
    }

    fun deleteOnlinePlaylist(playlistId: Long, name: String = "") {
        viewModelScope.launch {
            onlineLibraryRepository.deleteOnlinePlaylist(playlistId)
            if (name.isNotBlank()) {
                _libraryFeedbackMessage.value = "Deleted online playlist \"$name\"."
            }
        }
    }

    fun addTrackToOnlinePlaylist(playlistId: Long, song: Song, playlistName: String = "") {
        viewModelScope.launch {
            onlineLibraryRepository.addTrackToOnlinePlaylist(playlistId, song)
                .onSuccess {
                    val suffix = if (playlistName.isNotBlank()) " to \"$playlistName\"" else ""
                    _libraryFeedbackMessage.value = "Added \"${song.title}\"$suffix."
                    scheduleSonoraRecommendationsRefreshDebounced()
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "Could not add track to online playlist."
                }
        }
    }

    fun removeTrackFromOnlinePlaylist(playlistId: Long, trackStableId: Long) {
        viewModelScope.launch {
            onlineLibraryRepository.removeTrackFromOnlinePlaylist(playlistId, trackStableId)
        }
    }

    fun toggleSaveExternalOnlinePlaylist(item: OnlineTrack, details: OnlineCollectionDetails? = null) {
        viewModelScope.launch {
            onlineLibraryRepository.toggleSaveExternalPlaylist(item, details)
                .onSuccess { saved ->
                    val name = details?.title?.takeIf { it.isNotBlank() } ?: item.title
                    _libraryFeedbackMessage.value = if (saved) {
                        "Saved playlist \"$name\" to Online Library."
                    } else {
                        "Removed playlist \"$name\" from Online Library."
                    }
                }
                .onFailure { err ->
                    _libraryFeedbackMessage.value = err.message ?: "Unable to save this playlist."
                }
        }
    }

    fun clearOnlineListeningHistory() {
        viewModelScope.launch {
            onlineLibraryRepository.clearListeningHistory()
            _libraryFeedbackMessage.value = "Cleared online listening history (saved items preserved)."
        }
    }

    fun isOnlineAlbumSaved(item: OnlineTrack): Boolean {
        val canonicalUrl = item.contentUrl.trim()
        if (canonicalUrl.isBlank()) return false
        val id = OnlineTrackMapper.computeStableId(item.providerKey.ifBlank { "online" }, canonicalUrl)
        return id in savedOnlineAlbumIds.value
    }

    fun isOnlineArtistFollowed(item: OnlineTrack): Boolean {
        val url = if (item.kind == OnlineItemKind.ARTIST) item.contentUrl else item.artistUrl.orEmpty()
        if (url.isBlank()) return false
        val id = OnlineTrackMapper.computeStableId(item.providerKey.ifBlank { "online" }, url.trim())
        return id in followedOnlineArtistIds.value
    }

    fun isExternalOnlinePlaylistSaved(contentUrl: String): Boolean {
        return contentUrl.trim() in savedOnlinePlaylistUrls.value
    }

    fun deleteLocalSongs(
        songs: List<Song>,
        onRequiresSystemConfirmation: (android.content.IntentSender, List<Long>) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val downloaded = songs.filter { it.sourceType == "DOWNLOADED" }
            val localDevice = songs.filter { !it.isOnline && it.sourceType != "DOWNLOADED" }

            if (downloaded.isNotEmpty()) {
                downloadRepository.deleteDownloadedTracks(downloaded.map { it.id })
            }

            if (localDevice.isNotEmpty()) {
                when (val result = offlineLibraryRepository.deleteLocalAudioTracks(localDevice)) {
                    is domain.library.LocalSongDeleteResult.Deleted -> {
                        val total = result.deletedCount + downloaded.size
                        _libraryFeedbackMessage.value = if (total == 1) {
                            "Removed 1 track from device."
                        } else {
                            "Removed $total tracks from device."
                        }
                        loadLocalSongs()
                    }
                    is domain.library.LocalSongDeleteResult.RequiresSystemConfirmation -> {
                        onRequiresSystemConfirmation(result.intentSender, result.pendingSongIds)
                    }
                    is domain.library.LocalSongDeleteResult.Failed -> {
                        _libraryFeedbackMessage.value = result.message
                    }
                }
            } else if (downloaded.isNotEmpty()) {
                _libraryFeedbackMessage.value = if (downloaded.size == 1) {
                    "Deleted 1 downloaded track."
                } else {
                    "Deleted ${downloaded.size} downloaded tracks."
                }
            }
        }
    }

    fun onSystemLocalDeleteConfirmed(deletedSongIds: List<Long>) {
        viewModelScope.launch {
            offlineLibraryRepository.cleanupDeletedSongReferences(deletedSongIds)
            _libraryFeedbackMessage.value = if (deletedSongIds.size == 1) {
                "Deleted 1 local track."
            } else {
                "Deleted ${deletedSongIds.size} local tracks."
            }
            loadLocalSongs()
        }
    }

    fun addOnlineTrackToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            if (song.isOnline && dao.getOnlineTrackById(song.id) == null) {
                val baseTrack = OnlineTrackMapper.toOnlineTrack(song)
                dao.upsertOnlineTrack(OnlineTrackMapper.mapOnlineTrackToEntity(baseTrack))
            }
            dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, song.id, 0))
        }
    }

    fun selectOnlineQualityPreference(preference: AudioQualityPreference) {
        _onlineQualityPreference.value = preference
        preferences.onlineQualityPreference = preference
        val cur = _currentSong.value
        if (cur != null && cur.isOnline) {
            val pos = musicService?.audioEngine?.currentPosition ?: _playbackInfo.value.currentPositionMs
            playSong(
                song = cur,
                newQueue = _queue.value,
                startIndex = _queueIndex.value,
                manualStreamId = null,
                resumePositionMs = pos
            )
        }
    }

    fun selectManualOnlineStreamOption(option: OnlineAudioStreamOption) {
        val cur = _currentSong.value ?: return
        if (!cur.isOnline) return
        val pos = musicService?.audioEngine?.currentPosition ?: _playbackInfo.value.currentPositionMs
        playSong(
            song = cur,
            newQueue = _queue.value,
            startIndex = _queueIndex.value,
            manualStreamId = option.id,
            resumePositionMs = pos
        )
    }

    // --- Music Download Manager & Downloaded Music Library Actions ---

    fun canonicalSourceKeyForTrack(track: OnlineTrack): String {
        return downloadCoordinator.storageHelper.computeCanonicalSourceKey(track.providerKey, track.contentUrl)
    }

    fun canonicalSourceKeyForSong(song: Song): String? {
        if (song.contentUrl.isBlank()) return null
        return downloadCoordinator.storageHelper.computeCanonicalSourceKey(
            song.providerKey.ifBlank { OnlineMusicProvider.KEY_YOUTUBE_MUSIC },
            song.contentUrl
        )
    }

    fun isTrackDownloaded(track: OnlineTrack): Boolean {
        return canonicalSourceKeyForTrack(track) in downloadedSourceKeys.value
    }

    fun isSongDownloaded(song: Song): Boolean {
        if (song.sourceType == "DOWNLOADED") return true
        val key = canonicalSourceKeyForSong(song) ?: return false
        return key in downloadedSourceKeys.value
    }

    fun requestDownloadSong(song: Song) {
        if (song.sourceType == "DOWNLOADED") {
            _downloadFeedbackMessage.value = "\"${song.title}\" is already in Library → Downloads."
            return
        }
        if (!song.isOnline) {
            _downloadFeedbackMessage.value = "\"${song.title}\" is already a local audio file on your device."
            return
        }
        requestDownloadTrack(OnlineTrackMapper.toOnlineTrack(song))
    }

    fun requestDownloadTrack(track: OnlineTrack) {
        viewModelScope.launch {
            when (val check = downloadCoordinator.checkDownloadability(track)) {
                is domain.download.DownloadabilityResult.AlreadyDownloaded -> {
                    _downloadFeedbackMessage.value = "\"${check.trackTitle}\" is already saved in Downloaded Music."
                    return@launch
                }
                is domain.download.DownloadabilityResult.AlreadyInQueue -> {
                    _downloadFeedbackMessage.value = "\"${check.trackTitle}\" is already in Download Manager (${check.state.displayLabel})."
                    return@launch
                }
                is domain.download.DownloadabilityResult.Unsupported -> {
                    _downloadFeedbackMessage.value = check.reason
                    return@launch
                }
                is domain.download.DownloadabilityResult.Downloadable -> Unit
            }

            val config = downloadCoordinator.preferences.getLocationConfig()
            val hasAccess = downloadCoordinator.hasAccessibleDownloadFolder()
            if (!config.isConfigured || !hasAccess) {
                val reason = if (config.isConfigured && !hasAccess) {
                    "Permission to \"${config.folderLabel.ifBlank { "previous folder" }}\" expired. Please reselect a download folder."
                } else {
                    null
                }
                _downloadFlowState.value = DownloadFlowUiState.AskFolderLocation(
                    pendingTrack = track,
                    suggestedInitialUri = downloadCoordinator.storageHelper.buildSuggestedInitialTreeUri(),
                    reasonMessage = reason
                )
            } else {
                openDownloadQualitySelector(track, config.folderLabel)
            }
        }
    }

    fun onDownloadFolderSelected(uri: android.net.Uri?) {
        val currentState = _downloadFlowState.value
        val pendingTrack = (currentState as? DownloadFlowUiState.AskFolderLocation)?.pendingTrack
        val pendingVideo = pendingVideoDownloadAfterFolderPick
        pendingVideoDownloadAfterFolderPick = null
        if (uri == null) {
            _downloadFlowState.value = DownloadFlowUiState.Idle
            return
        }
        viewModelScope.launch {
            val savedConfig = downloadCoordinator.persistSelectedFolder(uri)
            when {
                pendingTrack != null -> openDownloadQualitySelector(pendingTrack, savedConfig.folderLabel)
                pendingVideo != null -> {
                    _downloadFlowState.value = DownloadFlowUiState.Idle
                    _pendingVideoDownload.value = pendingVideo
                }
                else -> {
                    _downloadFlowState.value = DownloadFlowUiState.Idle
                    _downloadFeedbackMessage.value = "Download folder updated to ${savedConfig.folderLabel}."
                }
            }
        }
    }

    fun requestDownloadVideo(
        video: domain.video.OnlineVideoItem,
        details: domain.video.ResolvedVideoDetails?
    ) {
        val eligibility = data.download.VideoDownloadEligibilityEvaluator.evaluate(video, details)
        if (eligibility is data.download.VideoDownloadEligibility.NotPermitted) {
            _downloadFeedbackMessage.value = eligibility.reason
            return
        }
        val resolved = details ?: return
        viewModelScope.launch {
            val config = downloadCoordinator.preferences.getLocationConfig()
            val hasAccess = downloadCoordinator.hasAccessibleDownloadFolder()
            if (!config.isConfigured || !hasAccess) {
                pendingVideoDownloadAfterFolderPick = video to resolved
                _downloadFlowState.value = DownloadFlowUiState.AskFolderLocation(
                    pendingTrack = null,
                    suggestedInitialUri = downloadCoordinator.storageHelper.buildSuggestedInitialTreeUri(),
                    reasonMessage = "Choose a folder to save downloaded videos & audio offline."
                )
            } else {
                _pendingVideoDownload.value = video to resolved
            }
        }
    }

    fun confirmVideoDownload(
        video: domain.video.OnlineVideoItem,
        option: data.download.VideoDownloadOption
    ) {
        _pendingVideoDownload.value = null
        viewModelScope.launch {
            val config = downloadCoordinator.preferences.getLocationConfig()
            if (!config.isConfigured) {
                _downloadFeedbackMessage.value = "Please select a download folder in Settings first."
                return@launch
            }
            val res = downloadCoordinator.enqueueVideoDownload(
                video = video,
                option = option,
                targetFolderUri = config.treeUri
            )
            _downloadFeedbackMessage.value = if (res.isSuccess) {
                val kind = if (option.mediaType == domain.download.DownloadMediaType.VIDEO) "Video (${option.resolutionLabel})" else "Audio (${option.resolutionLabel})"
                "Downloading $kind: ${video.title}"
            } else {
                res.exceptionOrNull()?.message ?: "Unable to start download."
            }
        }
    }

    fun dismissVideoDownloadSheet() {
        _pendingVideoDownload.value = null
    }

    fun playDownloadedVideo(track: domain.download.DownloadedTrack) {
        if (track.isMissingFile) {
            _downloadFeedbackMessage.value = "Downloaded video file was moved or deleted from storage."
            return
        }
        videoController.playDownloadedLocalVideo(track, expandPlayer = true)
    }

    fun requestChangeDownloadFolder() {
        _downloadFlowState.value = DownloadFlowUiState.AskFolderLocation(
            pendingTrack = null,
            suggestedInitialUri = downloadCoordinator.storageHelper.buildSuggestedInitialTreeUri(),
            reasonMessage = null
        )
    }

    fun dismissDownloadFlow() {
        downloadFormatResolveJob?.cancel()
        downloadFormatResolveJob = null
        _downloadFlowState.value = DownloadFlowUiState.Idle
    }

    fun clearDownloadFeedback() {
        _downloadFeedbackMessage.value = null
    }

    private fun openDownloadQualitySelector(track: OnlineTrack, folderLabel: String) {
        downloadFormatResolveJob?.cancel()
        downloadFormatResolveJob = viewModelScope.launch {
            val defaultPreset = downloadCoordinator.preferences.defaultQualityFlow.first()
            _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
                domain.download.DownloadQualitySheetData(
                    track = track,
                    isResolvingFormats = true,
                    selectedPreset = defaultPreset,
                    destinationFolderLabel = folderLabel.ifBlank { "Selected Folder" }
                )
            )

            // Try pre-resolved stream from active player if it matches this exact track
            val currentResolved = _currentResolvedStream.value?.takeIf { it.contentUrl == track.contentUrl }
            val result = if (currentResolved != null) {
                downloadCoordinator.streamSelector.buildCandidatesFromResolved(currentResolved)
            } else {
                downloadCoordinator.streamSelector.extractDownloadableFormats(track)
            }

            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: "This track cannot be downloaded."
                _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
                    domain.download.DownloadQualitySheetData(
                        track = track,
                        isResolvingFormats = false,
                        selectedPreset = defaultPreset,
                        destinationFolderLabel = folderLabel.ifBlank { "Selected Folder" },
                        errorMessage = errMsg
                    )
                )
                return@launch
            }

            val rawCandidates = result.getOrThrow()
            val recommendations = downloadCoordinator.streamSelector.buildPresetRecommendations(rawCandidates)
            _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
                domain.download.DownloadQualitySheetData(
                    track = track,
                    isResolvingFormats = false,
                    candidates = rawCandidates,
                    recommendedByPreset = recommendations,
                    selectedPreset = defaultPreset,
                    selectedManualStreamId = null,
                    destinationFolderLabel = folderLabel.ifBlank { "Selected Folder" }
                )
            )

            // Optionally enrich genuine Content-Length asynchronously without blocking sheet interaction
            val enriched = downloadCoordinator.streamSelector.enrichContentLengthsIfUnknown(
                candidates = rawCandidates,
                headers = currentResolved?.selectedStream?.headers ?: emptyMap()
            )
            val updatedState = _downloadFlowState.value as? DownloadFlowUiState.QualitySelector
            if (updatedState != null && updatedState.data.track.contentUrl == track.contentUrl) {
                val updatedRecs = downloadCoordinator.streamSelector.buildPresetRecommendations(enriched)
                _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
                    updatedState.data.copy(
                        candidates = enriched,
                        recommendedByPreset = updatedRecs
                    )
                )
            }
        }
    }

    fun selectDownloadQualityPreset(preset: domain.download.DownloadQualityOption) {
        val current = (_downloadFlowState.value as? DownloadFlowUiState.QualitySelector)?.data ?: return
        _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
            current.copy(selectedPreset = preset, selectedManualStreamId = null)
        )
        viewModelScope.launch(Dispatchers.IO) {
            downloadCoordinator.preferences.setDefaultQuality(preset)
        }
    }

    fun selectDownloadFormatCandidate(streamId: String) {
        val current = (_downloadFlowState.value as? DownloadFlowUiState.QualitySelector)?.data ?: return
        _downloadFlowState.value = DownloadFlowUiState.QualitySelector(
            current.copy(selectedManualStreamId = streamId)
        )
    }

    fun confirmStartDownload() {
        val current = (_downloadFlowState.value as? DownloadFlowUiState.QualitySelector)?.data ?: return
        val chosen = current.selectedManualStreamId?.let { id ->
            current.candidates.firstOrNull { it.streamId == id }
        } ?: current.recommendedByPreset[current.selectedPreset]
            ?: current.candidates.firstOrNull()
        if (chosen == null) {
            _downloadFeedbackMessage.value = "No downloadable audio format available for this track."
            return
        }

        viewModelScope.launch {
            val location = downloadCoordinator.preferences.getLocationConfig()
            if (!location.isConfigured || !downloadCoordinator.hasAccessibleDownloadFolder()) {
                _downloadFlowState.value = DownloadFlowUiState.AskFolderLocation(
                    pendingTrack = current.track,
                    suggestedInitialUri = downloadCoordinator.storageHelper.buildSuggestedInitialTreeUri(),
                    reasonMessage = "Please select an accessible folder to save downloads."
                )
                return@launch
            }

            val enqueueResult = downloadCoordinator.enqueueDownload(
                track = current.track,
                qualityOption = current.selectedPreset,
                selectedCandidate = chosen,
                targetFolderUri = location.treeUri
            )
            _downloadFlowState.value = DownloadFlowUiState.Idle
            if (enqueueResult.isSuccess) {
                _downloadFeedbackMessage.value = "Downloading \"${current.track.title}\" (${chosen.codecLabel})."
            } else {
                _downloadFeedbackMessage.value = enqueueResult.exceptionOrNull()?.message
                    ?: "Could not start download."
            }
        }
    }

    fun pauseDownload(taskId: String) {
        viewModelScope.launch {
            downloadCoordinator.pauseDownload(taskId)
        }
    }

    fun resumeDownload(taskId: String) {
        viewModelScope.launch {
            downloadCoordinator.resumeDownload(taskId)
        }
    }

    fun cancelDownload(taskId: String) {
        viewModelScope.launch {
            downloadCoordinator.cancelDownload(taskId)
        }
    }

    fun retryDownload(taskId: String) {
        viewModelScope.launch {
            downloadCoordinator.retryDownload(taskId)
        }
    }

    fun removeDownloadTask(taskId: String) {
        viewModelScope.launch {
            downloadCoordinator.removeDownloadTask(taskId)
        }
    }

    fun deleteDownloadedTracks(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            downloadCoordinator.deleteDownloadedTracks(trackIds)
            _downloadFeedbackMessage.value = if (trackIds.size == 1) {
                "Deleted 1 downloaded track."
            } else {
                "Deleted ${trackIds.size} downloaded tracks."
            }
        }
    }

    fun clearDownloadHistory() {
        viewModelScope.launch {
            downloadCoordinator.clearDownloadHistory()
            _downloadFeedbackMessage.value = "Cleared download history (audio files kept intact)."
        }
    }

    fun setDownloadSortOption(option: domain.download.DownloadSortOption) {
        _downloadSortOption.value = option
    }

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
    }

    // --- Advanced Full-Screen Synchronized Lyrics Controls ---

    fun setFullScreenLyricsVisible(visible: Boolean) {
        isFullScreenLyricsOpen = visible
        if (visible) {
            val curPos = musicService?.audioEngine?.currentPosition ?: _playbackInfo.value.currentPositionMs
            _lyricsPositionMs.value = curPos
            if (_playbackInfo.value.isPlaying) {
                startHighPrecisionLyricsTracker()
            }
            val song = _currentSong.value
            if (song != null) {
                val expectedKey = lyricsRepository.trackKeyFor(song)
                val state = _lyricsLoadState.value
                val needsLoad = when (state) {
                    is domain.lyrics.LyricsLoadState.Loaded -> state.document.trackKey != expectedKey
                    is domain.lyrics.LyricsLoadState.Loading -> state.trackKey != expectedKey
                    is domain.lyrics.LyricsLoadState.NotFound -> state.trackKey != expectedKey
                    is domain.lyrics.LyricsLoadState.Error -> state.trackKey != expectedKey
                    domain.lyrics.LyricsLoadState.Idle -> true
                }
                if (needsLoad) {
                    loadLyricsForTrack(song)
                }
            }
        } else {
            lyricsPositionJob?.cancel()
            lyricsPositionJob = null
        }
    }

    /**
     * Polls real Media3 ExoPlayer / AudioEngine playback position at ~20Hz while
     * the Full-Screen Lyrics screen is visible & playing so line and word highlights
     * track audio accurately without arbitrary drift timers or a second player.
     */
    private fun startHighPrecisionLyricsTracker() {
        lyricsPositionJob?.cancel()
        lyricsPositionJob = viewModelScope.launch {
            while (isActive && isFullScreenLyricsOpen) {
                val engine = musicService?.audioEngine
                if (engine != null && engine.isPlaying) {
                    val pos = engine.currentPosition
                    _lyricsPositionMs.value = pos
                } else {
                    _lyricsPositionMs.value = _playbackInfo.value.currentPositionMs
                }
                delay(50L)
            }
        }
    }

    fun loadLyricsForTrack(
        song: Song,
        preferredSource: domain.lyrics.LyricsSourceType? = null,
        forceRefresh: Boolean = false
    ) {
        val requestToken = ++activeLyricsRequestToken
        val trackKey = lyricsRepository.trackKeyFor(song)
        lyricsLoadJob?.cancel()
        _currentTrackLyrics.value = null
        _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loading(
            trackKey = trackKey,
            message = if (preferredSource != null) {
                "Loading ${preferredSource.displayLabel}..."
            } else {
                "Finding synchronized lyrics..."
            }
        )

        lyricsLoadJob = viewModelScope.launch {
            try {
                // Restore per-track timing offset (or global default)
                val (savedOffset, hasCustom) = lyricsRepository.getSavedTrackOffset(song)
                if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch
                _lyricsTimingState.value = domain.lyrics.LyricsTimingState(
                    trackKey = trackKey,
                    trackOffsetMs = savedOffset,
                    globalOffsetMs = lyricsAppearance.value.globalTimingOffsetMs,
                    hasCustomTrackOffset = hasCustom
                )

                val doc = lyricsRepository.loadLyricsForSong(
                    song = song,
                    preferredSource = preferredSource,
                    forceRefresh = forceRefresh,
                    allowNetwork = connectivityMonitor.isOnline.value
                )
                if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch

                if (doc != null && (doc.hasLyrics || doc.isInstrumental)) {
                    _currentTrackLyrics.value = doc.rawText
                    _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loaded(doc)
                    _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(song, doc.sourceType)
                } else {
                    _currentTrackLyrics.value = null
                    val reason = when {
                        preferredSource != null -> "No lyrics found from ${preferredSource.displayLabel}."
                        !connectivityMonitor.isOnline.value -> "No embedded or local .lrc lyrics found. Connect to the internet to search online lyrics or import an .lrc file."
                        else -> "No matching lyrics found for \"${song.title}\". Try searching online, importing an .lrc file, or adding lyrics manually."
                    }
                    _lyricsLoadState.value = domain.lyrics.LyricsLoadState.NotFound(trackKey, reason)
                    _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(
                        song,
                        domain.lyrics.LyricsSourceType.NONE
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch
                Log.w(TAG, "Failed loading lyrics for ${song.title}", e)
                _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Error(
                    trackKey = trackKey,
                    message = e.message ?: "Unable to load lyrics right now."
                )
            }
        }
    }

    fun refreshCurrentLyrics() {
        val song = _currentSong.value ?: return
        lyricsRepository.invalidateMemoryCache(song)
        loadLyricsForTrack(song, preferredSource = null, forceRefresh = true)
    }

    fun selectLyricsSource(sourceType: domain.lyrics.LyricsSourceType) {
        val song = _currentSong.value ?: return
        loadLyricsForTrack(song, preferredSource = sourceType, forceRefresh = true)
    }

    fun searchOnlineLyrics(query: String, trackName: String? = null, artistName: String? = null) {
        val trimmed = query.trim()
        if (trimmed.isBlank() && trackName.isNullOrBlank()) return
        lyricsSearchJob?.cancel()
        _isSearchingLyrics.value = true
        lyricsSearchJob = viewModelScope.launch {
            try {
                val results = lyricsRepository.searchOnlineLyrics(
                    query = trimmed,
                    trackName = trackName,
                    artistName = artistName
                )
                _lyricsSearchResults.value = results
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.w(TAG, "Online lyrics search failed", e)
                _lyricsSearchResults.value = emptyList()
                _lyricsFeedbackMessage.value = "Lyrics search failed. Check your internet connection."
            } finally {
                _isSearchingLyrics.value = false
            }
        }
    }

    fun applyLyricsSearchCandidate(candidate: domain.lyrics.LyricsSearchCandidate) {
        val song = _currentSong.value ?: return
        val requestToken = ++activeLyricsRequestToken
        viewModelScope.launch {
            val doc = lyricsRepository.applySearchCandidate(song, candidate)
            if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch
            _currentTrackLyrics.value = doc.rawText
            _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loaded(doc)
            _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(song, doc.sourceType)
            _lyricsFeedbackMessage.value = "Applied lyrics from ${candidate.sourceLabel}"
        }
    }

    fun importLrcFile(uri: android.net.Uri) {
        val song = _currentSong.value ?: return
        val requestToken = ++activeLyricsRequestToken
        viewModelScope.launch {
            val displayName = resolveDocumentDisplayName(uri)
            val result = lyricsRepository.importLrcFromUri(song, uri, displayName)
            if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch
            result.onSuccess { doc ->
                _currentTrackLyrics.value = doc.rawText
                _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loaded(doc)
                _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(song, doc.sourceType)
                _lyricsFeedbackMessage.value = "Imported ${doc.syncType.displayLabel.lowercase()} lyrics"
            }.onFailure { err ->
                _lyricsFeedbackMessage.value = err.message ?: "Failed to import lyrics file."
            }
        }
    }

    fun saveEditedLyrics(rawText: String) {
        val song = _currentSong.value ?: return
        if (rawText.isBlank()) {
            _lyricsFeedbackMessage.value = "Lyrics cannot be empty."
            return
        }
        val requestToken = ++activeLyricsRequestToken
        viewModelScope.launch {
            val doc = lyricsRepository.saveEditedLyrics(song, rawText)
            if (requestToken != activeLyricsRequestToken || _currentSong.value?.id != song.id) return@launch
            _currentTrackLyrics.value = doc.rawText
            _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loaded(doc)
            _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(song, doc.sourceType)
            _lyricsFeedbackMessage.value = "Saved ${doc.syncType.displayLabel.lowercase()} lyrics locally"
        }
    }

    fun saveCurrentLyricsLocally() {
        val song = _currentSong.value ?: return
        val loaded = _lyricsLoadState.value as? domain.lyrics.LyricsLoadState.Loaded ?: return
        viewModelScope.launch {
            val saved = lyricsRepository.saveCurrentLyricsLocally(song, loaded.document)
            if (_currentSong.value?.id == song.id) {
                _lyricsLoadState.value = domain.lyrics.LyricsLoadState.Loaded(saved)
                _lyricsAvailableSources.value = lyricsRepository.inspectAvailableSources(song, saved.sourceType)
                _lyricsFeedbackMessage.value = "Lyrics saved locally for offline playback"
            }
        }
    }

    fun adjustLyricsTimingOffset(deltaMs: Long) {
        val current = _lyricsTimingState.value.effectiveOffsetMs
        setLyricsTimingOffset(current + deltaMs)
    }

    fun setLyricsTimingOffset(offsetMs: Long) {
        val song = _currentSong.value ?: return
        val clamped = offsetMs.coerceIn(
            domain.lyrics.LyricsTimingState.MIN_OFFSET_MS,
            domain.lyrics.LyricsTimingState.MAX_OFFSET_MS
        )
        val trackKey = lyricsRepository.trackKeyFor(song)
        _lyricsTimingState.value = _lyricsTimingState.value.copy(
            trackKey = trackKey,
            trackOffsetMs = clamped,
            hasCustomTrackOffset = true
        )
        viewModelScope.launch {
            lyricsRepository.saveTrackTimingOffset(song, clamped, hasCustomOffset = true)
        }
    }

    fun resetLyricsTimingOffset() {
        val song = _currentSong.value ?: return
        val trackKey = lyricsRepository.trackKeyFor(song)
        _lyricsTimingState.value = _lyricsTimingState.value.copy(
            trackKey = trackKey,
            trackOffsetMs = 0L,
            hasCustomTrackOffset = true
        )
        viewModelScope.launch {
            lyricsRepository.saveTrackTimingOffset(song, 0L, hasCustomOffset = true)
        }
    }

    fun setGlobalLyricsTimingOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(
            domain.lyrics.LyricsTimingState.MIN_OFFSET_MS,
            domain.lyrics.LyricsTimingState.MAX_OFFSET_MS
        )
        _lyricsTimingState.value = _lyricsTimingState.value.copy(globalOffsetMs = clamped)
        viewModelScope.launch {
            lyricsPreferences.setGlobalTimingOffset(clamped)
            _lyricsFeedbackMessage.value = "Saved global lyrics offset (${if (clamped > 0) "+$clamped" else "$clamped"} ms)"
        }
    }

    fun applyGlobalOffsetToCurrentTrack() {
        val song = _currentSong.value ?: return
        val global = lyricsAppearance.value.globalTimingOffsetMs
        val trackKey = lyricsRepository.trackKeyFor(song)
        _lyricsTimingState.value = domain.lyrics.LyricsTimingState(
            trackKey = trackKey,
            trackOffsetMs = global,
            globalOffsetMs = global,
            hasCustomTrackOffset = false
        )
        viewModelScope.launch {
            lyricsRepository.saveTrackTimingOffset(song, 0L, hasCustomOffset = false)
        }
    }

    fun updateLyricsAppearance(transform: (domain.lyrics.LyricsAppearanceConfig) -> domain.lyrics.LyricsAppearanceConfig) {
        val updated = transform(lyricsAppearance.value)
        viewModelScope.launch {
            lyricsPreferences.saveAppearance(updated)
        }
    }

    fun applyLyricsPreset(preset: domain.lyrics.LyricsPreset) {
        updateLyricsAppearance { it.withPreset(preset) }
    }

    fun seekToLyricLine(line: domain.lyrics.SyncedLyricLine) {
        if (!line.isTimed) return
        val loaded = _lyricsLoadState.value as? domain.lyrics.LyricsLoadState.Loaded
        val embeddedOffset = loaded?.document?.embeddedOffsetMs ?: 0L
        val targetMs = domain.lyrics.LyricsSyncEngine.calculateSeekPositionForLine(
            line = line,
            userOffsetMs = _lyricsTimingState.value.effectiveOffsetMs,
            embeddedOffsetMs = embeddedOffset
        )
        seekTo(targetMs)
    }

    fun clearLyricsFeedback() {
        _lyricsFeedbackMessage.value = null
    }

    private fun resolveDocumentDisplayName(uri: android.net.Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // GITHUB RELEASES IN-APP UPDATE & POST-UPDATE SECURITY REMINDER
    // =========================================================================

    val appUpdateManager: data.update.AppUpdateManager by lazy {
        data.update.AppUpdateManager(
            context = getApplication(),
            scope = viewModelScope
        )
    }

    val appUpdateSummaryState: StateFlow<domain.update.AppUpdateSummaryState>
        get() = appUpdateManager.summaryState

    val appUpdateDialogState: StateFlow<domain.update.AppUpdateDialogState>
        get() = appUpdateManager.dialogState

    val postUpdateReminder: StateFlow<domain.update.PostUpdateReminderState?>
        get() = appUpdateManager.postUpdateReminder

    fun triggerAppUpdateLaunchFlow() {
        appUpdateManager.triggerAutomaticLaunchCheckIfNeeded()
    }

    fun checkForAppUpdatesManual() {
        appUpdateManager.checkForUpdatesManual()
    }

    fun openCachedAvailableUpdate() {
        appUpdateManager.openCachedAvailableUpdateDialog()
    }

    fun deferAppUpdateLater() {
        appUpdateManager.deferUpdateLater()
    }

    fun startAppUpdateDownload(release: domain.update.GitHubReleaseInfo) {
        appUpdateManager.startDownloadForRelease(release)
    }

    fun cancelAppUpdateDownload() {
        appUpdateManager.cancelActiveDownload()
    }

    suspend fun prepareInstallVerifiedUpdate(
        release: domain.update.GitHubReleaseInfo,
        verifiedApk: domain.update.VerifiedApkInfo
    ): data.update.InstallPreparationResult {
        return appUpdateManager.prepareInstallNow(release, verifiedApk)
    }

    suspend fun onReturnedFromUnknownAppSourcesSettings(): data.update.InstallPreparationResult {
        appUpdateManager.refreshPostUpdateReminderPermissionState()
        return appUpdateManager.onReturnedFromUnknownSourcesSettings()
    }

    fun onPackageInstallerReturnedWithoutUpgrade(wasCancelled: Boolean) {
        appUpdateManager.onPackageInstallerReturnedWithoutUpgrade(wasCancelled)
    }

    fun dismissAppUpdateDialog() {
        appUpdateManager.dismissDialog()
    }

    fun dismissPostUpdateReminder() {
        appUpdateManager.dismissPostUpdateReminder()
    }

    fun buildManageUnknownAppSourcesIntent(): Intent? {
        return appUpdateManager.installerLauncher.buildManageUnknownAppSourcesIntent()
    }

    override fun onCleared() {
        super.onCleared()
        lyricsLoadJob?.cancel()
        lyricsSearchJob?.cancel()
        lyricsPositionJob?.cancel()
        queuePersistJob?.cancel()
        moodDiscoveryJob?.cancel()
        indiaDiscoveryJob?.cancel()
        currentSongRecsJob?.cancel()
        radioTopUpJob?.cancel()
        videoController.release()
        cacheManager.onMusicPlayerStateChanged(isOpen = false)
        downloadFormatResolveJob?.cancel()
        onlineSearchJob?.cancel()
        onlineSuggestionJob?.cancel()
        discoverSectionJobs.values.forEach { it.cancel() }
        discoverSectionJobs.clear()
        discoverLoadMoreJobs.values.forEach { it.cancel() }
        discoverLoadMoreJobs.clear()
        onlineCollectionJob?.cancel()
        unifiedPlaybackController?.cancelPendingResolution()
        connectivityMonitor.unregister()
        serviceCollectorsJob?.cancel()
        progressTrackingJob?.cancel()
        sleepTimerJob?.cancel()
        musicService?.let { svc ->
            if (svc.playbackCallback === servicePlaybackCallback) {
                svc.setPlaybackCallback(null)
            }
        }
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignored
            }
            isBound = false
        }
        musicService = null
    }
}
