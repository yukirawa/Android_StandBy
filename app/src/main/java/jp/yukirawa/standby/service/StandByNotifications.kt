package jp.yukirawa.standby.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import jp.yukirawa.standby.MainActivity
import jp.yukirawa.standby.R
import jp.yukirawa.standby.standby.StandByActivity

class StandByNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "スタンバイ監視",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "充電と画面の向きを監視する常駐通知"
            setShowBadge(false)
        }
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "スタンバイの起動",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "バックグラウンドからスタンバイ画面を開くための通知"
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }

    fun foreground(visible: Boolean, autoLaunch: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val previewIntent = PendingIntent.getActivity(
            context,
            11,
            StandByActivity.previewIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            visible -> "スタンバイを表示中"
            autoLaunch -> "充電中に横向きでスタンバイを表示します"
            else -> "自動起動はオフです"
        }
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_standby)
            .setContentTitle("StandBy")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "スタンバイを開く", previewIntent)
            .build()
    }

    fun showLaunchReady() {
        if (!manager.areNotificationsEnabled()) return
        val previewIntent = PendingIntent.getActivity(
            context,
            12,
            StandByActivity.previewIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_standby)
            .setContentTitle("スタンバイを表示できます")
            .setContentText("タップして横向きスタンバイを開きます")
            .setContentIntent(previewIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching { manager.notify(ID_LAUNCH_READY, notification) }
    }

    fun showFullScreenIntent() {
        if (!manager.areNotificationsEnabled()) return
        val intent = StandByActivity.launchIntent(context, preview = false)
        val pendingIntent = PendingIntent.getActivity(
            context,
            13,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_standby)
            .setContentTitle("StandBy")
            .setContentText("スタンバイを表示しています")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        runCatching { manager.notify(ID_FULL_SCREEN, notification) }
    }

    fun cancelLaunchNotifications() {
        manager.cancel(ID_LAUNCH_READY)
        manager.cancel(ID_FULL_SCREEN)
    }

    fun canUseFullScreenIntent(): Boolean = manager.canUseFullScreenIntent()

    companion object {
        const val CHANNEL_SERVICE = "standby_service"
        const val CHANNEL_ALERT = "standby_alert"
        const val ID_FOREGROUND = 1
        const val ID_LAUNCH_READY = 2
        const val ID_FULL_SCREEN = 3
    }
}
