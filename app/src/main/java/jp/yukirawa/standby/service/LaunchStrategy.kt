package jp.yukirawa.standby.service

import android.content.Context
import android.provider.Settings
import jp.yukirawa.standby.data.DiagnosticsLog
import jp.yukirawa.standby.standby.StandByActivity

enum class LaunchRoute {
    ACTIVITY,
    FULL_SCREEN_INTENT,
    NOTIFICATION,
}

class LaunchStrategy(
    private val context: Context,
    private val notifications: StandByNotifications,
    private val diagnostics: DiagnosticsLog,
) {
    fun launch(preview: Boolean): LaunchRoute {
        if (preview || Settings.canDrawOverlays(context)) {
            val started = runCatching {
                context.startActivity(StandByActivity.launchIntent(context, preview))
            }.isSuccess
            if (started) {
                diagnostics.add(if (preview) "プレビュー画面を直接起動" else "オーバーレイ権限で画面を起動")
                return LaunchRoute.ACTIVITY
            }
        }

        if (notifications.canUseFullScreenIntent()) {
            notifications.showFullScreenIntent()
            diagnostics.add("全画面インテント通知で起動を試行")
            return LaunchRoute.FULL_SCREEN_INTENT
        }

        notifications.showLaunchReady()
        diagnostics.add("通常通知で起動を案内")
        return LaunchRoute.NOTIFICATION
    }

    fun showFallback() {
        if (notifications.canUseFullScreenIntent()) {
            notifications.showFullScreenIntent()
            diagnostics.add("起動確認が取れないため全画面インテントへフォールバック")
        } else {
            notifications.showLaunchReady()
            diagnostics.add("起動確認が取れないため通知へフォールバック")
        }
    }
}
