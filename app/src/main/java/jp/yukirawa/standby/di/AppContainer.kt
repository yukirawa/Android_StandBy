package jp.yukirawa.standby.di

import android.content.Context
import jp.yukirawa.standby.data.DiagnosticsLog
import jp.yukirawa.standby.data.battery.BatteryRepository
import jp.yukirawa.standby.data.config.ConfigRepository
import jp.yukirawa.standby.data.music.MusicRepository
import jp.yukirawa.standby.data.weather.WeatherRepository
import jp.yukirawa.standby.permissions.PermissionController
import jp.yukirawa.standby.service.StandBySession
import jp.yukirawa.standby.trigger.OrientationMonitor
import jp.yukirawa.standby.trigger.ScreenStateMonitor
import jp.yukirawa.standby.xiaomi.XiaomiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val diagnostics = DiagnosticsLog()
    val config = ConfigRepository(appContext, scope)
    val battery = BatteryRepository(appContext)
    val screen = ScreenStateMonitor(appContext)
    val orientation = OrientationMonitor(appContext)
    val weather = WeatherRepository(appContext, scope, diagnostics)
    val music = MusicRepository(appContext, diagnostics)
    val session = StandBySession(diagnostics)
    val permissions = PermissionController(appContext)
    val xiaomi = XiaomiSettings(appContext)
}
