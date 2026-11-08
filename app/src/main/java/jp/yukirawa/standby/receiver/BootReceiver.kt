package jp.yukirawa.standby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.yukirawa.standby.StandByApplication
import jp.yukirawa.standby.service.StandByService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        (context.applicationContext as? StandByApplication)?.container?.diagnostics
            ?.add("端末起動を検知しました")
        StandByService.start(context)
    }
}
