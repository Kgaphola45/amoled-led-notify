package com.example.lednotify

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_LED || action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent != null) {
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

        return START_NOT_STICKY
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

    private fun startAsForeground() {
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

        val statusText = when {
            !currentSenderName.isNullOrBlank() -> "Message from $currentSenderName"
            !currentPackageName.isNullOrBlank() -> "Notification received"
            else -> "LED dot active"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AMOLED LED Notify")
            .setContentText(statusText)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Stop LED", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    fun applySize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val boxPx = maxOf((48 * density).toInt(), sizePx)
        val dotOffsetPx = (boxPx - sizePx) / 2f

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

            val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPosX = ledPrefs.getFloat(KEY_POS_X, -1f)
            val savedPosY = ledPrefs.getFloat(KEY_POS_Y, -1f)
            val (realScreenW, realScreenH) = getRealScreenSize(this)

            if (savedPosX >= 0f && savedPosY >= 0f) {
                val dotX = savedPosX.coerceIn(0f, (realScreenW - sizePx).coerceAtLeast(0).toFloat())
                val dotY = savedPosY.coerceIn(0f, (realScreenH - sizePx).coerceAtLeast(0).toFloat())
                lp.x = (dotX - dotOffsetPx).toInt()
                lp.y = (dotY - dotOffsetPx).toInt()
            }

            try {
                windowManager?.updateViewLayout(overlayView, lp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun applyColor(color: Int) {
        activeColor = color

        // Pure solid dot: crisp solid color oval, no glow or halo
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        ledDot?.background = dotDrawable
    }

    fun applyBlinkSpeed(speedMs: Int) {
        activeSpeedMs = speedMs.coerceIn(50, 3000)
        startBlinkAnimation(activeSpeedMs)
    }

    fun applyPosition(dotX: Float, dotY: Float) {
        val lp = layoutParams ?: return
        val density = resources.displayMetrics.density
        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = ledPrefs.getInt(KEY_SIZE, 2).coerceIn(2, 100)
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val boxPx = maxOf((48 * density).toInt(), sizePx)
        val dotOffsetPx = (boxPx - sizePx) / 2f

        lp.x = (dotX - dotOffsetPx).toInt()
        lp.y = (dotY - dotOffsetPx).toInt()
        try {
            windowManager?.updateViewLayout(overlayView, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startBlinkAnimation(speedMs: Int = activeSpeedMs) {
        ledDot?.let { dot ->
            dotAnimator?.cancel()
            val durationMs = speedMs.coerceIn(50, 3000).toLong()
            dotAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.05f).apply {
                duration = durationMs
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
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

        const val PREFS_NAME = "led_prefs"
        const val KEY_SIZE = "size"
        const val KEY_COLOR = "color"
        const val KEY_POS_X = "posX"
        const val KEY_POS_Y = "posY"
        const val KEY_LATEST_COLOR = "latest_color"
        const val KEY_LATEST_SPEED = "latest_speed"

        private const val CHANNEL_ID = "led_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        private var instanceRef: WeakReference<LedOverlayService>? = null

        var onPositionChangedListener: ((Float, Float) -> Unit)? = null

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

        fun start(context: Context, packageName: String? = null, senderName: String? = null) {
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_START_LED
                if (packageName != null) {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
                if (senderName != null) {
                    putExtra(EXTRA_SENDER_NAME, senderName)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startPositioning(context: Context) {
            start(context)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_STOP_LED
            }
            context.startService(intent)
        }
    }
}
