package viewmodel

import android.content.Context
import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import com.example.data.SonoraDao
import data.preferences.ExperiencePreferences
import data.preferences.VideoAccountPreferences
import data.preferences.VideoExperienceSettings
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import data.video.PersonalizedVideoShelf
import data.video.VideoFeedbackType
import data.video.VideoRecommendationEngine
import data.video.VideoRecommendationFeedbackEntity
import data.video.VideoRecommendationSignals
import domain.download.DownloadedTrack
import domain.online.OnlineErrorKind
import domain.online.OnlineResult
import domain.video.ChannelDetailUtils
import domain.video.VideoAdaptiveQualitySelector
import domain.video.ChannelNotificationPreference
import domain.video.ChannelPlaylistDetailState
import domain.video.ChannelTabPageState
import domain.video.ChannelTabType
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.ProviderAuthState
import domain.video.ResolvedVideoDetails
import domain.video.VideoAccountActionResult
import domain.video.VideoAspectRatioMode
import domain.video.VideoAudioOption
import domain.video.VideoChannelUiState
import domain.video.VideoHomeSectionType
import domain.video.VideoNotificationItem
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerGestureSettings
import domain.video.VideoPlayerUiState
import domain.video.VideoRatingState
import domain.video.VideoRegionalLanguage
import domain.video.VideoSearchFilter
import domain.video.VideoSearchUiState
import domain.video.VideoSectionState
import domain.video.VideoStreamOption
import domain.video.VideoSubtitleOption
import domain.video.VideoSwipeSensitivity
import domain.video.defaultVideoSectionsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import playback.VideoPlaybackController

/**
 * Modular feature controller coordinating SONORA's Online Video experience:
 * - 12 Home discovery sections (Indian content prioritized + 8 regional languages + pagination)
 * - Dedicated Video Search (Videos, Channels, Playlists, Suggestions, Recent Searches)
 * - Dedicated Media3 [VideoPlaybackController] isolated from audio-only 15-band EQ / Audio Studio
 * - Safe audio <-> video handoff without disrupting playback when switching Home modes
 * - Online Video Library (Watch Later, Video History, Saved Videos, Subscribed Channels, Video Playlists)
 * - Official YouTube Data API v3 OAuth account actions (Like, Dislike, Subscribe, Notification Bell)
 */
class OnlineVideoController(
    context: Context,
    private val scope: CoroutineScope,
    val repository: OnlineVideoRepository,
    val accountPreferences: VideoAccountPreferences,
    private val getCountryCode: () -> String,
    private val getLanguageCode: () -> String,
    private val onPauseMusicForVideo: () -> Unit,
    private val onActiveVideoArtworkChanged: (OnlineVideoItem?) -> Unit = {},
    private val onPlayChannelAudioInBackground: (List<OnlineVideoItem>) -> Unit = {},
    private val experiencePreferences: ExperiencePreferences? = null,
    private val isMeteredNetwork: () -> Boolean = { false },
    private val dao: SonoraDao? = null
) {
    private val appContext: Context = context.applicationContext
    private val cacheManager: data.cache.SonoraMediaCacheManager =
        data.cache.SonoraMediaCacheManager.getInstance(appContext)

    private val _homeSections =
        MutableStateFlow<Map<VideoHomeSectionType, VideoSectionState>>(defaultVideoSectionsState())
    val homeSections: StateFlow<Map<VideoHomeSectionType, VideoSectionState>> = _homeSections.asStateFlow()

    val regionalLanguage: StateFlow<VideoRegionalLanguage> =
        accountPreferences.regionalLanguageFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VideoRegionalLanguage.ALL_INDIA
        )

    val authState: StateFlow<ProviderAuthState> =
        accountPreferences.authStateFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ProviderAuthState.Disconnected
        )

    val preferredMaxHeight: StateFlow<Int> =
        accountPreferences.preferredMaxHeightFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = 1080
        )

    val gestureSettings: StateFlow<VideoPlayerGestureSettings> =
        accountPreferences.gestureSettingsFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VideoPlayerGestureSettings()
        )

    val watchLaterVideos: StateFlow<List<OnlineVideoItem>> =
        repository.watchLaterVideosFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val savedVideos: StateFlow<List<OnlineVideoItem>> =
        repository.savedVideosFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val videoHistory: StateFlow<List<OnlineVideoItem>> =
        repository.videoHistoryFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val likedVideos: StateFlow<List<OnlineVideoItem>> =
        repository.likedVideosFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val subscribedChannels: StateFlow<List<OnlineVideoChannel>> =
        repository.subscribedChannelsFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val videoPlaylists: StateFlow<List<OnlineVideoPlaylistSummary>> =
        repository.videoPlaylistsFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val videoSearchHistory: StateFlow<List<String>> =
        repository.videoSearchHistoryFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val videoNotifications: StateFlow<List<VideoNotificationItem>> =
        repository.videoNotificationsFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val unreadVideoNotificationCount: StateFlow<Int> =
        repository.unreadVideoNotificationCountFlow.stateIn(scope, SharingStarted.Eagerly, 0)

    private val _isSyncingNotifications = MutableStateFlow(false)
    val isSyncingNotifications: StateFlow<Boolean> = _isSyncingNotifications.asStateFlow()

    val recommendationFeedback: StateFlow<List<VideoRecommendationFeedbackEntity>> =
        (dao?.getAllVideoRecommendationFeedbackFlow() ?: flowOf(emptyList()))
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val userInterestTopics: StateFlow<List<String>> =
        recommendationFeedback.combine(MutableStateFlow(Unit)) { entries, _ ->
            VideoRecommendationEngine.extractUserInterests(entries)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    // Ignore position-only Room updates while a video is actively playing so home shelves
    // do not recompute on CPU/main-thread during playback.
    private val distinctHistoryForRecommendations = videoHistory
        .distinctUntilChanged { old, new ->
            old.size == new.size && old.indices.all { idx -> old[idx].videoId == new[idx].videoId }
        }

    val personalizedHomeShelves: StateFlow<List<PersonalizedVideoShelf>> =
        combine(
            _homeSections,
            distinctHistoryForRecommendations,
            subscribedChannels,
            videoSearchHistory,
            recommendationFeedback
        ) { sections, history, channels, searches, feedback ->
            VideoRecommendationEngine.buildPersonalizedHomeShelves(
                rawSections = sections,
                signals = VideoRecommendationSignals(
                    watchHistory = history,
                    subscribedChannels = channels,
                    searchHistory = searches,
                    feedbackEntries = feedback,
                    countryCode = getCountryCode(),
                    regionalLanguageLabel = regionalLanguage.value.label
                )
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(VideoSearchFilter.VIDEOS)
    val searchFilter: StateFlow<VideoSearchFilter> = _searchFilter.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchState = MutableStateFlow<VideoSearchUiState>(VideoSearchUiState.Idle)
    val searchState: StateFlow<VideoSearchUiState> = _searchState.asStateFlow()

    private val _channelState = MutableStateFlow<VideoChannelUiState>(VideoChannelUiState.Idle)
    val channelState: StateFlow<VideoChannelUiState> = _channelState.asStateFlow()

    private val _channelPlaylistState =
        MutableStateFlow<ChannelPlaylistDetailState>(ChannelPlaylistDetailState.Idle)
    val channelPlaylistState: StateFlow<ChannelPlaylistDetailState> = _channelPlaylistState.asStateFlow()

    private val _playerState = MutableStateFlow(
        VideoPlayerUiState(
            supportsNotificationApi = repository.supportsNotificationPreferenceApi,
            backgroundPlaybackAllowed = false
        )
    )
    val playerState: StateFlow<VideoPlayerUiState> = _playerState.asStateFlow()

    /**
     * High-frequency playback progress stream separated from [playerState] so 500ms position
     * updates only recompose the seek bar and mini-player progress indicator.
     */
    private val _playbackProgress = MutableStateFlow(VideoPlaybackProgress())
    val playbackProgress: StateFlow<VideoPlaybackProgress> = _playbackProgress.asStateFlow()

    private val _pendingAccountActionPrompt = MutableStateFlow<VideoAccountActionResult?>(null)
    val pendingAccountActionPrompt: StateFlow<VideoAccountActionResult?> = _pendingAccountActionPrompt.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val sectionJobs = mutableMapOf<VideoHomeSectionType, Job>()
    private val loadMoreJobs = mutableMapOf<VideoHomeSectionType, Job>()
    private val channelTabJobs = mutableMapOf<ChannelTabType, Job>()
    private val channelTabLoadMoreJobs = mutableMapOf<ChannelTabType, Job>()
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var resolveJob: Job? = null
    private var channelJob: Job? = null
    private var channelPlaylistJob: Job? = null
    private var positionTickerJob: Job? = null
    private var videoPreloadJob: Job? = null
    private var activeResolveToken: Long = 0L
    private var expiredRefreshAttemptedForVideoId: String? = null
    private var fallbackStreamAttemptedForVideoId: String? = null
    private var lastQualitySwitchElapsedMs: Long = 0L
    private var lastObservedMeteredNetwork: Boolean = isMeteredNetwork()
    private var lastNotificationAutoSyncMs: Long = 0L

    val playbackController = VideoPlaybackController(
        context = context,
        onStateSnapshot = { snap ->
            _playerState.update { state ->
                val nextDuration = snap.durationMs.takeIf { it > 0L } ?: state.durationMs
                if (state.isPlaying == snap.isPlaying &&
                    state.isBuffering == snap.isBuffering &&
                    state.durationMs == nextDuration &&
                    state.videoWidth == snap.videoWidth &&
                    state.videoHeight == snap.videoHeight &&
                    state.pixelWidthHeightRatio == snap.pixelWidthHeightRatio &&
                    state.unappliedRotationDegrees == snap.unappliedRotationDegrees &&
                    state.hasKnownVideoDimensions == snap.hasKnownDimensions &&
                    state.activeSubtitleCues == snap.subtitleCues &&
                    state.actualVideoCodec == snap.actualVideoCodec &&
                    state.actualVideoMimeType == snap.actualVideoMimeType &&
                    state.actualVideoBitrateKbps == snap.actualVideoBitrateKbps &&
                    state.actualVideoFps == snap.actualVideoFps
                ) {
                    state
                } else {
                    state.copy(
                        isPlaying = snap.isPlaying,
                        isBuffering = snap.isBuffering,
                        durationMs = nextDuration,
                        videoWidth = snap.videoWidth,
                        videoHeight = snap.videoHeight,
                        pixelWidthHeightRatio = snap.pixelWidthHeightRatio,
                        unappliedRotationDegrees = snap.unappliedRotationDegrees,
                        hasKnownVideoDimensions = snap.hasKnownDimensions,
                        activeSubtitleCues = snap.subtitleCues,
                        estimatedBandwidthBps = snap.estimatedBandwidthBps,
                        actualVideoCodec = snap.actualVideoCodec,
                        actualVideoMimeType = snap.actualVideoMimeType,
                        actualVideoBitrateKbps = snap.actualVideoBitrateKbps,
                        actualVideoFps = snap.actualVideoFps
                    )
                }
            }
        },
        onVideoEnded = {
            if (_playerState.value.hasNextInQueue) {
                playNextInQueue()
            } else {
                _playerState.update { it.copy(hasVideoEnded = true, isPlaying = false) }
            }
        },
        onError = { message, kind, mayBeExpired ->
            val active = _playerState.value.activeVideo
            val currentStream = _playerState.value.selectedVideoStream
            val details = _playerState.value.resolvedDetails
            val resumePos: Long = _playbackProgress.value.currentPositionMs.takeIf { it > 0L }
                ?: _playerState.value.currentPositionMs

            // 1. Bounded expired-URL refresh (max 1 forced re-extraction per video to avoid endless retries)
            if (mayBeExpired && active != null && expiredRefreshAttemptedForVideoId != active.videoId) {
                expiredRefreshAttemptedForVideoId = active.videoId
                repository.clearDetailsCache(active.videoId)
                playVideo(
                    video = active,
                    queuePool = _playerState.value.queue,
                    expandPlayer = _playerState.value.isPlayerExpanded,
                    startPositionMs = resumePos,
                    forceRefreshStream = true
                )
                return@VideoPlaybackController
            }

            // 2. Bounded single fallback to a compatible hardware-friendly lower/muxed stream
            if (active != null && details != null && fallbackStreamAttemptedForVideoId != active.videoId) {
                val alternate = details.videoStreams.firstOrNull {
                    it.id != currentStream?.id && !it.isAv1Codec && (!it.isVideoOnly || details.audioStreams.isNotEmpty())
                }
                if (alternate != null) {
                    fallbackStreamAttemptedForVideoId = active.videoId
                    selectVideoStream(alternate)
                    return@VideoPlaybackController
                }
            }

            _playerState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    isResolving = false,
                    errorMessage = message,
                    errorKind = kind
                )
            }
        },
        onProgressTick = { progress ->
            _playbackProgress.update { current ->
                val resolvedDur = progress.durationMs.takeIf { it > 0L } ?: current.durationMs
                if (current.currentPositionMs == progress.currentPositionMs &&
                    current.durationMs == resolvedDur &&
                    current.bufferedPositionMs == progress.bufferedPositionMs
                ) {
                    current
                } else {
                    progress.copy(durationMs = resolvedDur)
                }
            }
            evaluateAdaptiveQualityDuringPlayback(progress)
        }
    )

    init {
        scope.launch {
            accountPreferences.gestureSettingsFlow.collect { prefs ->
                _playerState.update { state ->
                    state.copy(
                        aspectRatioMode = prefs.defaultAspectRatio,
                        gestureSettings = prefs
                    )
                }
            }
        }
        experiencePreferences?.let { expPrefs ->
            scope.launch {
                expPrefs.videoExperienceFlow.collect { videoExp ->
                    _playerState.update { state ->
                        state.copy(
                            videoExperienceSettings = videoExp,
                            isAutoQualityMode = videoExp.autoVideoQualityEnabled
                        )
                    }
                    if (videoExp.autoVideoQualityEnabled && _playerState.value.resolvedDetails != null && !_playerState.value.isLocalOfflineVideo) {
                        val decision = VideoAdaptiveQualitySelector.selectBestQuality(
                            availableStreams = _playerState.value.resolvedDetails?.videoStreams.orEmpty(),
                            settings = videoExp,
                            isMeteredMobile = isMeteredNetwork(),
                            estimatedBandwidthBps = playbackController.estimatedBandwidthBps(),
                            bufferedAheadMs = (_playerState.value.bufferedPositionMs - _playerState.value.currentPositionMs).coerceAtLeast(0L),
                            rebufferCount = playbackController.rebufferCount
                        )
                        playbackController.applyAdaptiveMaxVideoHeight(decision.targetMaxHeightPx)
                        _playerState.update { it.copy(qualityStatusLabel = decision.reasonLabel) }
                    }
                }
            }
        }
        // Keep Video Player, Channel Detail Screen, and WorkManager upload notifications reactively synced.
        scope.launch {
            subscribedChannels.collect { saved ->
                val activeVideo = _playerState.value.activeVideo
                if (activeVideo != null) {
                    val activeMatch = findMatchingSubscribedChannel(
                        channels = saved,
                        channelUrl = activeVideo.channelUrl,
                        channelName = activeVideo.channelName
                    )
                    _playerState.update { state ->
                        if (state.activeVideo?.videoId != activeVideo.videoId) state
                        else state.copy(
                            isSubscribedLocally = activeMatch != null,
                            notificationPreference = activeMatch?.notificationPreference
                                ?: ChannelNotificationPreference.NONE
                        )
                    }
                }

                val current = _channelState.value as? VideoChannelUiState.Success
                if (current != null) {
                    val match = findMatchingSubscribedChannel(
                        channels = saved,
                        channelUrl = current.channel.channelUrl,
                        channelName = current.channel.name,
                        channelId = current.channel.channelId
                    )
                    val isLocal = match != null
                    val isProvider = match?.isSubscribedOnProvider == true
                    val pref = match?.notificationPreference ?: ChannelNotificationPreference.NONE
                    if (current.channel.isSubscribedLocal != isLocal ||
                        current.channel.isSubscribedOnProvider != isProvider ||
                        current.channel.notificationPreference != pref
                    ) {
                        _channelState.value = current.copy(
                            channel = current.channel.copy(
                                isSubscribedLocal = isLocal,
                                isSubscribedOnProvider = isProvider,
                                notificationPreference = pref
                            )
                        )
                    }
                }

                val hasAlertsEnabled = saved.any {
                    it.notificationPreference == ChannelNotificationPreference.ALL
                }
                data.video.ChannelUploadNotificationScheduler.syncSchedule(appContext, hasAlertsEnabled)
            }
        }
    }

    // --- Home Discovery (12 sections, Indian priority, 8 regional languages, pagination) ---

    fun ensureHomeDiscoveryLoaded() {
        val hasAnyLoaded = _homeSections.value.values.any { it is VideoSectionState.Success }
        val anyActive = sectionJobs.values.any { it.isActive }
        if (!hasAnyLoaded && !anyActive) {
            refreshHomeDiscovery(forceRefresh = false)
        }
    }

    fun refreshHomeDiscovery(forceRefresh: Boolean = false) {
        if (forceRefresh) repository.clearDiscoveryCaches()
        val remoteSections = VideoHomeSectionType.entries.filter { it != VideoHomeSectionType.RECENTLY_WATCHED }
        remoteSections.forEach { section ->
            loadHomeSection(section, forceRefresh = forceRefresh)
        }
    }

    fun loadHomeSection(section: VideoHomeSectionType, forceRefresh: Boolean = false) {
        if (section == VideoHomeSectionType.RECENTLY_WATCHED) return
        sectionJobs[section]?.cancel()
        sectionJobs[section] = scope.launch {
            _homeSections.update { it + (section to VideoSectionState.Loading) }
            val recentHint = videoHistory.value.firstOrNull()?.channelName
            when (
                val res = repository.fetchHomeSection(
                    section = section,
                    countryCode = getCountryCode(),
                    languageCode = getLanguageCode(),
                    regionalLanguage = regionalLanguage.value,
                    recentWatchHint = recentHint,
                    forceRefresh = forceRefresh
                )
            ) {
                is OnlineResult.Success -> {
                    _homeSections.update {
                        it + (section to VideoSectionState.Success(
                            items = res.value.items,
                            nextPageToken = res.value.nextPageToken,
                            isLoadingMore = false,
                            sourceLabel = res.value.sourceLabel
                        ))
                    }
                }
                is OnlineResult.Error -> {
                    _homeSections.update {
                        it + (section to VideoSectionState.Error(
                            kind = res.kind,
                            message = res.message,
                            isRetryable = res.isRetryable
                        ))
                    }
                }
            }
        }
    }

    fun loadMoreHomeSection(section: VideoHomeSectionType) {
        val current = _homeSections.value[section] as? VideoSectionState.Success ?: return
        val token = current.nextPageToken ?: return
        if (current.isLoadingMore || loadMoreJobs[section]?.isActive == true) return

        loadMoreJobs[section] = scope.launch {
            _homeSections.update { it + (section to current.copy(isLoadingMore = true)) }
            when (val res = repository.loadMoreHomeSection(token)) {
                is OnlineResult.Success -> {
                    val merged = (current.items + res.value.items).distinctBy { it.videoId }
                    _homeSections.update {
                        it + (section to current.copy(
                            items = merged,
                            nextPageToken = res.value.nextPageToken,
                            isLoadingMore = false
                        ))
                    }
                }
                is OnlineResult.Error -> {
                    _homeSections.update { it + (section to current.copy(isLoadingMore = false)) }
                    _feedbackMessage.value = res.message
                }
            }
        }
    }

    fun selectRegionalLanguage(language: VideoRegionalLanguage) {
        if (regionalLanguage.value == language) return
        scope.launch {
            accountPreferences.setRegionalLanguage(language)
            repository.clearDiscoveryCaches()
            refreshHomeDiscovery(forceRefresh = true)
        }
    }

    fun onRegionOrLanguageChanged() {
        repository.clearDiscoveryCaches()
        val hasLoaded = _homeSections.value.values.any { it is VideoSectionState.Success || it is VideoSectionState.Error }
        if (hasLoaded) {
            refreshHomeDiscovery(forceRefresh = true)
        }
    }

    // --- Dedicated Online Video Search (Separate from Online Music search) ---

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        suggestionJob?.cancel()
        if (query.trim().length < 2) {
            _searchSuggestions.value = emptyList()
            return
        }
        suggestionJob = scope.launch {
            delay(220L)
            _searchSuggestions.value = repository.getVideoSuggestions(query)
        }
    }

    fun selectSearchFilter(filter: VideoSearchFilter) {
        if (_searchFilter.value == filter) return
        _searchFilter.value = filter
        val q = _searchQuery.value.trim()
        if (q.isNotBlank()) executeSearch(q)
    }

    fun executeSearch(query: String = _searchQuery.value) {
        val clean = query.trim()
        _searchQuery.value = clean
        _searchSuggestions.value = emptyList()
        if (clean.isBlank()) {
            _searchState.value = VideoSearchUiState.Idle
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            val activeFilter = _searchFilter.value
            _searchState.value = VideoSearchUiState.Loading(clean)
            when (val res = repository.searchVideos(clean, activeFilter)) {
                is OnlineResult.Success -> {
                    _searchState.value = VideoSearchUiState.Success(
                        query = clean,
                        filter = activeFilter,
                        videos = res.value.videos,
                        channels = res.value.channels,
                        playlists = res.value.playlists,
                        nextPageToken = res.value.nextPageToken,
                        isLoadingMore = false
                    )
                }
                is OnlineResult.Error -> {
                    _searchState.value = VideoSearchUiState.Error(
                        kind = res.kind,
                        message = res.message,
                        isRetryable = res.isRetryable
                    )
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        val current = _searchState.value as? VideoSearchUiState.Success ?: return
        val token = current.nextPageToken ?: return
        if (current.isLoadingMore) return
        searchJob = scope.launch {
            _searchState.value = current.copy(isLoadingMore = true)
            when (val res = repository.loadMoreSearch(token)) {
                is OnlineResult.Success -> {
                    _searchState.value = current.copy(
                        videos = (current.videos + res.value.videos).distinctBy { it.videoId },
                        channels = (current.channels + res.value.channels).distinctBy { it.channelId },
                        playlists = (current.playlists + res.value.playlists).distinctBy { it.externalUrl ?: it.name },
                        nextPageToken = res.value.nextPageToken,
                        isLoadingMore = false
                    )
                }
                is OnlineResult.Error -> {
                    _searchState.value = current.copy(isLoadingMore = false)
                    _feedbackMessage.value = res.message
                }
            }
        }
    }

    fun removeSearchHistory(query: String) {
        scope.launch(Dispatchers.IO) { repository.removeSearchHistory(query) }
    }

    fun clearSearchHistory() {
        scope.launch(Dispatchers.IO) { repository.clearSearchHistory() }
    }

    // --- Video Playback, Queue, Resolution/Audio/Subtitle Switching, PiP & Lifecycle ---

    fun playVideo(
        video: OnlineVideoItem,
        queuePool: List<OnlineVideoItem> = emptyList(),
        expandPlayer: Boolean = true,
        startPositionMs: Long = video.lastPositionMs,
        forceRefreshStream: Boolean = false
    ) {
        // Safe audio -> video handoff: pause music playback only when video actively starts
        onPauseMusicForVideo()

        if (!forceRefreshStream) {
            expiredRefreshAttemptedForVideoId = null
            fallbackStreamAttemptedForVideoId = null
        }

        val effectiveQueue = if (queuePool.isNotEmpty()) {
            val distinct = queuePool.distinctBy { it.videoId }
            if (distinct.any { it.videoId == video.videoId }) distinct else listOf(video) + distinct
        } else {
            val existing = _playerState.value.queue
            if (existing.any { it.videoId == video.videoId }) existing else listOf(video) + existing
        }
        val idx = effectiveQueue.indexOfFirst { it.videoId == video.videoId }.coerceAtLeast(0)

        val token = ++activeResolveToken
        resolveJob?.cancel()
        videoPreloadJob?.cancel()
        cacheManager.cancelBackgroundPreloadsForForegroundPlayback()

        val matchedChannel = findMatchingSubscribedChannel(
            channels = subscribedChannels.value,
            channelUrl = video.channelUrl,
            channelName = video.channelName
        )

        val safeStartMs = startPositionMs.coerceAtLeast(0L)
        _playbackProgress.value = VideoPlaybackProgress(
            currentPositionMs = safeStartMs,
            durationMs = video.durationMs,
            bufferedPositionMs = safeStartMs,
            estimatedBandwidthBps = playbackController.estimatedBandwidthBps()
        )
        _playerState.update {
            it.copy(
                activeVideo = video,
                resolvedDetails = null,
                isResolving = true,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = safeStartMs,
                durationMs = video.durationMs,
                isPlayerExpanded = expandPlayer,
                queue = effectiveQueue,
                queueIndex = idx,
                errorMessage = null,
                errorKind = null,
                activeSubtitleCues = emptyList(),
                ratingState = VideoRatingState.NONE,
                isSubscribedLocally = matchedChannel != null,
                isSubscribedOnProvider = false,
                notificationPreference = matchedChannel?.notificationPreference ?: ChannelNotificationPreference.NONE,
                hasVideoEnded = false,
                isLocalOfflineVideo = false
            )
        }
        onActiveVideoArtworkChanged(video)

        scope.launch(Dispatchers.IO) {
            val savedRating = repository.getLocalVideoRating(video.videoId)
            _playerState.update { state ->
                if (state.activeVideo?.videoId == video.videoId) state.copy(ratingState = savedRating) else state
            }
            repository.recordVideoWatched(video, safeStartMs)
        }

        resolveJob = scope.launch {
            try {
                val extractStartMs = SystemClock.elapsedRealtime()
                when (val res = repository.resolveVideoDetails(video, forceRefresh = forceRefreshStream)) {
                    is OnlineResult.Success -> {
                        if (token != activeResolveToken) return@launch
                        val extractionDurationMs = (SystemClock.elapsedRealtime() - extractStartMs).coerceAtLeast(0L)
                        val details = res.value
                        val videoExp = _playerState.value.videoExperienceSettings
                        val meteredNow = isMeteredNetwork()
                        lastObservedMeteredNetwork = meteredNow
                        lastQualitySwitchElapsedMs = SystemClock.elapsedRealtime()
                        val deviceMaxHeight = playbackController.detectDeviceMaxVideoHeightPx()
                        val adaptiveDecision = VideoAdaptiveQualitySelector.selectBestQuality(
                            availableStreams = details.videoStreams,
                            settings = videoExp,
                            isMeteredMobile = meteredNow,
                            estimatedBandwidthBps = playbackController.estimatedBandwidthBps(),
                            bufferedAheadMs = 0L,
                            rebufferCount = 0,
                            deviceMaxHeightPx = deviceMaxHeight,
                            isInitialStartup = true
                        )
                        val chosenVideo = adaptiveDecision.selectedStream
                            ?: pickPreferredStream(details.videoStreams, deviceMaxHeight)
                        val chosenAudio = details.audioStreams.firstOrNull()
                        // Prefer direct highest-quality extracted stream (+ merged high-bitrate audio) when available,
                        // falling back to HLS/DASH adaptive manifest for live streams or when direct streams are absent.
                        val useAdaptiveManifest = (details.video.isLive || chosenVideo == null) &&
                            (!details.hlsUrl.isNullOrBlank() || !details.dashMpdUrl.isNullOrBlank())

                        // Append related videos into queue if queue only had the single video
                        val filteredRelated = VideoRecommendationEngine.filterDismissed(
                            details.relatedVideos,
                            recommendationFeedback.value
                        )
                        val enrichedQueue = if (_playerState.value.queue.size <= 1 && filteredRelated.isNotEmpty()) {
                            (listOf(details.video) + filteredRelated.take(15)).distinctBy { it.videoId }
                        } else {
                            _playerState.value.queue.map { if (it.videoId == details.video.videoId) details.video else it }
                        }
                        val updatedIdx = enrichedQueue.indexOfFirst { it.videoId == details.video.videoId }.coerceAtLeast(0)

                        val matchedAfterResolve = findMatchingSubscribedChannel(
                            channels = subscribedChannels.value,
                            channelUrl = details.video.channelUrl,
                            channelName = details.video.channelName
                        )
                        _playerState.update {
                            it.copy(
                                activeVideo = details.video,
                                resolvedDetails = details,
                                isResolving = false,
                                durationMs = details.video.durationMs.takeIf { d -> d > 0L } ?: it.durationMs,
                                selectedVideoStream = chosenVideo,
                                selectedAudioStream = chosenAudio,
                                selectedSubtitle = null,
                                isAutoQualityMode = adaptiveDecision.isAutoMode,
                                qualityStatusLabel = adaptiveDecision.reasonLabel,
                                isUsingAdaptiveManifest = useAdaptiveManifest,
                                queue = enrichedQueue,
                                queueIndex = updatedIdx,
                                isSubscribedLocally = matchedAfterResolve != null,
                                notificationPreference = matchedAfterResolve?.notificationPreference
                                    ?: ChannelNotificationPreference.NONE,
                                errorMessage = null,
                                errorKind = null,
                                hasVideoEnded = false
                            )
                        }
                        onActiveVideoArtworkChanged(details.video)
                        playbackController.prepareVideo(
                            details = details,
                            videoStream = chosenVideo,
                            audioStream = chosenAudio,
                            subtitle = null,
                            startPositionMs = safeStartMs,
                            speed = _playerState.value.playbackSpeed,
                            playWhenReady = true,
                            preferAdaptiveManifest = useAdaptiveManifest,
                            adaptiveMaxHeightPx = if (adaptiveDecision.isAutoMode) {
                                maxOf(adaptiveDecision.targetMaxHeightPx, chosenVideo?.heightPx ?: deviceMaxHeight)
                            } else {
                                maxOf(chosenVideo?.heightPx ?: 2160, 2160)
                            },
                            extractionDurationMs = extractionDurationMs
                        )
                        startPositionTicker()
                        refreshProviderAccountStatus(details.video)
                        scheduleUpcomingVideoPreload(details.video, enrichedQueue)
                    }
                    is OnlineResult.Error -> {
                        if (token != activeResolveToken) return@launch
                        _playerState.update {
                            it.copy(
                                isResolving = false,
                                isBuffering = false,
                                isPlaying = false,
                                errorMessage = res.message,
                                errorKind = res.kind
                            )
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            }
        }
    }

    /**
     * Plays a locally downloaded offline video file directly in SONORA's Media3 VideoPlayer.
     */
    fun playDownloadedLocalVideo(track: DownloadedTrack, expandPlayer: Boolean = true) {
        onPauseMusicForVideo()
        resolveJob?.cancel()
        activeResolveToken++

        val offlineItem = OnlineVideoItem(
            videoId = "offline_video_${track.id}",
            contentUrl = track.localFileUri,
            title = track.title,
            channelName = track.artist.ifBlank { "Downloaded Video" },
            durationSeconds = track.durationMs / 1000L,
            thumbnailUrl = track.localArtworkPath ?: track.artworkUrl,
            providerKey = "offline_download",
            providerName = "Offline Download"
        )

        _playerState.update {
            it.copy(
                activeVideo = offlineItem,
                resolvedDetails = null,
                isResolving = false,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                isPlayerExpanded = expandPlayer,
                isAutoQualityMode = false,
                qualityStatusLabel = track.resolutionLabel?.takeIf { r -> r.isNotBlank() } ?: "Offline Video",
                queue = listOf(offlineItem),
                queueIndex = 0,
                errorMessage = null,
                errorKind = null,
                activeSubtitleCues = emptyList(),
                hasVideoEnded = false,
                isLocalOfflineVideo = true
            )
        }
        onActiveVideoArtworkChanged(offlineItem)
        playbackController.prepareLocalVideo(
            videoId = offlineItem.videoId,
            localUri = track.localFileUri,
            startPositionMs = 0L,
            speed = _playerState.value.playbackSpeed,
            playWhenReady = true
        )
        startPositionTicker()
    }

    fun retryActiveVideo() {
        val active = _playerState.value.activeVideo ?: return
        repository.clearDetailsCache(active.videoId)
        playVideo(
            video = active,
            queuePool = _playerState.value.queue,
            expandPlayer = _playerState.value.isPlayerExpanded,
            startPositionMs = _playbackProgress.value.currentPositionMs.takeIf { it > 0L }
                ?: _playerState.value.currentPositionMs,
            forceRefreshStream = true
        )
    }

    fun toggleVideoPlayPause() {
        if (_playerState.value.isPlaying) {
            playbackController.pause()
            persistCurrentWatchPosition()
        } else {
            onPauseMusicForVideo()
            playbackController.play()
        }
    }

    fun pauseForAudioHandoff() {
        if (_playerState.value.isPlaying || playbackController.isPlaying) {
            playbackController.pause()
            persistCurrentWatchPosition()
        }
    }

    fun seekVideoTo(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0L)
        playbackController.seekTo(safe)
        _playbackProgress.update { it.copy(currentPositionMs = safe) }
    }

    fun seekVideoBy(deltaMs: Long) {
        playbackController.seekBy(deltaMs)
    }

    fun setVideoPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 2.0f)
        _playerState.update { it.copy(playbackSpeed = safe) }
        playbackController.setPlaybackSpeed(safe)
    }

    fun selectAutoVideoQuality() {
        val details = _playerState.value.resolvedDetails ?: return
        val resumePos = playbackController.currentPositionMs().takeIf { it > 0L } ?: _playerState.value.currentPositionMs
        val wasPlaying = _playerState.value.isPlaying
        val autoSettings = _playerState.value.videoExperienceSettings.copy(autoVideoQualityEnabled = true)
        val deviceMaxHeight = playbackController.detectDeviceMaxVideoHeightPx()
        val decision = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = details.videoStreams,
            settings = autoSettings,
            isMeteredMobile = isMeteredNetwork(),
            estimatedBandwidthBps = playbackController.estimatedBandwidthBps(),
            bufferedAheadMs = (_playerState.value.bufferedPositionMs - resumePos).coerceAtLeast(0L),
            rebufferCount = playbackController.rebufferCount,
            deviceMaxHeightPx = deviceMaxHeight,
            isInitialStartup = true
        )
        val chosenVideo = decision.selectedStream ?: _playerState.value.selectedVideoStream
        val useAdaptiveManifest = (details.video.isLive || chosenVideo == null) &&
            (!details.hlsUrl.isNullOrBlank() || !details.dashMpdUrl.isNullOrBlank())
        _playerState.update {
            it.copy(
                selectedVideoStream = chosenVideo,
                isAutoQualityMode = true,
                qualityStatusLabel = decision.reasonLabel,
                isUsingAdaptiveManifest = useAdaptiveManifest,
                videoExperienceSettings = autoSettings,
                errorMessage = null,
                errorKind = null
            )
        }
        scope.launch { experiencePreferences?.setAutoVideoQualityEnabled(true) }
        playbackController.prepareVideo(
            details = details,
            videoStream = chosenVideo,
            audioStream = _playerState.value.selectedAudioStream,
            subtitle = _playerState.value.selectedSubtitle,
            startPositionMs = resumePos,
            speed = _playerState.value.playbackSpeed,
            playWhenReady = wasPlaying,
            preferAdaptiveManifest = useAdaptiveManifest,
            adaptiveMaxHeightPx = maxOf(decision.targetMaxHeightPx, chosenVideo?.heightPx ?: deviceMaxHeight)
        )
    }

    fun selectVideoStream(option: VideoStreamOption) {
        val details = _playerState.value.resolvedDetails ?: return
        val resumePos = playbackController.currentPositionMs().takeIf { it > 0L } ?: _playerState.value.currentPositionMs
        val wasPlaying = _playerState.value.isPlaying
        val manualLabel = "Manual • ${VideoAdaptiveQualitySelector.formatResolutionLabel(option.heightPx, option.resolutionLabel)}"
        _playerState.update {
            it.copy(
                selectedVideoStream = option,
                isAutoQualityMode = false,
                qualityStatusLabel = manualLabel,
                isUsingAdaptiveManifest = false,
                errorMessage = null,
                errorKind = null
            )
        }
        if (option.heightPx > 0) {
            scope.launch {
                accountPreferences.setPreferredMaxHeight(option.heightPx)
                experiencePreferences?.setAutoVideoQualityEnabled(false)
                experiencePreferences?.setLastSelectedVideoQualityHeight(option.heightPx)
            }
        }
        playbackController.prepareVideo(
            details = details,
            videoStream = option,
            audioStream = _playerState.value.selectedAudioStream,
            subtitle = _playerState.value.selectedSubtitle,
            startPositionMs = resumePos,
            speed = _playerState.value.playbackSpeed,
            playWhenReady = wasPlaying,
            preferAdaptiveManifest = false,
            adaptiveMaxHeightPx = maxOf(option.heightPx, 2160)
        )
    }

    fun selectVideoAudioTrack(audioOption: VideoAudioOption) {
        val details = _playerState.value.resolvedDetails ?: return
        val resumePos = playbackController.currentPositionMs().takeIf { it > 0L } ?: _playerState.value.currentPositionMs
        val wasPlaying = _playerState.value.isPlaying
        // Audio track selection requires an adaptive video-only stream if currently muxed
        val currentVideo = _playerState.value.selectedVideoStream
        val compatibleVideo = if (currentVideo?.isVideoOnly == true) {
            currentVideo
        } else {
            details.videoStreams.firstOrNull { it.isVideoOnly && it.heightPx == (currentVideo?.heightPx ?: 720) }
                ?: details.videoStreams.firstOrNull { it.isVideoOnly }
                ?: currentVideo
        }
        _playerState.update { it.copy(selectedVideoStream = compatibleVideo, selectedAudioStream = audioOption) }
        playbackController.prepareVideo(
            details = details,
            videoStream = compatibleVideo,
            audioStream = audioOption,
            subtitle = _playerState.value.selectedSubtitle,
            startPositionMs = resumePos,
            speed = _playerState.value.playbackSpeed,
            playWhenReady = wasPlaying
        )
    }

    fun selectVideoSubtitle(subtitle: VideoSubtitleOption?) {
        val details = _playerState.value.resolvedDetails ?: return
        val resumePos = playbackController.currentPositionMs().takeIf { it > 0L } ?: _playerState.value.currentPositionMs
        val wasPlaying = _playerState.value.isPlaying
        _playerState.update { it.copy(selectedSubtitle = subtitle, activeSubtitleCues = emptyList()) }
        playbackController.prepareVideo(
            details = details,
            videoStream = _playerState.value.selectedVideoStream,
            audioStream = _playerState.value.selectedAudioStream,
            subtitle = subtitle,
            startPositionMs = resumePos,
            speed = _playerState.value.playbackSpeed,
            playWhenReady = wasPlaying
        )
    }

    fun playNextInQueue() {
        val state = _playerState.value
        if (!state.hasNextInQueue) return
        val next = state.queue.getOrNull(state.queueIndex + 1) ?: return
        playVideo(next, state.queue, expandPlayer = state.isPlayerExpanded, startPositionMs = 0L)
    }

    fun playPreviousInQueue() {
        val state = _playerState.value
        val curPos = _playbackProgress.value.currentPositionMs.takeIf { it > 0L } ?: state.currentPositionMs
        if (curPos > 4_000L) {
            seekVideoTo(0L)
            return
        }
        if (!state.hasPreviousInQueue) return
        val prev = state.queue.getOrNull(state.queueIndex - 1) ?: return
        playVideo(prev, state.queue, expandPlayer = state.isPlayerExpanded, startPositionMs = 0L)
    }

    fun enqueueVideoNext(video: OnlineVideoItem) {
        _playerState.update { state ->
            val mutable = state.queue.filterNot { it.videoId == video.videoId }.toMutableList()
            val insertAt = (state.queueIndex + 1).coerceIn(0, mutable.size)
            mutable.add(insertAt, video)
            state.copy(queue = mutable)
        }
        _feedbackMessage.value = "Queued next: ${video.title}"
    }

    fun addVideoToQueue(video: OnlineVideoItem) {
        val currentState = _playerState.value
        if (!currentState.hasActiveVideo) {
            playVideo(video, listOf(video), expandPlayer = false)
            _feedbackMessage.value = "Added to video queue: ${video.title}"
            return
        }
        _playerState.update { state ->
            val mutable = state.queue.filterNot { it.videoId == video.videoId }.toMutableList()
            mutable.add(video)
            val newIdx = state.activeVideo?.let { active ->
                mutable.indexOfFirst { it.videoId == active.videoId }
            } ?: state.queueIndex
            state.copy(queue = mutable, queueIndex = newIdx)
        }
        _feedbackMessage.value = "Added to video queue: ${video.title}"
    }

    fun playAllLikedVideos(videos: List<OnlineVideoItem>) {
        if (videos.isEmpty()) return
        playVideo(
            video = videos.first(),
            queuePool = videos,
            expandPlayer = true,
            startPositionMs = 0L
        )
    }

    fun removeVideoFromQueue(videoId: String) {
        _playerState.update { state ->
            val filtered = state.queue.filterNot { it.videoId == videoId }
            val newIdx = state.activeVideo?.let { active ->
                filtered.indexOfFirst { it.videoId == active.videoId }
            } ?: -1
            state.copy(queue = filtered, queueIndex = newIdx)
        }
    }

    fun expandVideoPlayer() {
        if (_playerState.value.hasActiveVideo) {
            _playerState.update { it.copy(isPlayerExpanded = true) }
        }
    }

    fun minimizeVideoPlayer() {
        persistCurrentWatchPosition()
        _playerState.update { it.copy(isPlayerExpanded = false, isFullScreenLandscape = false) }
    }

    fun setFullScreenLandscape(enabled: Boolean) {
        if (_playerState.value.isFullScreenLandscape == enabled) return
        _playerState.update { it.copy(isFullScreenLandscape = enabled) }
    }

    /**
     * Changes the video player's aspect ratio live without interrupting or restarting Media3 playback.
     */
    fun setVideoAspectRatioMode(mode: VideoAspectRatioMode, persistPreference: Boolean = true) {
        _playerState.update { state ->
            state.copy(
                aspectRatioMode = mode,
                gestureSettings = state.gestureSettings.copy(defaultAspectRatio = mode)
            )
        }
        if (persistPreference) {
            scope.launch { accountPreferences.setAspectRatioMode(mode) }
        }
    }

    fun setSwipeFullscreenEnabled(enabled: Boolean) {
        _playerState.update { it.copy(gestureSettings = it.gestureSettings.copy(swipeFullscreenEnabled = enabled)) }
        scope.launch { accountPreferences.setSwipeFullscreenEnabled(enabled) }
    }

    fun setAutoAspectRatioEnabled(enabled: Boolean) {
        _playerState.update { it.copy(gestureSettings = it.gestureSettings.copy(autoAspectRatioEnabled = enabled)) }
        scope.launch { accountPreferences.setAutoAspectRatioEnabled(enabled) }
    }

    fun setDefaultAspectRatio(mode: VideoAspectRatioMode) {
        setVideoAspectRatioMode(mode, persistPreference = true)
    }

    fun setSwipeSensitivity(sensitivity: VideoSwipeSensitivity) {
        _playerState.update { it.copy(gestureSettings = it.gestureSettings.copy(swipeSensitivity = sensitivity)) }
        scope.launch { accountPreferences.setSwipeSensitivity(sensitivity) }
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 30)
        _playerState.update { it.copy(gestureSettings = it.gestureSettings.copy(doubleTapSeekSeconds = clamped)) }
        scope.launch { accountPreferences.setDoubleTapSeekSeconds(clamped) }
    }

    fun setAutoOrientationEnabled(enabled: Boolean) {
        _playerState.update { it.copy(gestureSettings = it.gestureSettings.copy(autoOrientationEnabled = enabled)) }
        scope.launch { accountPreferences.setAutoOrientationEnabled(enabled) }
    }

    fun resetVideoGestureSettings() {
        val defaults = VideoPlayerGestureSettings()
        _playerState.update {
            it.copy(
                aspectRatioMode = defaults.defaultAspectRatio,
                gestureSettings = defaults
            )
        }
        scope.launch { accountPreferences.resetPlayerGestureSettings() }
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        _playerState.update { it.copy(isInPictureInPicture = inPip) }
    }

    /**
     * Policy-compliant lifecycle handling: pauses video when backgrounded unless PiP is active.
     */
    fun onAppBackgrounded() {
        val state = _playerState.value
        if (!state.isInPictureInPicture && !state.backgroundPlaybackAllowed && state.isPlaying) {
            playbackController.pause()
        }
    }

    fun closeVideoPlayer() {
        activeResolveToken++
        resolveJob?.cancel()
        videoPreloadJob?.cancel()
        positionTickerJob?.cancel()
        persistCurrentWatchPosition()
        playbackController.stop()
        cacheManager.onVideoPlayerStateChanged(isOpen = false)
        val currentGestures = gestureSettings.value
        _playerState.value = VideoPlayerUiState(
            aspectRatioMode = currentGestures.defaultAspectRatio,
            gestureSettings = currentGestures,
            supportsNotificationApi = repository.supportsNotificationPreferenceApi,
            backgroundPlaybackAllowed = false
        )
        _playbackProgress.value = VideoPlaybackProgress()
        onActiveVideoArtworkChanged(null)
    }

    fun attachVideoSurfaceView(surfaceView: SurfaceView) = playbackController.attachSurfaceView(surfaceView)
    fun detachVideoSurfaceView(surfaceView: SurfaceView) = playbackController.detachSurfaceView(surfaceView)
    fun attachVideoSurface(textureView: TextureView) = playbackController.attachTextureView(textureView)
    fun detachVideoSurface(textureView: TextureView) = playbackController.detachTextureView(textureView)

    // --- Channel Detail Browsing (Full Page, Tabs, Pagination, Playlists, Quick Actions) ---

    fun openChannelProfile(
        channelUrl: String,
        channelName: String,
        initialAvatarUrl: String? = null,
        forceRefresh: Boolean = false
    ) {
        val cleanUrl = channelUrl.trim()
        if (cleanUrl.isBlank()) {
            _feedbackMessage.value = "Channel link is unavailable for $channelName"
            return
        }
        // Preserve active video playback seamlessly: minimize expanded player to floating mini-player instead of stopping ExoPlayer.
        if (_playerState.value.isPlayerExpanded) {
            minimizeVideoPlayer()
        }

        channelJob?.cancel()
        channelTabJobs.values.forEach { it.cancel() }
        channelTabJobs.clear()
        channelTabLoadMoreJobs.values.forEach { it.cancel() }
        channelTabLoadMoreJobs.clear()
        _channelPlaylistState.value = ChannelPlaylistDetailState.Idle

        channelJob = scope.launch {
            _channelState.value = VideoChannelUiState.Loading(
                name = channelName.ifBlank { "Channel" },
                channelUrl = cleanUrl,
                avatarUrl = initialAvatarUrl
            )
            when (val res = repository.fetchChannelProfileDetailed(cleanUrl, channelName, forceRefresh)) {
                is OnlineResult.Success -> {
                    val result = res.value
                    val savedChannel = subscribedChannels.value.firstOrNull {
                        it.channelId == result.channel.channelId ||
                            ChannelDetailUtils.canonicalChannelKey(it.channelUrl, it.channelId) ==
                            ChannelDetailUtils.canonicalChannelKey(result.channel.channelUrl, result.channel.channelId)
                    }
                    val hydratedChannel = result.channel.copy(
                        isSubscribedLocal = savedChannel != null,
                        isSubscribedOnProvider = savedChannel?.isSubscribedOnProvider == true,
                        notificationPreference = savedChannel?.notificationPreference
                            ?: ChannelNotificationPreference.NONE
                    )
                    val initialTabStates = mutableMapOf<ChannelTabType, ChannelTabPageState>()
                    hydratedChannel.availableTabs.forEach { tab ->
                        initialTabStates[tab] = when {
                            tab == ChannelTabType.ABOUT -> ChannelTabPageState.Success()
                            tab == result.initialTab && result.initialTabPage != null -> ChannelTabPageState.Success(
                                videos = result.initialTabPage.videos,
                                playlists = result.initialTabPage.playlists,
                                nextPageToken = result.initialTabPage.nextPageToken
                            )
                            else -> ChannelTabPageState.Idle
                        }
                    }
                    _channelState.value = VideoChannelUiState.Success(
                        channel = hydratedChannel,
                        selectedTab = result.initialTab,
                        availableTabs = hydratedChannel.availableTabs,
                        tabStates = initialTabStates
                    )
                }
                is OnlineResult.Error -> {
                    _channelState.value = VideoChannelUiState.Error(
                        message = res.message,
                        channelUrl = cleanUrl,
                        channelName = channelName.ifBlank { "Channel" },
                        kind = res.kind
                    )
                }
            }
        }
    }

    fun refreshChannelProfile() {
        when (val cur = _channelState.value) {
            is VideoChannelUiState.Success -> {
                repository.clearChannelCache(cur.channel.channelUrl)
                openChannelProfile(
                    channelUrl = cur.channel.channelUrl,
                    channelName = cur.channel.name,
                    initialAvatarUrl = cur.channel.avatarUrl,
                    forceRefresh = true
                )
            }
            is VideoChannelUiState.Error -> {
                openChannelProfile(cur.channelUrl, cur.channelName, forceRefresh = true)
            }
            else -> Unit
        }
    }

    fun selectChannelTab(tab: ChannelTabType) {
        val current = _channelState.value as? VideoChannelUiState.Success ?: return
        if (tab !in current.availableTabs) return
        _channelState.value = current.copy(selectedTab = tab)

        if (tab == ChannelTabType.ABOUT) return
        val existingState = current.tabStates[tab]
        if (existingState == null || existingState is ChannelTabPageState.Idle || existingState is ChannelTabPageState.Error) {
            loadChannelTab(tab, forceRefresh = false)
        }
    }

    fun retryChannelTab(tab: ChannelTabType) {
        loadChannelTab(tab, forceRefresh = true)
    }

    private fun loadChannelTab(tab: ChannelTabType, forceRefresh: Boolean) {
        val current = _channelState.value as? VideoChannelUiState.Success ?: return
        if (tab == ChannelTabType.ABOUT) return
        val channel = current.channel
        channelTabJobs[tab]?.cancel()
        channelTabJobs[tab] = scope.launch {
            updateChannelTabState(channel.channelId, tab, ChannelTabPageState.Loading)
            when (val res = repository.fetchChannelTab(channel.channelUrl, channel.channelId, tab, forceRefresh)) {
                is OnlineResult.Success -> {
                    val page = res.value
                    val successState = ChannelTabPageState.Success(
                        videos = page.videos,
                        playlists = page.playlists,
                        nextPageToken = page.nextPageToken,
                        isLoadingMore = false
                    )
                    updateChannelTabState(channel.channelId, tab, successState)
                }
                is OnlineResult.Error -> {
                    updateChannelTabState(
                        channel.channelId,
                        tab,
                        ChannelTabPageState.Error(
                            kind = res.kind,
                            message = res.message,
                            isRetryable = res.isRetryable
                        )
                    )
                }
            }
        }
    }

    fun loadMoreChannelTab(tab: ChannelTabType) {
        val current = _channelState.value as? VideoChannelUiState.Success ?: return
        val tabState = current.tabStates[tab] as? ChannelTabPageState.Success ?: return
        val token = tabState.nextPageToken ?: return
        if (tabState.isLoadingMore) return
        if (channelTabLoadMoreJobs[tab]?.isActive == true) return

        val channelId = current.channel.channelId
        updateChannelTabState(channelId, tab, tabState.copy(isLoadingMore = true))

        channelTabLoadMoreJobs[tab] = scope.launch {
            when (val res = repository.loadMoreChannelTab(token)) {
                is OnlineResult.Success -> {
                    val latest = (_channelState.value as? VideoChannelUiState.Success)
                        ?.takeIf { it.channel.channelId == channelId }
                        ?.tabStates?.get(tab) as? ChannelTabPageState.Success
                        ?: return@launch
                    val mergedVideos = ChannelDetailUtils.mergeVideosUnique(latest.videos, res.value.videos)
                    val mergedPlaylists = ChannelDetailUtils.mergePlaylistsUnique(latest.playlists, res.value.playlists)
                    updateChannelTabState(
                        channelId,
                        tab,
                        latest.copy(
                            videos = mergedVideos,
                            playlists = mergedPlaylists,
                            nextPageToken = res.value.nextPageToken,
                            isLoadingMore = false
                        )
                    )
                }
                is OnlineResult.Error -> {
                    val latest = (_channelState.value as? VideoChannelUiState.Success)
                        ?.takeIf { it.channel.channelId == channelId }
                        ?.tabStates?.get(tab) as? ChannelTabPageState.Success
                        ?: return@launch
                    updateChannelTabState(channelId, tab, latest.copy(isLoadingMore = false))
                    _feedbackMessage.value = res.message
                }
            }
        }
    }

    private fun updateChannelTabState(
        channelId: String,
        tab: ChannelTabType,
        newState: ChannelTabPageState
    ) {
        _channelState.update { current ->
            if (current !is VideoChannelUiState.Success || current.channel.channelId != channelId) {
                current
            } else {
                val updatedMap = current.tabStates.toMutableMap().apply { put(tab, newState) }
                val updatedChannel = if (tab == ChannelTabType.VIDEOS && newState is ChannelTabPageState.Success) {
                    current.channel.copy(videos = newState.videos)
                } else {
                    current.channel
                }
                current.copy(channel = updatedChannel, tabStates = updatedMap)
            }
        }
    }

    fun playAllChannelVideos(videos: List<OnlineVideoItem>) {
        val playable = videos.filter { it.contentUrl.isNotBlank() }
        if (playable.isEmpty()) {
            _feedbackMessage.value = "No videos available to play on this channel"
            return
        }
        playVideo(playable.first(), queuePool = playable, expandPlayer = true)
    }

    fun playChannelVideosInPopup(videos: List<OnlineVideoItem>) {
        val playable = videos.filter { it.contentUrl.isNotBlank() }
        if (playable.isEmpty()) {
            _feedbackMessage.value = "No videos available for Popup Player"
            return
        }
        playVideo(playable.first(), queuePool = playable, expandPlayer = false)
        _feedbackMessage.value = "Playing in floating Popup Player"
    }

    fun playChannelVideosInBackground(videos: List<OnlineVideoItem>, channelName: String? = null) {
        val playable = videos.filter { it.contentUrl.isNotBlank() && !it.isLive }
            .ifEmpty { videos.filter { it.contentUrl.isNotBlank() } }
        if (playable.isEmpty()) {
            _feedbackMessage.value = "No audio-playable videos found on this channel"
            return
        }
        if (_playerState.value.isPlaying) {
            playbackController.pause()
        }
        onPlayChannelAudioInBackground(playable)
        val label = channelName?.takeIf { it.isNotBlank() } ?: playable.first().channelName
        _feedbackMessage.value = "Playing $label audio in background"
    }

    fun openChannelPlaylistDetail(playlist: OnlineVideoPlaylistSummary) {
        val url = playlist.externalUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            if (playlist.videos.isNotEmpty()) {
                _channelPlaylistState.value = ChannelPlaylistDetailState.Success(playlist = playlist)
            } else {
                _feedbackMessage.value = "This playlist link is unavailable"
            }
            return
        }
        channelPlaylistJob?.cancel()
        channelPlaylistJob = scope.launch {
            _channelPlaylistState.value = ChannelPlaylistDetailState.Loading(
                title = playlist.name,
                thumbnailUrl = playlist.displayThumbnailUrl
            )
            when (val res = repository.fetchExternalPlaylistDetailed(url, playlist.name)) {
                is OnlineResult.Success -> {
                    _channelPlaylistState.value = ChannelPlaylistDetailState.Success(
                        playlist = res.value.playlist,
                        nextPageToken = res.value.nextPageToken
                    )
                }
                is OnlineResult.Error -> {
                    _channelPlaylistState.value = ChannelPlaylistDetailState.Error(
                        message = res.message,
                        playlistUrl = url,
                        title = playlist.name
                    )
                }
            }
        }
    }

    fun loadMoreChannelPlaylist() {
        val current = _channelPlaylistState.value as? ChannelPlaylistDetailState.Success ?: return
        val token = current.nextPageToken ?: return
        if (current.isLoadingMore) return
        _channelPlaylistState.value = current.copy(isLoadingMore = true)
        scope.launch {
            when (val res = repository.loadMoreExternalPlaylist(token)) {
                is OnlineResult.Success -> {
                    val latest = _channelPlaylistState.value as? ChannelPlaylistDetailState.Success ?: return@launch
                    val merged = ChannelDetailUtils.mergeVideosUnique(latest.playlist.videos, res.value.first)
                    _channelPlaylistState.value = latest.copy(
                        playlist = latest.playlist.copy(videos = merged),
                        nextPageToken = res.value.second,
                        isLoadingMore = false
                    )
                }
                is OnlineResult.Error -> {
                    val latest = _channelPlaylistState.value as? ChannelPlaylistDetailState.Success ?: return@launch
                    _channelPlaylistState.value = latest.copy(isLoadingMore = false)
                    _feedbackMessage.value = res.message
                }
            }
        }
    }

    fun dismissChannelPlaylistDetail() {
        channelPlaylistJob?.cancel()
        _channelPlaylistState.value = ChannelPlaylistDetailState.Idle
    }

    fun dismissChannelProfile() {
        channelJob?.cancel()
        channelTabJobs.values.forEach { it.cancel() }
        channelTabJobs.clear()
        channelTabLoadMoreJobs.values.forEach { it.cancel() }
        channelTabLoadMoreJobs.clear()
        channelPlaylistJob?.cancel()
        _channelPlaylistState.value = ChannelPlaylistDetailState.Idle
        _channelState.value = VideoChannelUiState.Idle
    }

    // --- Deep Link from SONORA Channel Upload Notification ---

    fun openVideoFromNotification(
        videoId: String,
        contentUrl: String,
        title: String,
        channelName: String,
        channelUrl: String? = null,
        thumbnailUrl: String? = null
    ) {
        val item = OnlineVideoItem(
            videoId = videoId,
            contentUrl = contentUrl,
            title = title,
            channelName = channelName,
            channelUrl = channelUrl,
            thumbnailUrl = thumbnailUrl
        )
        scope.launch(Dispatchers.IO) {
            repository.markVideoNotificationRead("video:$videoId")
        }
        playVideo(video = item, expandPlayer = true, startPositionMs = 0L)
    }

    // --- SONORA Global Online Video Notification Center ---

    fun openNotificationItem(item: VideoNotificationItem) {
        markVideoNotificationRead(item.notificationId)
        if (item.isVideoNotification && !item.videoId.isNullOrBlank() && !item.videoUrl.isNullOrBlank()) {
            openVideoFromNotification(
                videoId = item.videoId,
                contentUrl = item.videoUrl,
                title = item.videoTitle ?: item.message,
                channelName = item.channelName,
                channelUrl = item.channelUrl,
                thumbnailUrl = item.thumbnailUrl
            )
        } else if (item.channelUrl.isNotBlank()) {
            openChannelProfile(
                channelUrl = item.channelUrl,
                channelName = item.channelName,
                initialAvatarUrl = item.channelAvatarUrl
            )
        }
    }

    fun openNotificationChannel(item: VideoNotificationItem) {
        markVideoNotificationRead(item.notificationId)
        if (item.channelUrl.isNotBlank()) {
            openChannelProfile(
                channelUrl = item.channelUrl,
                channelName = item.channelName,
                initialAvatarUrl = item.channelAvatarUrl
            )
        }
    }

    fun markVideoNotificationRead(notificationId: String) {
        scope.launch(Dispatchers.IO) {
            repository.markVideoNotificationRead(notificationId)
        }
    }

    fun markAllVideoNotificationsRead() {
        scope.launch(Dispatchers.IO) {
            repository.markAllVideoNotificationsRead()
        }
    }

    fun deleteVideoNotification(notificationId: String) {
        scope.launch(Dispatchers.IO) {
            repository.deleteVideoNotification(notificationId)
        }
    }

    fun clearAllVideoNotifications() {
        scope.launch(Dispatchers.IO) {
            repository.clearAllVideoNotifications()
            _feedbackMessage.value = "Cleared all video notifications"
        }
    }

    fun syncFollowedChannelNotificationsNow(userInitiated: Boolean = true) {
        if (_isSyncingNotifications.value) return
        val now = SystemClock.elapsedRealtime()
        if (!userInitiated && now - lastNotificationAutoSyncMs < 30L * 60_000L) return
        lastNotificationAutoSyncMs = now
        scope.launch(Dispatchers.IO) {
            _isSyncingNotifications.value = true
            try {
                val res = repository.syncFollowedChannelNotificationsNow(forceRefresh = userInitiated)
                if (userInitiated) {
                    _feedbackMessage.value = when {
                        res.enabledChannelCount == 0 -> "Turn on the bell on followed channels to check for new uploads"
                        res.newNotifications.isNotEmpty() -> "Found ${res.newNotifications.size} new upload${if (res.newNotifications.size == 1) "" else "s"}"
                        else -> "You're all caught up"
                    }
                }
            } catch (_: Throwable) {
                if (userInitiated) {
                    _feedbackMessage.value = "Could not check channel uploads right now"
                }
            } finally {
                _isSyncingNotifications.value = false
            }
        }
    }

    // --- SONORA Local Interactions (Like, Dislike, Subscribe, Notification Bell) ---

    fun onToggleLikeVideo() {
        val video = _playerState.value.activeVideo ?: return
        val nextRating = if (_playerState.value.ratingState == VideoRatingState.LIKED) {
            VideoRatingState.NONE
        } else {
            VideoRatingState.LIKED
        }
        applySonoraLocalRating(video, nextRating)
    }

    fun onToggleDislikeVideo() {
        val video = _playerState.value.activeVideo ?: return
        val nextRating = if (_playerState.value.ratingState == VideoRatingState.DISLIKED) {
            VideoRatingState.NONE
        } else {
            VideoRatingState.DISLIKED
        }
        applySonoraLocalRating(video, nextRating)
    }

    private fun applySonoraLocalRating(video: OnlineVideoItem, target: VideoRatingState) {
        _playerState.update { state ->
            if (state.activeVideo?.videoId == video.videoId) state.copy(ratingState = target) else state
        }
        scope.launch(Dispatchers.IO) {
            repository.setLocalVideoRating(video, target)
        }
    }

    fun unlikeVideo(videoId: String) {
        _playerState.update { state ->
            if (state.activeVideo?.videoId == videoId && state.ratingState == VideoRatingState.LIKED) {
                state.copy(ratingState = VideoRatingState.NONE)
            } else {
                state
            }
        }
        scope.launch(Dispatchers.IO) {
            repository.unlikeVideo(videoId)
        }
        _feedbackMessage.value = "Removed from Liked Videos"
    }

    fun clearAllLikedVideos() {
        _playerState.update { state ->
            if (state.ratingState == VideoRatingState.LIKED) {
                state.copy(ratingState = VideoRatingState.NONE)
            } else {
                state
            }
        }
        scope.launch(Dispatchers.IO) {
            repository.clearAllLikedVideos()
        }
        _feedbackMessage.value = "Cleared all liked videos"
    }

    /**
     * SONORA local Subscribe / Unsubscribe for channels.
     * Persists in Room under Online Library → Followed Channels and never falsely claims
     * to subscribe on the external provider.
     */
    fun toggleSonoraChannelSubscribe(channel: OnlineVideoChannel) {
        val existing = findMatchingSubscribedChannel(
            channels = subscribedChannels.value,
            channelUrl = channel.channelUrl,
            channelName = channel.name,
            channelId = channel.channelId
        )
        val subscribing = existing == null
        val targetChannelId = existing?.channelId ?: channel.channelId

        // Optimistic immediate UI update for crisp responsiveness
        _playerState.update { state ->
            val active = state.activeVideo
            if (active != null && isSameChannel(active.channelUrl, active.channelName, channel.channelUrl, channel.name, channel.channelId)) {
                state.copy(
                    isSubscribedLocally = subscribing,
                    notificationPreference = if (subscribing) {
                        existing?.notificationPreference ?: ChannelNotificationPreference.NONE
                    } else {
                        ChannelNotificationPreference.NONE
                    }
                )
            } else {
                state
            }
        }

        scope.launch(Dispatchers.IO) {
            if (subscribing) {
                val activeVidId = _playerState.value.activeVideo
                    ?.takeIf { isSameChannel(it.channelUrl, it.channelName, channel.channelUrl, channel.name, channel.channelId) }
                    ?.videoId
                repository.saveLocalChannelBookmark(
                    channel = channel.copy(notificationPreference = ChannelNotificationPreference.NONE),
                    providerSynced = false,
                    initialLastKnownVideoId = activeVidId
                )
                _feedbackMessage.value = "Subscribed to ${channel.name} in ${domain.branding.AppBranding.APP_NAME}"
                // Seed channel baseline upload ID so enabling notifications later only alerts on genuinely new uploads
                if (channel.channelUrl.isNotBlank()) {
                    runCatching {
                        repository.seedChannelBaselineUploadIfMissing(
                            channelId = channel.channelId,
                            channelUrl = channel.channelUrl,
                            fallbackName = channel.name
                        )
                    }
                }
            } else {
                repository.removeLocalChannelBookmark(targetChannelId)
                _feedbackMessage.value = "Unsubscribed from ${channel.name} in ${domain.branding.AppBranding.APP_NAME}"
            }
        }
    }

    /**
     * Updates SONORA's local per-channel upload notification preference (All Notifications vs Off)
     * and schedules periodic background WorkManager checks when enabled.
     */
    fun setSonoraChannelNotificationPreference(
        channel: OnlineVideoChannel,
        preference: ChannelNotificationPreference
    ) {
        val existing = findMatchingSubscribedChannel(
            channels = subscribedChannels.value,
            channelUrl = channel.channelUrl,
            channelName = channel.name,
            channelId = channel.channelId
        )
        if (existing == null) {
            _feedbackMessage.value = "Subscribe to ${channel.name} first to manage notifications"
            return
        }

        val normalizedPref = if (preference == ChannelNotificationPreference.ALL) {
            ChannelNotificationPreference.ALL
        } else {
            ChannelNotificationPreference.NONE
        }

        _playerState.update { state ->
            val active = state.activeVideo
            if (active != null && isSameChannel(active.channelUrl, active.channelName, channel.channelUrl, channel.name, channel.channelId)) {
                state.copy(notificationPreference = normalizedPref)
            } else {
                state
            }
        }

        scope.launch(Dispatchers.IO) {
            repository.updateLocalChannelNotificationPreference(existing.channelId, normalizedPref)
            if (normalizedPref == ChannelNotificationPreference.ALL) {
                if (existing.channelUrl.isNotBlank()) {
                    runCatching {
                        repository.seedChannelBaselineUploadIfMissing(
                            channelId = existing.channelId,
                            channelUrl = existing.channelUrl,
                            fallbackName = existing.name
                        )
                    }
                }
                data.video.ChannelUploadNotificationScheduler.schedulePeriodicChecks(appContext)
                _feedbackMessage.value = "Notifications enabled for ${channel.name} (periodic checks)"
            } else {
                _feedbackMessage.value = "Notifications turned off for ${channel.name}"
            }
        }
    }

    fun onToggleSubscribeChannel(channel: OnlineVideoChannel) {
        toggleSonoraChannelSubscribe(channel)
    }

    fun toggleFollowChannelLocally(channel: OnlineVideoChannel) {
        toggleSonoraChannelSubscribe(channel)
    }

    fun bookmarkChannelLocally(channel: OnlineVideoChannel) = toggleSonoraChannelSubscribe(channel)

    fun onSelectNotificationPreference(channel: OnlineVideoChannel, preference: ChannelNotificationPreference) {
        setSonoraChannelNotificationPreference(channel, preference)
    }

    private fun findMatchingSubscribedChannel(
        channels: List<OnlineVideoChannel>,
        channelUrl: String?,
        channelName: String?,
        channelId: String? = null
    ): OnlineVideoChannel? {
        if (channels.isEmpty()) return null
        val targetCanonical = if (!channelUrl.isNullOrBlank() || !channelId.isNullOrBlank()) {
            ChannelDetailUtils.canonicalChannelKey(channelUrl.orEmpty(), channelId)
        } else null
        val cleanName = channelName?.trim().orEmpty()
        return channels.firstOrNull { ch ->
            (!channelId.isNullOrBlank() && ch.channelId == channelId) ||
                (targetCanonical != null && targetCanonical.isNotBlank() &&
                    ChannelDetailUtils.canonicalChannelKey(ch.channelUrl, ch.channelId) == targetCanonical) ||
                (targetCanonical.isNullOrBlank() && cleanName.isNotBlank() &&
                    ch.name.equals(cleanName, ignoreCase = true))
        }
    }

    private fun isSameChannel(
        urlA: String?,
        nameA: String?,
        urlB: String?,
        nameB: String?,
        idB: String? = null
    ): Boolean {
        val keyA = if (!urlA.isNullOrBlank()) ChannelDetailUtils.canonicalChannelKey(urlA) else ""
        val keyB = if (!urlB.isNullOrBlank() || !idB.isNullOrBlank()) ChannelDetailUtils.canonicalChannelKey(urlB.orEmpty(), idB) else ""
        if (keyA.isNotBlank() && keyB.isNotBlank() && keyA == keyB) return true
        return !nameA.isNullOrBlank() && !nameB.isNullOrBlank() && nameA.equals(nameB, ignoreCase = true)
    }

    fun connectOAuthToken(accountLabel: String, accessToken: String, expiresInSeconds: Long, scopes: Set<String>) {
        scope.launch {
            val expiresAt = System.currentTimeMillis() + (expiresInSeconds.coerceAtLeast(300L) * 1000L)
            accountPreferences.saveOAuthSession(accountLabel, accessToken, expiresAt, scopes)
            _pendingAccountActionPrompt.value = null
            _feedbackMessage.value = "Connected YouTube OAuth session for $accountLabel"
            _playerState.value.activeVideo?.let { refreshProviderAccountStatus(it) }
        }
    }

    fun disconnectOAuthSession() {
        scope.launch {
            accountPreferences.clearOAuthSession()
            _playerState.update { it.copy(ratingState = VideoRatingState.NONE, isSubscribedOnProvider = false) }
            _feedbackMessage.value = "Signed out of YouTube account session"
        }
    }

    fun dismissAccountActionPrompt() {
        _pendingAccountActionPrompt.value = null
    }

    // --- Watch Later, Saved Videos, History, Video Playlists ---

    fun toggleWatchLater(video: OnlineVideoItem) {
        scope.launch(Dispatchers.IO) {
            val added = repository.toggleWatchLater(video)
            _feedbackMessage.value = if (added) "Added to Watch Later" else "Removed from Watch Later"
        }
    }

    fun toggleSaveVideo(video: OnlineVideoItem) {
        scope.launch(Dispatchers.IO) {
            val saved = repository.toggleSaveVideo(video)
            _feedbackMessage.value = if (saved) "Saved to Video Library" else "Removed from Saved Videos"
        }
    }

    fun removeFromVideoHistory(videoId: String) {
        scope.launch(Dispatchers.IO) {
            repository.removeFromHistory(videoId)
            _feedbackMessage.value = "Removed from Video History"
        }
    }

    fun clearVideoHistory() {
        scope.launch(Dispatchers.IO) {
            repository.clearVideoHistory()
            _feedbackMessage.value = "Video History cleared"
        }
    }

    fun createVideoPlaylist(name: String, initialVideo: OnlineVideoItem? = null) {
        scope.launch(Dispatchers.IO) {
            repository.createVideoPlaylist(name, initialVideo)
            _feedbackMessage.value = "Created playlist \"$name\""
        }
    }

    fun addVideoToPlaylist(playlistId: Long, playlistName: String, video: OnlineVideoItem) {
        scope.launch(Dispatchers.IO) {
            repository.addVideoToPlaylist(playlistId, video)
            _feedbackMessage.value = "Added to $playlistName"
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoId: String) {
        scope.launch(Dispatchers.IO) {
            repository.removeVideoFromPlaylist(playlistId, videoId)
            _feedbackMessage.value = "Removed from playlist"
        }
    }

    fun renameVideoPlaylist(playlistId: Long, newName: String) {
        scope.launch(Dispatchers.IO) {
            repository.renameVideoPlaylist(playlistId, newName)
        }
    }

    fun deleteVideoPlaylist(playlistId: Long) {
        scope.launch(Dispatchers.IO) {
            repository.deleteVideoPlaylist(playlistId)
            _feedbackMessage.value = "Deleted video playlist"
        }
    }

    fun playExternalVideoPlaylist(summary: OnlineVideoPlaylistSummary) {
        val url = summary.externalUrl ?: return
        scope.launch {
            when (val res = repository.fetchExternalPlaylist(url, summary.name)) {
                is OnlineResult.Success -> {
                    val first = res.value.videos.firstOrNull()
                    if (first != null) {
                        playVideo(first, res.value.videos, expandPlayer = true)
                    } else {
                        _feedbackMessage.value = "This playlist has no playable videos."
                    }
                }
                is OnlineResult.Error -> _feedbackMessage.value = res.message
            }
        }
    }

    // --- Local Video Recommendation Personalization & Dismissal Feedback ---

    fun markVideoNotInterested(video: OnlineVideoItem) {
        val dbDao = dao ?: return
        scope.launch(Dispatchers.IO) {
            dbDao.upsertVideoRecommendationFeedback(
                VideoRecommendationFeedbackEntity(
                    feedbackKey = "${VideoFeedbackType.NOT_INTERESTED_VIDEO}:${video.videoId}",
                    feedbackType = VideoFeedbackType.NOT_INTERESTED_VIDEO,
                    targetValue = video.videoId
                )
            )
            _feedbackMessage.value = "We'll show fewer videos like this in ${domain.branding.AppBranding.APP_NAME}"
        }
    }

    fun dontRecommendChannel(video: OnlineVideoItem) {
        val dbDao = dao ?: return
        val target = video.channelName.trim().ifBlank { video.channelUrl?.trim().orEmpty() }
        if (target.isBlank()) return
        scope.launch(Dispatchers.IO) {
            dbDao.upsertVideoRecommendationFeedback(
                VideoRecommendationFeedbackEntity(
                    feedbackKey = "${VideoFeedbackType.DONT_RECOMMEND_CHANNEL}:${target.lowercase()}",
                    feedbackType = VideoFeedbackType.DONT_RECOMMEND_CHANNEL,
                    targetValue = target.lowercase()
                )
            )
            _feedbackMessage.value = "Won't recommend ${video.channelName.ifBlank { "this channel" }} in ${domain.branding.AppBranding.APP_NAME}"
        }
    }

    fun hideVideoFromFeed(video: OnlineVideoItem) {
        val dbDao = dao ?: return
        scope.launch(Dispatchers.IO) {
            dbDao.upsertVideoRecommendationFeedback(
                VideoRecommendationFeedbackEntity(
                    feedbackKey = "${VideoFeedbackType.HIDE_VIDEO}:${video.videoId}",
                    feedbackType = VideoFeedbackType.HIDE_VIDEO,
                    targetValue = video.videoId
                )
            )
            _feedbackMessage.value = "Hidden from your ${domain.branding.AppBranding.APP_NAME} video feed"
        }
    }

    fun toggleUserInterestTopic(topic: String) {
        val dbDao = dao ?: return
        val clean = topic.trim()
        if (clean.isBlank()) return
        val key = "${VideoFeedbackType.USER_INTEREST}:${clean.lowercase()}"
        val exists = recommendationFeedback.value.any { it.feedbackKey == key }
        scope.launch(Dispatchers.IO) {
            if (exists) {
                dbDao.deleteVideoRecommendationFeedback(key)
            } else {
                dbDao.upsertVideoRecommendationFeedback(
                    VideoRecommendationFeedbackEntity(
                        feedbackKey = key,
                        feedbackType = VideoFeedbackType.USER_INTEREST,
                        targetValue = clean
                    )
                )
            }
        }
    }

    fun resetVideoRecommendationFeedback() {
        val dbDao = dao ?: return
        scope.launch(Dispatchers.IO) {
            dbDao.clearAllVideoRecommendationFeedback()
            _feedbackMessage.value = "Reset ${domain.branding.AppBranding.APP_NAME} video recommendation filters"
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun release() {
        positionTickerJob?.cancel()
        videoPreloadJob?.cancel()
        resolveJob?.cancel()
        searchJob?.cancel()
        suggestionJob?.cancel()
        channelJob?.cancel()
        sectionJobs.values.forEach { it.cancel() }
        loadMoreJobs.values.forEach { it.cancel() }
        playbackController.release()
        cacheManager.onVideoPlayerStateChanged(isOpen = false)
    }

    /**
     * Prepares a small bounded cache window for the next Video in queue or the next Short,
     * after yielding bandwidth priority to the currently playing video.
     */
    private fun scheduleUpcomingVideoPreload(
        currentVideo: OnlineVideoItem,
        queue: List<OnlineVideoItem>
    ) {
        videoPreloadJob?.cancel()
        val currentSettings = cacheManager.settings.value
        if (!currentSettings.preloadUpcomingEnabled || !currentSettings.cacheVideosEnabled) return
        if (currentVideo.isLive) return

        val nextCandidate: OnlineVideoItem? = run {
            val curIdx = queue.indexOfFirst { it.videoId == currentVideo.videoId }
            val queueNext = if (curIdx in 0 until queue.lastIndex) queue[curIdx + 1] else null
            if (queueNext != null && !queueNext.isLive) {
                queueNext
            } else if (currentVideo.isShort) {
                val shortsPool = _homeSections.value.values
                    .filterIsInstance<VideoSectionState.Success>()
                    .flatMap { it.items }
                    .filter { it.isShort && !it.isLive }
                    .distinctBy { it.videoId }
                val shortIdx = shortsPool.indexOfFirst { it.videoId == currentVideo.videoId }
                when {
                    shortIdx in 0 until shortsPool.lastIndex -> shortsPool[shortIdx + 1]
                    shortsPool.isNotEmpty() && shortsPool.first().videoId != currentVideo.videoId -> shortsPool.first()
                    else -> null
                }
            } else {
                null
            }
        }
        if (nextCandidate == null || nextCandidate.videoId == currentVideo.videoId) return

        videoPreloadJob = scope.launch(Dispatchers.IO) {
            // Prioritize active foreground playback before warming the next item
            delay(1_800L)
            if (!isActive) return@launch
            val latest = cacheManager.settings.value
            if (!latest.preloadUpcomingEnabled || !latest.cacheVideosEnabled) return@launch
            if (!_playerState.value.hasActiveVideo || _playerState.value.activeVideo?.videoId != currentVideo.videoId) {
                return@launch
            }
            // Avoid large background video preloads on metered mobile connections unless it's a compact Short
            val metered = isMeteredNetwork()
            if (metered && !nextCandidate.isShort) return@launch

            when (val res = repository.resolveVideoDetails(nextCandidate, forceRefresh = false)) {
                is OnlineResult.Success -> {
                    if (!isActive) return@launch
                    val details = res.value
                    if (details.video.isLive) return@launch
                    val preloadDecision = VideoAdaptiveQualitySelector.selectBestQuality(
                        availableStreams = details.videoStreams,
                        settings = _playerState.value.videoExperienceSettings,
                        isMeteredMobile = metered,
                        estimatedBandwidthBps = playbackController.estimatedBandwidthBps(),
                        bufferedAheadMs = 0L,
                        rebufferCount = 0,
                        deviceMaxHeightPx = playbackController.detectDeviceMaxVideoHeightPx(),
                        isInitialStartup = true
                    )
                    val stream = preloadDecision.selectedStream
                        ?: pickPreferredStream(details.videoStreams, playbackController.detectDeviceMaxVideoHeightPx())
                        ?: return@launch
                    val streamTag = if (stream.isVideoOnly) "v_${stream.id}" else "m_${stream.id}"
                    playbackController.preloadUpcomingVideoStream(
                        videoId = details.video.videoId,
                        streamTag = streamTag,
                        streamUrl = stream.url,
                        headers = stream.headers,
                        isShort = details.video.isShort,
                        isLive = details.video.isLive
                    )
                    if (stream.isVideoOnly) {
                        val audio = details.audioStreams.firstOrNull()
                        if (audio != null) {
                            playbackController.preloadUpcomingVideoStream(
                                videoId = details.video.videoId,
                                streamTag = "a_${audio.id}",
                                streamUrl = audio.url,
                                headers = audio.headers,
                                isShort = details.video.isShort,
                                isLive = details.video.isLive
                            )
                        }
                    }
                }
                is OnlineResult.Error -> {
                    // Fail silently; foreground playback is unaffected
                }
            }
        }
    }

    private fun refreshProviderAccountStatus(video: OnlineVideoItem) {
        val currentAuth = authState.value
        if (currentAuth !is ProviderAuthState.Connected || !currentAuth.isTokenValid()) return
        scope.launch {
            val rating = repository.fetchProviderRating(video.videoId, currentAuth)
            val channelId = OnlineVideoDataSource.extractChannelIdFromUrl(video.channelUrl)
            val subscribed = if (channelId.isNotBlank()) {
                repository.fetchProviderSubscriptionStatus(channelId, currentAuth)
            } else null
            _playerState.update { state ->
                if (state.activeVideo?.videoId != video.videoId) state
                else state.copy(
                    ratingState = rating ?: state.ratingState,
                    isSubscribedOnProvider = subscribed ?: state.isSubscribedOnProvider
                )
            }
        }
    }

    private fun evaluateAdaptiveQualityDuringPlayback(progress: VideoPlaybackProgress) {
        val state = _playerState.value
        if (!state.isAutoQualityMode || state.isLocalOfflineVideo) return
        val details = state.resolvedDetails ?: return
        val currentStream = state.selectedVideoStream ?: return
        val now = SystemClock.elapsedRealtime()
        val meteredNow = isMeteredNetwork()
        val networkChanged = meteredNow != lastObservedMeteredNetwork
        if (networkChanged) {
            lastObservedMeteredNetwork = meteredNow
        }
        val elapsedSinceSwitch = now - lastQualitySwitchElapsedMs
        // Fast bailout unless network type changed or cooldown window elapsed
        if (!networkChanged && elapsedSinceSwitch < 8_000L) return

        val bufferedAheadMs = (progress.bufferedPositionMs - progress.currentPositionMs).coerceAtLeast(0L)
        val rebuffers = playbackController.rebufferCount
        val decision = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = details.videoStreams,
            settings = state.videoExperienceSettings,
            isMeteredMobile = meteredNow,
            estimatedBandwidthBps = progress.estimatedBandwidthBps,
            bufferedAheadMs = bufferedAheadMs,
            rebufferCount = rebuffers,
            deviceMaxHeightPx = playbackController.detectDeviceMaxVideoHeightPx(),
            isInitialStartup = false
        )

        val shouldSwitch = VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
            currentHeightPx = currentStream.heightPx,
            candidateHeightPx = decision.selectedStream?.heightPx ?: decision.targetMaxHeightPx,
            elapsedSinceLastSwitchMs = elapsedSinceSwitch,
            bufferedAheadMs = bufferedAheadMs,
            rebufferCount = rebuffers,
            networkTypeChanged = networkChanged
        )
        if (!shouldSwitch) return

        lastQualitySwitchElapsedMs = now
        val usingAdaptiveManifest = state.isUsingAdaptiveManifest &&
            (!details.hlsUrl.isNullOrBlank() || !details.dashMpdUrl.isNullOrBlank())
        playbackController.applyAdaptiveMaxVideoHeight(decision.targetMaxHeightPx)
        if (usingAdaptiveManifest) {
            // Adaptive HLS/DASH switches seamlessly inside ExoPlayer without restarting the MediaSource
            _playerState.update { it.copy(qualityStatusLabel = decision.reasonLabel) }
        } else {
            val targetStream = decision.selectedStream ?: return
            if (targetStream.id != currentStream.id) {
                val resumePos = playbackController.currentPositionMs().takeIf { it > 0L } ?: progress.currentPositionMs
                val wasPlaying = state.isPlaying
                _playerState.update {
                    it.copy(
                        selectedVideoStream = targetStream,
                        qualityStatusLabel = decision.reasonLabel
                    )
                }
                playbackController.prepareVideo(
                    details = details,
                    videoStream = targetStream,
                    audioStream = state.selectedAudioStream,
                    subtitle = state.selectedSubtitle,
                    startPositionMs = resumePos,
                    speed = state.playbackSpeed,
                    playWhenReady = wasPlaying,
                    preferAdaptiveManifest = false,
                    adaptiveMaxHeightPx = decision.targetMaxHeightPx
                )
            }
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            var tickCount = 0
            while (isActive && _playerState.value.hasActiveVideo) {
                if (_playerState.value.isPlaying || _playerState.value.isBuffering) {
                    playbackController.pollPositionSnapshot()
                    tickCount++
                    // Persist watch position every 15s (plus on pause/minimize/close) instead of spamming Room every 5s
                    if (tickCount % 30 == 0) {
                        persistCurrentWatchPosition()
                    }
                }
                delay(500L)
            }
        }
    }

    private fun persistCurrentWatchPosition() {
        val active = _playerState.value.activeVideo ?: return
        val pos = _playbackProgress.value.currentPositionMs.takeIf { it > 0L }
            ?: playbackController.currentPositionMs()
        if (pos > 0L) {
            scope.launch(Dispatchers.IO) {
                repository.updateWatchProgress(active.videoId, pos)
            }
        }
    }

    private fun pickPreferredStream(streams: List<VideoStreamOption>, maxHeight: Int): VideoStreamOption? {
        if (streams.isEmpty()) return null
        val distinct = VideoAdaptiveQualitySelector.distinctAvailableQualityOptions(streams)
        return distinct.firstOrNull { it.heightPx in 1..maxHeight }
            ?: distinct.firstOrNull()
            ?: streams.first()
    }
}
