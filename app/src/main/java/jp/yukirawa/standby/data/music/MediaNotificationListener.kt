package jp.yukirawa.standby.data.music

import android.service.notification.NotificationListenerService
import jp.yukirawa.standby.StandByApplication

/**
 * Exists only to obtain MediaSession access. Notification content is never read or stored.
 */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as? StandByApplication)?.container?.music?.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        (application as? StandByApplication)?.container?.music?.onListenerDisconnected()
    }
}
