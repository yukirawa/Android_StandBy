package jp.yukirawa.standby.service

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import jp.yukirawa.standby.StandByApplication
import jp.yukirawa.standby.standby.StandByActivity

class StandByTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val container = (application as StandByApplication).container
        val autoLaunch = container.config.config.value.autoLaunch
        val visible = container.session.visible.value
        qsTile?.apply {
            state = when {
                visible -> Tile.STATE_ACTIVE
                autoLaunch -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            label = "StandBy"
            subtitle = when {
                visible -> "表示中"
                autoLaunch -> "充電中に自動表示"
                else -> "タップでプレビュー"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val container = (application as StandByApplication).container
        container.diagnostics.add("QSタイルからプレビューを起動")
        val pending = PendingIntent.getActivity(
            this,
            20,
            StandByActivity.previewIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { startActivityAndCollapse(pending) }
        if (container.config.config.value.autoLaunch) {
            StandByService.start(this)
        }
    }
}
