package jp.yukirawa.standby.trigger

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class OrientationState(
    val isLandscape: Boolean = false,
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val flat: Boolean = false,
    val displayLandscape: Boolean = false,
    val source: String = "初期値",
)

/**
 * Uses the accelerometer while screen is off and the display rotation while the screen is on.
 * A flat device falls back to the last display rotation, as documented in the plan.
 */
class OrientationMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(OrientationState(displayLandscape = readDisplayLandscape()))
    val state: StateFlow<OrientationState> = _state.asStateFlow()

    private var screenOn = true
    private var sensorLandscape = false
    private var lastDisplayLandscape = readDisplayLandscape()
    private var pendingLandscape: Boolean? = null
    private var applied = false
    private var monitoring = false
    private var lastRoll = 0f
    private var lastPitch = 0f
    private var lastFlat = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            lastDisplayLandscape = readDisplayLandscape()
            if (screenOn) publish(source = "画面回転")
        }
    }

    private val commitRunnable = Runnable {
        if (pendingLandscape != null) {
            sensorLandscape = pendingLandscape!!
            pendingLandscape = null
            publish(source = if (screenOn) "画面回転" else "加速度")
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, handler)
    }

    fun setScreenOn(on: Boolean) {
        screenOn = on
        if (on) {
            lastDisplayLandscape = readDisplayLandscape()
            publish(source = "画面回転")
        }
    }

    fun start() {
        if (monitoring) return
        monitoring = true
        applied = false
        accelerometer?.let {
            // NORMAL is plenty: the trigger only needs rotation, not a smooth animation.
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        publish(source = "監視開始")
    }

    fun stop() {
        if (!monitoring) return
        monitoring = false
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(commitRunnable)
        pendingLandscape = null
    }

    fun close() {
        stop()
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2(-z.toDouble(), sqrt((x * x + y * y).toDouble()))).toFloat()
        val flat = sqrt((x * x + y * y).toDouble()) < 2.0
        lastRoll = roll
        lastPitch = pitch
        lastFlat = flat

        val candidate = if (flat) {
            lastDisplayLandscape
        } else {
            val magnitude = abs(roll)
            if (sensorLandscape) magnitude in 55f..125f else magnitude in 65f..115f
        }

        // Never emit on every accelerometer sample. StateFlow is only updated when
        // the debounced landscape decision changes (see publish()).
        if (candidate == sensorLandscape) {
            pendingLandscape = null
            handler.removeCallbacks(commitRunnable)
            return
        }
        if (pendingLandscape != candidate) {
            pendingLandscape = candidate
            handler.removeCallbacks(commitRunnable)
            handler.postDelayed(commitRunnable, DEBOUNCE_MS)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish(source: String) {
        val landscape = if (screenOn) {
            lastDisplayLandscape || sensorLandscape
        } else {
            sensorLandscape
        }
        val next = _state.value.copy(
            isLandscape = landscape,
            rollDeg = lastRoll,
            pitchDeg = lastPitch,
            flat = lastFlat,
            displayLandscape = lastDisplayLandscape,
            source = if (screenOn) "画面回転" else source,
        )
        if (next != _state.value) {
            _state.value = next
        }
        applied = true
    }

    private fun readDisplayLandscape(): Boolean {
        val rotation = runCatching {
            displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation
        }.getOrDefault(Surface.ROTATION_0)
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
