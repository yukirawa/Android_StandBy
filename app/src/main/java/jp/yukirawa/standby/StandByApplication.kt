package jp.yukirawa.standby

import android.app.Application
import jp.yukirawa.standby.di.AppContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StandByApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.diagnostics.add("アプリケーションを起動しました")
        installCrashLogger()
    }

    /**
     * Writes the last uncaught exception to app storage so the diagnostics screen can show
     * it without ADB. The system handler still runs afterwards.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
                    .format(Date())
                File(filesDir, CRASH_FILE).writeText(
                    buildString {
                        appendLine("時刻: $timestamp")
                        appendLine("端末: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine("Android: API ${android.os.Build.VERSION.SDK_INT}")
                        appendLine()
                        appendLine(error.stackTraceToString())
                    },
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            }
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
