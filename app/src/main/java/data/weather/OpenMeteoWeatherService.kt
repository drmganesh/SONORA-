package data.weather

import domain.weather.HourlyWeatherPoint
import domain.weather.OdishaWeatherLocation
import domain.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight HTTP transport abstraction so Open-Meteo requests can be unit-tested cleanly
 * without live internet dependencies.
 */
fun interface WeatherHttpTransport {
    @Throws(IOException::class)
    fun fetchUrl(url: String): String
}

/**
 * Public Open-Meteo weather client (https://api.open-meteo.com/v1/forecast).
 *
 * Privacy & Location Safety Guarantees:
 * - Accepts ONLY a manually selected [OdishaWeatherLocation] enum value (5 hardcoded Odisha towns).
 * - Sends ONLY the selected town's fixed coordinates and standard weather parameters.
 * - No API key required, no GPS, no LocationManager, no IP geolocation, no personal data.
 */
class OpenMeteoWeatherService(
    private val transport: WeatherHttpTransport = defaultTransport()
) {
    companion object {
        const val OPEN_METEO_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val TIMEZONE = "Asia/Kolkata"

        val CURRENT_VARIABLES = listOf(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "is_day",
            "precipitation",
            "rain",
            "showers",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m"
        )

        val DAILY_VARIABLES = listOf("sunrise", "sunset")
        val HOURLY_VARIABLES = listOf("temperature_2m", "weather_code", "precipitation", "cloud_cover")

        private fun defaultTransport(): WeatherHttpTransport {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
            return WeatherHttpTransport { url ->
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SONORA-Android/${com.example.BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Open-Meteo HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty Open-Meteo response body")
                }
            }
        }

        /**
         * Builds the exact Open-Meteo forecast URL containing ONLY the fixed town coordinates
         * and required meteorological variables in metric units and Asia/Kolkata timezone.
         */
        fun buildForecastUrl(location: OdishaWeatherLocation): String {
            return OPEN_METEO_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", location.latitude.toString())
                .addQueryParameter("longitude", location.longitude.toString())
                .addQueryParameter("current", CURRENT_VARIABLES.joinToString(","))
                .addQueryParameter("daily", DAILY_VARIABLES.joinToString(","))
                .addQueryParameter("hourly", HOURLY_VARIABLES.joinToString(","))
                .addQueryParameter("timezone", TIMEZONE)
                .addQueryParameter("temperature_unit", "celsius")
                .addQueryParameter("wind_speed_unit", "kmh")
                .addQueryParameter("precipitation_unit", "mm")
                .addQueryParameter("forecast_days", "1")
                .build()
                .toString()
        }

        /**
         * Parses an Open-Meteo JSON response into a strongly typed [WeatherSnapshot].
         */
        fun parseForecastJson(
            location: OdishaWeatherLocation,
            rawJson: String,
            fetchedAtEpochMs: Long = System.currentTimeMillis(),
            isFromCache: Boolean = false
        ): WeatherSnapshot {
            val root = JSONObject(rawJson)
            val current = root.getJSONObject("current")

            val daily = root.optJSONObject("daily")
            val sunriseIso = daily?.optJSONArray("sunrise")?.optString(0)?.takeIf { it.isNotBlank() }
            val sunsetIso = daily?.optJSONArray("sunset")?.optString(0)?.takeIf { it.isNotBlank() }

            val hourlyPoints = mutableListOf<HourlyWeatherPoint>()
            val hourly = root.optJSONObject("hourly")
            if (hourly != null) {
                val times = hourly.optJSONArray("time")
                val temps = hourly.optJSONArray("temperature_2m")
                val codes = hourly.optJSONArray("weather_code")
                val precips = hourly.optJSONArray("precipitation")
                val clouds = hourly.optJSONArray("cloud_cover")
                val count = minOf(times?.length() ?: 0, 24)
                for (i in 0 until count) {
                    hourlyPoints.add(
                        HourlyWeatherPoint(
                            timeIso = times?.optString(i).orEmpty(),
                            temperatureC = temps?.optDouble(i, 0.0) ?: 0.0,
                            weatherCode = codes?.optInt(i, 0) ?: 0,
                            precipitationMm = precips?.optDouble(i, 0.0) ?: 0.0,
                            cloudCoverPct = clouds?.optInt(i, 0) ?: 0
                        )
                    )
                }
            }

            return WeatherSnapshot(
                locationId = location.id,
                latitude = root.optDouble("latitude", location.latitude),
                longitude = root.optDouble("longitude", location.longitude),
                temperatureC = current.optDouble("temperature_2m", 0.0),
                relativeHumidityPct = current.optInt("relative_humidity_2m", 0).coerceIn(0, 100),
                apparentTemperatureC = current.optDouble("apparent_temperature", current.optDouble("temperature_2m", 0.0)),
                isDay = current.optInt("is_day", 1) == 1,
                precipitationMm = current.optDouble("precipitation", 0.0).coerceAtLeast(0.0),
                rainMm = current.optDouble("rain", 0.0).coerceAtLeast(0.0),
                showersMm = current.optDouble("showers", 0.0).coerceAtLeast(0.0),
                snowfallCm = current.optDouble("snowfall", 0.0).coerceAtLeast(0.0),
                weatherCode = current.optInt("weather_code", 0),
                cloudCoverPct = current.optInt("cloud_cover", 0).coerceIn(0, 100),
                windSpeedKmh = current.optDouble("wind_speed_10m", 0.0).coerceAtLeast(0.0),
                windDirectionDeg = ((current.optDouble("wind_direction_10m", 0.0) % 360.0) + 360.0) % 360.0,
                windGustsKmh = current.optDouble("wind_gusts_10m", 0.0).coerceAtLeast(0.0),
                sunriseIso = sunriseIso,
                sunsetIso = sunsetIso,
                hourlyForecast = hourlyPoints,
                fetchedAtEpochMs = fetchedAtEpochMs,
                isFromCache = isFromCache
            )
        }

        /**
         * Serializes a [WeatherSnapshot] into compact JSON for local offline cache persistence.
         */
        fun serializeSnapshotToCacheJson(snapshot: WeatherSnapshot): String {
            val root = JSONObject().apply {
                put("locationId", snapshot.locationId)
                put("latitude", snapshot.latitude)
                put("longitude", snapshot.longitude)
                put("temperatureC", snapshot.temperatureC)
                put("relativeHumidityPct", snapshot.relativeHumidityPct)
                put("apparentTemperatureC", snapshot.apparentTemperatureC)
                put("isDay", snapshot.isDay)
                put("precipitationMm", snapshot.precipitationMm)
                put("rainMm", snapshot.rainMm)
                put("showersMm", snapshot.showersMm)
                put("snowfallCm", snapshot.snowfallCm)
                put("weatherCode", snapshot.weatherCode)
                put("cloudCoverPct", snapshot.cloudCoverPct)
                put("windSpeedKmh", snapshot.windSpeedKmh)
                put("windDirectionDeg", snapshot.windDirectionDeg)
                put("windGustsKmh", snapshot.windGustsKmh)
                put("sunriseIso", snapshot.sunriseIso ?: "")
                put("sunsetIso", snapshot.sunsetIso ?: "")
                put("fetchedAtEpochMs", snapshot.fetchedAtEpochMs)
                val hourlyArr = JSONArray()
                for (pt in snapshot.hourlyForecast.take(24)) {
                    hourlyArr.put(
                        JSONObject().apply {
                            put("timeIso", pt.timeIso)
                            put("temperatureC", pt.temperatureC)
                            put("weatherCode", pt.weatherCode)
                            put("precipitationMm", pt.precipitationMm)
                            put("cloudCoverPct", pt.cloudCoverPct)
                        }
                    )
                }
                put("hourlyForecast", hourlyArr)
            }
            return root.toString()
        }

        fun deserializeCachedSnapshot(rawJson: String): WeatherSnapshot? {
            return runCatching {
                val root = JSONObject(rawJson)
                val hourly = mutableListOf<HourlyWeatherPoint>()
                val arr = root.optJSONArray("hourlyForecast")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        hourly.add(
                            HourlyWeatherPoint(
                                timeIso = obj.optString("timeIso"),
                                temperatureC = obj.optDouble("temperatureC", 0.0),
                                weatherCode = obj.optInt("weatherCode", 0),
                                precipitationMm = obj.optDouble("precipitationMm", 0.0),
                                cloudCoverPct = obj.optInt("cloudCoverPct", 0)
                            )
                        )
                    }
                }
                WeatherSnapshot(
                    locationId = root.getString("locationId"),
                    latitude = root.getDouble("latitude"),
                    longitude = root.getDouble("longitude"),
                    temperatureC = root.getDouble("temperatureC"),
                    relativeHumidityPct = root.optInt("relativeHumidityPct", 0),
                    apparentTemperatureC = root.optDouble("apparentTemperatureC", root.getDouble("temperatureC")),
                    isDay = root.optBoolean("isDay", true),
                    precipitationMm = root.optDouble("precipitationMm", 0.0),
                    rainMm = root.optDouble("rainMm", 0.0),
                    showersMm = root.optDouble("showersMm", 0.0),
                    snowfallCm = root.optDouble("snowfallCm", 0.0),
                    weatherCode = root.optInt("weatherCode", 0),
                    cloudCoverPct = root.optInt("cloudCoverPct", 0),
                    windSpeedKmh = root.optDouble("windSpeedKmh", 0.0),
                    windDirectionDeg = root.optDouble("windDirectionDeg", 0.0),
                    windGustsKmh = root.optDouble("windGustsKmh", 0.0),
                    sunriseIso = root.optString("sunriseIso").takeIf { it.isNotBlank() },
                    sunsetIso = root.optString("sunsetIso").takeIf { it.isNotBlank() },
                    hourlyForecast = hourly,
                    fetchedAtEpochMs = root.optLong("fetchedAtEpochMs", 0L),
                    isFromCache = true
                )
            }.getOrNull()
        }
    }

    suspend fun fetchCurrentWeather(
        location: OdishaWeatherLocation,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildForecastUrl(location)
            val body = transport.fetchUrl(url)
            parseForecastJson(
                location = location,
                rawJson = body,
                fetchedAtEpochMs = nowEpochMs,
                isFromCache = false
            )
        }
    }
}
