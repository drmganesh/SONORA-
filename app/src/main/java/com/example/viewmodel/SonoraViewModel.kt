package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.audiofx.PresetReverb
import android.os.IBinder
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
import com.example.model.EqualizerBand
import com.example.model.EqPreset
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.PlayerStyle
import data.preferences.PlayerStylePreferences
import ui.player.seekbar.SeekbarStyle
import data.preferences.SeekbarPreferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = SonoraPreferences(application)

    private val db = SonoraDatabase.getDatabase(application)
    private val dao = db.dao()
    private val scanner = LocalMusicScanner(application, dao)

    private var musicService: SonoraMusicService? = null
    private var isBound = false

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

    // Equalizer & Audio FX State (Restored from preferences)
    private val _equalizerEnabled = MutableStateFlow(preferences.equalizerEnabled)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

    private val _bassBoost = MutableStateFlow(preferences.bassBoost) // 0..1000
    val bassBoost: StateFlow<Int> = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow(preferences.virtualizer) // 0..1000
    val virtualizer: StateFlow<Int> = _virtualizer.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(preferences.reverbPreset)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(preferences.loudnessGainMb) // 0..1000 millibels
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    // 360° Spatial Audio State (Restored from preferences)
    private val _spatialConfig = MutableStateFlow(preferences.spatialConfig)
    val spatialConfig: StateFlow<SpatialAudioConfig> = _spatialConfig.asStateFlow()

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

    // Player Style System (9 Premium Styles)
    private val playerStylePrefs = PlayerStylePreferences(application)
    private val _playerStyle = MutableStateFlow(preferences.playerStyle)
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    fun setPlayerStyle(style: PlayerStyle) {
        _playerStyle.value = style
        preferences.playerStyle = style
        viewModelScope.launch(Dispatchers.IO) {
            playerStylePrefs.setPlayerStyle(style)
        }
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

    // Expanded Equalizer Presets (21 Audio Engineering Profiles calibrated for 15-Band ISO EQ)
    // Bands: [25Hz, 40Hz, 63Hz, 100Hz, 160Hz, 250Hz, 400Hz, 630Hz, 1kHz, 1.6kHz, 2.5kHz, 4kHz, 6.3kHz, 10kHz, 16kHz]
    val standardPresets = listOf(
        EqPreset("Flat (Reference)", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Poweramp Deep Bass", listOf(7f, 6.5f, 6f, 5f, 3.5f, 2f, 0.5f, 0f, 0f, 1f, 1.5f, 2.5f, 3f, 4f, 4.5f), bassBoost = 850, virtualizer = 600),
        EqPreset("Sub-Bass Rumble 25Hz", listOf(9f, 8f, 7f, 5.5f, 3f, 1f, 0f, -0.5f, -1f, 0f, 0.5f, 1.5f, 2f, 2.5f, 3f), bassBoost = 1000, virtualizer = 400),
        EqPreset("Bass & Treble Loudness", listOf(6.5f, 6f, 5f, 3.5f, 1f, -1f, -2f, -1.5f, 0f, 1.5f, 3f, 4.5f, 6f, 7f, 7.5f), bassBoost = 700, virtualizer = 650),
        EqPreset("Rock Studio Punch", listOf(4.5f, 4f, 3.5f, 2f, -1f, -2f, -1.5f, 1f, 2f, 3.5f, 4.5f, 5f, 4.5f, 4f, 3.5f), bassBoost = 500, virtualizer = 400),
        EqPreset("Metal & Hardcore", listOf(6f, 5.5f, 4.5f, 3f, -2f, -3f, -2.5f, 0.5f, 2f, 3.5f, 5f, 6f, 6f, 5.5f, 5f), bassBoost = 650, virtualizer = 500),
        EqPreset("Pop Vibrant Modern", listOf(2f, 2f, 2.5f, 3.5f, 4f, 3.5f, 2.5f, 1.5f, 2f, 3f, 3.5f, 4f, 4.5f, 5f, 5f), bassBoost = 450, virtualizer = 550),
        EqPreset("Hip-Hop & Urban", listOf(7f, 6.5f, 6f, 5f, 3f, 1f, -0.5f, 1f, 2f, 3f, 3.5f, 4.5f, 5f, 5.5f, 6f), bassBoost = 900, virtualizer = 600),
        EqPreset("R&B Smooth Groove", listOf(4.5f, 4f, 3.5f, 2.5f, 1.5f, 0.5f, 0f, 1f, 2f, 3f, 3.5f, 4f, 4f, 3.5f, 3f), bassBoost = 550, virtualizer = 450),
        EqPreset("Electronic 360 EDM", listOf(6f, 5.5f, 5f, 4f, 2f, 0f, -1f, 1.5f, 3f, 4.5f, 5.5f, 6.5f, 7f, 7.5f, 8f), bassBoost = 800, virtualizer = 900),
        EqPreset("Techno & House Pulse", listOf(7.5f, 7f, 6f, 4.5f, 1f, -1f, -2f, 0f, 2f, 3.5f, 5f, 6f, 6.5f, 6f, 5.5f), bassBoost = 850, virtualizer = 700),
        EqPreset("Trance Euphoria", listOf(5.5f, 5f, 4.5f, 3f, 1.5f, 0f, 1f, 2.5f, 4f, 5f, 6f, 7f, 7.5f, 7f, 6.5f), bassBoost = 700, virtualizer = 850),
        EqPreset("Classical Symphony", listOf(3.5f, 3f, 2.5f, 1.5f, 0.5f, 0f, 0.5f, 1.5f, 2f, 2.5f, 3.5f, 4f, 4.5f, 4f, 3.5f), reverbPreset = PresetReverb.PRESET_LARGEHALL),
        EqPreset("Opera & Reverb Hall", listOf(2.5f, 2f, 1.5f, 0.5f, 1f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5f, 4.5f, 4f), reverbPreset = PresetReverb.PRESET_LARGEHALL),
        EqPreset("Jazz Club Live", listOf(3f, 2.5f, 2f, 1f, 1f, 1.5f, 2f, 2f, 2f, 2f, 2.5f, 3f, 3.5f, 3f, 2.5f), reverbPreset = PresetReverb.PRESET_SMALLROOM),
        EqPreset("Acoustic Warmth", listOf(4f, 3.5f, 3f, 2f, 1.5f, 2f, 2f, 2f, 2.5f, 3f, 3.5f, 4f, 3.5f, 3f, 2.5f), bassBoost = 350, virtualizer = 350),
        EqPreset("Vocal Clarity & Speech", listOf(-3f, -2.5f, -1.5f, 0f, 2f, 4f, 5f, 5f, 4.5f, 3.5f, 2.5f, 1.5f, 0.5f, -0.5f, -1.5f), virtualizer = 300),
        EqPreset("Air & Treble Crisp", listOf(-1.5f, -1f, -1f, 0f, 0.5f, 1f, 2f, 3f, 4.5f, 6f, 7f, 8f, 8.5f, 8f, 7.5f), virtualizer = 400),
        EqPreset("Lo-Fi Warm Tape", listOf(3.5f, 3f, 2.5f, 1.5f, 0.5f, -0.5f, -1f, -1f, 0f, 0f, -1f, -2f, -3.5f, -5f, -6f), bassBoost = 400),
        EqPreset("Gaming Surround", listOf(5.5f, 5f, 4f, 2.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5f, 6f, 6.5f, 6.5f, 6f, 5.5f, 5f), bassBoost = 650, virtualizer = 950),
        EqPreset("Audiophile Master Neutral", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    )

    private val _presets = MutableStateFlow(standardPresets)
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _currentPresetIndex = MutableStateFlow(preferences.currentEqPresetIndex)
    val currentPresetIndex: StateFlow<Int> = _currentPresetIndex.asStateFlow()

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
            name = "Cinematic Surround 7.1",
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SonoraMusicService.SonoraBinder
            musicService = binder.service
            isBound = true

            musicService?.setPlaybackCallback(object : SonoraMusicService.ServicePlaybackCallback {
                override fun onRemotePlay() = togglePlayPause()
                override fun onRemotePause() = togglePlayPause()
                override fun onRemoteNext() = playNext()
                override fun onRemotePrev() = playPrevious()
            })

            // Bind audio visualizer & bands flows from AudioEngine
            val engine = musicService?.audioEngine
            engine?.let { eng ->
                // Apply restored settings to audio engine
                eng.setEqualizerEnabled(_equalizerEnabled.value)
                eng.setBassBoost(_bassBoost.value)
                eng.setVirtualizer(_virtualizer.value)
                eng.setReverbPreset(_reverbPreset.value)
                eng.setLoudnessEnhancer(_loudnessGainMb.value)
                eng.updateSpatialAudioConfig(_spatialConfig.value)

                // Restore custom band gains if saved
                val savedBands = preferences.getSavedBandGains()
                if (savedBands != null) {
                    savedBands.forEachIndexed { idx, gainDb ->
                        val gainMb = (gainDb * 100).toInt().toShort()
                        eng.setBandGain(idx.toShort(), gainMb)
                    }
                } else if (_currentPresetIndex.value in standardPresets.indices) {
                    val p = standardPresets[_currentPresetIndex.value]
                    p.gainsDb.forEachIndexed { idx, gainDb ->
                        val gainMb = (gainDb * 100).toInt().toShort()
                        eng.setBandGain(idx.toShort(), gainMb)
                    }
                }

                viewModelScope.launch {
                    eng.visualizerFrequencies.collect { _visualizerFrequencies.value = it }
                }
                viewModelScope.launch {
                    eng.waveformSamples.collect { _waveformSamples.value = it }
                }
                viewModelScope.launch {
                    eng.equalizerBands.collect { bands ->
                        if (bands.isNotEmpty()) {
                            _equalizerBands.value = bands
                        }
                    }
                }
                viewModelScope.launch {
                    eng.isTwsConnected.collect { _isTwsConnected.value = it }
                }
                viewModelScope.launch {
                    eng.connectedAudioDeviceName.collect { _connectedAudioDeviceName.value = it }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    init {
        bindMusicService()
        loadLocalSongs()
        observePlaylists()
    }

    private fun bindMusicService() {
        val intent = Intent(getApplication(), SonoraMusicService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun loadLocalSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = scanner.scanSongs()
            _allSongs.value = songs
            if (_queue.value.isEmpty() && songs.isNotEmpty()) {
                val lastId = preferences.lastPlayedSongId
                val savedSong = if (lastId != -1L) songs.find { it.id == lastId } else null
                val targetSong = savedSong ?: songs.first()
                val targetIndex = if (savedSong != null) songs.indexOf(savedSong).coerceAtLeast(0) else 0

                _queue.value = songs
                _queueIndex.value = targetIndex
                _currentSong.value = targetSong
                _currentTrackLyrics.value = com.example.util.LyricsExtractor.getOrExtractLyrics(getApplication(), targetSong)
            }
            generateDailyMixes(songs)
            updateDailyAnalytics()
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            dao.getAllPlaylistsFlow().collect { entities ->
                _playlists.value = entities.map { entity ->
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songCount = 0, // will count dynamically
                        createdAt = entity.createdAt,
                        coverArtUri = entity.coverArtUri
                    )
                }
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

    // --- Playback Controls ---

    fun playSong(song: Song, newQueue: List<Song>? = null, startIndex: Int = -1) {
        val q = newQueue ?: _queue.value
        val idx = if (startIndex >= 0) startIndex else q.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        _queue.value = q
        _queueIndex.value = idx
        _currentSong.value = song

        // Save last played song and queue to preferences
        preferences.lastPlayedSongId = song.id
        preferences.lastQueueIndex = idx
        preferences.lastQueueSongIds = q.map { it.id }

        // Find and pre-match the best next track for smooth automix
        findNextMatchedSong(song, q, idx)

        // Extract embedded or sidecar lyrics
        viewModelScope.launch(Dispatchers.IO) {
            val lyrics = com.example.util.LyricsExtractor.getOrExtractLyrics(getApplication(), song)
            _currentTrackLyrics.value = lyrics
        }

        val engine = musicService?.audioEngine ?: return
        engine.prepareAndPlay(
            uri = song.uri,
            onPrepared = {
                _playbackInfo.value = _playbackInfo.value.copy(
                    isPlaying = true,
                    durationMs = song.durationMs
                )
                startProgressTracker()
                musicService?.updateForegroundNotification(song, true)
                incrementPlayCount(song)

                // If crossfade is enabled, smoothly fade in
                if (_crossfadeSeconds.value > 0) {
                    engine.crossfadeIn((_crossfadeSeconds.value * 1000L).coerceAtMost(3000L))
                }
            },
            onCompletion = {
                handleTrackCompletion()
            },
            onError = { errMsg ->
                _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false)
            }
        )
    }

    fun togglePlayPause() {
        val engine = musicService?.audioEngine ?: return
        if (engine.isPlaying) {
            engine.pause()
            _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false)
            musicService?.updateForegroundNotification(_currentSong.value, false)
        } else {
            val cur = _currentSong.value
            if (cur != null) {
                if (engine.isPrepared) {
                    engine.play()
                    _playbackInfo.value = _playbackInfo.value.copy(isPlaying = true)
                    startProgressTracker()
                    musicService?.updateForegroundNotification(cur, true)
                } else {
                    playSong(cur)
                }
            } else if (_allSongs.value.isNotEmpty()) {
                playSong(_allSongs.value.first())
            }
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

        // Choose next track: Automix match if enabled and nextMatchedSong is in queue, or next in queue
        val targetSong = if (_automixEnabled.value && _matchNextSongEnabled.value && _nextMatchedSong.value != null) {
            _nextMatchedSong.value!!
        } else {
            var nextIdx = _queueIndex.value + 1
            if (nextIdx >= q.size) {
                if (_playbackInfo.value.repeatMode == RepeatMode.ALL) nextIdx = 0 else return
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
    }

    fun toggleShuffle() {
        val newMode = if (_playbackInfo.value.shuffleMode == ShuffleMode.OFF) ShuffleMode.ON else ShuffleMode.OFF
        _playbackInfo.value = _playbackInfo.value.copy(shuffleMode = newMode)
        preferences.shuffleMode = newMode
        if (newMode == ShuffleMode.ON) {
            val cur = _currentSong.value
            val shuffled = _queue.value.shuffled()
            val finalQueue = if (cur != null) listOf(cur) + (shuffled - cur) else shuffled
            _queue.value = finalQueue
            _queueIndex.value = 0
        } else {
            _queue.value = _allSongs.value
            _queueIndex.value = _queue.value.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(0)
        }
    }

    fun cycleRepeatMode() {
        val newMode = when (_playbackInfo.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackInfo.value = _playbackInfo.value.copy(repeatMode = newMode)
        preferences.repeatMode = newMode
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
                    musicService?.updateForegroundNotification(_currentSong.value, false)
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
                        durationMs = dur
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
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayKey = format.format(java.util.Date())
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
            val newFav = !song.isFavorite
            dao.updateFavorite(song.id, newFav)
            _allSongs.value = _allSongs.value.map {
                if (it.id == song.id) it.copy(isFavorite = newFav) else it
            }
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = _currentSong.value?.copy(isFavorite = newFav)
            }
        }
    }

    private fun incrementPlayCount(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.incrementPlayCount(song.id, now)
            _allSongs.value = _allSongs.value.map {
                if (it.id == song.id) it.copy(playCount = it.playCount + 1, lastPlayedTimestamp = now) else it
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
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val list = _queue.value.toMutableList()
        list.removeAt(index)
        _queue.value = list
        val cur = _currentSong.value
        _queueIndex.value = list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
    }

    fun clearQueue() {
        val cur = _currentSong.value
        _queue.value = if (cur != null) listOf(cur) else emptyList()
        _queueIndex.value = 0
    }

    fun saveQueueAsPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = dao.insertPlaylist(PlaylistEntity(name = name))
            _queue.value.forEachIndexed { idx, s ->
                dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, s.id, idx))
            }
        }
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
    }

    fun playNextInQueue(song: Song) {
        val q = _queue.value.toMutableList()
        val insertPos = (_queueIndex.value + 1).coerceAtMost(q.size)
        q.add(insertPos, song)
        _queue.value = q
    }

    // --- Equalizer & Spatial Controls ---

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        preferences.equalizerEnabled = enabled
        musicService?.audioEngine?.setEqualizerEnabled(enabled)
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        musicService?.audioEngine?.setBandGain(bandIndex, gainMb)
        val updated = _equalizerBands.value.map {
            if (it.bandIndex == bandIndex) it.copy(currentLevelMb = gainMb) else it
        }
        _equalizerBands.value = updated
        // Save custom band gains
        val gainsDb = updated.map { (it.currentLevelMb / 100f) }
        preferences.saveBandGains(gainsDb)
    }

    fun selectPreset(index: Int) {
        if (index !in _presets.value.indices) return
        _currentPresetIndex.value = index
        preferences.currentEqPresetIndex = index
        val preset = _presets.value[index]

        // Apply band gains
        val bands = _equalizerBands.value
        bands.forEachIndexed { i, band ->
            val gainDb = preset.gainsDb.getOrElse(i) { 0f }
            val gainMb = (gainDb * 100).toInt().toShort()
            musicService?.audioEngine?.setBandGain(band.bandIndex, gainMb)
        }

        // Also save custom band gains array
        preferences.saveBandGains(preset.gainsDb)

        if (preset.bassBoost > 0) setBassBoost(preset.bassBoost)
        if (preset.virtualizer > 0) setVirtualizer(preset.virtualizer)
        if (preset.reverbPreset != PresetReverb.PRESET_NONE) setReverb(preset.reverbPreset)
    }

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
            listenerY = p.listenerY
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

    fun setVisualizerStyle(style: VisualizerStyle) {
        _visualizerStyle.value = style
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
        _gesturesEnabled.value = enabled
        preferences.gesturesEnabled = enabled
    }

    fun toggleSwipeArtworkToSkip(enabled: Boolean) {
        _swipeArtworkToSkip.value = enabled
        preferences.swipeArtworkToSkip = enabled
    }

    fun toggleDoubleTapToSeek(enabled: Boolean) {
        _doubleTapToSeek.value = enabled
        preferences.doubleTapToSeek = enabled
    }

    fun toggleSwipeDownToCollapse(enabled: Boolean) {
        _swipeDownToCollapse.value = enabled
        preferences.swipeDownToCollapse = enabled
    }

    fun toggleMiniPlayerGestures(enabled: Boolean) {
        _miniPlayerGestures.value = enabled
        preferences.miniPlayerGestures = enabled
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
        if (songs.isEmpty()) {
            _dailyMixes.value = emptyList()
            return
        }

        val mixes = mutableListOf<com.example.model.DailyMix>()

        // Daily Mix 1: Dynamic Energy Flow (Upbeat / Pop / Electronic or high tempo)
        val highEnergy = songs.filter {
            it.genre.contains("rock", ignoreCase = true) ||
            it.genre.contains("electronic", ignoreCase = true) ||
            it.genre.contains("pop", ignoreCase = true) ||
            it.genre.contains("dance", ignoreCase = true)
        }.ifEmpty { songs.shuffled().take(8) }

        mixes.add(
            com.example.model.DailyMix(
                id = "daily_mix_1",
                title = "Daily Mix 1",
                subtitle = "High Energy & Upbeat",
                description = "Harmonically sequenced driving beats and studio highs",
                badge = "ENERGETIC",
                songs = highEnergy,
                coverSong = highEnergy.firstOrNull(),
                gradientColors = listOf(0xFFE5A93C, 0xFFE55D3C)
            )
        )

        // Daily Mix 2: Focus & Acoustic Chill (Acoustic, Classical, Lo-Fi, Jazz)
        val chill = songs.filter {
            it.genre.contains("acoustic", ignoreCase = true) ||
            it.genre.contains("classical", ignoreCase = true) ||
            it.genre.contains("jazz", ignoreCase = true) ||
            it.genre.contains("ambient", ignoreCase = true)
        }.ifEmpty { songs.sortedByDescending { it.durationMs }.take(8) }

        mixes.add(
            com.example.model.DailyMix(
                id = "daily_mix_2",
                title = "Daily Mix 2",
                subtitle = "Deep Focus & Warmth",
                description = "Soothing acoustic spaces and binaural relaxation",
                badge = "ACOUSTIC",
                songs = chill,
                coverSong = chill.firstOrNull(),
                gradientColors = listOf(0xFF4AC4E2, 0xFF2D82B7)
            )
        )

        // Daily Mix 3: Heavy Rotation & Favorites
        val favoritesAndTop = (songs.filter { it.isFavorite } + songs.sortedByDescending { it.playCount }).distinctBy { it.id }.take(10)
        val mix3Songs = favoritesAndTop.ifEmpty { songs.take(8) }

        mixes.add(
            com.example.model.DailyMix(
                id = "daily_mix_3",
                title = "Daily Mix 3",
                subtitle = "Your Heavy Rotation",
                description = "Curated based on your most repeated listening sessions",
                badge = "ON REPEAT",
                songs = mix3Songs,
                coverSong = mix3Songs.firstOrNull(),
                gradientColors = listOf(0xFF8E44AD, 0xFFE91E63)
            )
        )

        // Daily Mix 4: Deep Archive & Unheard Gems
        val unplayed = songs.filter { it.playCount == 0 }.ifEmpty { songs.shuffled() }.take(8)
        mixes.add(
            com.example.model.DailyMix(
                id = "daily_mix_4",
                title = "Daily Mix 4",
                subtitle = "Hidden Gems & Archive",
                description = "Rediscover overlooked masterpieces in your local library",
                badge = "REDISCOVER",
                songs = unplayed,
                coverSong = unplayed.firstOrNull(),
                gradientColors = listOf(0xFF16A085, 0xFF27AE60)
            )
        )

        _dailyMixes.value = mixes
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
            // Timer expired -> pause playback
            musicService?.audioEngine?.pause()
            _playbackInfo.value = _playbackInfo.value.copy(isPlaying = false, sleepTimerMinutesRemaining = null)
            musicService?.updateForegroundNotification(_currentSong.value, false)
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
            dao.upsertMetadata(
                SongMetadataEntity(
                    id = songId,
                    customTitle = newTitle,
                    customArtist = newArtist,
                    customAlbum = newAlbum,
                    customGenre = newGenre,
                    customLyrics = lyrics
                )
            )
            // Update in-memory songs
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

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, 0))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
        sleepTimerJob?.cancel()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignored
            }
            isBound = false
        }
    }
}
