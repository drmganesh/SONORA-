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
import com.example.model.Album
import com.example.model.Artist
import com.example.model.EqualizerBand
import com.example.model.EqPreset
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.PlaybackInfo
import com.example.model.Playlist
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
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

enum class SortOption {
    TITLE_ASC,
    TITLE_DESC,
    ARTIST_ASC,
    ALBUM_ASC,
    DURATION_DESC,
    DATE_ADDED_DESC
}

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _playbackInfo = MutableStateFlow(PlaybackInfo())
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.TITLE_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // Equalizer & Audio FX State
    private val _equalizerEnabled = MutableStateFlow(true)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

    private val _bassBoost = MutableStateFlow(600) // 0..1000
    val bassBoost: StateFlow<Int> = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow(750) // 0..1000
    val virtualizer: StateFlow<Int> = _virtualizer.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(PresetReverb.PRESET_NONE)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(300) // 0..1000 millibels
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    // 360° Spatial Audio State
    private val _spatialConfig = MutableStateFlow(SpatialAudioConfig())
    val spatialConfig: StateFlow<SpatialAudioConfig> = _spatialConfig.asStateFlow()

    // Real-time Visualizer Flows
    private val _visualizerFrequencies = MutableStateFlow(FloatArray(32) { 0f })
    val visualizerFrequencies: StateFlow<FloatArray> = _visualizerFrequencies.asStateFlow()

    private val _waveformSamples = MutableStateFlow(FloatArray(64) { 0f })
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples.asStateFlow()

    private val _visualizerStyle = MutableStateFlow(VisualizerStyle.SPECTRUM_BARS)
    val visualizerStyle: StateFlow<VisualizerStyle> = _visualizerStyle.asStateFlow()

    // Theme & Audio Settings
    private val _themeMode = MutableStateFlow(ThemeMode.AMOLED_BLACK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _gaplessEnabled = MutableStateFlow(true)
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    // Presets
    val standardPresets = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Poweramp Deep Bass", listOf(6f, 5f, 4f, 2f, 0f, 0f, 1f, 2f, 3f, 4f), bassBoost = 850, virtualizer = 600),
        EqPreset("Rock Studio", listOf(4.5f, 3f, -1.5f, -2f, 1.5f, 3f, 4.5f, 5f, 4f, 3.5f), bassBoost = 500, virtualizer = 400),
        EqPreset("Pop Vibrant", listOf(1f, 2f, 3f, 4.5f, 3f, 1f, 2f, 3.5f, 4f, 4.5f), bassBoost = 400, virtualizer = 500),
        EqPreset("Classical Master", listOf(3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 3f), reverbPreset = PresetReverb.PRESET_LARGEHALL),
        EqPreset("Jazz Club", listOf(2f, 1.5f, 0f, 1.5f, 2f, 2f, 1f, 2f, 3f, 2.5f), reverbPreset = PresetReverb.PRESET_SMALLROOM),
        EqPreset("Electronic 360", listOf(5f, 4.5f, 2f, 0f, -1f, 2f, 4f, 5.5f, 6f, 6.5f), bassBoost = 750, virtualizer = 900),
        EqPreset("Acoustic Warmth", listOf(3f, 2.5f, 1f, 1.5f, 2f, 2f, 3f, 3.5f, 3f, 2f), bassBoost = 300, virtualizer = 350),
        EqPreset("Vocal Clarity", listOf(-2f, -1f, 1f, 3.5f, 5f, 4.5f, 3.5f, 2f, 0f, -1f), virtualizer = 300)
    )

    private val _presets = MutableStateFlow(standardPresets)
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _currentPresetIndex = MutableStateFlow(0)
    val currentPresetIndex: StateFlow<Int> = _currentPresetIndex.asStateFlow()

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
                _queue.value = songs
                _currentSong.value = songs.first()
            }
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

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        var nextIdx = _queueIndex.value + 1
        if (nextIdx >= q.size) {
            nextIdx = if (_playbackInfo.value.repeatMode == RepeatMode.ALL) 0 else return
        }
        playSong(q[nextIdx], q, nextIdx)
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
                }
            }
        }
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
        musicService?.audioEngine?.setEqualizerEnabled(enabled)
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        musicService?.audioEngine?.setBandGain(bandIndex, gainMb)
        val updated = _equalizerBands.value.map {
            if (it.bandIndex == bandIndex) it.copy(currentLevelMb = gainMb) else it
        }
        _equalizerBands.value = updated
    }

    fun selectPreset(index: Int) {
        if (index !in _presets.value.indices) return
        _currentPresetIndex.value = index
        val preset = _presets.value[index]

        // Apply band gains
        val bands = _equalizerBands.value
        bands.forEachIndexed { i, band ->
            val gainDb = preset.gainsDb.getOrElse(i) { 0f }
            val gainMb = (gainDb * 100).toInt().toShort()
            musicService?.audioEngine?.setBandGain(band.bandIndex, gainMb)
        }

        if (preset.bassBoost > 0) setBassBoost(preset.bassBoost)
        if (preset.virtualizer > 0) setVirtualizer(preset.virtualizer)
        if (preset.reverbPreset != PresetReverb.PRESET_NONE) setReverb(preset.reverbPreset)
    }

    fun setBassBoost(value: Int) {
        _bassBoost.value = value
        musicService?.audioEngine?.setBassBoost(value)
    }

    fun setVirtualizer(value: Int) {
        _virtualizer.value = value
        musicService?.audioEngine?.setVirtualizer(value)
    }

    fun setReverb(preset: Short) {
        _reverbPreset.value = preset
        musicService?.audioEngine?.setReverbPreset(preset)
    }

    fun setLoudness(gainMb: Int) {
        _loudnessGainMb.value = gainMb
        musicService?.audioEngine?.setLoudnessEnhancer(gainMb)
    }

    fun updateSpatialConfig(config: SpatialAudioConfig) {
        _spatialConfig.value = config
        musicService?.audioEngine?.updateSpatialAudioConfig(config)
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _visualizerStyle.value = style
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setCrossfadeSeconds(sec: Int) {
        _crossfadeSeconds.value = sec
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
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
