package jp.yukirawa.standby.model

import org.json.JSONArray
import org.json.JSONObject

enum class ClockFace(val label: String) {
    DIGITAL("Digital"),
    ANALOG("Analog"),
    WORLD("World"),
    SOLAR("Solar"),
    FLIP("Flip"),
}

enum class StandByLayout(val label: String) {
    HERO("Hero"),
    SPLIT("Split"),
    STACK("Stack"),
}

enum class AccentColor(val label: String) {
    WHITE("White"),
    CYAN("Cyan"),
    AMBER("Amber"),
    VIOLET("Violet"),
    RED("Red"),
    GREEN("Green"),
}

enum class FontWeightOption(val label: String) {
    THIN("Thin"),
    LIGHT("Light"),
    REGULAR("Regular"),
}

enum class WidgetType(val label: String) {
    WEATHER("天気"),
    MUSIC("音楽"),
    BATTERY("バッテリー"),
}

enum class WidgetSlot(val index: Int, val label: String) {
    LEFT(0, "左"),
    CENTER(1, "中央"),
    RIGHT(2, "右"),
}

data class StandByConfig(
    val version: Int = 1,
    val presetId: String = Preset.MIDNIGHT.id,
    val layout: StandByLayout = StandByLayout.HERO,
    val clockFace: ClockFace = ClockFace.DIGITAL,
    val accent: AccentColor = AccentColor.WHITE,
    val fontWeight: FontWeightOption = FontWeightOption.THIN,
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val datePattern: String = "M月d日 (E)",
    val worldCities: List<String> = listOf("Asia/Tokyo", "America/New_York", "Europe/London"),
    val widgetSlots: List<WidgetType?> = listOf(WidgetType.WEATHER, WidgetType.MUSIC, WidgetType.BATTERY),
    val nightMode: Boolean = false,
    val brightness: Float = 0.4f,
    val dimDelaySeconds: Int = 60,
    val dimBrightness: Float = 0.2f,
    val burnInShift: Boolean = true,
    val burnInIntervalSeconds: Int = 90,
    val autoLaunch: Boolean = true,
    val wakeOnCharge: Boolean = true,
    val weatherEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val batteryEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val hyperOsConfirmed: Boolean = false,
) {
    fun widgetAt(slot: WidgetSlot): WidgetType? = widgetSlots.getOrNull(slot.index)

    fun withWidget(slot: WidgetSlot, type: WidgetType?): StandByConfig {
        val slots = widgetSlots.toMutableList().also { list ->
            while (list.size < 3) list.add(null)
        }
        // Each widget can only occupy one slot. Remove the new type from other slots.
        if (type != null) {
            for (i in slots.indices) {
                if (i != slot.index && slots[i] == type) slots[i] = null
            }
        }
        slots[slot.index] = type
        return copy(widgetSlots = slots.take(3))
    }

    fun normalized(): StandByConfig {
        val slots = widgetSlots.toMutableList().also { list ->
            while (list.size < 3) list.add(null)
        }.take(3)
        val seen = mutableSetOf<WidgetType>()
        val unique = slots.map { type ->
            if (type == null) null else if (seen.add(type)) type else null
        }
        return copy(
            brightness = brightness.coerceIn(0.02f, 1f),
            dimBrightness = dimBrightness.coerceIn(0.05f, 0.5f),
            dimDelaySeconds = dimDelaySeconds.coerceIn(0, 300),
            burnInIntervalSeconds = burnInIntervalSeconds.coerceIn(60, 120),
            worldCities = worldCities.distinct().take(3),
            widgetSlots = unique,
            datePattern = datePattern.ifBlank { "M月d日 (E)" },
        )
    }

    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("presetId", presetId)
        put("layout", layout.name)
        put("clockFace", clockFace.name)
        put("accent", accent.name)
        put("fontWeight", fontWeight.name)
        put("use24Hour", use24Hour)
        put("showSeconds", showSeconds)
        put("showDate", showDate)
        put("datePattern", datePattern)
        put("worldCities", JSONArray(worldCities))
        put("widgetSlots", JSONArray().also { array ->
            widgetSlots.take(3).forEach { array.put(it?.name ?: JSONObject.NULL) }
        })
        put("nightMode", nightMode)
        put("brightness", brightness.toDouble())
        put("dimDelaySeconds", dimDelaySeconds)
        put("dimBrightness", dimBrightness.toDouble())
        put("burnInShift", burnInShift)
        put("burnInIntervalSeconds", burnInIntervalSeconds)
        put("autoLaunch", autoLaunch)
        put("wakeOnCharge", wakeOnCharge)
        put("weatherEnabled", weatherEnabled)
        put("musicEnabled", musicEnabled)
        put("batteryEnabled", batteryEnabled)
        put("onboardingCompleted", onboardingCompleted)
        put("hyperOsConfirmed", hyperOsConfirmed)
    }.toString()

    companion object {
        fun fromJson(raw: String?): StandByConfig {
            if (raw.isNullOrBlank()) return StandByConfig()
            return runCatching {
                val json = JSONObject(raw)
                val slots = mutableListOf<WidgetType?>(null, null, null)
                json.optJSONArray("widgetSlots")?.let { array ->
                    for (i in 0 until minOf(3, array.length())) {
                        if (array.isNull(i)) {
                            slots[i] = null
                        } else {
                            slots[i] = enumOrNull<WidgetType>(array.optString(i))
                        }
                    }
                }
                val cities = json.optJSONArray("worldCities")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                } ?: listOf("Asia/Tokyo", "America/New_York", "Europe/London")
                StandByConfig(
                    version = json.optInt("version", 1),
                    presetId = json.optString("presetId", Preset.MIDNIGHT.id),
                    layout = enumOrNull<StandByLayout>(json.optString("layout")) ?: StandByLayout.HERO,
                    clockFace = enumOrNull<ClockFace>(json.optString("clockFace")) ?: ClockFace.DIGITAL,
                    accent = enumOrNull<AccentColor>(json.optString("accent")) ?: AccentColor.WHITE,
                    fontWeight = enumOrNull<FontWeightOption>(json.optString("fontWeight")) ?: FontWeightOption.THIN,
                    use24Hour = json.optBoolean("use24Hour", true),
                    showSeconds = json.optBoolean("showSeconds", false),
                    showDate = json.optBoolean("showDate", true),
                    datePattern = json.optString("datePattern", "M月d日 (E)"),
                    worldCities = cities.take(3).ifEmpty { listOf("Asia/Tokyo") },
                    widgetSlots = slots,
                    nightMode = json.optBoolean("nightMode", false),
                    brightness = json.optDouble("brightness", 0.4).toFloat(),
                    dimDelaySeconds = json.optInt("dimDelaySeconds", 60),
                    dimBrightness = json.optDouble("dimBrightness", 0.2).toFloat(),
                    burnInShift = json.optBoolean("burnInShift", true),
                    burnInIntervalSeconds = json.optInt("burnInIntervalSeconds", 90),
                    autoLaunch = json.optBoolean("autoLaunch", true),
                    wakeOnCharge = json.optBoolean("wakeOnCharge", true),
                    weatherEnabled = json.optBoolean("weatherEnabled", true),
                    musicEnabled = json.optBoolean("musicEnabled", true),
                    batteryEnabled = json.optBoolean("batteryEnabled", true),
                    onboardingCompleted = json.optBoolean("onboardingCompleted", false),
                    hyperOsConfirmed = json.optBoolean("hyperOsConfirmed", false),
                ).normalized()
            }.getOrElse { StandByConfig() }
        }

        private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
            value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
    }
}

data class CityOption(val id: String, val label: String)

val WORLD_CITIES = listOf(
    CityOption("Asia/Tokyo", "東京"),
    CityOption("America/New_York", "ニューヨーク"),
    CityOption("Europe/London", "ロンドン"),
    CityOption("Europe/Paris", "パリ"),
    CityOption("Europe/Berlin", "ベルリン"),
    CityOption("America/Los_Angeles", "ロサンゼルス"),
    CityOption("America/Vancouver", "バンクーバー"),
    CityOption("America/Honolulu", "ホノルル"),
    CityOption("Asia/Seoul", "ソウル"),
    CityOption("Asia/Shanghai", "上海"),
    CityOption("Asia/Taipei", "台北"),
    CityOption("Asia/Singapore", "シンガポール"),
    CityOption("Asia/Bangkok", "バンコク"),
    CityOption("Asia/Dubai", "ドバイ"),
    CityOption("Australia/Sydney", "シドニー"),
)
