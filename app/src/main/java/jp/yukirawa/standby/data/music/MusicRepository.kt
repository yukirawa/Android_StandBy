package jp.yukirawa.standby.data.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import jp.yukirawa.standby.data.DiagnosticsLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicState(
    val hasNotificationAccess: Boolean = false,
    val hasSession: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val albumArt: Bitmap? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canPlayPause: Boolean = true,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val packageName: String? = null,
)

class MusicRepository(
    private val context: Context,
    private val diagnostics: DiagnosticsLog,
) {
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaNotificationListener::class.java)

    private val _state = MutableStateFlow(MusicState(hasNotificationAccess = hasAccess()))
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            diagnostics.add("音楽セッションが変化しました")
            bindBest(controllers.orEmpty())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController()
        }

        override fun onSessionDestroyed() {
            diagnostics.add("音楽セッションが終了しました")
            controller?.unregisterCallback(this)
            controller = null
            _state.value = MusicState(hasNotificationAccess = hasAccess())
        }
    }

    fun hasAccess(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

    fun onListenerConnected() {
        diagnostics.add("通知アクセスが接続されました")
        refresh()
    }

    fun onListenerDisconnected() {
        diagnostics.add("通知アクセスが切断されました")
        stopListening()
        _state.value = MusicState(hasNotificationAccess = false)
    }

    fun refresh() {
        val access = hasAccess()
        if (!access) {
            stopListening()
            _state.value = MusicState(hasNotificationAccess = false)
            return
        }
        startListening()
        val sessions = runCatching {
            sessionManager.getActiveSessions(component)
        }.getOrElse {
            diagnostics.add("音楽セッションの取得に失敗しました: ${it.javaClass.simpleName}")
            emptyList()
        }
        bindBest(sessions)
    }

    fun togglePlayPause() {
        val active = controller ?: return
        if (_state.value.isPlaying) active.transportControls.pause() else active.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    fun openMusicApp() {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent("android.intent.action.MUSIC_PLAYER"),
            Intent(Intent.ACTION_VIEW).setType("audio/*"),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                runCatching { context.startActivity(intent) }
                return
            }
        }
        diagnostics.add("音楽アプリが見つかりませんでした")
    }

    private fun startListening() {
        if (listening) return
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            listening = true
        }.onFailure {
            diagnostics.add("音楽セッション監視を開始できませんでした")
        }
    }

    private fun stopListening() {
        if (!listening) return
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        listening = false
    }

    private fun bindBest(controllers: List<MediaController>) {
        val selected = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()
        if (selected == null) {
            controller?.unregisterCallback(controllerCallback)
            controller = null
            _state.value = MusicState(hasNotificationAccess = true)
            return
        }
        if (selected.sessionToken == controller?.sessionToken) {
            updateFromController()
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = selected
        selected.registerCallback(controllerCallback)
        diagnostics.add("音楽セッションに接続: ${selected.packageName}")
        updateFromController()
    }

    private fun updateFromController() {
        val active = controller ?: return
        val metadata = active.metadata
        val playback = active.playbackState
        val actions = playback?.actions ?: 0L
        val state = MusicState(
            hasNotificationAccess = true,
            hasSession = true,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            artworkUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            positionMs = playback?.position?.coerceAtLeast(0L) ?: 0L,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            packageName = active.packageName,
        )
        _state.value = state
    }
}
