package jp.yukirawa.standby.standby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.yukirawa.standby.di.AppContainer
import jp.yukirawa.standby.model.Preset
import jp.yukirawa.standby.model.StandByConfig
import jp.yukirawa.standby.model.applyPreset
import kotlinx.coroutines.launch

class StandByViewModel(private val container: AppContainer) : ViewModel() {
    val config = container.config.config
    val weather = container.weather.state
    val music = container.music.state
    val battery = container.battery.state

    // Refreshes are triggered from onResume via [onVisible]; doing them here as well would
    // duplicate the work on every launch.

    fun onVisible() {
        container.music.refresh()
        container.weather.refresh()
    }

    fun refreshWeather() {
        container.weather.refresh()
    }

    fun refreshMusic() {
        container.music.refresh()
    }

    fun updateConfig(transform: (StandByConfig) -> StandByConfig) {
        viewModelScope.launch { container.config.update(transform) }
    }

    fun applyPreset(preset: Preset) {
        updateConfig { it.applyPreset(preset) }
    }

    fun toggleNightMode() {
        updateConfig { it.copy(nightMode = !it.nightMode) }
    }

    fun toggleMusicPlayback() = container.music.togglePlayPause()

    fun previousTrack() = container.music.previous()

    fun nextTrack() = container.music.next()

    fun openMusicApp() = container.music.openMusicApp()

    fun openNotificationAccess() = container.permissions.openNotificationAccessSettings()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StandByViewModel(container) as T
    }
}
