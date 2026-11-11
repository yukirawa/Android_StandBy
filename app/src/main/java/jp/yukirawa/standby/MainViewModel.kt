package jp.yukirawa.standby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.yukirawa.standby.data.DiagnosticEntry
import jp.yukirawa.standby.di.AppContainer
import jp.yukirawa.standby.model.Preset
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.model.applyPreset
import jp.yukirawa.standby.permissions.PermissionStatus
import jp.yukirawa.standby.service.StandByService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {
    val config = container.config.config
    val configLoaded = container.config.loaded
    val battery = container.battery.state
    val weather = container.weather.state
    val diagnostics = container.diagnostics.entries
    val sessionVisible = container.session.visible

    private val _permissions = MutableStateFlow(container.permissions.status())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    val isXiaomi: Boolean = container.xiaomi.isXiaomi

    fun refreshPermissions() {
        _permissions.value = container.permissions.status()
        container.music.refresh()
    }

    fun updateConfig(transform: (StandByConfig) -> StandByConfig) {
        viewModelScope.launch { container.config.update(transform) }
    }

    fun applyPreset(preset: Preset) {
        updateConfig { it.applyPreset(preset) }
    }

    fun completeOnboarding() {
        updateConfig { it.copy(onboardingCompleted = true) }
    }

    fun refreshWeather() {
        container.weather.refresh()
    }

    fun clearDiagnostics() {
        container.diagnostics.clear()
    }

    fun startService() {
        StandByService.start(appContext)
    }

    fun stopService() {
        StandByService.stop(appContext)
    }

    fun preview(context: Context) {
        runCatching {
            context.startActivity(
                jp.yukirawa.standby.standby.StandByActivity.previewIntent(context),
            )
        }
    }

    fun entriesText(): String = container.diagnostics.asText()

    fun openOverlaySettings() = container.permissions.openOverlaySettings()

    fun openNotificationSettings() = container.permissions.openNotificationSettings()

    fun requestBatteryExemption() = container.permissions.requestBatteryExemption()

    fun openNotificationAccess() = container.permissions.openNotificationAccessSettings()

    fun openFullScreenIntentSettings() = container.permissions.openFullScreenIntentSettings()

    fun openAppDetails() = container.permissions.openAppDetails()

    fun openXiaomiAutoStart() {
        container.xiaomi.openAutoStart()
    }

    fun openXiaomiBattery() {
        container.xiaomi.openBatterySettings()
    }

    fun openXiaomiPermissionEditor() {
        container.xiaomi.openPermissionEditor()
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(container, appContext) as T
    }
}
