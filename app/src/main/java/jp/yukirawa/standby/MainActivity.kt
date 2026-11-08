package jp.yukirawa.standby

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yukirawa.standby.data.battery.BatteryState
import jp.yukirawa.standby.data.weather.WeatherState
import jp.yukirawa.standby.model.AccentColor
import jp.yukirawa.standby.model.ClockFace
import jp.yukirawa.standby.model.FontWeightOption
import jp.yukirawa.standby.model.Preset
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.model.StandByLayout
import jp.yukirawa.standby.model.WidgetSlot
import jp.yukirawa.standby.model.WidgetType
import jp.yukirawa.standby.model.applyPreset
import jp.yukirawa.standby.permissions.PermissionStatus
import jp.yukirawa.standby.standby.StandByPreview
import jp.yukirawa.standby.ui.theme.StandByTheme
import jp.yukirawa.standby.ui.theme.accentPalette
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as StandByApplication).container }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(container, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StandByTheme {
                MainApp(viewModel, this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

private enum class MainDestination {
    HOME,
    SETTINGS,
    DIAGNOSTICS,
    ONBOARDING,
}

@Composable
private fun MainApp(viewModel: MainViewModel, activity: MainActivity) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val sessionVisible by viewModel.sessionVisible.collectAsStateWithLifecycle()

    var destinationName by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
    val destination = runCatching { MainDestination.valueOf(destinationName) }
        .getOrDefault(MainDestination.HOME)
    var onboardingChecked by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
        scope.launch {
            snackbar.showSnackbar(
                if (viewModel.permissions.value.notifications) "通知を許可しました" else "通知は許可されていません",
            )
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
        viewModel.refreshWeather()
        scope.launch { snackbar.showSnackbar("位置情報の設定を更新しました") }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        if (!viewModel.config.value.onboardingCompleted) {
            destinationName = MainDestination.ONBOARDING.name
        }
        onboardingChecked = true
    }

    LaunchedEffect(config.autoLaunch) {
        if (config.autoLaunch) viewModel.startService() else viewModel.stopService()
    }

    fun navigate(next: MainDestination) {
        destinationName = next.name
    }

    val notificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.openNotificationSettings()
        }
    }
    val locationPermission = {
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF08080A),
                    0.45f to Color.Black,
                    1f to Color(0xFF050506),
                ),
            ),
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "main-nav",
        ) { screen ->
            when (screen) {
                MainDestination.HOME -> HomeScreen(
                    config = config,
                    battery = battery,
                    weather = weather,
                    permissions = permissions,
                    sessionVisible = sessionVisible,
                    isXiaomi = viewModel.isXiaomi,
                    onPreview = { viewModel.preview(activity) },
                    onSettings = { navigate(MainDestination.SETTINGS) },
                    onDiagnostics = { navigate(MainDestination.DIAGNOSTICS) },
                    onNotificationPermission = notificationPermission,
                    onLocationPermission = locationPermission,
                    onOverlaySettings = viewModel::openOverlaySettings,
                    onBatteryExemption = viewModel::requestBatteryExemption,
                    onNotificationAccess = viewModel::openNotificationAccess,
                    onFullScreenIntent = viewModel::openFullScreenIntentSettings,
                    onXiaomiAutoStart = viewModel::openXiaomiAutoStart,
                    onXiaomiBattery = viewModel::openXiaomiBattery,
                    onXiaomiPermissionEditor = viewModel::openXiaomiPermissionEditor,
                    onRefreshWeather = viewModel::refreshWeather,
                    onToggleAutoLaunch = { enabled ->
                        viewModel.updateConfig { it.copy(autoLaunch = enabled) }
                    },
                    onToggleWakeOnCharge = { enabled ->
                        viewModel.updateConfig { it.copy(wakeOnCharge = enabled) }
                    },
                )

                MainDestination.SETTINGS -> SettingsScreen(
                    config = config,
                    onBack = { navigate(MainDestination.HOME) },
                    onApplyPreset = viewModel::applyPreset,
                    onUpdate = viewModel::updateConfig,
                )

                MainDestination.DIAGNOSTICS -> DiagnosticsScreen(
                    viewModel = viewModel,
                    permissions = permissions,
                    isXiaomi = viewModel.isXiaomi,
                    onBack = { navigate(MainDestination.HOME) },
                    onClear = viewModel::clearDiagnostics,
                    onCopy = {
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("StandBy diagnostics", viewModel.entriesText()),
                        )
                        scope.launch { snackbar.showSnackbar("診断ログをコピーしました") }
                    },
                    onPermissions = {
                        viewModel.refreshPermissions()
                        scope.launch { snackbar.showSnackbar("権限の状態を更新しました") }
                    },
                    onXiaomiAutoStart = viewModel::openXiaomiAutoStart,
                    onXiaomiBattery = viewModel::openXiaomiBattery,
                    onXiaomiPermissionEditor = viewModel::openXiaomiPermissionEditor,
                    onOpenAppDetails = viewModel::openAppDetails,
                )

                MainDestination.ONBOARDING -> OnboardingScreen(
                    onFinish = {
                        viewModel.completeOnboarding()
                        navigate(MainDestination.HOME)
                    },
                    onNotificationPermission = notificationPermission,
                    onOverlaySettings = viewModel::openOverlaySettings,
                    onBatteryExemption = viewModel::requestBatteryExemption,
                    onNotificationAccess = viewModel::openNotificationAccess,
                    isXiaomi = viewModel.isXiaomi,
                    onXiaomiAutoStart = viewModel::openXiaomiAutoStart,
                    onXiaomiPermissionEditor = viewModel::openXiaomiPermissionEditor,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        )
    }
}

@Composable
private fun HomeScreen(
    config: StandByConfig,
    battery: BatteryState,
    weather: WeatherState,
    permissions: PermissionStatus,
    sessionVisible: Boolean,
    isXiaomi: Boolean,
    onPreview: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onNotificationPermission: () -> Unit,
    onLocationPermission: () -> Unit,
    onOverlaySettings: () -> Unit,
    onBatteryExemption: () -> Unit,
    onNotificationAccess: () -> Unit,
    onFullScreenIntent: () -> Unit,
    onXiaomiAutoStart: () -> Unit,
    onXiaomiBattery: () -> Unit,
    onXiaomiPermissionEditor: () -> Unit,
    onRefreshWeather: () -> Unit,
    onToggleAutoLaunch: (Boolean) -> Unit,
    onToggleWakeOnCharge: (Boolean) -> Unit,
) {
    val palette = remember(config.accent, config.nightMode) {
        accentPalette(config.accent, config.nightMode)
    }
    val readyCount = listOf(
        permissions.notifications,
        permissions.overlay,
        permissions.batteryUnrestricted,
    ).count { it }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "StandBy",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                    Text(
                        text = "充電中、横向きで時計になる常時表示",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderChip(if (sessionVisible) "表示中" else "待機", active = sessionVisible)
                    Spacer(Modifier.width(8.dp))
                    HeaderChip("設定", onClick = onSettings)
                }
            }
        }

        item {
            PreviewCard(
                config = config,
                palette = palette,
                onPreview = onPreview,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "バッテリー",
                    value = "${battery.level}%",
                    detail = if (battery.isCharging) "${battery.plugType.label}充電中" else "未充電",
                    accent = if (battery.isCharging) Color(0xFF30D158) else Color(0xFF8E8E93),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "天気",
                    value = weather.temperature?.let { "${it.toInt()}°" } ?: "--",
                    detail = if (weather.available) weather.summary() else "未取得",
                    accent = Color(0xFF5AC8FA),
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshWeather,
                )
                StatCard(
                    title = "自動起動",
                    value = if (config.autoLaunch) "オン" else "オフ",
                    detail = if (config.autoLaunch) "横向き待ち" else "停止中",
                    accent = if (config.autoLaunch) Color(0xFFFFB340) else Color(0xFF8E8E93),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SetupCard(
                config = config,
                permissions = permissions,
                readyCount = readyCount,
                onToggleAutoLaunch = onToggleAutoLaunch,
                onToggleWakeOnCharge = onToggleWakeOnCharge,
            )
        }

        item {
            SectionCard(title = "はじめに") {
                PermissionRow(
                    title = "通知",
                    description = "常駐通知と起動の案内に使います",
                    granted = permissions.notifications,
                    action = "許可",
                    onAction = onNotificationPermission,
                )
                PermissionRow(
                    title = "重ねて表示",
                    description = "他アプリの上にスタンバイを開きます",
                    granted = permissions.overlay,
                    action = "設定",
                    onAction = onOverlaySettings,
                )
                PermissionRow(
                    title = "バッテリー最適化の解除",
                    description = "画面オフ中も監視を続けます",
                    granted = permissions.batteryUnrestricted,
                    action = "解除",
                    onAction = onBatteryExemption,
                )
                PermissionRow(
                    title = "全画面インテント通知",
                    description = "ロック画面から直接表示する補助経路",
                    granted = permissions.fullScreenIntent,
                    action = "設定",
                    onAction = onFullScreenIntent,
                )
            }
        }

        item {
            SectionCard(title = "あると便利") {
                PermissionRow(
                    title = "通知アクセス",
                    description = "音楽ウィジェットの再生情報を取得します",
                    granted = permissions.notificationAccess,
                    action = "設定",
                    onAction = onNotificationAccess,
                )
                PermissionRow(
                    title = "位置情報",
                    description = "天気を現在地で取得します（未許可なら東京）",
                    granted = permissions.location,
                    action = "許可",
                    onAction = onLocationPermission,
                )
            }
        }

        if (isXiaomi) {
            item {
                XiaomiCard(
                    onAutoStart = onXiaomiAutoStart,
                    onBattery = onXiaomiBattery,
                    onPermissionEditor = onXiaomiPermissionEditor,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDiagnostics,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("診断ログ")
                }
                Button(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("カスタマイズ")
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    config: StandByConfig,
    palette: jp.yukirawa.standby.ui.theme.AccentPalette,
    onPreview: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp, max = 240.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Color(0xFF262628), RoundedCornerShape(26.dp)),
    ) {
        StandByPreview(
            config = config,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "${config.clockFace.label}・${config.layout.label}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "長押しでクイックコントロール",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Button(
                onClick = onPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text("プレビュー")
            }
        }
    }
}

@Composable
private fun SetupCard(
    config: StandByConfig,
    permissions: PermissionStatus,
    readyCount: Int,
    onToggleAutoLaunch: (Boolean) -> Unit,
    onToggleWakeOnCharge: (Boolean) -> Unit,
) {
    SectionCard(title = "セットアップ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$readyCount / 3",
                color = Color.White,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Spacer(Modifier.width(12.dp))
            LinearWavyProgressIndicator(
                progress = { readyCount / 3f },
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp),
                color = Color.White,
                trackColor = Color(0xFF2C2C2E),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (readyCount) {
                3 -> "自動起動の準備ができています"
                2 -> "あと1つで自動起動できます"
                else -> "権限を許可すると自動起動します"
            },
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "自動起動",
            description = "充電中に横向きになるとスタンバイを表示",
            checked = config.autoLaunch,
            onCheckedChange = onToggleAutoLaunch,
        )
        ToggleRow(
            title = "充電開始で画面を点ける",
            description = "ワイヤレス充電でも起動しやすくなります",
            checked = config.wakeOnCharge,
            onCheckedChange = onToggleWakeOnCharge,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen(
    config: StandByConfig,
    onBack: () -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onUpdate: ((StandByConfig) -> StandByConfig) -> Unit,
) {
    val palette = remember(config.accent, config.nightMode) {
        accentPalette(config.accent, config.nightMode)
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        ScreenHeader(title = "カスタマイズ", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "プリセット") {
                Preset.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            PresetTile(
                                preset = preset,
                                selected = config.presetId == preset.id,
                                modifier = Modifier.weight(1f),
                                onClick = { onApplyPreset(preset) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionCard(title = "時計") {
                ChipsRow(
                    options = ClockFace.entries.map { it.name to it.label },
                    selected = config.clockFace.name,
                    onSelect = { value ->
                        onUpdate { it.copy(clockFace = ClockFace.valueOf(value)) }
                    },
                )
                Spacer(Modifier.height(10.dp))
                ChipsRow(
                    options = FontWeightOption.entries.map { it.name to it.label },
                    selected = config.fontWeight.name,
                    onSelect = { value ->
                        onUpdate { it.copy(fontWeight = FontWeightOption.valueOf(value)) }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        checked = config.use24Hour,
                        onCheckedChange = { on -> onUpdate { c -> c.copy(use24Hour = on) } },
                        modifier = Modifier.weight(1f),
                    ) { Text("24時間") }
                    ToggleButton(
                        checked = config.showSeconds,
                        onCheckedChange = { on -> onUpdate { c -> c.copy(showSeconds = on) } },
                        modifier = Modifier.weight(1f),
                    ) { Text("秒") }
                    ToggleButton(
                        checked = config.showDate,
                        onCheckedChange = { on -> onUpdate { c -> c.copy(showDate = on) } },
                        modifier = Modifier.weight(1f),
                    ) { Text("日付") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = config.datePattern,
                    onValueChange = { value ->
                        onUpdate { it.copy(datePattern = value.ifBlank { "M月d日 (E)" }) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("日付フォーマット") },
                    singleLine = true,
                )
            }

            SectionCard(title = "レイアウト") {
                ChipsRow(
                    options = StandByLayout.entries.map { it.name to it.label },
                    selected = config.layout.name,
                    onSelect = { value ->
                        onUpdate { it.copy(layout = StandByLayout.valueOf(value)) }
                    },
                )
            }

            SectionCard(title = "アクセント") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColor.entries.forEach { accent ->
                        val color = accentPalette(accent, config.nightMode).primary
                        val selected = config.accent == accent
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.White else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(50),
                                )
                                .clickable { onUpdate { it.copy(accent = accent) } },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "夜間は赤色ナイトモードで眩しさと焼き付きを抑えます。",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "赤色ナイトモード",
                    description = null,
                    checked = config.nightMode,
                    onCheckedChange = { on -> onUpdate { it.copy(nightMode = on) } },
                )
            }

            SectionCard(title = "ウィジェット") {
                WidgetSlot.entries.forEach { slot ->
                    Text(
                        text = "${slot.label}スロット",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ChipsRow(
                        options = listOf("none" to "なし") +
                            WidgetType.entries.map { it.name to it.label },
                        selected = config.widgetAt(slot)?.name ?: "none",
                        onSelect = { value ->
                            onUpdate {
                                it.withWidget(
                                    slot,
                                    if (value == "none") null else WidgetType.valueOf(value),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionCard(title = "明るさ・減光") {
                SliderRow(
                    label = "通常の明るさ",
                    value = config.brightness,
                    range = 0.02f..1f,
                    format = { "${(it * 100).toInt()}%" },
                    onValueChange = { value -> onUpdate { it.copy(brightness = value) } },
                )
                SliderRow(
                    label = "減光時の明るさ",
                    value = config.dimBrightness,
                    range = 0.05f..0.5f,
                    format = { "${(it * 100).toInt()}%" },
                    onValueChange = { value -> onUpdate { it.copy(dimBrightness = value) } },
                )
                Text(
                    text = "減光までの時間",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelMedium,
                )
                ChipsRow(
                    options = listOf(
                        "0" to "なし",
                        "30" to "30秒",
                        "60" to "1分",
                        "120" to "2分",
                        "300" to "5分",
                    ),
                    selected = config.dimDelaySeconds.toString(),
                    onSelect = { value ->
                        onUpdate { it.copy(dimDelaySeconds = value.toIntOrNull() ?: 60) }
                    },
                )
            }

            SectionCard(title = "焼き付き防止") {
                ToggleRow(
                    title = "ピクセルシフト",
                    description = "一定間隔で表示位置を数px動かします",
                    checked = config.burnInShift,
                    onCheckedChange = { on -> onUpdate { it.copy(burnInShift = on) } },
                )
                Text(
                    text = "移動間隔",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelMedium,
                )
                ChipsRow(
                    options = listOf("60" to "60秒", "90" to "90秒", "120" to "120秒"),
                    selected = config.burnInIntervalSeconds.toString(),
                    onSelect = { value ->
                        onUpdate {
                            it.copy(burnInIntervalSeconds = value.toIntOrNull() ?: 90)
                        }
                    },
                )
            }

            SectionCard(title = "動作") {
                ToggleRow(
                    title = "自動起動",
                    description = "充電中・横向き・画面オフ/ロック中で表示",
                    checked = config.autoLaunch,
                    onCheckedChange = { on -> onUpdate { it.copy(autoLaunch = on) } },
                )
                ToggleRow(
                    title = "充電開始で画面を点ける",
                    description = null,
                    checked = config.wakeOnCharge,
                    onCheckedChange = { on -> onUpdate { it.copy(wakeOnCharge = on) } },
                )
            }

            Text(
                text = "背景は純黒、文字は低輝度を前提にしています。OLED の焼き付きを完全に防ぐものではありません。",
                color = Color(0xFF6E6E73),
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(palette.primary, palette.secondary, Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    viewModel: MainViewModel,
    permissions: PermissionStatus,
    isXiaomi: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onPermissions: () -> Unit,
    onXiaomiAutoStart: () -> Unit,
    onXiaomiBattery: () -> Unit,
    onXiaomiPermissionEditor: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    val entries by viewModel.diagnostics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crashFile = remember { File(context.filesDir, StandByApplication.CRASH_FILE) }
    var crashLog by remember {
        mutableStateOf(
            runCatching {
                crashFile.takeIf { it.exists() }?.readText()
            }.getOrNull(),
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        ScreenHeader(title = "診断", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "端末") {
                InfoRow("メーカー", Build.MANUFACTURER)
                InfoRow("モデル", Build.MODEL)
                InfoRow("Android", "API ${Build.VERSION.SDK_INT}")
                InfoRow("メーカー設定", if (isXiaomi) "Xiaomi / HyperOS" else "標準")
            }

            SectionCard(title = "権限") {
                PermissionRow(
                    "通知",
                    null,
                    permissions.notifications,
                    "更新",
                    onPermissions,
                )
                PermissionRow(
                    "重ねて表示",
                    null,
                    permissions.overlay,
                    "更新",
                    onPermissions,
                )
                PermissionRow(
                    "バッテリー最適化の解除",
                    null,
                    permissions.batteryUnrestricted,
                    "更新",
                    onPermissions,
                )
                PermissionRow(
                    "全画面インテント",
                    null,
                    permissions.fullScreenIntent,
                    "更新",
                    onPermissions,
                )
                PermissionRow(
                    "通知アクセス",
                    null,
                    permissions.notificationAccess,
                    "更新",
                    onPermissions,
                )
                PermissionRow(
                    "位置情報",
                    null,
                    permissions.location,
                    "更新",
                    onPermissions,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenAppDetails,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("アプリ情報") }
                    Button(
                        onClick = onPermissions,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("再チェック") }
                }
            }

            if (isXiaomi) {
                SectionCard(title = "HyperOS") {
                    Text(
                        text = "自起動・バッテリー制限・ロック画面/バックグラウンドポップアップを許可すると、画面オフ中の起動が安定します。",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onXiaomiAutoStart,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1C1C1E),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(50),
                        ) { Text("自起動") }
                        Button(
                            onClick = onXiaomiBattery,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1C1C1E),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(50),
                        ) { Text("バッテリー") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onXiaomiPermissionEditor,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("権限エディタ（ロック画面/ポップアップ）") }
                }
            }

            if (crashLog != null) {
                SectionCard(title = "前回のクラッシュ") {
                    Text(
                        text = crashLog.orEmpty(),
                        color = Color(0xFFFFB340),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            runCatching { crashFile.delete() }
                            crashLog = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("クラッシュログを消去") }
                }
            }

            SectionCard(title = "ログ（最新100件）") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("コピー") }
                    Button(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color(0xFFFF6961),
                        ),
                        shape = RoundedCornerShape(50),
                    ) { Text("クリア") }
                }
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text(
                        text = "ログはまだありません",
                        color = Color(0xFF6E6E73),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    entries.reversed().forEach { entry ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = entry.time,
                                color = Color(0xFF6E6E73),
                                modifier = Modifier.width(64.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = entry.message,
                                color = Color(0xFFB8B8BD),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    onFinish: () -> Unit,
    onNotificationPermission: () -> Unit,
    onOverlaySettings: () -> Unit,
    onBatteryExemption: () -> Unit,
    onNotificationAccess: () -> Unit,
    isXiaomi: Boolean,
    onXiaomiAutoStart: () -> Unit,
    onXiaomiPermissionEditor: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val steps = 4

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "StandBy",
                color = Color.White,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = "${step + 1} / $steps",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> OnboardingPage(
                    eyebrow = "WELCOME",
                    title = "置くだけで、時計になる。",
                    body = "充電中にスマートフォンを横向きに置くと、スタンバイ画面が自動で表示されます。時計・天気・音楽・バッテリーを暗い画面にまとめます。",
                )

                1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPage(
                        eyebrow = "PERMISSIONS",
                        title = "3つの権限を許可",
                        body = "自動起動に必要な権限です。あとから設定画面で変更できます。",
                    )
                    OnboardingPermission("通知を許可", onNotificationPermission)
                    OnboardingPermission("重ねて表示を許可", onOverlaySettings)
                    OnboardingPermission("バッテリー最適化を解除", onBatteryExemption)
                }

                2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingPage(
                        eyebrow = "EXTRAS",
                        title = "音楽と天気も使う",
                        body = "通知アクセスは音楽の再生情報だけに使い、通知の中身は読みません。位置情報が無い場合は東京の天気を表示します。",
                    )
                    OnboardingPermission("通知アクセスを許可", onNotificationAccess)
                    if (isXiaomi) {
                        OnboardingPage(
                            eyebrow = "HYPEROS",
                            title = "Xiaomi の追加設定",
                            body = "HyperOS では自起動とバッテリー制限の許可が必要です。",
                        )
                        OnboardingPermission("自起動を許可", onXiaomiAutoStart)
                        OnboardingPermission("ロック画面/ポップアップを許可", onXiaomiPermissionEditor)
                    }
                }

                else -> OnboardingPage(
                    eyebrow = "READY",
                    title = "準備できました",
                    body = "充電器に接続して横向きに置いてみてください。長押しで明るさ・ナイトモード・プリセットを変更できます。下スワイプか戻るで閉じます。",
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                Button(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(50),
                ) { Text("戻る") }
            }
            Button(
                onClick = {
                    if (step >= steps - 1) onFinish() else step++
                },
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(if (step >= steps - 1) "はじめる" else "次へ")
            }
        }
        if (step == 0) {
            Text(
                text = "スキップ",
                color = Color(0xFF8E8E93),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(onClick = onFinish),
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OnboardingPage(eyebrow: String, title: String, body: String) {
    Text(
        text = eyebrow,
        color = Color(0xFF8E8E93),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = title,
        color = Color.White,
        style = MaterialTheme.typography.displaySmallEmphasized,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        color = Color(0xFFB8B8BD),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun OnboardingPermission(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151516))
            .border(1.dp, Color(0xFF262628), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("開く", color = Color(0xFF5AC8FA), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "←",
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101011))
            .border(1.dp, Color(0xFF222224), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
        )
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    text = description,
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF2C2C2E),
            ),
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String?,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (granted) Color(0xFF30D158) else Color(0xFFFFB340)),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (description != null) {
                    Text(
                        text = description,
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (!granted) {
            Text(
                text = action,
                color = Color(0xFF5AC8FA),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Text(
                text = "✓",
                color = Color(0xFF30D158),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HeaderChip(
    label: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF30D158).copy(alpha = 0.16f) else Color(0xFF1C1C1E))
            .border(
                1.dp,
                if (active) Color(0xFF30D158).copy(alpha = 0.4f) else Color(0xFF2C2C2E),
                RoundedCornerShape(50),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFF30D158) else Color(0xFFB8B8BD),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101011))
            .border(1.dp, Color(0xFF222224), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Text(title, color = Color(0xFF8E8E93), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = detail,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun XiaomiCard(
    onAutoStart: () -> Unit,
    onBattery: () -> Unit,
    onPermissionEditor: () -> Unit,
) {
    SectionCard(title = "Xiaomi / HyperOS") {
        Text(
            text = "自起動・バッテリー制限・ロック画面/バックグラウンドポップアップを許可すると、画面オフ中の自動表示が安定します。",
            color = Color(0xFFB8B8BD),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAutoStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(50),
            ) { Text("自起動") }
            Button(
                onClick = onBattery,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(50),
            ) { Text("バッテリー") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPermissionEditor,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(50),
        ) { Text("権限エディタ（ロック画面/ポップアップ）") }
    }
}

@Composable
private fun ChipsRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color.White else Color(0xFF1C1C1E))
                    .border(
                        1.dp,
                        if (active) Color.White else Color(0xFF2C2C2E),
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    color = if (active) Color.Black else Color(0xFFB8B8BD),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFF8E8E93),
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF2C2C2E),
            ),
        )
        Text(
            text = format(value),
            color = Color.White,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PresetTile(
    preset: Preset,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = remember(preset) { accentPalette(preset.accent, preset.nightMode) }
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
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.primary),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = preset.label,
                color = Color.White,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = preset.description,
            color = Color(0xFF8E8E93),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(palette.primary, palette.secondary))),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color(0xFF8E8E93), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
