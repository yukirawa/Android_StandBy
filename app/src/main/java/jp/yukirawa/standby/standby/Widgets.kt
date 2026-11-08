package jp.yukirawa.standby.standby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import jp.yukirawa.standby.data.battery.BatteryState
import jp.yukirawa.standby.data.music.MusicState
import jp.yukirawa.standby.data.weather.WeatherKind
import jp.yukirawa.standby.data.weather.WeatherState
import jp.yukirawa.standby.model.WidgetType
import jp.yukirawa.standby.ui.theme.AccentPalette
import kotlin.math.roundToInt

@Composable
fun StandByCard(
    palette: AccentPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, shape)
            .padding(11.dp),
        content = content,
    )
}

@Composable
fun WeatherWidget(
    weather: WeatherState,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    StandByCard(palette, modifier) {
        if (!weather.available) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WeatherGlyph(WeatherKind.CLOUD, palette.cardBorder, Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (weather.loading) "天気を取得中" else "天気を取得できません",
                    color = palette.muted,
                    style = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
                )
            }
            return@StandByCard
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.kind, palette.primary, Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = weather.temperature?.roundToInt()?.toString() ?: "--",
                            color = palette.text,
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = 30.sp,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Text(
                            text = "°",
                            color = palette.muted,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Light),
                        )
                    }
                    Text(
                        text = weather.summary() +
                            (weather.apparent?.let { "・体感 ${it.roundToInt()}°" } ?: ""),
                        color = palette.text.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 10.sp),
                    )
                    Text(
                        text = "${weather.locationLabel}の天気" +
                            if (weather.isFallback) "（位置情報なし）" else "",
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 9.sp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                weather.hourly.take(3).forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${hour.time.hour}時",
                            color = palette.muted,
                            style = TextStyle(fontSize = 8.sp),
                        )
                        Text(
                            text = "${hour.temperature.roundToInt()}°",
                            color = palette.text.copy(alpha = 0.85f),
                            style = TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                        )
                    }
                }
                if (weather.max != null && weather.min != null) {
                    Text(
                        text = "最高${weather.max.roundToInt()}° / 最低${weather.min.roundToInt()}°",
                        color = palette.muted,
                        maxLines = 1,
                        style = TextStyle(fontSize = 8.sp),
                    )
                }
            }
        }
    }
}

@Composable
fun MusicWidget(
    music: MusicState,
    palette: AccentPalette,
    nightMode: Boolean,
    onOpenAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StandByCard(palette, modifier) {
        when {
            !music.hasNotificationAccess -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "通知アクセスが必要",
                    color = palette.muted,
                    style = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
                )
                TextButton(onClick = onOpenAccess) {
                    Text("設定", color = palette.primary, fontSize = 10.sp)
                }
            }

            !music.hasSession -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "再生中の音楽はありません",
                    color = palette.muted,
                    style = TextStyle(fontSize = 9.sp, textAlign = TextAlign.Center),
                )
                TextButton(onClick = onOpenMusicApp) {
                    Text("音楽を再生", color = palette.primary, fontSize = 10.sp)
                }
            }

            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(music, palette, nightMode, 42.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = music.title ?: "不明な曲",
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = music.artist ?: "",
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 9.sp),
                        )
                    }
                }
                LinearWavyProgressIndicator(
                    progress = {
                        if (music.durationMs <= 0L) 0f
                        else (music.positionMs.toFloat() / music.durationMs).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = palette.primary,
                    trackColor = palette.cardBorder,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaControl(
                        modifier = Modifier.size(24.dp),
                        enabled = music.canSkipPrevious,
                        onClick = onPrevious,
                    ) { SkipGlyph(forward = false, color = palette.muted) }
                    MediaControl(
                        modifier = Modifier.size(28.dp),
                        enabled = true,
                        onClick = onTogglePlayback,
                    ) { PlayPauseGlyph(isPlaying = music.isPlaying, color = palette.primary) }
                    MediaControl(
                        modifier = Modifier.size(24.dp),
                        enabled = music.canSkipNext,
                        onClick = onNext,
                    ) { SkipGlyph(forward = true, color = palette.muted) }
                }
            }
        }
    }
}

@Composable
fun BatteryWidget(
    battery: BatteryState,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    StandByCard(palette, modifier) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BatteryGlyph(
                level = battery.level,
                charging = battery.isCharging,
                palette = palette,
                modifier = Modifier.width(44.dp).height(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "${battery.level}%",
                    color = palette.text,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = 30.sp,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Text(
                    text = if (battery.isCharging) "${battery.plugType.label}充電中" else "未充電",
                    color = if (battery.isCharging) palette.primary else palette.muted,
                    maxLines = 1,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    battery.temperatureC?.let {
                        Text(
                            text = "${it.roundToInt()}℃",
                            color = palette.muted,
                            style = TextStyle(fontSize = 8.sp),
                        )
                    }
                    battery.watts?.let {
                        Text(
                            text = "約${String.format("%.1f", it)}W",
                            color = palette.muted,
                            style = TextStyle(fontSize = 8.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArt(
    music: MusicState,
    palette: AccentPalette,
    nightMode: Boolean,
    size: Dp,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(size).clip(shape).background(palette.cardBorder),
        contentAlignment = Alignment.Center,
    ) {
        val filter = if (nightMode) {
            ColorFilter.tint(palette.primary.copy(alpha = 0.55f), BlendMode.Color)
        } else {
            null
        }
        when {
            music.albumArt != null -> Image(
                bitmap = music.albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = filter,
            )

            music.artworkUri != null -> AsyncImage(
                model = music.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = filter,
            )

            else -> Canvas(Modifier.size(size * 0.5f)) {
                val color = palette.muted
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.toPx() * 0.08f, size.toPx() * 0.08f),
                    size = Size(size.toPx() * 0.2f, size.toPx() * 0.32f),
                    cornerRadius = CornerRadius(size.toPx() * 0.04f),
                )
                drawLine(
                    color = color,
                    start = Offset(size.toPx() * 0.27f, size.toPx() * 0.1f),
                    end = Offset(size.toPx() * 0.27f, size.toPx() * 0.55f),
                    strokeWidth = size.toPx() * 0.04f,
                )
                drawCircle(
                    color = color,
                    radius = size.toPx() * 0.1f,
                    center = Offset(size.toPx() * 0.17f, size.toPx() * 0.55f),
                )
            }
        }
    }
}

@Composable
private fun MediaControl(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (enabled) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        if (isPlaying) {
            val barWidth = size.width * 0.17f
            val gap = size.width * 0.12f
            val left = size.width * 0.3f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height * 0.22f),
                size = Size(barWidth, size.height * 0.56f),
                cornerRadius = CornerRadius(barWidth * 0.25f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + barWidth + gap, size.height * 0.22f),
                size = Size(barWidth, size.height * 0.56f),
                cornerRadius = CornerRadius(barWidth * 0.25f),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.35f, size.height * 0.2f)
                lineTo(size.width * 0.35f, size.height * 0.8f)
                lineTo(size.width * 0.78f, size.height * 0.5f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun SkipGlyph(forward: Boolean, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val path = Path().apply {
            if (forward) {
                moveTo(size.width * 0.2f, size.height * 0.22f)
                lineTo(size.width * 0.2f, size.height * 0.78f)
                lineTo(size.width * 0.66f, size.height * 0.5f)
            } else {
                moveTo(size.width * 0.8f, size.height * 0.22f)
                lineTo(size.width * 0.8f, size.height * 0.78f)
                lineTo(size.width * 0.34f, size.height * 0.5f)
            }
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color = color,
            topLeft = Offset(
                if (forward) size.width * 0.68f else size.width * 0.22f,
                size.height * 0.22f,
            ),
            size = Size(size.width * 0.1f, size.height * 0.56f),
            cornerRadius = CornerRadius(size.width * 0.04f),
        )
    }
}

@Composable
private fun BatteryGlyph(
    level: Int,
    charging: Boolean,
    palette: AccentPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val capWidth = size.width * 0.08f
        val bodyWidth = size.width - capWidth - 2.dp.toPx()
        val bodyHeight = size.height
        val stroke = 1.5.dp.toPx()
        val radius = CornerRadius(bodyHeight * 0.2f)
        drawRoundRect(
            color = palette.muted.copy(alpha = 0.7f),
            topLeft = Offset.Zero,
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = radius,
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = palette.primary,
            topLeft = Offset(stroke + 1.dp.toPx(), stroke + 2.dp.toPx()),
            size = Size(
                (bodyWidth - stroke * 2f - 2.dp.toPx()) * (level / 100f),
                bodyHeight - stroke * 2f - 4.dp.toPx(),
            ),
            cornerRadius = CornerRadius(bodyHeight * 0.12f),
        )
        drawRoundRect(
            color = palette.muted.copy(alpha = 0.7f),
            topLeft = Offset(bodyWidth + 1.dp.toPx(), bodyHeight * 0.32f),
            size = Size(capWidth, bodyHeight * 0.36f),
            cornerRadius = CornerRadius(capWidth * 0.4f),
        )
        if (charging) {
            val bolt = Path().apply {
                moveTo(size.width * 0.42f, size.height * 0.1f)
                lineTo(size.width * 0.25f, size.height * 0.55f)
                lineTo(size.width * 0.45f, size.height * 0.55f)
                lineTo(size.width * 0.35f, size.height * 0.9f)
                lineTo(size.width * 0.68f, size.height * 0.4f)
                lineTo(size.width * 0.47f, size.height * 0.4f)
                close()
            }
            drawPath(bolt, palette.background)
        }
    }
}

@Composable
private fun WeatherGlyph(kind: WeatherKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cloudColor = color.copy(alpha = 0.85f)
        fun cloud(centerX: Float, centerY: Float, scale: Float) {
            drawCircle(cloudColor, w * 0.14f * scale, Offset(centerX - w * 0.12f, centerY))
            drawCircle(cloudColor, w * 0.18f * scale, Offset(centerX, centerY - h * 0.08f))
            drawCircle(cloudColor, w * 0.13f * scale, Offset(centerX + w * 0.16f, centerY))
            drawRoundRect(
                color = cloudColor,
                topLeft = Offset(centerX - w * 0.22f, centerY),
                size = Size(w * 0.46f, h * 0.16f * scale),
                cornerRadius = CornerRadius(h * 0.08f),
            )
        }
        when (kind) {
            WeatherKind.SUN -> {
                drawCircle(color, w * 0.2f, Offset(w * 0.5f, h * 0.45f))
                repeat(8) { i ->
                    val angle = Math.toRadians(i * 45.0)
                    val start = Offset(
                        w * 0.5f + kotlin.math.cos(angle).toFloat() * w * 0.28f,
                        h * 0.45f + kotlin.math.sin(angle).toFloat() * w * 0.28f,
                    )
                    val end = Offset(
                        w * 0.5f + kotlin.math.cos(angle).toFloat() * w * 0.39f,
                        h * 0.45f + kotlin.math.sin(angle).toFloat() * w * 0.39f,
                    )
                    drawLine(color, start, end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            WeatherKind.MOON -> {
                drawCircle(color, w * 0.24f, Offset(w * 0.5f, h * 0.45f))
                drawCircle(Color.Black, w * 0.22f, Offset(w * 0.61f, h * 0.36f))
            }

            WeatherKind.PARTLY_DAY -> {
                drawCircle(color, w * 0.15f, Offset(w * 0.34f, h * 0.3f))
                cloud(w * 0.55f, h * 0.68f, 1f)
            }

            WeatherKind.PARTLY_NIGHT -> {
                drawCircle(color, w * 0.13f, Offset(w * 0.34f, h * 0.3f))
                drawCircle(Color.Black, w * 0.11f, Offset(w * 0.4f, h * 0.26f))
                cloud(w * 0.55f, h * 0.68f, 1f)
            }

            WeatherKind.CLOUD -> cloud(w * 0.5f, h * 0.5f, 1.15f)

            WeatherKind.FOG -> {
                cloud(w * 0.5f, h * 0.36f, 1f)
                repeat(3) { i ->
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(w * (0.18f + i * 0.06f), h * (0.65f + i * 0.1f)),
                        end = Offset(w * (0.8f - i * 0.06f), h * (0.65f + i * 0.1f)),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            WeatherKind.RAIN, WeatherKind.SHOWER -> {
                cloud(w * 0.5f, h * 0.36f, 1f)
                repeat(3) { i ->
                    drawLine(
                        color = color.copy(alpha = 0.7f),
                        start = Offset(w * (0.3f + i * 0.17f), h * 0.62f),
                        end = Offset(w * (0.25f + i * 0.17f), h * 0.84f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            WeatherKind.SNOW -> {
                cloud(w * 0.5f, h * 0.36f, 1f)
                repeat(3) { i ->
                    drawCircle(
                        color = color.copy(alpha = 0.75f),
                        radius = 1.3.dp.toPx(),
                        center = Offset(w * (0.3f + i * 0.17f), h * 0.72f),
                    )
                }
            }

            WeatherKind.THUNDER -> {
                cloud(w * 0.5f, h * 0.34f, 1f)
                val bolt = Path().apply {
                    moveTo(w * 0.5f, h * 0.5f)
                    lineTo(w * 0.38f, h * 0.7f)
                    lineTo(w * 0.52f, h * 0.7f)
                    lineTo(w * 0.44f, h * 0.92f)
                    lineTo(w * 0.66f, h * 0.62f)
                    lineTo(w * 0.52f, h * 0.62f)
                    close()
                }
                drawPath(bolt, color)
            }
        }
    }
}
