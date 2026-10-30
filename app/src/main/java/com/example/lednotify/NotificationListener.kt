package com.example.lednotify

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return

        // Ignore notifications from our own app to prevent loops
        if (pkgName == packageName) return

        // Ignore ongoing background/media notifications
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return

        // Only activate LED if user explicitly enabled this app
        if (!AppColorManager.isAppEnabled(this, pkgName)) {
            return
        }

        // Start LedOverlayService with action START_LED and pass packageName
        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_START_LED
            putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, pkgName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
