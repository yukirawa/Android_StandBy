package jp.yukirawa.standby.standby

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import jp.yukirawa.standby.StandByApplication
import jp.yukirawa.standby.di.AppContainer
import jp.yukirawa.standby.service.DismissReason
import jp.yukirawa.standby.ui.theme.StandByTheme
import kotlinx.coroutines.launch

class StandByActivity : ComponentActivity() {
    private lateinit var container: AppContainer
    private val viewModel: StandByViewModel by viewModels {
        StandByViewModel.Factory(container)
    }

    private var isPreview = false
    private var finishing = false
    private var lastFinishSignal = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as StandByApplication).container
        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        container.session.preview.value = isPreview

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            screenBrightness = container.config.config.value.brightness.coerceIn(0.02f, 1f)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        lastFinishSignal = container.session.finishSignal.value
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.session.finishSignal.collect { signal ->
                    if (signal != lastFinishSignal) {
                        lastFinishSignal = signal
                        container.session.consumeFinishReason()
                        finishWithFade()
                    }
                }
            }
        }
        // Window brightness is applied by the Compose screen (dim / normal), which already
        // observes the config. A second collector here would fight with the dim animation.

        setContent {
            StandByTheme {
                StandByRoute(
                    activity = this,
                    viewModel = viewModel,
                    isPreview = isPreview,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setWindowBrightness(container.config.config.value.brightness)
        container.session.markStarted(isPreview)
        viewModel.onVisible()
    }

    override fun onStop() {
        container.session.markStopped()
        super.onStop()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    fun setWindowBrightness(value: Float) {
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = attributes
    }

    fun dismissWithFade() {
        if (!isPreview) container.session.dismissByUser()
        finishWithFade()
    }

    fun finishWithFade() {
        if (finishing) return
        finishing = true
        // overridePendingTransition is deprecated and ignored on Android 14+; use the
        // modern equivalent so the close still fades out.
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        finish()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_PREVIEW = "jp.yukirawa.standby.extra.PREVIEW"

        fun launchIntent(context: Context, preview: Boolean): Intent =
            Intent(context, StandByActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_PREVIEW, preview)
            }

        fun previewIntent(context: Context): Intent = launchIntent(context, preview = true)
    }
}
