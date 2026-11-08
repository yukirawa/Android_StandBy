package jp.yukirawa.standby.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import jp.yukirawa.standby.data.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class WeatherKind {
    SUN,
    MOON,
    PARTLY_DAY,
    PARTLY_NIGHT,
    CLOUD,
    FOG,
    RAIN,
    SHOWER,
    SNOW,
    THUNDER,
}

data class HourlyWeather(
    val time: LocalDateTime,
    val temperature: Float,
    val code: Int,
)

data class WeatherState(
    val available: Boolean = false,
    val temperature: Float? = null,
    val apparent: Float? = null,
    val humidity: Int? = null,
    val wind: Float? = null,
    val code: Int = 0,
    val isDay: Boolean = true,
    val max: Float? = null,
    val min: Float? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val hourly: List<HourlyWeather> = emptyList(),
    val updatedAt: Long = 0L,
    val locationLabel: String = "東京",
    val isFallback: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val kind: WeatherKind get() = weatherKind(code, isDay)

    fun summary(): String = weatherLabel(code)

    fun toCacheJson(): JSONObject = JSONObject().apply {
        put("temperature", temperature ?: JSONObject.NULL)
        put("apparent", apparent ?: JSONObject.NULL)
        put("humidity", humidity ?: JSONObject.NULL)
        put("wind", wind ?: JSONObject.NULL)
        put("code", code)
        put("isDay", isDay)
        put("max", max ?: JSONObject.NULL)
        put("min", min ?: JSONObject.NULL)
        put("sunrise", sunrise ?: JSONObject.NULL)
        put("sunset", sunset ?: JSONObject.NULL)
        put("updatedAt", updatedAt)
        put("locationLabel", locationLabel)
        put("isFallback", isFallback)
        put("hourly", JSONArray().also { array ->
            hourly.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("time", item.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        put("temperature", item.temperature.toDouble())
                        put("code", item.code)
                    },
                )
            }
        })
    }

    companion object {
        fun fromCacheJson(json: JSONObject): WeatherState {
            val hourly = buildList {
                json.optJSONArray("hourly")?.let { array ->
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val time = runCatching {
                            LocalDateTime.parse(
                                item.optString("time"),
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                            )
                        }.getOrNull() ?: continue
                        add(
                            HourlyWeather(
                                time = time,
                                temperature = item.optDouble("temperature", 0.0).toFloat(),
                                code = item.optInt("code", 0),
                            ),
                        )
                    }
                }
            }
            return WeatherState(
                available = true,
                temperature = json.optNullableDouble("temperature")?.toFloat(),
                apparent = json.optNullableDouble("apparent")?.toFloat(),
                humidity = json.optNullableDouble("humidity")?.toInt(),
                wind = json.optNullableDouble("wind")?.toFloat(),
                code = json.optInt("code", 0),
                isDay = json.optBoolean("isDay", true),
                max = json.optNullableDouble("max")?.toFloat(),
                min = json.optNullableDouble("min")?.toFloat(),
                sunrise = json.optNullableString("sunrise"),
                sunset = json.optNullableString("sunset"),
                hourly = hourly,
                updatedAt = json.optLong("updatedAt", 0L),
                locationLabel = json.optString("locationLabel", "東京"),
                isFallback = json.optBoolean("isFallback", true),
            )
        }

        private fun JSONObject.optNullableDouble(key: String): Double? =
            if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

        private fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

class WeatherRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val diagnostics: DiagnosticsLog,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val cacheFile = File(context.filesDir, "weather_cache.json")
    private val httpExecutor = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow(loadCache() ?: WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var inFlight = false

    fun refresh() {
        if (inFlight) return
        inFlight = true
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            val fresh = fetch()
            if (fresh != null) {
                _state.value = fresh
                saveCache(fresh)
                diagnostics.add(
                    "天気を更新: ${fresh.temperature?.roundToInt()}℃ ${fresh.summary()}" +
                        " (${fresh.locationLabel})",
                )
            } else {
                val cached = loadCache()
                _state.value = (cached ?: _state.value).copy(
                    loading = false,
                    error = "天気を取得できませんでした",
                )
                diagnostics.add("天気の取得に失敗しました")
            }
            inFlight = false
        }
    }

    private suspend fun fetch(): WeatherState? = withContext(Dispatchers.IO) {
        runCatching {
            val location = resolveLocation()
            val latitude = location?.latitude ?: TOKYO_LAT
            val longitude = location?.longitude ?: TOKYO_LON
            val fallback = location == null
            val label = if (fallback) "東京" else "現在地"

            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&timezone=auto&forecast_days=2"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StandBy-Android/0.1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                parse(body, label, fallback)
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveLocation(): Location? {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val last = providers
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000L) {
            return last
        }

        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER

            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER

            else -> null
        } ?: return last

        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                runCatching {
                    manager.getCurrentLocation(provider, cancellation, httpExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } ?: last
    }

    private fun parse(body: String, label: String, fallback: Boolean): WeatherState? {
        val json = JSONObject(body)
        val current = json.optJSONObject("current") ?: return null
        val daily = json.optJSONObject("daily")
        val hourly = json.optJSONObject("hourly")

        val currentTime = runCatching {
            LocalDateTime.parse(current.optString("time"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrNull()

        val forecast = buildList {
            val times = hourly?.optJSONArray("time")
            val temperatures = hourly?.optJSONArray("temperature_2m")
            val codes = hourly?.optJSONArray("weather_code")
            if (times != null && temperatures != null && codes != null) {
                for (i in 0 until minOf(times.length(), temperatures.length(), codes.length())) {
                    val time = runCatching {
                        LocalDateTime.parse(times.optString(i), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    }.getOrNull() ?: continue
                    if (currentTime != null && time.isBefore(currentTime.withMinute(0).withSecond(0))) continue
                    add(
                        HourlyWeather(
                            time = time,
                            temperature = temperatures.optDouble(i).toFloat(),
                            code = codes.optInt(i),
                        ),
                    )
                    if (size >= 4) break
                }
            }
        }

        return WeatherState(
            available = true,
            temperature = current.optDouble("temperature_2m").toFloat(),
            apparent = current.optDouble("apparent_temperature").toFloat(),
            humidity = current.optInt("relative_humidity_2m"),
            wind = current.optDouble("wind_speed_10m").toFloat(),
            code = current.optInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            max = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.toFloat(),
            min = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.toFloat(),
            sunrise = daily?.optJSONArray("sunrise")?.optString(0)?.substringAfter('T'),
            sunset = daily?.optJSONArray("sunset")?.optString(0)?.substringAfter('T'),
            hourly = forecast,
            updatedAt = System.currentTimeMillis(),
            locationLabel = label,
            isFallback = fallback,
            loading = false,
        )
    }

    private fun loadCache(): WeatherState? = runCatching {
        if (!cacheFile.exists()) null
        else WeatherState.fromCacheJson(JSONObject(cacheFile.readText()))
    }.getOrNull()?.copy(loading = false, error = null)

    private fun saveCache(state: WeatherState) {
        runCatching { cacheFile.writeText(state.toCacheJson().toString()) }
    }

    private companion object {
        const val TOKYO_LAT = 35.6762
        const val TOKYO_LON = 139.6503
    }
}

fun weatherLabel(code: Int): String = when (code) {
    0 -> "快晴"
    1 -> "晴れ"
    2 -> "晴れ時々曇り"
    3 -> "曇り"
    45, 48 -> "霧"
    51, 53, 55, 56, 57 -> "霧雨"
    61, 63, 65, 66, 67 -> "雨"
    71, 73, 75, 77 -> "雪"
    80, 81, 82 -> "にわか雨"
    85, 86 -> "にわか雪"
    95, 96, 99 -> "雷雨"
    else -> "—"
}

fun weatherKind(code: Int, isDay: Boolean): WeatherKind = when (code) {
    0 -> if (isDay) WeatherKind.SUN else WeatherKind.MOON
    1, 2 -> if (isDay) WeatherKind.PARTLY_DAY else WeatherKind.PARTLY_NIGHT
    3 -> WeatherKind.CLOUD
    45, 48 -> WeatherKind.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67 -> WeatherKind.RAIN
    71, 73, 75, 77 -> WeatherKind.SNOW
    80, 81, 82 -> WeatherKind.SHOWER
    85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.THUNDER
    else -> WeatherKind.CLOUD
}
