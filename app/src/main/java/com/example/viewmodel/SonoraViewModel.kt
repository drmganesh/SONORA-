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
import kotlinx.coroutines.flow.stateIn
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
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineSearchPageResult
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.defaultDiscoverSectionsState
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
    data class Success(val details: OnlineCollectionDetails) : OnlineCollectionUiState
    data class Error(val message: String, val item: OnlineTrack) : OnlineCollectionUiState
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

    private val _queueIndex = MutableStateFlow(preferences.lastQueueIndex)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

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

    private val _onlineCollectionState = MutableStateFlow<OnlineCollectionUiState>(OnlineCollectionUiState.Idle)
    val onlineCollectionState: StateFlow<OnlineCollectionUiState> = _onlineCollectionState.asStateFlow()

    private val onlineRegionPrefs = data.preferences.OnlineRegionPreferences(application)

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

    val onlineSavedTracks: StateFlow<List<Song>> = onlineRepository.savedOnlineTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayedOnlineTracks: StateFlow<List<Song>> = onlineRepository.recentlyPlayedOnlineTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOnlineTracks: StateFlow<List<Song>> = onlineRepository.allOnlineTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineSearchHistory: StateFlow<List<String>> = onlineRepository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var onlineSearchJob: Job? = null
    private var onlineSuggestionJob: Job? = null
    private val discoverSectionJobs = mutableMapOf<DiscoverSectionType, Job>()
    private var discoverGeneration: Long = 0L
    private var activeDiscoverProviderKey: String? = null
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

    private val _miniPlayerGestures = MutableStateFlow(preferences.miniPlayerGestures)
    val miniPlayerGestures: StateFlow<Boolean> = _miniPlayerGestures.asStateFlow()

    // Automix & Song Matching State (Restored from preferences)
    private val _automixEnabled = MutableStateFlow(preferences.automixEnabled)
    val automixEnabled: StateFlow<Boolean> = _automixEnabled.asStateFlow()

    private val _matchNextSongEnabled = MutableStateFlow(preferences.matchNextSongEnabled)
    val matchNextSongEnabled: StateFlow<Boolean> = _matchNextSongEnabled.asStateFlow()

    // Player Style System (18+ Styles)
    private val playerStylePrefs = PlayerStylePreferences(application)
    private val _playerStyle = MutableStateFlow(preferences.playerStyle)
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _miniPlayerStyle = MutableStateFlow(preferences.miniPlayerStyle)
    val miniPlayerStyle: StateFlow<com.example.model.MiniPlayerStyle> = _miniPlayerStyle.asStateFlow()

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

    fun updatePlayerGestureConfig(transform: (com.example.model.PlayerGestureConfig) -> com.example.model.PlayerGestureConfig) {
        val updated = transform(_playerGestureConfig.value)
        _playerGestureConfig.value = updated
        _gesturesEnabled.value = updated.gesturesEnabled
        _swipeArtworkToSkip.value = updated.swipeArtworkEnabled
        _doubleTapToSeek.value = updated.doubleTapEnabled
        _swipeDownToCollapse.value = updated.swipeDownToMinimizeEnabled
        _miniPlayerGestures.value = updated.miniPlayerGesturesEnabled
        preferences.playerGestureConfig = updated
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setGestureConfig(updated)
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
        updateDynamicThemeForSong(_currentSong.value)
    }

    fun setDynamicThemeMode(mode: DynamicThemeMode) {
        _dynamicThemeMode.value = mode
        preferences.dynamicThemeMode = mode
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicThemeMode(mode)
        }
        updateDynamicThemeForSong(_currentSong.value)
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
        updateDynamicThemeForSong(_currentSong.value)
    }

    fun setDynamicColorIntensity(intensity: com.example.model.DynamicColorIntensity) {
        _dynamicColorIntensity.value = intensity
        preferences.dynamicColorIntensity = intensity
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicColorIntensity(intensity)
        }
        updateDynamicThemeForSong(_currentSong.value)
    }

    fun setDynamicGradientEnabled(enabled: Boolean) {
        _dynamicGradientEnabled.value = enabled
        preferences.dynamicGradientEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            themePrefs.setDynamicGradientEnabled(enabled)
        }
        updateDynamicThemeForSong(_currentSong.value)
    }

    fun updateSystemDarkTheme(isDark: Boolean) {
        if (currentSystemIsDark != isDark) {
            currentSystemIsDark = isDark
            updateDynamicThemeForSong(_currentSong.value)
        }
    }

    private val _reduceAnimations = MutableStateFlow(preferences.reduceAnimations)
    val reduceAnimations: StateFlow<Boolean> = _reduceAnimations.asStateFlow()

    private val homeAnimationPrefs = data.preferences.HomeAnimationPreferences(application)
    private val _homeAnimationLevel = MutableStateFlow(homeAnimationPrefs.getInitialSettings().animationLevel)
    val homeAnimationLevel: StateFlow<ui.home.animations.HomeAnimationLevel> = _homeAnimationLevel.asStateFlow()

    private val _dailyMixAnimationStyle = MutableStateFlow(homeAnimationPrefs.getInitialSettings().dailyMixStyle)
    val dailyMixAnimationStyle: StateFlow<ui.home.animations.DailyMixAnimationStyle> = _dailyMixAnimationStyle.asStateFlow()

    // Dedicated User Profile state & DataStore persistence (strictly separate from song artwork)
    private val userProfilePrefs = data.preferences.UserProfilePreferences(application)
    val userProfile: StateFlow<com.example.model.UserProfile> = userProfilePrefs.userProfile

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

    fun setHomeCustomAccentArgb(argb: Long?) {
        _homeCustomAccentArgb.value = argb
        preferences.homeCustomAccentArgb = argb ?: -1L
        updateDynamicThemeForSong(_currentSong.value)
    }

    fun setReduceAnimations(reduce: Boolean) {
        _reduceAnimations.value = reduce
        preferences.reduceAnimations = reduce
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
        updateDynamicThemeForSong(_currentSong.value)
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

    // Dynamic Lyrics State (Extracted from embedded ID3/FLAC or sidecar .lrc/.txt files)
    private val _currentTrackLyrics = MutableStateFlow<String?>(null)
    val currentTrackLyrics: StateFlow<String?> = _currentTrackLyrics.asStateFlow()

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
                    viewModelScope.launch(Dispatchers.IO) {
                        _currentTrackLyrics.value = com.example.util.LyricsExtractor.getOrExtractLyrics(getApplication(), activeSvcSong)
                    }
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
                    _playbackInfo.value = _playbackInfo.value.copy(
                        isPlaying = eng.isPlaying,
                        isBuffering = eng.isBuffering.value,
                        bufferedPositionMs = eng.bufferedPositionMs,
                        currentPositionMs = eng.currentPosition,
                        durationMs = eng.duration.coerceAtLeast(activeSong?.durationMs ?: 0L),
                        playbackSpeed = _playbackSpeed.value
                    )
                    if (eng.isPlaying) {
                        startProgressTracker()
                    }
                }

                collectorScope.launch {
                    eng.isBuffering.collect { buffering ->
                        _playbackInfo.value = _playbackInfo.value.copy(
                            isBuffering = buffering,
                            bufferedPositionMs = eng.bufferedPositionMs
                        )
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

    init {
        NewPipeInitializer.ensureInitialized(application)
        initOnlineProviders()
        bindMusicService()
        loadLocalSongs()
        observePlaylists()
        observeFolderFiltering()
        viewModelScope.launch {
            homeAnimationPrefs.settingsFlow
                .catch { e -> Log.w(TAG, "Home animation DataStore stream error", e) }
                .collect { saved ->
                    _homeAnimationLevel.value = saved.animationLevel
                    _dailyMixAnimationStyle.value = saved.dailyMixStyle
                }
        }
        viewModelScope.launch {
            _currentSong.collect { song ->
                updateDynamicThemeForSong(song)
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
                    val combinedPool = filteredInitial + savedOnline
                    if (combinedPool.isNotEmpty()) {
                        val byId = combinedPool.associateBy { it.id }
                        val savedIds = preferences.lastQueueSongIds
                        val restoredQueue = savedIds.mapNotNull { byId[it] }.ifEmpty { filteredInitial.ifEmpty { savedOnline } }
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
                            _currentTrackLyrics.value = if (targetSong.isOnline) {
                                targetSong.lyrics
                            } else {
                                com.example.util.LyricsExtractor.getOrExtractLyrics(getApplication(), targetSong)
                            }
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

    private fun observePlaylists() {
        viewModelScope.launch {
            combine(
                dao.getAllPlaylistsFlow(),
                dao.getAllPlaylistSongCrossRefsFlow(),
                _allSongs,
                onlineRepository.allOnlineTracksFlow
            ) { entities, crossRefs, localSongs, onlineSongs ->
                val activeIds = (localSongs + onlineSongs).map { it.id }.toSet()
                val grouped = crossRefs.filter { it.songId in activeIds }.groupBy { it.playlistId }
                entities.map { entity ->
                    val refs = grouped[entity.id].orEmpty()
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songCount = refs.size,
                        createdAt = entity.createdAt,
                        coverArtUri = entity.coverArtUri
                    )
                }
            }.catch { e ->
                Log.e(TAG, "Playlist observation failed", e)
            }.collect { enriched ->
                _playlists.value = enriched
            }
        }
    }

    // --- Filtered and Organized Library Categories ---

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
            onlineRepository.allOnlineTracksFlow
        ) { refs, localSongs, onlineSongs ->
            val byId = (localSongs + onlineSongs).associateBy { it.id }
            refs.mapNotNull { byId[it.songId] }
        }
    }

    // --- Playback Controls ---

    fun playSong(
        song: Song,
        newQueue: List<Song>? = null,
        startIndex: Int = -1,
        manualStreamId: String? = null,
        resumePositionMs: Long = 0L
    ) {
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
        _queueIndex.value = idx
        _currentSong.value = hydratedSong
        _playbackInfo.value = _playbackInfo.value.copy(
            playbackError = null,
            isBuffering = hydratedSong.isOnline,
            currentPositionMs = resumePositionMs.coerceAtLeast(0L)
        )

        // Save last played song and queue to preferences
        preferences.lastPlayedSongId = hydratedSong.id
        preferences.lastQueueIndex = idx
        preferences.lastQueueSongIds = q.map { it.id }

        // Find and pre-match the best next track for smooth automix
        findNextMatchedSong(hydratedSong, q, idx)

        // Extract embedded or sidecar lyrics for local tracks
        if (hydratedSong.isOnline) {
            _currentTrackLyrics.value = hydratedSong.lyrics
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val lyrics = com.example.util.LyricsExtractor.getOrExtractLyrics(getApplication(), hydratedSong)
                _currentTrackLyrics.value = lyrics
            }
        }

        val engine = musicService?.audioEngine
        val controller = unifiedPlaybackController
        if (engine == null || controller == null) {
            pendingPlayRequest = PendingPlayRequest(hydratedSong, q, idx, manualStreamId, resumePositionMs)
            if (!isBound) bindMusicService()
            return
        }
        pendingPlayRequest = null
        pendingTogglePlayOnConnect = false

        ensureServiceStarted()
        musicService?.updateQueueSnapshot(q, idx)
        if (musicService?.requestAudioFocusIfNeeded() == false) {
            _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false, isBuffering = false)
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
                    playbackError = null
                )
            },
            onPrepared = { updatedSong, _, durMs ->
                _currentSong.value = updatedSong
                _queue.value = _queue.value.map { if (it.id == updatedSong.id) updatedSong else it }
                musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = true,
                    isBuffering = false,
                    bufferedPositionMs = engine.bufferedPositionMs,
                    durationMs = durMs.coerceAtLeast(updatedSong.durationMs),
                    playbackError = null
                )
                startProgressTracker()
                syncNotification(updatedSong, true)
                incrementPlayCount(updatedSong)

                // If crossfade is enabled, smoothly fade in
                if (_crossfadeSeconds.value > 0) {
                    engine.crossfadeIn((_crossfadeSeconds.value * 1000L).coerceAtMost(3000L))
                }
            },
            onCompletion = {
                handleTrackCompletion()
            },
            onError = { errMsg ->
                isCrossfadingNext = false
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    playbackError = errMsg
                )
                _dspDiagnosticMessage.value = errMsg
                syncNotification(_currentSong.value, false)
            }
        )
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
            if (engine.isPrepared) {
                ensureServiceStarted()
                if (musicService?.requestAudioFocusIfNeeded() == false) return
                engine.play()
                _playbackInfo.value = _playbackInfo.value.copy(isPlaying = true)
                startProgressTracker()
                syncNotification(cur, true)
            } else {
                playSong(cur)
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
        musicService?.abandonAudioFocus()
        _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false)
        syncNotification(_currentSong.value, false)
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
        if (!_matchNextSongEnabled.value || currentQueue.size <= 1) {
            val nextIdx = (currentIndex + 1) % currentQueue.size.coerceAtLeast(1)
            _nextMatchedSong.value = currentQueue.getOrNull(nextIdx)
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

    fun playNext(withCrossfade: Boolean = true) {
        val q = _queue.value
        if (q.isEmpty()) return

        val matched = _nextMatchedSong.value
        val targetSong = if (_automixEnabled.value && _matchNextSongEnabled.value && matched != null && q.any { it.id == matched.id }) {
            matched
        } else {
            var nextIdx = _queueIndex.value + 1
            if (nextIdx >= q.size) {
                if (_playbackInfo.value.repeatMode == RepeatMode.ALL) {
                    nextIdx = 0
                } else {
                    isCrossfadingNext = false
                    return
                }
            }
            q[nextIdx]
        }

        val targetIdx = q.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
        val fadeSec = _crossfadeSeconds.value

        if (withCrossfade && fadeSec > 0 && musicService?.audioEngine?.isPlaying == true && !isCrossfadingNext) {
            isCrossfadingNext = true
            musicService?.audioEngine?.crossfadeOut(fadeSec * 1000L) {
                isCrossfadingNext = false
                playSong(targetSong, q, targetIdx)
            }
        } else {
            playSong(targetSong, q, targetIdx)
        }
    }

    fun playPrevious() {
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
        playSong(q[prevIdx], q, prevIdx)
    }

    fun seekTo(positionMs: Long) {
        musicService?.audioEngine?.seekTo(positionMs)
        _playbackInfo.value = _playbackInfo.value.copy(currentPositionMs = positionMs)
        musicService?.notificationManager?.updatePlaybackState(
            isPlaying = _playbackInfo.value.isPlaying,
            positionMs = positionMs,
            durationMs = _playbackInfo.value.durationMs,
            isShuffle = _playbackInfo.value.shuffleMode == ShuffleMode.ON,
            repeatMode = _playbackInfo.value.repeatMode
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
        musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
        syncNotification()
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
        when (_playbackInfo.value.repeatMode) {
            RepeatMode.ONE -> {
                _currentSong.value?.let { playSong(it) }
            }
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                if (_queueIndex.value < _queue.value.size - 1) {
                    playNext()
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
                    _playbackInfo.value = _playbackInfo.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        isBuffering = engine.isBuffering.value,
                        bufferedPositionMs = engine.bufferedPositionMs
                    )

                    // Accumulate analytics listening time
                    secondsCounter++
                    if (secondsCounter >= 4) { // every 1 second (4 * 250ms)
                        secondsCounter = 0
                        recordListeningSecond()
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
        }
    }

    // --- Queue Management ---

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _queue.value.indices || toIndex !in _queue.value.indices) return
        val list = _queue.value.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _queue.value = list
        val cur = _currentSong.value
        _queueIndex.value = list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
        musicService?.updateQueueSnapshot(list, _queueIndex.value)
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val list = _queue.value.toMutableList()
        list.removeAt(index)
        _queue.value = list
        val cur = _currentSong.value
        _queueIndex.value = list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
        musicService?.updateQueueSnapshot(list, _queueIndex.value)
    }

    fun clearQueue() {
        val cur = _currentSong.value
        _queue.value = if (cur != null) listOf(cur) else emptyList()
        _queueIndex.value = 0
        musicService?.updateQueueSnapshot(_queue.value, 0)
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
        _queue.value = _queue.value + song
        musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
    }

    fun playNextInQueue(song: Song) {
        val q = _queue.value.toMutableList()
        val insertPos = (_queueIndex.value + 1).coerceAtMost(q.size)
        q.add(insertPos, song)
        _queue.value = q
        musicService?.updateQueueSnapshot(q, _queueIndex.value)
    }

    fun shuffleRemainingQueue() {
        val q = _queue.value
        if (q.size <= 2) return
        val curIdx = _queueIndex.value.coerceIn(0, q.lastIndex)
        val playedAndCurrent = q.subList(0, curIdx + 1)
        val upNextShuffled = q.subList(curIdx + 1, q.size).shuffled()
        _queue.value = playedAndCurrent + upNextShuffled
        musicService?.updateQueueSnapshot(_queue.value, _queueIndex.value)
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

    fun toggleMiniPlayerGestures(enabled: Boolean) {
        updatePlayerGestureConfig { it.copy(miniPlayerGesturesEnabled = enabled) }
    }

    // --- Automix & Song Matching Controls ---

    fun toggleAutomix(enabled: Boolean) {
        _automixEnabled.value = enabled
        preferences.automixEnabled = enabled
    }

    fun toggleMatchNextSong(enabled: Boolean) {
        _matchNextSongEnabled.value = enabled
        preferences.matchNextSongEnabled = enabled
        _currentSong.value?.let { cur ->
            findNextMatchedSong(cur, _queue.value, _queueIndex.value)
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

    private fun initOnlineProviders() {
        val providers = onlineRepository.getAvailableProviders()
        _onlineProviders.value = providers
        val savedKey = preferences.selectedOnlineProviderKey
        val selected = providers.firstOrNull { it.key == savedKey }
            ?: providers.firstOrNull { it.key == OnlineMusicProvider.KEY_YOUTUBE_MUSIC }
            ?: providers.firstOrNull()
        _selectedOnlineProvider.value = selected
        if (selected != null) {
            _selectedOnlineFilter.value = selected.defaultFilter
            refreshOnlineDiscovery(forceRefresh = false)
        }
    }

    fun selectOnlineProvider(providerKey: String) {
        val target = _onlineProviders.value.firstOrNull { it.key == providerKey } ?: return
        if (_selectedOnlineProvider.value?.key == target.key) return
        _selectedOnlineProvider.value = target
        preferences.selectedOnlineProviderKey = target.key
        if (_selectedOnlineFilter.value !in target.supportedFilters) {
            _selectedOnlineFilter.value = target.defaultFilter
        }
        _onlineSearchSuggestions.value = emptyList()
        refreshOnlineDiscovery(forceRefresh = false)
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
            val suggestions = onlineRepository.getSearchSuggestions(trimmed, provider)
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
        val generation = discoverGeneration

        discoverSectionJobs.values.forEach { it.cancel() }
        discoverSectionJobs.clear()
        _discoverSectionsState.value = defaultDiscoverSectionsState()

        val recentArtistSeed = recentlyPlayedOnlineTracks.value.firstOrNull()?.artist
        for (section in DiscoverSectionType.entries) {
            launchDiscoverSection(
                section = section,
                provider = provider,
                generation = generation,
                recentArtistSeed = recentArtistSeed,
                forceRefresh = forceRefresh
            )
        }
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
        val generation = discoverGeneration
        _discoverSectionsState.update { current ->
            current.toMutableMap().apply { put(section, DiscoverSectionState.Loading) }
        }
        val recentArtistSeed = recentlyPlayedOnlineTracks.value.firstOrNull()?.artist
        launchDiscoverSection(
            section = section,
            provider = provider,
            generation = generation,
            recentArtistSeed = recentArtistSeed,
            forceRefresh = true
        )
    }

    fun setOnlineContentCountry(countryCode: String) {
        viewModelScope.launch {
            val clean = onlineRegionPrefs.setContentCountry(countryCode)
            if (_onlineContentCountry.value != clean) {
                _onlineContentCountry.value = clean
                NewPipeInitializer.updateLocalization(_onlineContentLanguage.value, clean)
                onlineRepository.clearDiscoverCache()
                refreshOnlineDiscovery(forceRefresh = true)
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
        refreshOnlineDiscovery(forceRefresh = true)
    }

    private fun launchDiscoverSection(
        section: DiscoverSectionType,
        provider: OnlineMusicProvider,
        generation: Long,
        recentArtistSeed: String?,
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
                forceRefresh = forceRefresh
            )
            if (generation == discoverGeneration && _selectedOnlineProvider.value?.key == provider.key) {
                _discoverSectionsState.update { current ->
                    current.toMutableMap().apply { put(section, resultState) }
                }
            }
        }
        discoverSectionJobs[section] = job
    }

    fun openOnlineCollection(item: OnlineTrack) {
        onlineCollectionJob?.cancel()
        onlineCollectionJob = viewModelScope.launch {
            _onlineCollectionState.value = OnlineCollectionUiState.Loading(item.title)
            when (val res = onlineRepository.fetchCollectionDetails(item)) {
                is OnlineResult.Success -> {
                    _onlineCollectionState.value = OnlineCollectionUiState.Success(res.value)
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
        if (!song.isOnline) return
        viewModelScope.launch {
            onlineRepository.toggleSaveOnlineTrack(song)
        }
    }

    fun toggleSaveOnlineTrack(track: OnlineTrack) {
        toggleSaveOnlineTrack(OnlineTrackMapper.toUnifiedSong(track))
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

    override fun onCleared() {
        super.onCleared()
        onlineSearchJob?.cancel()
        onlineSuggestionJob?.cancel()
        discoverSectionJobs.values.forEach { it.cancel() }
        discoverSectionJobs.clear()
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
