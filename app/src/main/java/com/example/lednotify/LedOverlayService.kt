package com.example.lednotify

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.util.Calendar

class LedOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var ledDot: View? = null
    private var dragRing: View? = null
    private var dotAnimator: ObjectAnimator? = null
    private var currentPackageName: String? = null
    private var currentSenderName: String? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var activeColor: Int = 0xFF00FF00.toInt()
    private var activeSpeedMs: Int = ContactColorManager.DEFAULT_BLINK_SPEED_MS

    // Multi-notification cycling
    private var activeAlertColors: IntArray = intArrayOf()
    private var activeAlertSpeeds: IntArray = intArrayOf()
    private var activeAlertNames: Array<String> = arrayOf()
    private var activeAlertPatterns: Array<String> = arrayOf()
    private val cycleHandler = Handler(Looper.getMainLooper())
    private var cycleIndex = 0

    // Screen and lockscreen awareness
    private var isScreenOn = true
    private var isReceiverRegistered = false

    // Proximity / Pocket mode sensor
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isProximityCovered = false

    // Battery monitoring & hardware triggers
    private var currentBatteryPct = 100
    private var isBatteryCharging = false
    private var isBatteryFull = false
    private var isPluggedIn = false

    // Auto-off timer
    private var notificationStartTimeMs: Long = System.currentTimeMillis()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    // Burn-in protection (Pixel shift)
    private val pixelShiftOffsets = arrayOf(
        Pair(0, 0),
        Pair(2, 2),
        Pair(-2, 2),
        Pair(2, -2),
        Pair(-2, -2),
        Pair(0, 3),
        Pair(3, 0),
        Pair(0, -3),
        Pair(-3, 0)
    )
    private var currentShiftIndex = 0
    private var currentShiftX = 0
    private var currentShiftY = 0
    private val pixelShiftHandler = Handler(Looper.getMainLooper())

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || proximitySensor == null) return
            val distance = event.values[0]
            val maxRange = proximitySensor!!.maximumRange
            val threshold = minOf(maxRange, 5f)
            val covered = distance < threshold
            if (isProximityCovered != covered) {
                isProximityCovered = covered
                evaluateLedVisibilityState()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    evaluateLedVisibilityState()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    evaluateLedVisibilityState()
                }
                Intent.ACTION_USER_PRESENT -> {
                    evaluateLedVisibilityState()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    isPluggedIn = true
                    isBatteryCharging = true
                    evaluateLedVisibilityState()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isPluggedIn = false
                    isBatteryCharging = false
                    evaluateLedVisibilityState()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    isPluggedIn = plugged != 0
                    isBatteryCharging = isPluggedIn || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    isBatteryFull = status == BatteryManager.BATTERY_STATUS_FULL || (level >= scale && level > 0)
                    if (level >= 0 && scale > 0) {
                        currentBatteryPct = (level * 100 / scale.toFloat()).toInt()
                        evaluateLedVisibilityState()
                    }
                }
                Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY -> {
                    evaluateLedVisibilityState()
                }
            }
        }
    }

    private val cycleRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val shouldCycle = prefs.getBoolean(KEY_CYCLE_COLORS, true)

            if (!shouldCycle || activeAlertColors.size <= 1 || isManualMode) {
                return
            }

            cycleIndex = (cycleIndex + 1) % activeAlertColors.size
            val nextColor = activeAlertColors[cycleIndex]
            val nextSpeed = activeAlertSpeeds.getOrElse(cycleIndex) { ContactColorManager.DEFAULT_BLINK_SPEED_MS }
            val nextName = activeAlertNames.getOrElse(cycleIndex) { "Alert" }

            applyColor(nextColor)
            applyBlinkSpeed(nextSpeed)
            updateForegroundNotification("Pending alerts (${activeAlertColors.size}): $nextName")

            cycleHandler.postDelayed(this, CYCLE_INTERVAL_MS)
        }
    }

    private val pixelShiftRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val burnInEnabled = prefs.getBoolean(KEY_BURN_IN_PROTECTION, true)
            if (burnInEnabled && overlayView != null && layoutParams != null) {
                currentShiftIndex = (currentShiftIndex + 1) % pixelShiftOffsets.size
                val (dx, dy) = pixelShiftOffsets[currentShiftIndex]
                currentShiftX = dx
                currentShiftY = dy
                applyCurrentPositionWithShift()
            }
            pixelShiftHandler.postDelayed(this, PIXEL_SHIFT_INTERVAL_MS)
        }
    }

    private val timeoutRunnable = Runnable {
        evaluateLedVisibilityState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        createNotificationChannel()

        // Read initial battery level
        try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                currentBatteryPct = (level * 100 / scale.toFloat()).toInt()
            }
        } catch (e: Exception) {
            // ignore
        }

        // Register screen & battery broadcast receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(screenStateReceiver, filter)
        isReceiverRegistered = true

        // Register proximity sensor for pocket mode
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor != null) {
            sensorManager?.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start pixel shift timer
        pixelShiftHandler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_LED || action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val isTest = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false
        if (isTest) {
            isManualMode = true
        } else if (intent?.hasExtra(EXTRA_PACKAGE_NAME) == true || intent?.hasExtra(EXTRA_ALERT_COLORS) == true) {
            isManualMode = false
        }

        // Reset notification timer for newly arriving notification
        notificationStartTimeMs = System.currentTimeMillis()
        scheduleTimeout()

        if (intent != null) {
            if (intent.hasExtra(EXTRA_ALERT_COLORS)) {
                activeAlertColors = intent.getIntArrayExtra(EXTRA_ALERT_COLORS) ?: intArrayOf()
                activeAlertSpeeds = intent.getIntArrayExtra(EXTRA_ALERT_SPEEDS) ?: intArrayOf()
                activeAlertNames = intent.getStringArrayExtra(EXTRA_ALERT_NAMES) ?: arrayOf()
            }

            if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
                currentPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                currentSenderName = if (intent.hasExtra(EXTRA_SENDER_NAME)) {
                    intent.getStringExtra(EXTRA_SENDER_NAME)
                } else {
                    null
                }
            } else if (intent.hasExtra(EXTRA_SENDER_NAME)) {
                currentSenderName = intent.getStringExtra(EXTRA_SENDER_NAME)
            }
        }

        resolveActiveColorAndSpeed()
        startAsForeground()
        showOverlay()
        setupColorCycling()
        evaluateLedVisibilityState()

        return START_NOT_STICKY
    }

    private fun scheduleTimeout() {
        timeoutHandler.removeCallbacksAndMessages(null)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timeoutMinutes = prefs.getInt(KEY_TIMEOUT_MINUTES, 15)
        if (timeoutMinutes > 0 && !isManualMode) {
            val delayMs = timeoutMinutes * 60 * 1000L
            timeoutHandler.postDelayed(timeoutRunnable, delayMs)
        }
    }

    private fun setupColorCycling() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldCycle = prefs.getBoolean(KEY_CYCLE_COLORS, true)

        cycleHandler.removeCallbacksAndMessages(null)

        if (activeAlertColors.size > 1 && shouldCycle && !isManualMode) {
            cycleIndex = 0
            val initialColor = activeAlertColors[0]
            val initialSpeed = activeAlertSpeeds.getOrElse(0) { ContactColorManager.DEFAULT_BLINK_SPEED_MS }
            applyColor(initialColor)
            applyBlinkSpeed(initialSpeed)
            cycleHandler.postDelayed(cycleRunnable, CYCLE_INTERVAL_MS)
        } else if (activeAlertColors.size == 1) {
            val singleColor = activeAlertColors[0]
            val singleSpeed = activeAlertSpeeds.getOrElse(0) { ContactColorManager.DEFAULT_BLINK_SPEED_MS }
            applyColor(singleColor)
            applyBlinkSpeed(singleSpeed)
        }
    }

    private fun isWithinDndHours(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DND_ENABLED, false)) return false

        val startH = prefs.getInt(KEY_DND_START_HOUR, 23)
        val startM = prefs.getInt(KEY_DND_START_MINUTE, 0)
        val endH = prefs.getInt(KEY_DND_END_HOUR, 7)
        val endM = prefs.getInt(KEY_DND_END_MINUTE, 0)

        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight schedule (e.g. 11:00 PM to 07:00 AM)
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    private fun evaluateLedVisibilityState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Manual test / positioning from MainActivity takes precedence
        if (isManualMode) {
            overlayView?.visibility = View.VISIBLE
            resumeBlinkAnimation()
            return
        }

        // 2. Quiet Hours / DND check
        if (isWithinDndHours()) {
            overlayView?.visibility = View.GONE
            pauseBlinkAnimation()
            return
        }

        // 3. Pocket Mode (Proximity Sensor) check
        val pocketModeEnabled = prefs.getBoolean(KEY_POCKET_MODE, true)
        if (pocketModeEnabled && isProximityCovered) {
            overlayView?.visibility = View.GONE
            pauseBlinkAnimation()
            return
        }

        // 4. Low Battery Cutoff check
        val batteryCutoff = prefs.getInt(KEY_LOW_BATTERY_CUTOFF, 15)
        if (batteryCutoff > 0 && currentBatteryPct in 0..batteryCutoff) {
            overlayView?.visibility = View.GONE
            pauseBlinkAnimation()
            return
        }

        // 5. Auto-Off Timer (Timeout) check
        val timeoutMinutes = prefs.getInt(KEY_TIMEOUT_MINUTES, 15)
        if (timeoutMinutes > 0) {
            val elapsed = System.currentTimeMillis() - notificationStartTimeMs
            if (elapsed >= timeoutMinutes * 60 * 1000L) {
                overlayView?.visibility = View.GONE
                pauseBlinkAnimation()
                return
            }
        }

        // 6. Phone in use (unlocked) check
        val hideWhileUnlocked = prefs.getBoolean(KEY_HIDE_WHILE_UNLOCKED, true)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = km.isKeyguardLocked

        if (hideWhileUnlocked) {
            // When device is locked or screen is off -> show LED!
            // When device is unlocked (user present / actively using) -> hide LED!
            if (!isScreenOn || isLocked) {
                overlayView?.visibility = View.VISIBLE
                resumeBlinkAnimation()
            } else {
                overlayView?.visibility = View.GONE
                pauseBlinkAnimation()
            }
        } else {
            overlayView?.visibility = View.VISIBLE
            resumeBlinkAnimation()
        }
    }

    private fun resolveActiveColorAndSpeed() {
        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences(AppColorManager.PREFS_NAME, Context.MODE_PRIVATE)

        val globalColor = ledPrefs.getInt(KEY_COLOR, 0xFF00FF00.toInt())
        val defaultColor = appPrefs.getInt(AppColorManager.KEY_DEFAULT_COLOR, globalColor)

        // 1. Check contact-specific color & blink speed first (WhatsApp per-contact)
        val contactColor = if (!currentSenderName.isNullOrBlank()) {
            ContactColorManager.getColorForContact(this, currentSenderName)
        } else {
            null
        }

        if (contactColor != null) {
            activeColor = contactColor
            activeSpeedMs = ContactColorManager.getSpeedForContact(this, currentSenderName)
        } else if (!currentPackageName.isNullOrBlank() && appPrefs.contains(currentPackageName)) {
            // 2. Per-app color
            activeColor = appPrefs.getInt(currentPackageName, defaultColor)
            activeSpeedMs = ContactColorManager.DEFAULT_BLINK_SPEED_MS
        } else if (appPrefs.contains(AppColorManager.KEY_DEFAULT_COLOR)) {
            // 3. Fallback default app color
            activeColor = defaultColor
            activeSpeedMs = ContactColorManager.DEFAULT_BLINK_SPEED_MS
        } else {
            // 4. Fallback to latest persisted color or global color
            activeColor = ledPrefs.getInt(KEY_LATEST_COLOR, globalColor)
            activeSpeedMs = ledPrefs.getInt(KEY_LATEST_SPEED, ContactColorManager.DEFAULT_BLINK_SPEED_MS)
        }

        // Persist the latest notification's color and speed so dot keeps it until the next message
        ledPrefs.edit()
            .putInt(KEY_LATEST_COLOR, activeColor)
            .putInt(KEY_LATEST_SPEED, activeSpeedMs)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LED Notification Dot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the notification LED dot overlay"
                setShowBadge(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(customText: String? = null): Notification {
        val stopIntent = Intent(this, LedOverlayService::class.java).apply {
            action = ACTION_STOP_LED
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = customText ?: when {
            activeAlertColors.size > 1 -> "Pending alerts from ${activeAlertColors.size} apps"
            !currentSenderName.isNullOrBlank() -> "Message from $currentSenderName"
            !currentPackageName.isNullOrBlank() -> "Notification received"
            else -> "LED dot active"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AMOLED LED Notify")
            .setContentText(statusText)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Stop LED", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(customText: String? = null) {
        val notification = buildForegroundNotification(customText)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = ledPrefs.getInt(KEY_SIZE, 2).coerceIn(2, 100)

        // If overlay already exists, immediately apply the latest color, blink rate, and size live!
        if (overlayView != null && ledDot != null) {
            applyColor(activeColor)
            applyBlinkSpeed(activeSpeedMs)
            applySize(sizeDp)
            evaluateLedVisibilityState()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val boxPx = maxOf((48 * density).toInt(), sizePx)
        val dotOffsetPx = (boxPx - sizePx) / 2f

        // WRAP_CONTENT/boxPx, NO BLACK BACKGROUND, Screen remains fully usable underneath
        val params = WindowManager.LayoutParams(
            boxPx,
            boxPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // Force maximum display brightness for crisp solid dot on AMOLED
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val (realScreenW, realScreenH) = getRealScreenSize(this)
        val savedPosX = ledPrefs.getFloat(KEY_POS_X, -1f)
        val savedPosY = ledPrefs.getFloat(KEY_POS_Y, -1f)

        val dotX = if (savedPosX >= 0f) {
            savedPosX.coerceIn(0f, (realScreenW - sizePx).coerceAtLeast(0).toFloat())
        } else {
            // Default: Top-Right corner by battery
            (realScreenW - sizePx).coerceAtLeast(0).toFloat()
        }

        val dotY = if (savedPosY >= 0f) {
            savedPosY.coerceIn(0f, (realScreenH - sizePx).coerceAtLeast(0).toFloat())
        } else {
            // Default: 0f (top edge/corner)
            0f
        }

        params.x = (dotX - dotOffsetPx).toInt()
        params.y = (dotY - dotOffsetPx).toInt()
        layoutParams = params

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_led, null)
        view.fitsSystemWindows = false
        val dot = view.findViewById<View>(R.id.ledDot)
        val ring = view.findViewById<View>(R.id.dragRing)

        ledDot = dot
        dragRing = ring
        overlayView = view

        // Apply visual styling (pure solid dot + drag guide ring)
        applySize(sizeDp)
        applyColor(activeColor)

        // Setup drag feedback ring drawable (invisible until touched)
        val ringDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(35, 255, 255, 255))
            setStroke((2 * density).toInt(), Color.argb(180, 255, 255, 255))
        }
        ring.background = ringDrawable

        // Start blinking at the active speed (custom per-contact or default)
        startBlinkAnimation(activeSpeedMs)

        // Draggable overlay: touch anywhere in the box to move smoothly live across the entire screen
        var initialDotX = 0f
        var initialDotY = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            val currentSizeDp = ledPrefs.getInt(KEY_SIZE, 2).coerceIn(2, 100)
            val currentSizePx = (currentSizeDp * density).toInt().coerceAtLeast(2)
            val currentBoxPx = maxOf((48 * density).toInt(), currentSizePx)
            val currentOffsetPx = (currentBoxPx - currentSizePx) / 2f
            val (screenW, screenH) = getRealScreenSize(this)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialDotX = (lp.x + currentOffsetPx)
                    initialDotY = (lp.y + currentOffsetPx)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragRing?.visibility = View.VISIBLE
                    pauseBlinkAnimation()
                    dot.alpha = 1f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    val targetDotX = (initialDotX + deltaX).coerceIn(0f, (screenW - currentSizePx).coerceAtLeast(0).toFloat())
                    val targetDotY = (initialDotY + deltaY).coerceIn(0f, (screenH - currentSizePx).coerceAtLeast(0).toFloat())

                    lp.x = (targetDotX - currentOffsetPx).toInt()
                    lp.y = (targetDotY - currentOffsetPx).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, lp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onPositionChangedListener?.invoke(targetDotX, targetDotY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragRing?.visibility = View.INVISIBLE
                    resumeBlinkAnimation()
                    val finalDotX = (lp.x + currentOffsetPx)
                        .coerceIn(0f, (screenW - currentSizePx).coerceAtLeast(0).toFloat())
                    val finalDotY = (lp.y + currentOffsetPx)
                        .coerceIn(0f, (screenH - currentSizePx).coerceAtLeast(0).toFloat())
                    ledPrefs.edit()
                        .putFloat(KEY_POS_X, finalDotX)
                        .putFloat(KEY_POS_Y, finalDotY)
                        .apply()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragRing?.visibility = View.INVISIBLE
                    resumeBlinkAnimation()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
            evaluateLedVisibilityState()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun applyCurrentPositionWithShift() {
        val lp = layoutParams ?: return
        val density = resources.displayMetrics.density
        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = ledPrefs.getInt(KEY_SIZE, 2).coerceIn(2, 100)
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val boxPx = maxOf((48 * density).toInt(), sizePx)
        val dotOffsetPx = (boxPx - sizePx) / 2f
        val (realScreenW, realScreenH) = getRealScreenSize(this)

        val savedPosX = ledPrefs.getFloat(KEY_POS_X, -1f)
        val savedPosY = ledPrefs.getFloat(KEY_POS_Y, -1f)

        val baseDotX = if (savedPosX >= 0f) {
            savedPosX.coerceIn(0f, (realScreenW - sizePx).coerceAtLeast(0).toFloat())
        } else {
            (realScreenW - sizePx).coerceAtLeast(0).toFloat()
        }

        val baseDotY = if (savedPosY >= 0f) {
            savedPosY.coerceIn(0f, (realScreenH - sizePx).coerceAtLeast(0).toFloat())
        } else {
            0f
        }

        val shiftedDotX = (baseDotX + currentShiftX).coerceIn(0f, (realScreenW - sizePx).coerceAtLeast(0).toFloat())
        val shiftedDotY = (baseDotY + currentShiftY).coerceIn(0f, (realScreenH - sizePx).coerceAtLeast(0).toFloat())

        lp.x = (shiftedDotX - dotOffsetPx).toInt()
        lp.y = (shiftedDotY - dotOffsetPx).toInt()

        try {
            windowManager?.updateViewLayout(overlayView, lp)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun applySize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val boxPx = maxOf((48 * density).toInt(), sizePx)

        ledDot?.let { dot ->
            val lp = dot.layoutParams
            lp.width = sizePx
            lp.height = sizePx
            dot.layoutParams = lp
        }

        overlayView?.let { view ->
            val vlp = view.layoutParams
            if (vlp != null) {
                vlp.width = boxPx
                vlp.height = boxPx
                view.layoutParams = vlp
            }
        }

        val lp = layoutParams
        if (overlayView != null && lp != null) {
            lp.width = boxPx
            lp.height = boxPx
            applyCurrentPositionWithShift()
        }
    }

    fun applyColor(color: Int) {
        activeColor = color
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val style = prefs.getString(KEY_VISUAL_STYLE, STYLE_DOT) ?: STYLE_DOT
        val density = resources.displayMetrics.density

        val dotDrawable = if (style == STYLE_RING) {
            val strokePx = (3 * density).toInt().coerceIn(2, 8)
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(strokePx, color)
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        ledDot?.background = dotDrawable
    }

    fun applyBlinkSpeed(speedMs: Int) {
        activeSpeedMs = speedMs.coerceIn(50, 3000)
        startBlinkAnimation(activeSpeedMs)
    }

    fun applyPosition(dotX: Float, dotY: Float) {
        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ledPrefs.edit()
            .putFloat(KEY_POS_X, dotX)
            .putFloat(KEY_POS_Y, dotY)
            .apply()
        applyCurrentPositionWithShift()
    }

    fun startBlinkAnimation(speedMs: Int = activeSpeedMs) {
        ledDot?.let { dot ->
            dotAnimator?.cancel()
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pattern = prefs.getString(KEY_ANIMATION_PATTERN, PATTERN_BLINK) ?: PATTERN_BLINK

            when (pattern) {
                PATTERN_SOLID -> {
                    dot.alpha = 1f
                    dotAnimator = null
                }
                PATTERN_PULSE -> {
                    val durationMs = maxOf(800L, speedMs.toLong() * 2)
                    dotAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.15f, 1f).apply {
                        duration = durationMs
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                        start()
                    }
                }
                PATTERN_HEARTBEAT -> {
                    val kf0 = Keyframe.ofFloat(0.00f, 0.05f)
                    val kf1 = Keyframe.ofFloat(0.12f, 1.00f)
                    val kf2 = Keyframe.ofFloat(0.24f, 0.05f)
                    val kf3 = Keyframe.ofFloat(0.36f, 1.00f)
                    val kf4 = Keyframe.ofFloat(0.50f, 0.05f)
                    val kf5 = Keyframe.ofFloat(1.00f, 0.05f)
                    val pvh = PropertyValuesHolder.ofKeyframe(View.ALPHA, kf0, kf1, kf2, kf3, kf4, kf5)
                    val durationMs = maxOf(1200L, speedMs.toLong() * 2)
                    dotAnimator = ObjectAnimator.ofPropertyValuesHolder(dot, pvh).apply {
                        duration = durationMs
                        repeatCount = ObjectAnimator.INFINITE
                        start()
                    }
                }
                else -> { // PATTERN_BLINK
                    val durationMs = speedMs.coerceIn(50, 3000).toLong()
                    dotAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.05f).apply {
                        duration = durationMs
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                        start()
                    }
                }
            }
        }
    }

    private fun pauseBlinkAnimation() {
        dotAnimator?.pause()
    }

    private fun resumeBlinkAnimation() {
        if (dotAnimator?.isPaused == true) {
            dotAnimator?.resume()
        }
    }

    private fun removeOverlay() {
        dotAnimator?.cancel()
        dotAnimator = null

        overlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        ledDot = null
        dragRing = null
        layoutParams = null
    }

    override fun onDestroy() {
        instanceRef = null
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                // ignore
            }
            isReceiverRegistered = false
        }
        sensorManager?.unregisterListener(proximityListener)
        cycleHandler.removeCallbacksAndMessages(null)
        pixelShiftHandler.removeCallbacksAndMessages(null)
        timeoutHandler.removeCallbacksAndMessages(null)
        isManualMode = false
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_LED = "START_LED"
        const val ACTION_STOP_LED = "STOP_LED"
        const val ACTION_START = "com.example.lednotify.ACTION_START"
        const val ACTION_STOP = "com.example.lednotify.ACTION_STOP"
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_SENDER_NAME = "senderName"
        const val EXTRA_ALERT_COLORS = "alert_colors"
        const val EXTRA_ALERT_SPEEDS = "alert_speeds"
        const val EXTRA_ALERT_NAMES = "alert_names"
        const val EXTRA_IS_TEST = "is_test"

        const val PREFS_NAME = "led_prefs"
        const val KEY_SIZE = "size"
        const val KEY_COLOR = "color"
        const val KEY_POS_X = "posX"
        const val KEY_POS_Y = "posY"
        const val KEY_LATEST_COLOR = "latest_color"
        const val KEY_LATEST_SPEED = "latest_speed"

        // Screen & lifecycle behavior settings
        const val KEY_HIDE_WHILE_UNLOCKED = "hide_while_unlocked"
        const val KEY_CYCLE_COLORS = "cycle_colors"
        const val KEY_AUTO_DISMISS = "auto_dismiss"

        // AMOLED Health & Battery settings
        const val KEY_BURN_IN_PROTECTION = "burn_in_protection"
        const val KEY_TIMEOUT_MINUTES = "timeout_minutes"
        const val KEY_LOW_BATTERY_CUTOFF = "low_battery_cutoff"
        const val KEY_POCKET_MODE = "pocket_mode"
        const val KEY_DND_ENABLED = "dnd_enabled"
        const val KEY_DND_START_HOUR = "dnd_start_hour"
        const val KEY_DND_START_MINUTE = "dnd_start_minute"
        const val KEY_DND_END_HOUR = "dnd_end_hour"
        const val KEY_DND_END_MINUTE = "dnd_end_minute"

        // Visual styles & animations
        const val KEY_VISUAL_STYLE = "visual_style"
        const val STYLE_DOT = "dot"
        const val STYLE_RING = "ring"

        const val KEY_ANIMATION_PATTERN = "animation_pattern"
        const val PATTERN_BLINK = "blink"
        const val PATTERN_PULSE = "pulse"
        const val PATTERN_HEARTBEAT = "heartbeat"
        const val PATTERN_SOLID = "solid"

        const val CYCLE_INTERVAL_MS = 2500L
        const val PIXEL_SHIFT_INTERVAL_MS = 60000L

        private const val CHANNEL_ID = "led_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        private var instanceRef: WeakReference<LedOverlayService>? = null

        var onPositionChangedListener: ((Float, Float) -> Unit)? = null

        var isManualMode: Boolean = false

        fun getRealScreenSize(context: Context): Pair<Int, Int> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                @Suppress("DEPRECATION")
                val display = wm.defaultDisplay
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(dm)
                Pair(dm.widthPixels, dm.heightPixels)
            }
        }

        fun isRunning(): Boolean = instanceRef?.get() != null

        fun updateLivePosition(dotX: Float, dotY: Float) {
            instanceRef?.get()?.applyPosition(dotX, dotY)
        }

        fun updateLiveSize(sizeDp: Int) {
            instanceRef?.get()?.applySize(sizeDp)
        }

        fun updateLiveColor(color: Int) {
            instanceRef?.get()?.applyColor(color)
        }

        fun updateLiveBlinkSpeed(speedMs: Int) {
            instanceRef?.get()?.applyBlinkSpeed(speedMs)
        }

        fun updateLiveStyle() {
            instanceRef?.get()?.let { service ->
                service.applyColor(service.activeColor)
            }
        }

        fun updateLiveAnimationPattern() {
            instanceRef?.get()?.let { service ->
                service.startBlinkAnimation()
            }
        }

        fun start(context: Context, packageName: String? = null, senderName: String? = null, isTest: Boolean = false) {
            if (isTest) {
                isManualMode = true
            }
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_START_LED
                if (packageName != null) {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
                if (senderName != null) {
                    putExtra(EXTRA_SENDER_NAME, senderName)
                }
                putExtra(EXTRA_IS_TEST, isTest)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startPositioning(context: Context) {
            start(context, isTest = true)
        }

        fun stop(context: Context) {
            isManualMode = false
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_STOP_LED
            }
            context.startService(intent)
        }
    }
}
