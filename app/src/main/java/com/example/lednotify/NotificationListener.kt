package com.example.lednotify

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncActiveAlerts()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(powerReceiver, filter)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

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

    private fun isBatteryTriggerActive(): Boolean {
        return try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isCharging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (isCharging) return true

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
            val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            val lowBatteryWarn = prefs.getBoolean(LedOverlayService.KEY_LOW_BATTERY_WARNING, true)
            lowBatteryWarn && pct in 1..15
        } catch (e: Exception) {
            false
        }
    }

    private fun syncActiveAlerts(postedSbn: StatusBarNotification? = null, removedKey: String? = null) {
        if (!LedTileService.isMasterEnabled(this)) {
            if (!LedOverlayService.isManualMode) {
                LedOverlayService.stop(this)
            }
            return
        }

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

        val suppressWhenInApp = prefs.getBoolean(LedOverlayService.KEY_SUPPRESS_WHEN_APP_FOREGROUND, true)
        val currentFgApp = if (suppressWhenInApp) ForegroundAppDetector.getForegroundPackage(this) else null

        val alerts = mutableListOf<AlertInfo>()
        for (sbn in activeList) {
            if (sbn.key == removedKey) continue

            val pkg = sbn.packageName ?: continue
            if (pkg == packageName) continue

            // Don't blink if the user is currently using the app that sent the alert
            if (suppressWhenInApp && currentFgApp != null && pkg == currentFgApp) {
                continue
            }

            val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            if (isOngoing) continue

            if (!AppColorManager.isAppEnabled(this, pkg)) continue

            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()

            // Skip generic group summary containers (e.g. "WhatsApp", "X new messages")
            if (isSummary && isGenericSummaryContainer(title)) {
                continue
            }

            val isMissedCall = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sbn.notification.category == Notification.CATEGORY_MISSED_CALL else sbn.notification.category == "missed_call") ||
                    (pkg in MISSED_CALL_PACKAGES && (title?.contains("missed", ignoreCase = true) == true || title?.contains("call", ignoreCase = true) == true))

            val isSms = pkg in SMS_PACKAGES || sbn.notification.category == Notification.CATEGORY_MESSAGE

            var contactName: String? = null
            if (pkg in MESSAGING_PACKAGES || isSms || isMissedCall) {
                contactName = extractSenderContact(sbn)
            }

            val resolvedColor: Int
            val resolvedSpeed: Int
            val resolvedPattern: String
            val priorityRank: Int
            val displayName: String

            if (isMissedCall && prefs.getBoolean(LedOverlayService.KEY_MISSED_CALL_ALERT, true)) {
                val defaultMissedColor = prefs.getInt(LedOverlayService.KEY_MISSED_CALL_COLOR, 0xFFFF1744.toInt())
                val contactColor = if (!contactName.isNullOrBlank()) ContactColorManager.getColorForContact(this, contactName) else null
                resolvedColor = contactColor ?: defaultMissedColor
                resolvedSpeed = 200 // Fast attention blink
                resolvedPattern = LedOverlayService.PATTERN_HEARTBEAT // Distinct double-pulse
                priorityRank = 1
                displayName = "Missed Call: ${contactName ?: title ?: "Unknown"}"
            } else if (isSms && prefs.getBoolean(LedOverlayService.KEY_SMS_ALERT, true)) {
                val defaultSmsColor = prefs.getInt(LedOverlayService.KEY_SMS_COLOR, 0xFFFFD600.toInt())
                val contactColor = if (!contactName.isNullOrBlank()) ContactColorManager.getColorForContact(this, contactName) else null
                resolvedColor = contactColor ?: defaultSmsColor
                resolvedSpeed = if (!contactName.isNullOrBlank()) ContactColorManager.getSpeedForContact(this, contactName) else 400
                resolvedPattern = prefs.getString(LedOverlayService.KEY_ANIMATION_PATTERN, LedOverlayService.PATTERN_BLINK) ?: LedOverlayService.PATTERN_BLINK
                priorityRank = 2
                displayName = "SMS: ${contactName ?: title ?: "Message"}"
            } else {
                val contactColor = if (!contactName.isNullOrBlank()) {
                    ContactColorManager.getColorForContact(this, contactName)
                } else {
                    null
                }

                resolvedColor = contactColor
                    ?: if (appPrefs.contains(pkg)) appPrefs.getInt(pkg, defaultAppColor)
                    else defaultAppColor

                resolvedSpeed = if (!contactName.isNullOrBlank()) {
                    ContactColorManager.getSpeedForContact(this, contactName)
                } else {
                    ContactColorManager.DEFAULT_BLINK_SPEED_MS
                }

                resolvedPattern = prefs.getString(LedOverlayService.KEY_ANIMATION_PATTERN, LedOverlayService.PATTERN_BLINK) ?: LedOverlayService.PATTERN_BLINK
                priorityRank = 3
                displayName = contactName ?: getAppName(pkg)
            }

            alerts.add(AlertInfo(pkg, contactName, displayName, resolvedColor, resolvedSpeed, resolvedPattern, priorityRank))
        }

        // Deduplicate alerts and prioritize Missed Calls & SMS first
        val distinctAlerts = alerts.distinctBy { Pair(it.pkgName, it.contactName ?: "") }
            .sortedBy { it.priorityRank }

        if (distinctAlerts.isEmpty()) {
            val autoDismiss = prefs.getBoolean(LedOverlayService.KEY_AUTO_DISMISS, true)
            val batteryIndicatorEnabled = prefs.getBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, true)
            if (batteryIndicatorEnabled && isBatteryTriggerActive()) {
                val intent = Intent(this, LedOverlayService::class.java).apply {
                    action = LedOverlayService.ACTION_START_LED
                    putExtra(LedOverlayService.EXTRA_ALERT_COLORS, intArrayOf())
                    putExtra(LedOverlayService.EXTRA_ALERT_SPEEDS, intArrayOf())
                    putExtra(LedOverlayService.EXTRA_ALERT_NAMES, arrayOf<String>())
                    putExtra(LedOverlayService.EXTRA_ALERT_PATTERNS, arrayOf<String>())
                    putExtra(LedOverlayService.EXTRA_IS_TEST, false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else if (autoDismiss && !LedOverlayService.isManualMode) {
                LedOverlayService.stop(this)
            }
            return
        }

        val colors = distinctAlerts.map { it.color }.toIntArray()
        val speeds = distinctAlerts.map { it.speed }.toIntArray()
        val names = distinctAlerts.map { it.displayName }.toTypedArray()
        val patterns = distinctAlerts.map { it.pattern }.toTypedArray()
        val packages = distinctAlerts.map { it.pkgName }.toTypedArray()
        val primary = distinctAlerts.first()

        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_START_LED
            putExtra(LedOverlayService.EXTRA_ALERT_COLORS, colors)
            putExtra(LedOverlayService.EXTRA_ALERT_SPEEDS, speeds)
            putExtra(LedOverlayService.EXTRA_ALERT_NAMES, names)
            putExtra(LedOverlayService.EXTRA_ALERT_PATTERNS, patterns)
            putExtra(LedOverlayService.EXTRA_ALERT_PACKAGES, packages)
            putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, primary.pkgName)
            if (!primary.contactName.isNullOrBlank()) {
                putExtra(LedOverlayService.EXTRA_SENDER_NAME, primary.contactName)
            }
            putExtra(LedOverlayService.EXTRA_IS_TEST, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun extractSenderContact(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        // 1. Android 9+ MessagingStyle Person object
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                @Suppress("DEPRECATION")
                val person = extras.getParcelable<android.app.Person>(Notification.EXTRA_MESSAGING_PERSON)
                val personName = person?.name?.toString()?.trim()
                if (!personName.isNullOrEmpty() && !isGenericAppName(personName)) {
                    return personName
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. Format "Sender: Message" in group chats (WhatsApp, Telegram, Signal, Discord, Messages)
        if (text != null && text.contains(":") && !text.startsWith("http://") && !text.startsWith("https://")) {
            val prefix = text.substringBefore(":").trim()
            if (prefix.isNotEmpty() && prefix.length <= 30 && !prefix.contains("\n") && !isGenericAppName(prefix)) {
                return prefix
            }
        }

        // 3. SubText presence: title is sender when subText is group name or channel
        if (!subText.isNullOrEmpty() && !title.isNullOrEmpty() && !isGenericAppName(title)) {
            return title
        }

        // 4. Fallback to title
        if (!title.isNullOrEmpty() && !isGenericAppName(title)) {
            return title
        }

        return null
    }

    private fun isGenericSummaryContainer(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.lowercase()
        return lower in setOf("whatsapp", "whatsapp business", "telegram", "signal", "discord", "messages") ||
                lower.contains("new messages") || lower.contains("unread messages")
    }

    private fun isGenericAppName(name: String): Boolean {
        val lower = name.lowercase()
        return lower in setOf(
            "whatsapp", "whatsapp business", "telegram", "signal", "discord",
            "messages", "google messages", "sms", "mms", "phone", "dialer"
        ) || lower.endsWith("messages") || lower.contains("new message")
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

    companion object {
        val MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.thoughtcrime.securesms",
            "com.discord",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms"
        )

        val MISSED_CALL_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.android.dialer"
        )

        val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms"
        )
    }
}

data class AlertInfo(
    val pkgName: String,
    val contactName: String?,
    val displayName: String,
    val color: Int,
    val speed: Int,
    val pattern: String = LedOverlayService.PATTERN_BLINK,
    val priorityRank: Int = 3
)
