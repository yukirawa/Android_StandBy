package jp.yukirawa.standby.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class PlugType(val label: String) {
    NONE("未接続"),
    AC("有線"),
    USB("USB"),
    WIRELESS("ワイヤレス"),
    DOCK("ドック"),
}

data class BatteryState(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val plugType: PlugType = PlugType.NONE,
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val watts: Float? = null,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
)

class BatteryRepository(private val context: Context) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val _state = MutableStateFlow(readIntent(stickyIntent(context)))
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            _state.value = readIntent(intent)
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun close() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun readIntent(intent: Intent?): BatteryState {
        if (intent == null) return BatteryState()
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
            else -> PlugType.NONE
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE && it > 0 }
            ?.div(10f)

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            .takeIf { it in 2_000..20_000 }

        val watts = estimateWatts(voltage)
        return BatteryState(
            level = percent.coerceIn(0, 100),
            isCharging = isCharging,
            plugType = plugType,
            temperatureC = temperature,
            voltageMv = voltage,
            watts = watts,
            status = status,
        )
    }

    private fun estimateWatts(voltageMv: Int?): Float? {
        if (voltageMv == null) return null
        val currentUa = runCatching {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull() ?: return null
        if (currentUa == Long.MIN_VALUE || currentUa == 0L) return null
        val watts = abs(currentUa) / 1_000_000f * voltageMv / 1_000f
        return watts.takeIf { it.isFinite() && it in 0.1f..150f }
    }

    private companion object {
        fun stickyIntent(context: Context): Intent? =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
}
