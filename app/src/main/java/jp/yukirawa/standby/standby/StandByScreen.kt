package jp.yukirawa.standby.standby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import jp.yukirawa.standby.data.battery.BatteryState
import jp.yukirawa.standby.data.music.MusicState
import jp.yukirawa.standby.data.weather.WeatherState
import jp.yukirawa.standby.model.ClockFace
import jp.yukirawa.standby.model.Preset
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.model.StandByLayout
import jp.yukirawa.standby.model.WidgetSlot
import jp.yukirawa.standby.model.WidgetType
import jp.yukirawa.standby.model.applyPreset
import jp.yukirawa.standby.ui.theme.AccentPalette
import jp.yukirawa.standby.ui.theme.accentPalette
import jp.yukirawa.standby.util.TimeFormat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandByRoute(
    activity: StandByActivity,
    viewModel: StandByViewModel,
    isPreview: Boolean,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val music by viewModel.music.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()

    StandByScreen(
        config = config,
        weather = weather,
        music = music,
        battery = battery,
        isPreview = isPreview,
        onBrightnessChange = { value ->
            viewModel.updateConfig { it.copy(brightness = value) }
        },
        onDimBrightnessChange = { value ->
            viewModel.updateConfig { it.copy(dimBrightness = value) }
        },
        onDimDelayChange = { value ->
            viewModel.updateConfig { it.copy(dimDelaySeconds = value) }
        },
        onNightModeToggle = viewModel::toggleNightMode,
        onPreset = viewModel::applyPreset,
        onMusicAccess = viewModel::openNotificationAccess,
        onOpenMusicApp = viewModel::openMusicApp,
        onTogglePlayback = viewModel::toggleMusicPlayback,
        onPrevious = viewModel::previousTrack,
        onNext = viewModel::nextTrack,
        onApplyBrightness = activity::setWindowBrightness,
        onClose = activity::dismissWithFade,
        onRefreshWeather = viewModel::refreshWeather,
    )

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(WEATHER_REFRESH_MS)
            viewModel.refreshWeather()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandByScreen(
    config: StandByConfig,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    isPreview: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onDimBrightnessChange: (Float) -> Unit,
    onDimDelayChange: (Int) -> Unit,
    onNightModeToggle: () -> Unit,
    onPreset: (Preset) -> Unit,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onApplyBrightness: (Float) -> Unit,
    onClose: () -> Unit,
    onRefreshWeather: () -> Unit,
) {
    val palette = remember(config.accent, config.nightMode) {
        accentPalette(config.accent, config.nightMode)
    }

    val now = rememberNowTicker(config.needsSecondTick)

    var dimmed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    // Interactions are signalled through a conflated channel instead of snapshot state:
    // a drag emits dozens of events per second and must not recompose the whole screen.
    val interactions = remember { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(config.dimDelaySeconds) {
        if (config.dimDelaySeconds <= 0) {
            dimmed = false
            return@LaunchedEffect
        }
        dimmed = false
        while (isActive) {
            val idle = withTimeoutOrNull(config.dimDelaySeconds * 1_000L) {
                interactions.receive()
            } == null
            if (!idle) continue
            dimmed = true
            // Stay dimmed until the next tap/long-press, then restart the timer.
            interactions.receive()
            dimmed = false
        }
    }

    LaunchedEffect(dimmed, config.brightness, config.dimBrightness) {
        onApplyBrightness(if (dimmed) config.dimBrightness else config.brightness)
    }

    val dimAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.22f else 1f,
        animationSpec = tween(900),
        label = "dim",
    )

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(600),
        label = "enter",
    )

    var shift by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(config.burnInShift, config.burnInIntervalSeconds) {
        val limit = with(density) { 7.dp.roundToPx() }
        while (isActive) {
            if (config.burnInShift) {
                shift = IntOffset(
                    Random.nextInt(-limit, limit + 1),
                    Random.nextInt(-limit, limit + 1),
                )
            } else {
                shift = IntOffset.Zero
            }
            delay(config.burnInIntervalSeconds * 1_000L)
        }
    }
    val animatedShift by animateIntOffsetAsState(
        targetValue = shift,
        animationSpec = tween(1_400),
        label = "burnIn",
    )

    BackHandler { onClose() }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        interactions.trySend(Unit)
                        if (dimmed) dimmed = false
                    },
                    onLongPress = {
                        interactions.trySend(Unit)
                        dimmed = false
                        showControls = true
                    },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        interactions.trySend(Unit)
                    },
                    onDragEnd = {
                        if (dragAccumulator > size.height * 0.18f) onClose()
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                ) { _, dragAmount ->
                    dragAccumulator += dragAmount
                }
            },
    ) {
        if (config.clockFace == ClockFace.SOLAR) {
            SolarSky(now = now, palette = palette)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = if (config.nightMode) 0.05f else 0.035f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = enterAlpha * dimAlpha)
                .offset { animatedShift }
                .padding(horizontal = 26.dp, vertical = 16.dp),
        ) {
            TopStatus(
                config = config,
                weather = weather,
                battery = battery,
                isPreview = isPreview,
                palette = palette,
                dimmed = dimmed,
            )

            val layoutModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp, bottom = 4.dp)

            when (config.layout) {
                StandByLayout.HERO -> HeroLayout(
                    config = config,
                    now = now,
                    palette = palette,
                    weather = weather,
                    music = music,
                    battery = battery,
                    modifier = layoutModifier,
                    onMusicAccess = onMusicAccess,
                    onOpenMusicApp = onOpenMusicApp,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )

                StandByLayout.SPLIT -> SplitLayout(
                    config = config,
                    now = now,
                    palette = palette,
                    weather = weather,
                    music = music,
                    battery = battery,
                    modifier = layoutModifier,
                    onMusicAccess = onMusicAccess,
                    onOpenMusicApp = onOpenMusicApp,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )

                StandByLayout.STACK -> StackLayout(
                    config = config,
                    now = now,
                    palette = palette,
                    weather = weather,
                    music = music,
                    battery = battery,
                    modifier = layoutModifier,
                    onMusicAccess = onMusicAccess,
                    onOpenMusicApp = onOpenMusicApp,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }

            BottomHint(palette = palette, dimmed = dimmed)
        }

        if (dimmed && !showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable {
                        dimmed = false
                        interactions.trySend(Unit)
                    },
            )
        }
    }

    if (showControls) {
        QuickControlsSheet(
            config = config,
            palette = palette,
            isPreview = isPreview,
            battery = battery,
            onBrightnessChange = onBrightnessChange,
            onDimBrightnessChange = onDimBrightnessChange,
            onDimDelayChange = onDimDelayChange,
            onNightModeToggle = onNightModeToggle,
            onPreset = onPreset,
            onRefreshWeather = onRefreshWeather,
            onDismiss = { showControls = false },
            onClose = {
                showControls = false
                onClose()
            },
        )
    }
}

@Composable
private fun TopStatus(
    config: StandByConfig,
    weather: WeatherState,
    battery: BatteryState,
    isPreview: Boolean,
    palette: AccentPalette,
    dimmed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "STANDBY",
                color = palette.muted,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.4.sp,
            )
            if (isPreview) {
                PreviewBadge(palette)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (weather.available && !dimmed) {
                Text(
                    text = "天気 ${TimeFormat.relativeUpdatedLabel(weather.updatedAt)}",
                    color = palette.muted.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (battery.isCharging) {
                    "${battery.plugType.label} ${battery.level}%"
                } else {
                    "${battery.level}%"
                },
                color = if (battery.isCharging) palette.primary else palette.muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PreviewBadge(palette: AccentPalette) {
    Box(
        Modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.primary.copy(alpha = 0.12f))
            .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "プレビュー",
            color = palette.primary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BottomHint(palette: AccentPalette, dimmed: Boolean) {
    Text(
        text = if (dimmed) "タップで復帰" else "長押しでコントロール・下スワイプ / 戻るで閉じる",
        color = palette.muted.copy(alpha = if (dimmed) 0.5f else 0.35f),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun HeroLayout(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    modifier: Modifier,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier) {
        ClockSection(
            config = config,
            now = now,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        WidgetRow(
            config = config,
            palette = palette,
            weather = weather,
            music = music,
            battery = battery,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (config.widgetSlots.all { it == null }) 4.dp else 128.dp)
                .padding(top = 14.dp),
            onMusicAccess = onMusicAccess,
            onOpenMusicApp = onOpenMusicApp,
            onTogglePlayback = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}

@Composable
private fun SplitLayout(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    modifier: Modifier,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        ClockSection(
            config = config,
            now = now,
            palette = palette,
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            config.widgetSlots.take(3).forEachIndexed { index, type ->
                WidgetSlotView(
                    type = type,
                    slot = WidgetSlot.entries[index],
                    palette = palette,
                    weather = weather,
                    music = music,
                    battery = battery,
                    nightMode = config.nightMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onMusicAccess = onMusicAccess,
                    onOpenMusicApp = onOpenMusicApp,
                    onTogglePlayback = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }
        }
    }
}

@Composable
private fun StackLayout(
    config: StandByConfig,
    now: State<Instant>,
    palette: AccentPalette,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    modifier: Modifier,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier) {
        ClockSection(
            config = config,
            now = now,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.85f),
        )
        WidgetRow(
            config = config,
            palette = palette,
            weather = weather,
            music = music,
            battery = battery,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (config.widgetSlots.all { it == null }) 4.dp else 118.dp)
                .padding(top = 10.dp),
            onMusicAccess = onMusicAccess,
            onOpenMusicApp = onOpenMusicApp,
            onTogglePlayback = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}

@Composable
private fun WidgetRow(
    config: StandByConfig,
    palette: AccentPalette,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    modifier: Modifier,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (config.widgetSlots.all { it == null }) {
        Spacer(modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        config.widgetSlots.take(3).forEachIndexed { index, type ->
            WidgetSlotView(
                type = type,
                slot = WidgetSlot.entries[index],
                palette = palette,
                weather = weather,
                music = music,
                battery = battery,
                nightMode = config.nightMode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onMusicAccess = onMusicAccess,
                onOpenMusicApp = onOpenMusicApp,
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun WidgetSlotView(
    type: WidgetType?,
    slot: WidgetSlot,
    palette: AccentPalette,
    weather: WeatherState,
    music: MusicState,
    battery: BatteryState,
    nightMode: Boolean,
    modifier: Modifier,
    onMusicAccess: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    when (type) {
        WidgetType.WEATHER -> WeatherWidget(weather, palette, modifier)
        WidgetType.MUSIC -> MusicWidget(
            music = music,
            palette = palette,
            nightMode = nightMode,
            onOpenAccess = onMusicAccess,
            onOpenMusicApp = onOpenMusicApp,
            onTogglePlayback = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = modifier,
        )

        WidgetType.BATTERY -> BatteryWidget(battery, palette, modifier)
        null -> Spacer(modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickControlsSheet(
    config: StandByConfig,
    palette: AccentPalette,
    isPreview: Boolean,
    battery: BatteryState,
    onBrightnessChange: (Float) -> Unit,
    onDimBrightnessChange: (Float) -> Unit,
    onDimDelayChange: (Int) -> Unit,
    onNightModeToggle: () -> Unit,
    onPreset: (Preset) -> Unit,
    onRefreshWeather: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E0E0F),
        contentColor = Color.White,
        shape = sheetShape,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "クイックコントロール",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = Color.White,
                    )
                    Text(
                        text = if (isPreview) "プレビュー表示中" else "スタンバイ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${battery.level}%",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("画面の明るさ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunGlyph(brightness = config.brightness, color = Color(0xFFB8B8BD))
                Slider(
                    value = config.brightness,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    valueRange = 0.02f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0xFF2C2C2E),
                    ),
                )
                Text(
                    text = "${(config.brightness * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("赤色ナイトモード")
                    Text(
                        text = "焼き付きと眩しさを抑える暗い赤表示",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ToggleButton(
                    checked = config.nightMode,
                    onCheckedChange = { onNightModeToggle() },
                ) {
                    Text(if (config.nightMode) "オン" else "オフ")
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("減光までの時間")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "なし", 30 to "30秒", 60 to "1分", 120 to "2分", 300 to "5分")
                    .forEach { (seconds, label) ->
                        ChoiceChip(
                            label = label,
                            selected = config.dimDelaySeconds == seconds,
                            palette = palette,
                            onClick = { onDimDelayChange(seconds) },
                        )
                    }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("減光時の明るさ", Modifier.weight(1f))
                Text(
                    text = "${(config.dimBrightness * 100).roundToInt()}%",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = config.dimBrightness,
                onValueChange = onDimBrightnessChange,
                valueRange = 0.05f..0.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF3A3A3C),
                    inactiveTrackColor = Color(0xFF2C2C2E),
                ),
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("プリセット")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                selected = config.presetId == preset.id,
                                palette = palette,
                                modifier = Modifier.weight(1f),
                                onClick = { onPreset(preset) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    label = "天気を更新",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshWeather,
                )
                SheetButton(
                    label = "スタンバイを閉じる",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Color(0xFFB8B8BD),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    palette: AccentPalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color(0xFF1C1C1E))
            .border(
                1.dp,
                if (selected) Color.White else Color(0xFF2C2C2E),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color(0xFFB8B8BD),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    palette: AccentPalette,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val previewPalette = remember(preset) { accentPalette(preset.accent, preset.nightMode) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF232325) else Color(0xFF151516))
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.55f) else Color(0xFF262628),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(previewPalette.primary),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = preset.label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            if (selected) {
                Spacer(Modifier.weight(1f))
                Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = preset.description,
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(previewPalette.primary, previewPalette.secondary),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color(0xFF1C1C1E))
            .border(
                1.dp,
                if (filled) Color.White else Color(0xFF2C2C2E),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (filled) Color.Black else Color(0xFFB8B8BD),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SunGlyph(brightness: Float, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val radius = size.minDimension * 0.2f
        drawCircle(color, radius, center)
        val inner = size.minDimension * 0.3f
        val outer = size.minDimension * 0.44f
        repeat(8) { i ->
            val angle = Math.toRadians(i * 45.0)
            drawLine(
                color = color.copy(alpha = 0.45f + brightness * 0.55f),
                start = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * inner,
                    center.y + kotlin.math.sin(angle).toFloat() * inner,
                ),
                end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * outer,
                    center.y + kotlin.math.sin(angle).toFloat() * outer,
                ),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
    }
}

/**
 * Single ticking [State] shared by the clock composables. The value is read where it is shown,
 * so a tick invalidates the clock only instead of the whole standby screen. Ticking pauses when
 * the screen is not started (app backgrounded, or the preview scrolled out of view).
 */
@Composable
internal fun rememberNowTicker(needsSeconds: Boolean): State<Instant> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now = remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(lifecycleOwner, needsSeconds) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = Instant.now()
                delay(
                    if (needsSeconds) TimeFormat.nextSecondDelayMillis()
                    else TimeFormat.nextMinuteDelayMillis(),
                )
            }
        }
    }
    return now
}

/** Analog clocks need a second hand even when the digital seconds are hidden. */
internal val StandByConfig.needsSecondTick: Boolean
    get() = showSeconds || clockFace == ClockFace.ANALOG

@Composable
fun StandByPreview(
    config: StandByConfig,
    modifier: Modifier = Modifier,
) {
    val palette = remember(config.accent, config.nightMode) {
        accentPalette(config.accent, config.nightMode)
    }
    // Keep the preview clock live, aligned to the next second or minute.
    val now = rememberNowTicker(config.needsSecondTick)
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        if (config.clockFace == ClockFace.SOLAR) {
            SolarSky(now = now, palette = palette)
        }
        ClockSection(
            config = config,
            now = now,
            palette = palette,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val WEATHER_REFRESH_MS = 30 * 60 * 1_000L
