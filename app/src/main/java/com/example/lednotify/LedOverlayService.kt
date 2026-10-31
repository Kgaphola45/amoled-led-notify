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
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class LedOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var ledDot: View? = null
    private var animator: ObjectAnimator? = null
    private var currentPackageName: String? = null
    private var currentSenderName: String? = null
    private var layoutParams: WindowManager.LayoutParams? = null

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

        if (intent?.hasExtra(EXTRA_PACKAGE_NAME) == true) {
            currentPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        }
        if (intent?.hasExtra(EXTRA_SENDER_NAME) == true) {
            currentSenderName = intent.getStringExtra(EXTRA_SENDER_NAME)
        }

        startAsForeground()
        showOverlay()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LED Notification Dot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the notification LED dot overlay"
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.led_circle)
            .setContentTitle("AMOLED LED Notify")
            .setContentText("LED dot active")
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

        // Color loading priority:
        // 1. Check contact_colors prefs using senderName extra (e.g. WhatsApp contact)
        // 2. Check app_colors prefs using packageName extra
        // 3. Use default_color from app_colors
        // 4. Fallback to global color from led_prefs
        val ledPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences(AppColorManager.PREFS_NAME, Context.MODE_PRIVATE)

        val globalColor = ledPrefs.getInt(KEY_COLOR, 0xFF00FF00.toInt())
        val defaultColor = appPrefs.getInt(AppColorManager.KEY_DEFAULT_COLOR, globalColor)

        val contactColor = if (!currentSenderName.isNullOrBlank()) {
            ContactColorManager.getColorForContact(this, currentSenderName)
        } else {
            null
        }

        val resolvedColor = contactColor
            ?: (if (currentPackageName != null && appPrefs.contains(currentPackageName)) {
                appPrefs.getInt(currentPackageName, defaultColor)
            } else if (appPrefs.contains(AppColorManager.KEY_DEFAULT_COLOR)) {
                defaultColor
            } else {
                globalColor
            })

        val sizeDp = ledPrefs.getInt(KEY_SIZE, 22).coerceIn(10, 100)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(10)

        // If overlay already exists, just update dot color and size dynamically
        if (overlayView != null && ledDot != null) {
            val dotLayoutParams = ledDot?.layoutParams
            if (dotLayoutParams != null) {
                dotLayoutParams.width = sizePx
                dotLayoutParams.height = sizePx
                ledDot?.layoutParams = dotLayoutParams
            }
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(resolvedColor)
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

        // WRAP_CONTENT x WRAP_CONTENT, NO BLACK BACKGROUND, Screen remains fully usable underneath
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val savedPosX = ledPrefs.getFloat(KEY_POS_X, -1f)
        val savedPosY = ledPrefs.getFloat(KEY_POS_Y, -1f)

        if (savedPosX >= 0f && savedPosY >= 0f) {
            params.x = savedPosX.toInt()
            params.y = savedPosY.toInt()
        } else {
            val screenW = resources.displayMetrics.widthPixels
            val margin = (16 * density).toInt()
            params.x = screenW - sizePx - margin
            params.y = (24 * density).toInt()
        }
        layoutParams = params

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_led, null)
        val dot = view.findViewById<View>(R.id.ledDot)

        // Apply size
        val dotLayoutParams = dot.layoutParams
        dotLayoutParams.width = sizePx
        dotLayoutParams.height = sizePx
        dot.layoutParams = dotLayoutParams

        // Apply color
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(resolvedColor)
        }
        dot.background = drawable

        // Blink with ObjectAnimator alpha 1 to 0, 600ms, INFINITE REVERSE
        animator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0f).apply {
            duration = 600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        // Draggable dot: updates x, y in layoutParams and saves on ACTION_UP
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dot.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    animator?.pause()
                    dot.alpha = 1f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    lp.x = (initialX + deltaX).coerceAtLeast(0)
                    lp.y = (initialY + deltaY).coerceAtLeast(0)
                    try {
                        windowManager?.updateViewLayout(overlayView, lp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (animator?.isPaused == true) {
                        animator?.resume()
                    }
                    ledPrefs.edit()
                        .putFloat(KEY_POS_X, lp.x.toFloat())
                        .putFloat(KEY_POS_Y, lp.y.toFloat())
                        .apply()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (animator?.isPaused == true) {
                        animator?.resume()
                    }
                    true
                }
                else -> false
            }
        }

        ledDot = dot
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
        ledDot = null
        layoutParams = null
    }

    override fun onDestroy() {
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

        private const val CHANNEL_ID = "led_overlay_channel"
        private const val NOTIFICATION_ID = 1001

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
