package com.example.lednotify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val currentEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        val newEnabled = !currentEnabled
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, newEnabled).apply()

        if (!newEnabled) {
            LedOverlayService.stop(this)
        } else {
            val intent = Intent(this, LedOverlayService::class.java).apply {
                action = LedOverlayService.ACTION_START_LED
                putExtra(LedOverlayService.EXTRA_IS_TEST, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true)

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "LED Notify"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Active" else "Disabled"
        }
        tile.updateTile()
    }

    companion object {
        const val KEY_MASTER_ENABLED = "master_enabled"

        fun isMasterEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MASTER_ENABLED, true)
        }

        fun requestTileUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, LedTileService::class.java)
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
