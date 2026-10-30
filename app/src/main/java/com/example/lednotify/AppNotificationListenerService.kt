package com.example.lednotify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AppNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Ignore notifications from our own app to avoid looping
        if (sbn.packageName == packageName) return

        // Ignore ongoing sticky notifications (e.g. media player, system services)
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return

        // Start the LED overlay foreground service
        LedOverlayService.start(this)
    }
}
