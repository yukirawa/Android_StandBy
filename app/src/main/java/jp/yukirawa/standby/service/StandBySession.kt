package jp.yukirawa.standby.service

import jp.yukirawa.standby.data.DiagnosticsLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DismissReason(val label: String) {
    USER("ユーザー操作"),
    POWER("画面オフ"),
    CHARGING_OFF("充電解除"),
    PORTRAIT("縦向き"),
    UNLOCKED("ロック解除"),
    BACKGROUND("バックグラウンド"),
}

/**
 * State shared between [jp.yukirawa.standby.service.StandByService] and
 * [jp.yukirawa.standby.standby.StandByActivity].
 */
class StandBySession(private val diagnostics: DiagnosticsLog) {
    val visible = MutableStateFlow(false)
    val preview = MutableStateFlow(false)
    val startedAt = MutableStateFlow(0L)
    val finishSignal = MutableStateFlow(0L)
    val pendingFinishReason = MutableStateFlow<DismissReason?>(null)
    val lastUserDismissAt = MutableStateFlow(0L)

    private val _dismissEvents = MutableSharedFlow<DismissReason>(extraBufferCapacity = 8)
    val dismissEvents = _dismissEvents.asSharedFlow()

    fun markStarted(isPreview: Boolean) {
        preview.value = isPreview
        visible.value = true
        startedAt.value = System.currentTimeMillis()
        diagnostics.add(if (isPreview) "プレビュー表示を開始" else "スタンバイ表示を開始")
    }

    fun markStopped() {
        if (visible.value) {
            visible.value = false
            diagnostics.add("スタンバイ表示を終了")
        }
    }

    fun requestFinish(reason: DismissReason) {
        pendingFinishReason.value = reason
        finishSignal.update { it + 1 }
        diagnostics.add("終了要求: ${reason.label}")
    }

    fun consumeFinishReason(): DismissReason? {
        val reason = pendingFinishReason.value
        pendingFinishReason.value = null
        return reason
    }

    fun dismissByUser() {
        lastUserDismissAt.value = System.currentTimeMillis()
        diagnostics.add("ユーザーがスタンバイを閉じました")
        _dismissEvents.tryEmit(DismissReason.USER)
    }

    fun dismissBySystem(reason: DismissReason) {
        _dismissEvents.tryEmit(reason)
    }
}
