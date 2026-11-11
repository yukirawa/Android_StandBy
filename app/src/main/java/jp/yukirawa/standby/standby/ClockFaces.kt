package jp.yukirawa.standby.standby

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.standby.model.ClockFace
import jp.yukirawa.standby.model.FontWeightOption
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.ui.theme.AccentPalette
import jp.yukirawa.standby.util.TimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * [now] is passed as a [State] on purpose: the value is read inside each clock, so a tick only
 * invalidates the clock instead of the whole standby screen.
 */
@Composable
fun ClockSection(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    when (config.clockFace) {
        ClockFace.DIGITAL -> DigitalClock(config, now, palette, modifier)
        ClockFace.ANALOG -> AnalogClock(config, now, palette, modifier)
        ClockFace.WORLD -> WorldClock(config, now, palette, modifier)
        ClockFace.SOLAR -> SolarClock(config, now, palette, modifier)
        ClockFace.FLIP -> FlipClock(config, now, palette, modifier)
    }
}

@Composable
fun DigitalClock(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val time = now.value.atZone(zone)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxHeight.value * 0.54f, maxWidth.value * 0.19f).coerceIn(54f, 190f)
        val weight = config.fontWeight.composeWeight()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TimeFormat.clock(time, config.use24Hour, config.showSeconds),
                    color = palette.text,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = weight,
                        fontSize = size.sp,
                        letterSpacing = (-size * 0.035f).sp,
                        fontFeatureSettings = "tnum",
                    ),
                )
                TimeFormat.amPm(time, config.use24Hour)?.let { amPm ->
                    Text(
                        text = amPm,
                        color = palette.muted,
                        modifier = Modifier.padding(start = 10.dp, bottom = size.dp * 0.11f),
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            fontSize = (size * 0.17f).coerceIn(11f, 26f).sp,
                        ),
                    )
                }
            }
            if (config.showDate) {
                Text(
                    text = TimeFormat.date(time, config.datePattern),
                    color = palette.text.copy(alpha = 0.68f),
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = (size * 0.115f).coerceIn(13f, 24f).sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AnalogClock(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    val time = now.value.atZone(ZoneId.systemDefault())
    // Reused across ticks instead of allocating a Paint on every frame.
    val datePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth.value, maxHeight.value).coerceIn(130f, 300f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(diameter.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f * 0.9f
                val rim = 1.dp.toPx()

                drawCircle(color = palette.cardBorder, radius = radius, style = Stroke(rim))
                for (i in 0 until 60) {
                    val major = i % 5 == 0
                    val angle = Math.toRadians(i * 6.0 - 90.0)
                    val outer = radius - 2.dp.toPx()
                    val inner = outer - if (major) 9.dp.toPx() else 5.dp.toPx()
                    drawLine(
                        color = if (major) palette.text.copy(alpha = 0.75f) else palette.muted.copy(alpha = 0.4f),
                        start = polar(center, inner, angle),
                        end = polar(center, outer, angle),
                        strokeWidth = if (major) 1.7.dp.toPx() else 0.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                val second = time.second.toFloat()
                val minute = time.minute + second / 60f
                val hour = (time.hour % 12) + minute / 60f
                drawHand(
                    center = center,
                    angle = hour * 30f - 90f,
                    length = radius * 0.5f,
                    color = palette.text,
                    width = 4.dp.toPx(),
                )
                drawHand(
                    center = center,
                    angle = minute * 6f - 90f,
                    length = radius * 0.7f,
                    color = palette.text,
                    width = 3.dp.toPx(),
                )
                drawHand(
                    center = center,
                    angle = second * 6f - 90f,
                    length = radius * 0.8f,
                    color = palette.primary,
                    width = 1.5.dp.toPx(),
                )
                drawCircle(color = palette.primary, radius = 3.dp.toPx(), center = center)

                val windowSize = Size(radius * 0.42f, radius * 0.24f)
                val windowTopLeft = Offset(
                    center.x - windowSize.width / 2f,
                    center.y + radius * 0.38f,
                )
                drawRoundRect(
                    color = palette.card,
                    topLeft = windowTopLeft,
                    size = windowSize,
                    cornerRadius = CornerRadius(radius * 0.055f),
                )
                drawRoundRect(
                    color = palette.cardBorder,
                    topLeft = windowTopLeft,
                    size = windowSize,
                    cornerRadius = CornerRadius(radius * 0.055f),
                    style = Stroke(1.dp.toPx()),
                )
                drawIntoCanvas { canvas ->
                    datePaint.color = palette.primary.toArgb()
                    datePaint.textSize = radius * 0.13f
                    val baseline = windowTopLeft.y + windowSize.height / 2f -
                        (datePaint.ascent() + datePaint.descent()) / 2f
                    canvas.nativeCanvas.drawText(
                        time.dayOfMonth.toString(),
                        center.x,
                        baseline,
                        datePaint,
                    )
                }
            }
            if (config.showDate) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = TimeFormat.date(time, config.datePattern),
                    color = palette.text.copy(alpha = 0.68f),
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WorldClock(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    val instant = now.value
    val time = instant.atZone(ZoneId.systemDefault())
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxHeight.value * 0.4f, maxWidth.value * 0.15f).coerceIn(46f, 136f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = TimeFormat.clock(time, config.use24Hour, config.showSeconds),
                color = palette.text,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = config.fontWeight.composeWeight(),
                    fontSize = size.sp,
                    letterSpacing = (-size * 0.035f).sp,
                    fontFeatureSettings = "tnum",
                ),
            )
            if (config.showDate) {
                Text(
                    text = TimeFormat.date(time, config.datePattern),
                    color = palette.text.copy(alpha = 0.68f),
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = (size * 0.13f).coerceIn(12f, 20f).sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                config.worldCities.take(3).forEach { zoneId ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = TimeFormat.cityLabel(zoneId),
                            color = palette.muted,
                            maxLines = 1,
                            style = TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                            ),
                        )
                        Text(
                            text = TimeFormat.worldTime(instant, zoneId, config.use24Hour),
                            color = palette.primary,
                            maxLines = 1,
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = (size * 0.19f).coerceIn(15f, 28f).sp,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SolarClock(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    DigitalClock(config, now, palette, modifier)
}

@Composable
private fun FlipClock(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    val time = now.value.atZone(ZoneId.systemDefault())
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val card = minOf(maxHeight.value * 0.42f, maxWidth.value * 0.115f).coerceIn(38f, 100f)
        // Locale-independent digits: the flip clock always shows western numerals.
        val hours = String.format(Locale.US, "%02d", if (config.use24Hour) time.hour else ((time.hour % 12).takeIf { it != 0 } ?: 12))
        val minutes = String.format(Locale.US, "%02d", time.minute)
        val seconds = String.format(Locale.US, "%02d", time.second)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlipPair(hours, card, palette)
                Text(
                    text = ":",
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = TextStyle(
                        fontWeight = FontWeight.Thin,
                        fontSize = (card * 0.55f).sp,
                    ),
                )
                FlipPair(minutes, card, palette)
                if (config.showSeconds) {
                    Text(
                        text = ":",
                        color = palette.muted,
                        modifier = Modifier.padding(horizontal = 6.dp),
                        style = TextStyle(
                            fontWeight = FontWeight.Thin,
                            fontSize = (card * 0.55f).sp,
                        ),
                    )
                    FlipPair(seconds, card, palette)
                }
            }
            if (config.showDate) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = TimeFormat.date(time, config.datePattern),
                    color = palette.text.copy(alpha = 0.68f),
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FlipPair(value: String, card: Float, palette: AccentPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        value.forEach { digit ->
            FlipDigit(digit, card, palette)
        }
    }
}

@Composable
private fun FlipDigit(value: Char, card: Float, palette: AccentPalette) {
    Box(
        modifier = Modifier
            .size(card.dp)
            .clip(RoundedCornerShape(card.dp * 0.18f))
            .background(palette.card)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(card.dp * 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(110))
            },
            label = "flip",
        ) { digit ->
            Text(
                text = digit.toString(),
                color = palette.text,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Thin,
                    fontSize = (card * 0.68f).sp,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

@Composable
fun SolarSky(now: State<Instant>, palette: AccentPalette) {
    // The sky changes slowly; only recompute it when the minute changes instead of every tick.
    val minute by remember(now) {
        derivedStateOf { now.value.truncatedTo(ChronoUnit.MINUTES) }
    }
    val time = minute.atZone(ZoneId.systemDefault())
    val hour = time.hour + time.minute / 60f
    val (top, glow) = when {
        hour < 5f || hour >= 20f -> Color(0xFF07101F) to Color(0xFF16213A)
        hour < 7f -> Color(0xFF2A1630) to Color(0xFFB45A3C)
        hour < 17f -> Color(0xFF0B2338) to Color(0xFF2A6FA0)
        hour < 19.5f -> Color(0xFF2B1430) to Color(0xFFD06A3A)
        else -> Color(0xFF160F26) to Color(0xFF54326A)
    }
    val dayProgress = ((hour - 6f) / 12f).coerceIn(0f, 1f)
    val isDay = hour in 6f..18f
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to top.copy(alpha = 0.55f),
                0.55f to glow.copy(alpha = 0.16f),
                1f to Color.Black,
            ),
        )
        val x = size.width * dayProgress
        val y = size.height * (0.5f - sin(dayProgress * Math.PI).toFloat() * 0.34f)
        val radius = size.minDimension * 0.055f
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(
                    (if (isDay) Color(0xFFFFD27A) else Color(0xFFDCE6FF)).copy(alpha = 0.55f),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = radius * 3.2f,
            ),
            radius = radius * 3.2f,
            center = Offset(x, y),
        )
        drawCircle(
            color = if (isDay) Color(0xFFFFE2A0) else Color(0xFFDCE6FF),
            radius = radius,
            center = Offset(x, y),
        )
        if (!isDay) {
            drawCircle(
                color = top.copy(alpha = 0.92f),
                radius = radius * 0.82f,
                center = Offset(x + radius * 0.45f, y - radius * 0.25f),
            )
        }
    }
}

private fun DrawScope.drawHand(
    center: Offset,
    angle: Float,
    length: Float,
    color: Color,
    width: Float,
) {
    val rad = Math.toRadians(angle.toDouble())
    drawLine(
        color = color,
        start = center,
        end = Offset(
            center.x + cos(rad).toFloat() * length,
            center.y + sin(rad).toFloat() * length,
        ),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

private fun polar(center: Offset, radius: Float, angle: Double): Offset = Offset(
    center.x + cos(angle).toFloat() * radius,
    center.y + sin(angle).toFloat() * radius,
)

private fun FontWeightOption.composeWeight(): FontWeight = when (this) {
    FontWeightOption.THIN -> FontWeight.Thin
    FontWeightOption.LIGHT -> FontWeight.Light
    FontWeightOption.REGULAR -> FontWeight.Normal
}
