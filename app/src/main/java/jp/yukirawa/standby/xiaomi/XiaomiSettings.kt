package jp.yukirawa.standby.xiaomi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class XiaomiSettings(private val context: Context) {
    val isXiaomi: Boolean
        get() {
            val maker = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            return maker.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") || brand.contains("poco")
        }

    /**
     * HyperOS changes these components between releases, so every path is best-effort.
     * Returns true when a settings screen was opened.
     */
    fun openAutoStart(): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartDetailManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", context.packageName)
                putExtra("extra_pkgname", context.packageName)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null &&
                runCatching { context.startActivity(intent) }.isSuccess
            ) {
                return true
            }
        }
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("extra_pkgname", context.packageName)
        }
        if (context.packageManager.resolveActivity(miuiIntent, 0) != null &&
            runCatching { context.startActivity(miuiIntent) }.isSuccess
        ) {
            return true
        }
        return openAppDetails()
    }

    /**
     * Opens HyperOS' per-app permission editor, where "lock screen display" and
     * "background pop-up" live on many HyperOS builds.
     */
    fun openPermissionEditor(): Boolean {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("extra_pkgname", context.packageName)
            putExtra("package_name", context.packageName)
        }
        if (context.packageManager.resolveActivity(intent, 0) != null &&
            runCatching { context.startActivity(intent) }.isSuccess
        ) {
            return true
        }
        return openAppDetails()
    }

    fun openBatterySettings(): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.BatterySettings",
            ),
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) != null &&
                runCatching { context.startActivity(intent) }.isSuccess
            ) {
                return true
            }
        }
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess || openAppDetails()
    }

    fun openAppDetails(): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
