package jp.yukirawa.standby.trigger

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenState(
    val isOn: Boolean,
    val isLocked: Boolean,
)

class ScreenStateMonitor(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _state.value = read()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun refresh() {
        _state.value = read()
    }

    fun close() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun read(): ScreenState = ScreenState(
        isOn = powerManager.isInteractive,
        isLocked = keyguardManager.isKeyguardLocked,
    )
}
