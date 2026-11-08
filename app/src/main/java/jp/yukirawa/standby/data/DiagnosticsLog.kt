package jp.yukirawa.standby.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DiagnosticEntry(
    val time: String,
    val message: String,
)

class DiagnosticsLog {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    fun add(message: String) {
        Log.i(TAG, message)
        val entry = DiagnosticEntry(LocalTime.now().format(formatter), message)
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
        Log.i(TAG, "診断ログをクリアしました")
    }

    fun asText(): String = _entries.value.joinToString(separator = "\n") { entry ->
        "${entry.time}  ${entry.message}"
    }

    private companion object {
        const val TAG = "StandBy"
        const val MAX_ENTRIES = 100
    }
}
