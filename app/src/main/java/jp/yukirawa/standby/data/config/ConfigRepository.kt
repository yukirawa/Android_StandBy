package jp.yukirawa.standby.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.yukirawa.standby.model.StandByConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.standbyDataStore by preferencesDataStore(name = "standby_config")

class ConfigRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val key = stringPreferencesKey("config_json")
    private val _config = MutableStateFlow(StandByConfig())
    val config: StateFlow<StandByConfig> = _config.asStateFlow()

    private var persistJob: Job? = null
    private var changedSinceLoad = false

    init {
        scope.launch {
            val preferences = runCatching {
                context.standbyDataStore.data
                    .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                    .first()
            }.getOrNull()
            // Only seed the in-memory value once. Continuously collecting the DataStore
            // would let a debounced write overwrite a newer slider value.
            if (!changedSinceLoad && preferences != null) {
                _config.value = StandByConfig.fromJson(preferences[key])
            }
        }
    }

    suspend fun update(transform: (StandByConfig) -> StandByConfig) {
        val next = transform(_config.value).normalized()
        _config.value = next
        changedSinceLoad = true
        // Sliders can call this dozens of times per second. Keep the in-memory value
        // immediate but debounce the DataStore write so the UI stays responsive.
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            runCatching {
                context.standbyDataStore.edit { preferences ->
                    preferences[key] = next.toJson()
                }
            }
        }
    }

    suspend fun set(config: StandByConfig) {
        val next = config.normalized()
        _config.value = next
        changedSinceLoad = true
        persistJob?.cancel()
        persistJob = scope.launch {
            runCatching {
                context.standbyDataStore.edit { preferences ->
                    preferences[key] = next.toJson()
                }
            }
        }
    }

    suspend fun read(): StandByConfig =
        runCatching {
            val preferences = context.standbyDataStore.data.first()
            StandByConfig.fromJson(preferences[key])
        }.getOrElse { _config.value }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 220L
    }
}
