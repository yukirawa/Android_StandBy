package jp.yukirawa.standby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.yukirawa.standby.StandByApplication
import jp.yukirawa.standby.service.StandByService

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        (context.applicationContext as? StandByApplication)?.container?.diagnostics
            ?.add("アプリ更新を検知しました")
        StandByService.start(context)
    }
}
