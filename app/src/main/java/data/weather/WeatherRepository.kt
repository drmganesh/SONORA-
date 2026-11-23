package data.weather

import android.util.Log
import data.preferences.AmbientEffectsPreferences
import domain.weather.OdishaWeatherLocation
import domain.weather.WeatherFetchState
import domain.weather.WeatherSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val WEATHER_REPO_TAG = "WeatherRepository"

/**
 * Coordinates Open-Meteo public weather fetching, DataStore caching, offline fallback,
 * rate-limited periodic refreshes (every 20 minutes while active), and per-location
 * summaries for the 5 fixed Odisha locations.
 *
 * Never fabricates weather data when offline without a prior valid cache.
 */
class WeatherRepository(
    private val preferences: AmbientEffectsPreferences,
    private val service: OpenMeteoWeatherService = OpenMeteoWeatherService(),
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** Standard periodic refresh interval (20 minutes, inside the 15–30 minute target window). */
        const val PERIODIC_REFRESH_INTERVAL_MS = 20L * 60L * 1000L

        /** Minimum freshness window before a non-forced location switch re-fetches from network. */
        const val MIN_FRESH_CACHE_MS = 5L * 60L * 1000L
    }

    private val fetchMutex = Mutex()

    private val _selectedWeatherState = MutableStateFlow<WeatherFetchState>(WeatherFetchState.Idle)
    val selectedWeatherState: StateFlow<WeatherFetchState> = _selectedWeatherState.asStateFlow()

    private val _cachedSnapshotsByLocation = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())
    val cachedSnapshotsByLocation: StateFlow<Map<String, WeatherSnapshot>> = _cachedSnapshotsByLocation.asStateFlow()

    /**
     * Hydrates all previously cached snapshots from DataStore on startup so the UI immediately
     * shows last valid weather while online refresh runs in the background.
     */
    suspend fun hydrateCacheFromDisk(selectedLocation: OdishaWeatherLocation) {
        val rawMap = runCatching { preferences.cachedWeatherJsonByLocationFlow.first() }.getOrDefault(emptyMap())
        val parsed = buildMap {
            for ((locId, json) in rawMap) {
                OpenMeteoWeatherService.deserializeCachedSnapshot(json)?.let { snap ->
                    put(locId, snap)
                }
            }
        }
        _cachedSnapshotsByLocation.value = parsed
        val selectedCached = parsed[selectedLocation.id]
        if (selectedCached != null && _selectedWeatherState.value is WeatherFetchState.Idle) {
            _selectedWeatherState.value = WeatherFetchState.Success(
                snapshot = selectedCached,
                isOfflineCache = true
            )
        }
    }

    /**
     * Refreshes weather for [location].
     * - If a fresh cached response exists (< [MIN_FRESH_CACHE_MS]) and [force] is false, avoids redundant API calls.
     * - On network/API failure, gracefully falls back to the last valid cached response without fabricating data.
     */
    suspend fun refreshWeather(
        location: OdishaWeatherLocation,
        force: Boolean = false
    ): WeatherFetchState = fetchMutex.withLock {
        val now = clockEpochMs()
        val cached = _cachedSnapshotsByLocation.value[location.id]

        if (!force && cached != null && (now - cached.fetchedAtEpochMs) in 0L until MIN_FRESH_CACHE_MS) {
            val freshState = WeatherFetchState.Success(snapshot = cached, isOfflineCache = false)
            _selectedWeatherState.value = freshState
            return@withLock freshState
        }

        _selectedWeatherState.value = WeatherFetchState.Loading(previousSnapshot = cached)

        val result = service.fetchCurrentWeather(location = location, nowEpochMs = now)
        result.fold(
            onSuccess = { snapshot ->
                _cachedSnapshotsByLocation.update { it + (location.id to snapshot) }
                val serialized = OpenMeteoWeatherService.serializeSnapshotToCacheJson(snapshot)
                preferences.saveCachedWeatherJson(location.id, serialized)
                val success = WeatherFetchState.Success(snapshot = snapshot, isOfflineCache = false)
                _selectedWeatherState.value = success
                success
            },
            onFailure = { error ->
                Log.w(WEATHER_REPO_TAG, "Weather fetch failed for ${location.townName}: ${error.message}")
                val fallbackState = if (cached != null) {
                    WeatherFetchState.Success(
                        snapshot = cached.copy(isFromCache = true),
                        isOfflineCache = true
                    )
                } else {
                    WeatherFetchState.Error(
                        message = "Weather temporarily unavailable (offline or network error)",
                        cachedSnapshot = null
                    )
                }
                _selectedWeatherState.value = fallbackState
                fallbackState
            }
        )
    }

    /**
     * Switches active weather state immediately when the user selects another Odisha town,
     * showing cached data right away (if any) and refreshing if stale or uncached.
     */
    suspend fun selectAndRefreshLocation(
        location: OdishaWeatherLocation,
        force: Boolean = false
    ): WeatherFetchState {
        val cached = _cachedSnapshotsByLocation.value[location.id]
        if (cached != null) {
            _selectedWeatherState.value = WeatherFetchState.Success(snapshot = cached, isOfflineCache = cached.isFromCache)
        } else {
            _selectedWeatherState.value = WeatherFetchState.Loading(previousSnapshot = null)
        }
        return refreshWeather(location = location, force = force)
    }

    /**
     * Lazily refreshes stale cards for the other four fixed Odisha towns when the user opens
     * the Weather Location picker, avoiding unnecessary requests if already fresh.
     */
    suspend fun refreshLocationSummariesIfStale(activeLocation: OdishaWeatherLocation) {
        val now = clockEpochMs()
        for (loc in OdishaWeatherLocation.entries) {
            if (loc == activeLocation) continue
            val existing = _cachedSnapshotsByLocation.value[loc.id]
            if (existing != null && (now - existing.fetchedAtEpochMs) in 0L until PERIODIC_REFRESH_INTERVAL_MS) {
                continue
            }
            val res = service.fetchCurrentWeather(location = loc, nowEpochMs = now)
            res.onSuccess { snapshot ->
                _cachedSnapshotsByLocation.update { it + (loc.id to snapshot) }
                val serialized = OpenMeteoWeatherService.serializeSnapshotToCacheJson(snapshot)
                preferences.saveCachedWeatherJson(loc.id, serialized)
            }
        }
    }
}
