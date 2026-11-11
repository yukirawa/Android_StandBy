package jp.yukirawa.standby.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TimeFormat {
    private val japanese = Locale.JAPANESE

    // Patterns are compiled once. The clock formats on every tick, so rebuilding a
    // DateTimeFormatter each time would be pure allocation churn.
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String): DateTimeFormatter =
        formatters.computeIfAbsent(pattern) { DateTimeFormatter.ofPattern(it, japanese) }

    fun zoned(now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        now.atZone(zoneId)

    fun clock(
        time: ZonedDateTime,
        use24Hour: Boolean,
        showSeconds: Boolean,
    ): String {
        val pattern = when {
            use24Hour && showSeconds -> "HH:mm:ss"
            use24Hour -> "HH:mm"
            showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        return formatter(pattern).format(time)
    }

    fun amPm(time: ZonedDateTime, use24Hour: Boolean): String? {
        if (use24Hour) return null
        return if (time.hour < 12) "午前" else "午後"
    }

    fun date(time: ZonedDateTime, pattern: String = "M月d日 (E)"): String =
        runCatching { formatter(pattern).format(time) }
            .getOrElse { formatter("M月d日 (E)").format(time) }

    fun worldTime(instant: Instant, zoneId: String, use24Hour: Boolean): String =
        runCatching {
            clock(instant.atZone(ZoneId.of(zoneId)), use24Hour, showSeconds = false)
        }.getOrElse { "--:--" }

    fun cityLabel(zoneId: String): String =
        jp.yukirawa.standby.model.WORLD_CITIES.firstOrNull { it.id == zoneId }?.label
            ?: runCatching { ZoneId.of(zoneId).id.substringAfterLast('/').replace('_', ' ') }
                .getOrDefault(zoneId)

    fun nextMinuteDelayMillis(now: Instant = Instant.now()): Long {
        val millis = now.toEpochMilli()
        return 60_000L - (millis % 60_000L)
    }

    fun nextSecondDelayMillis(now: Instant = Instant.now()): Long {
        val millis = now.toEpochMilli()
        return 1_000L - (millis % 1_000L)
    }

    fun relativeUpdatedLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "未取得"
        val minutes = ((now - epochMillis) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> "たった今"
            minutes < 60 -> "${minutes}分前"
            else -> "${minutes / 60}時間前"
        }
    }
}
