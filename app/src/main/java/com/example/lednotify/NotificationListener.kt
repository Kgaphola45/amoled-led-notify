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

        var contactName: String? = null

        // WhatsApp per-contact extraction
        if (pkgName == "com.whatsapp" || pkgName == "com.whatsapp.w4b") {
            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val extras = sbn.notification.extras
            if (extras != null) {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

                // Skip generic summary notifications (e.g. "WhatsApp", "X new messages") so they don't overwrite contact
                if (isSummary && (title.equals("WhatsApp", ignoreCase = true) ||
                                  title.equals("WhatsApp Business", ignoreCase = true) ||
                                  title?.contains("new messages", ignoreCase = true) == true)) {
                    return
                }

                // Detect group chat: if subText is present or text has "Sender: message" format
                val isGroup = !subText.isNullOrEmpty() || (text != null && text.contains(":") && title != null && !title.contains(":"))
                if (isGroup && text != null && text.contains(":")) {
                    val extracted = text.substringBefore(":").trim()
                    if (extracted.isNotEmpty() && !extracted.equals("WhatsApp", ignoreCase = true)) {
                        contactName = extracted
                    }
                }

                if (contactName.isNullOrEmpty() && !title.isNullOrEmpty() &&
                    !title.equals("WhatsApp", ignoreCase = true) &&
                    !title.equals("WhatsApp Business", ignoreCase = true)) {
                    contactName = title
                }
            }
        }

        // Start LedOverlayService with action START_LED and pass packageName (+ senderName if WhatsApp)
        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_START_LED
            putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, pkgName)
            if (!contactName.isNullOrBlank()) {
                putExtra(LedOverlayService.EXTRA_SENDER_NAME, contactName.trim())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
