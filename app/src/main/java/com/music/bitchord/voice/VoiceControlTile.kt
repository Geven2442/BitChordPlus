package com.music.bitchord.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings

/** Quick Settings tile: tap, then say a command. See [VoiceListenActivity]. */
class VoiceControlTile : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (AppSettings.voiceControl.value) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        if (!AppSettings.voiceControl.value) {
            Toast.makeText(this, R.string.voice_not_enabled, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, VoiceListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
