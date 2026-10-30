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
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class LedOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var animator: ObjectAnimator? = null
    private var isPositioningMode = false
    private var currentPackageName: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_LED || action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        isPositioningMode = (action == ACTION_POSITION_LED)
        if (intent?.hasExtra(EXTRA_PACKAGE_NAME) == true) {
            currentPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        }

        startAsForeground()
        acquireWakeLock()
        showOverlay()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AMOLED LED Notification Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AMOLED LED overlay active for notifications"
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

        val notificationText = if (isPositioningMode) {
            "LED positioning mode active. Tap screen when finished."
        } else {
            "LED notification active. Tap black screen to dismiss."
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.led_circle)
            .setContentTitle("AMOLED LED Notify")
            .setContentText(notificationText)
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
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AMOLEDLEDNotify:LedWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        // Per-App color takes precedence, fallback to default_color in app_colors or led_prefs
        val color = AppColorManager.getColorForPackage(this, currentPackageName)

        // If overlay already displayed, simply refresh the LED color to the new app's color
        if (overlayView != null) {
            val ledDot = overlayView?.findViewById<View>(R.id.ledDot)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            ledDot?.background = drawable
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Full screen flags including cutout extension and reaching all 4 corners
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_led, null)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt(KEY_SIZE, 22)
        val savedPosX = prefs.getFloat(KEY_POS_X, -1f)
        val savedPosY = prefs.getFloat(KEY_POS_Y, -1f)

        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)

        val ledTouchContainer = view.findViewById<FrameLayout>(R.id.ledTouchContainer)
        val ledDot = view.findViewById<View>(R.id.ledDot)
        val dragGuideRing = view.findViewById<View>(R.id.dragGuideRing)
        val tvPositionPrompt = view.findViewById<TextView>(R.id.tvPositionPrompt)

        if (isPositioningMode) {
            tvPositionPrompt.visibility = View.VISIBLE
        } else {
            tvPositionPrompt.visibility = View.GONE
        }

        // Apply dot size
        val dotParams = ledDot.layoutParams
        dotParams.width = sizePx
        dotParams.height = sizePx
        ledDot.layoutParams = dotParams

        // Apply per-app color
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        ledDot.background = drawable

        // Generous touch hitbox (at least 54dp) so even a 2dp dot is easy to drag into corners
        val touchSize = maxOf(sizePx, (54 * density).toInt())
        val containerParams = ledTouchContainer.layoutParams
        containerParams.width = touchSize
        containerParams.height = touchSize
        ledTouchContainer.layoutParams = containerParams

        // Guide ring around touch target for precise visual alignment while dragging
        val ringSize = maxOf(sizePx + (16 * density).toInt(), (48 * density).toInt())
        val ringParams = dragGuideRing.layoutParams
        ringParams.width = ringSize
        ringParams.height = ringSize
        dragGuideRing.layoutParams = ringParams
        dragGuideRing.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((2 * density).toInt(), Color.argb(200, 255, 255, 255))
        }

        var dotX = 0f
        var dotY = 0f

        // Calculate and apply initial position
        view.post {
            val screenW = view.width.toFloat()
            val screenH = view.height.toFloat()

            if (savedPosX >= 0f && savedPosY >= 0f) {
                dotX = savedPosX.coerceIn(0f, (screenW - sizePx).coerceAtLeast(0f))
                dotY = savedPosY.coerceIn(0f, (screenH - sizePx).coerceAtLeast(0f))
            } else {
                // Default to top-right corner
                val margin = 16 * density
                dotX = (screenW - sizePx - margin).coerceAtLeast(0f)
                dotY = margin
            }

            ledTouchContainer.x = dotX - (touchSize - sizePx) / 2f
            ledTouchContainer.y = dotY - (touchSize - sizePx) / 2f
        }

        // Setup blink animation: alpha 1f to 0f, 600ms, INFINITE REVERSE
        animator = ObjectAnimator.ofFloat(ledDot, View.ALPHA, 1f, 0f).apply {
            duration = 600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        // Touch listener on container enables dragging to any position including extreme corners
        var dX = 0f
        var dY = 0f
        ledTouchContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = dotX - event.rawX
                    dY = dotY - event.rawY
                    animator?.pause()
                    ledDot.alpha = 1f
                    dragGuideRing.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rawX = event.rawX + dX
                    val rawY = event.rawY + dY

                    val screenW = overlayView?.width?.toFloat() ?: 0f
                    val screenH = overlayView?.height?.toFloat() ?: 0f

                    val maxX = (screenW - sizePx).coerceAtLeast(0f)
                    val maxY = (screenH - sizePx).coerceAtLeast(0f)

                    // Allow positioning to the literal edge/corners [0..maxX, 0..maxY]
                    dotX = if (maxX > 0f) rawX.coerceIn(0f, maxX) else rawX
                    dotY = if (maxY > 0f) rawY.coerceIn(0f, maxY) else rawY

                    v.x = dotX - (touchSize - sizePx) / 2f
                    v.y = dotY - (touchSize - sizePx) / 2f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragGuideRing.visibility = View.GONE
                    if (animator?.isPaused == true) {
                        animator?.resume()
                    }
                    prefs.edit()
                        .putFloat(KEY_POS_X, dotX)
                        .putFloat(KEY_POS_Y, dotY)
                        .apply()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragGuideRing.visibility = View.GONE
                    if (animator?.isPaused == true) {
                        animator?.resume()
                    }
                    true
                }
                else -> false
            }
        }

        // Tapping overlay background (pure black) stops the service
        val overlayRoot = view.findViewById<View>(R.id.overlayRoot)
        overlayRoot.setOnClickListener {
            stopSelf()
        }

        overlayView = view
        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun removeOverlay() {
        animator?.cancel()
        animator = null

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
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        removeOverlay()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_LED = "START_LED"
        const val ACTION_STOP_LED = "STOP_LED"
        const val ACTION_POSITION_LED = "POSITION_LED"
        const val ACTION_START = "com.example.lednotify.ACTION_START"
        const val ACTION_STOP = "com.example.lednotify.ACTION_STOP"
        const val EXTRA_PACKAGE_NAME = "packageName"

        const val PREFS_NAME = "led_prefs"
        const val KEY_SIZE = "size"
        const val KEY_COLOR = "color"
        const val KEY_POS_X = "posX"
        const val KEY_POS_Y = "posY"

        private const val CHANNEL_ID = "led_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, packageName: String? = null) {
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_START_LED
                if (packageName != null) {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startPositioning(context: Context) {
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_POSITION_LED
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LedOverlayService::class.java).apply {
                action = ACTION_STOP_LED
            }
            context.startService(intent)
        }
    }
}
