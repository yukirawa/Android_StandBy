package jp.yukirawa.standby.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import jp.yukirawa.standby.StandByApplication
import jp.yukirawa.standby.di.AppContainer
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.trigger.TriggerAction
import jp.yukirawa.standby.trigger.TriggerEngine
import jp.yukirawa.standby.trigger.TriggerInput
import jp.yukirawa.standby.trigger.TriggerOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StandByService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var engine: TriggerEngine
    private lateinit var notifications: StandByNotifications
    private lateinit var launchStrategy: LaunchStrategy

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastConfig = StandByConfig()
    private var lastDecision: TriggerOutput? = null
    private var lastScreenOn = true
    private var lastVisible = false
    private var finishingRequested = false
    private var nextLaunchAllowedAt = 0L
    private var launchCheckJob: Job? = null
    private var launchGeneration = 0
    private var foregroundActive = false
    private var lastNotificationVisible: Boolean? = null
    private var lastNotificationAutoLaunch: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as StandByApplication).container
        engine = TriggerEngine()
        notifications = StandByNotifications(this)
        notifications.createChannels()
        launchStrategy = LaunchStrategy(this, notifications, container.diagnostics)

        foregroundActive = runCatching {
            ServiceCompat.startForeground(
                this,
                StandByNotifications.ID_FOREGROUND,
                notifications.foreground(visible = false, autoLaunch = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure {
            container.diagnostics.add("常駐通知の開始に失敗: ${it.javaClass.simpleName}")
        }.isSuccess
        container.diagnostics.add("監視サービスを開始しました")
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelLaunchCheck()
        serviceScope.cancel()
        container.orientation.stop()
        releaseWakeLock()
        container.session.markStopped()
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        container.diagnostics.add("監視サービスを停止しました")
        super.onDestroy()
    }

    private fun observe() {
        serviceScope.launch {
            container.config.config.collect { config ->
                lastConfig = config
                if (!config.autoLaunch) {
                    container.diagnostics.add("自動起動がオフになったためサービスを停止します")
                    cancelLaunchCheck()
                    if (container.session.visible.value) {
                        requestFinish(DismissReason.BACKGROUND)
                    }
                    serviceScope.launch {
                        delay(400)
                        stopSelf()
                    }
                    return@collect
                }
                updateOrientationMonitoring()
                evaluate()
            }
        }

        serviceScope.launch {
            container.battery.state.collect {
                updateOrientationMonitoring()
                evaluate()
            }
        }

        serviceScope.launch {
            container.screen.state.collect { screen ->
                val justTurnedOff = lastScreenOn && !screen.isOn
                lastScreenOn = screen.isOn
                container.orientation.setScreenOn(screen.isOn)
                if (justTurnedOff && container.session.visible.value && !container.session.preview.value) {
                    engine.onPowerOffWhileVisible()
                }
                evaluate()
            }
        }

        serviceScope.launch {
            container.orientation.state.collect {
                evaluate()
            }
        }

        serviceScope.launch {
            container.session.visible.collect { visible ->
                if (visible) {
                    cancelLaunchCheck()
                    engine.onActivityStarted()
                    nextLaunchAllowedAt = 0L
                    notifications.cancelLaunchNotifications()
                } else if (lastVisible) {
                    cancelLaunchCheck()
                    val wasPreview = container.session.preview.value
                    if (!wasPreview) {
                        when {
                            !lastScreenOn -> engine.onPowerOffWhileVisible()
                            finishingRequested -> Unit
                            System.currentTimeMillis() - container.session.lastUserDismissAt.value < 1_500L ->
                                engine.onUserDismissed()

                            // The screen stayed on but the activity vanished: the user unlocked
                            // or another app took over. Do not treat this as a dismissal.
                            else -> engine.onUnlocked()
                        }
                        evaluate()
                    }
                }
                lastVisible = visible
                if (!visible) {
                    finishingRequested = false
                    container.session.pendingFinishReason.value = null
                }
                evaluate()
                updateForegroundNotification()
            }
        }

        serviceScope.launch {
            container.session.dismissEvents.collect { reason ->
                cancelLaunchCheck()
                when (reason) {
                    DismissReason.USER -> engine.onUserDismissed()
                    DismissReason.POWER -> engine.onPowerOffWhileVisible()
                    DismissReason.UNLOCKED -> engine.onUnlocked()
                    DismissReason.BACKGROUND -> engine.onVisibleLost()
                    DismissReason.CHARGING_OFF, DismissReason.PORTRAIT -> Unit
                }
                evaluate()
            }
        }
    }

    private fun evaluate() {
        if (!lastConfig.autoLaunch) return
        val battery = container.battery.state.value
        val screen = container.screen.state.value
        val orientation = container.orientation.state.value
        val visible = container.session.visible.value && !container.session.preview.value
        val input = TriggerInput(
            autoLaunch = lastConfig.autoLaunch,
            charging = battery.isCharging,
            landscape = orientation.isLandscape,
            screenOn = screen.isOn,
            locked = screen.isLocked,
            visible = visible,
        )
        val decision = engine.evaluate(input)
        if (lastDecision?.state != decision.state || lastDecision?.reason != decision.reason) {
            container.diagnostics.add("トリガー ${decision.state}: ${decision.reason}")
            lastDecision = decision
        }
        when (decision.action) {
            TriggerAction.LAUNCH -> {
                if (System.currentTimeMillis() >= nextLaunchAllowedAt) {
                    launchStandBy(orientation.isLandscape, screen.isOn, screen.isLocked)
                }
            }

            TriggerAction.FINISH -> {
                val reason = when (decision.reason) {
                    "充電解除" -> DismissReason.CHARGING_OFF
                    "縦向き" -> DismissReason.PORTRAIT
                    "ロック解除" -> DismissReason.UNLOCKED
                    else -> DismissReason.BACKGROUND
                }
                requestFinish(reason)
            }

            TriggerAction.NONE -> Unit
        }
        updateWakeLock()
        updateForegroundNotification()
    }

    private fun launchStandBy(landscape: Boolean, screenOn: Boolean, locked: Boolean) {
        container.diagnostics.add(
            "スタンバイ起動: 横向き=$landscape 画面オン=$screenOn ロック=$locked",
        )
        val route = launchStrategy.launch(preview = false)
        if (route != LaunchRoute.ACTIVITY) return
        cancelLaunchCheck()
        val generation = launchGeneration
        launchCheckJob = serviceScope.launch {
            delay(3_000)
            if (generation != launchGeneration) return@launch
            if (container.session.visible.value || finishingRequested) return@launch
            container.diagnostics.add("3秒以内に表示を確認できませんでした")
            engine.onLaunchFailed()
            nextLaunchAllowedAt = System.currentTimeMillis() + 15_000L
            launchStrategy.showFallback()
            evaluate()
        }
    }

    /**
     * Invalidates the pending "did the activity appear?" check. Without this, dismissing or
     * unlocking the standby within three seconds makes the check fire and re-show the activity
     * through the full screen intent fallback.
     */
    private fun cancelLaunchCheck() {
        launchGeneration++
        launchCheckJob?.cancel()
        launchCheckJob = null
    }

    private fun requestFinish(reason: DismissReason) {
        if (finishingRequested) return
        cancelLaunchCheck()
        finishingRequested = true
        container.session.requestFinish(reason)
    }

    private fun updateOrientationMonitoring() {
        val battery = container.battery.state.value
        if (lastConfig.autoLaunch && battery.isCharging) {
            container.orientation.start()
        } else {
            container.orientation.stop()
        }
    }

    private fun updateWakeLock() {
        val need = lastConfig.autoLaunch &&
            container.battery.state.value.isCharging &&
            !container.screen.state.value.isOn
        if (need) {
            if (wakeLock == null) {
                val power = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StandBy:monitor").apply {
                    setReferenceCounted(false)
                }
            }
            val lock = wakeLock
            if (lock != null && !lock.isHeld) {
                runCatching { lock.acquire(10 * 60 * 1_000L) }
                container.diagnostics.add("画面オフ監視のためウェイクロックを取得")
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        if (lock?.isHeld == true) {
            runCatching { lock.release() }
            container.diagnostics.add("ウェイクロックを解放")
        }
        wakeLock = null
    }

    // POST_NOTIFICATIONS is checked via canPostNotifications(); the call is also wrapped
    // in runCatching because the permission can be revoked at any time.
    @SuppressLint("MissingPermission")
    private fun updateForegroundNotification() {
        val visible = container.session.visible.value
        val autoLaunch = lastConfig.autoLaunch
        if (lastNotificationVisible == visible && lastNotificationAutoLaunch == autoLaunch) {
            return
        }
        lastNotificationVisible = visible
        lastNotificationAutoLaunch = autoLaunch
        val notification = notifications.foreground(visible = visible, autoLaunch = autoLaunch)
        if (foregroundActive) {
            if (canPostNotifications()) {
                runCatching {
                    NotificationManagerCompat.from(this)
                        .notify(StandByNotifications.ID_FOREGROUND, notification)
                }
            }
        } else {
            foregroundActive = runCatching {
                ServiceCompat.startForeground(
                    this,
                    StandByNotifications.ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }.isSuccess
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, StandByService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StandByService::class.java))
        }
    }
}
