package com.example.lednotify

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        if (pkgName == packageName) return

        syncActiveAlerts(postedSbn = sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        if (pkgName == packageName) return

        syncActiveAlerts(removedKey = sbn.key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncActiveAlerts()
    }

    private fun syncActiveAlerts(postedSbn: StatusBarNotification? = null, removedKey: String? = null) {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences(AppColorManager.PREFS_NAME, Context.MODE_PRIVATE)
        val globalColor = prefs.getInt(LedOverlayService.KEY_COLOR, 0xFF00FF00.toInt())
        val defaultAppColor = appPrefs.getInt(AppColorManager.KEY_DEFAULT_COLOR, globalColor)

        val activeList = try {
            activeNotifications?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

        // Include the newly posted notification if not yet reflected in activeNotifications
        if (postedSbn != null && activeList.none { it.key == postedSbn.key }) {
            activeList.add(postedSbn)
        }

        val alerts = mutableListOf<AlertInfo>()
        for (sbn in activeList) {
            if (sbn.key == removedKey) continue

            val pkg = sbn.packageName ?: continue
            if (pkg == packageName) continue

            val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            if (isOngoing) continue

            if (!AppColorManager.isAppEnabled(this, pkg)) continue

            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()

            // Skip generic group summary containers (e.g. "WhatsApp", "X new messages")
            if (isSummary && (title.equals("WhatsApp", ignoreCase = true) ||
                              title.equals("WhatsApp Business", ignoreCase = true) ||
                              title?.contains("new messages", ignoreCase = true) == true)) {
                continue
            }

            var contactName: String? = null
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
                contactName = extractWhatsAppContact(sbn)
            }

            // Resolve color
            val contactColor = if (!contactName.isNullOrBlank()) {
                ContactColorManager.getColorForContact(this, contactName)
            } else {
                null
            }

            val resolvedColor = contactColor
                ?: if (appPrefs.contains(pkg)) appPrefs.getInt(pkg, defaultAppColor)
                else defaultAppColor

            // Resolve speed
            val resolvedSpeed = if (!contactName.isNullOrBlank()) {
                ContactColorManager.getSpeedForContact(this, contactName)
            } else {
                ContactColorManager.DEFAULT_BLINK_SPEED_MS
            }

            val displayName = contactName ?: getAppName(pkg)
            alerts.add(AlertInfo(pkg, contactName, displayName, resolvedColor, resolvedSpeed))
        }

        // Deduplicate alerts so multiple notifications from the same sender/app only count once
        val distinctAlerts = alerts.distinctBy { Pair(it.pkgName, it.contactName ?: "") }

        if (distinctAlerts.isEmpty()) {
            val autoDismiss = prefs.getBoolean(LedOverlayService.KEY_AUTO_DISMISS, true)
            if (autoDismiss && !LedOverlayService.isManualMode) {
                LedOverlayService.stop(this)
            }
            return
        }

        val colors = distinctAlerts.map { it.color }.toIntArray()
        val speeds = distinctAlerts.map { it.speed }.toIntArray()
        val names = distinctAlerts.map { it.displayName }.toTypedArray()
        val latest = distinctAlerts.last()

        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_START_LED
            putExtra(LedOverlayService.EXTRA_ALERT_COLORS, colors)
            putExtra(LedOverlayService.EXTRA_ALERT_SPEEDS, speeds)
            putExtra(LedOverlayService.EXTRA_ALERT_NAMES, names)
            putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, latest.pkgName)
            if (!latest.contactName.isNullOrBlank()) {
                putExtra(LedOverlayService.EXTRA_SENDER_NAME, latest.contactName)
            }
            putExtra(LedOverlayService.EXTRA_IS_TEST, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun extractWhatsAppContact(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        val isGroup = !subText.isNullOrEmpty() || (text != null && text.contains(":") && title != null && !title.contains(":"))
        if (isGroup && text != null && text.contains(":")) {
            val extracted = text.substringBefore(":").trim()
            if (extracted.isNotEmpty() && !extracted.equals("WhatsApp", ignoreCase = true)) {
                return extracted
            }
        }

        if (!title.isNullOrEmpty() &&
            !title.equals("WhatsApp", ignoreCase = true) &&
            !title.equals("WhatsApp Business", ignoreCase = true)) {
            return title
        }
        return null
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}

data class AlertInfo(
    val pkgName: String,
    val contactName: String?,
    val displayName: String,
    val color: Int,
    val speed: Int
)
