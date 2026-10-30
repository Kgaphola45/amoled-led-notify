package com.example.lednotify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var btnOverlayPermission: Button
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnNotificationAccess: Button
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvSizeLabel: TextView
    private lateinit var seekBarSize: SeekBar
    private lateinit var previewLedDot: View
    private lateinit var layoutColors: LinearLayout
    private lateinit var btnTestLed: Button
    private lateinit var btnStopLed: Button

    private val colorOptions = listOf(
        0xFF00FF00.toInt(), // Green
        0xFFFF0000.toInt(), // Red
        0xFF0000FF.toInt(), // Blue
        0xFFFFFF00.toInt(), // Yellow
        0xFFFF00FF.toInt(), // Magenta
        0xFFFFFFFF.toInt(), // White
        0xFFFFA500.toInt()  // Orange
    )

    private val colorViews = mutableListOf<View>()

    private val postNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupPermissions()
        setupSizeControl()
        setupColorPicker()
        setupActionButtons()

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun bindViews() {
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnNotificationAccess = findViewById(R.id.btnNotificationAccess)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvSizeLabel = findViewById(R.id.tvSizeLabel)
        seekBarSize = findViewById(R.id.seekBarSize)
        previewLedDot = findViewById(R.id.previewLedDot)
        layoutColors = findViewById(R.id.layoutColors)
        btnTestLed = findViewById(R.id.btnTestLed)
        btnStopLed = findViewById(R.id.btnStopLed)
    }

    private fun setupPermissions() {
        btnOverlayPermission.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is already granted", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnNotificationAccess.setOnClickListener {
            if (isNotificationListenerEnabled()) {
                Toast.makeText(this, "Notification access is already granted", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
        }

        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (hasOverlay) {
            "Overlay Permission: GRANTED"
        } else {
            "Overlay Permission: MISSING (Required to show LED dot)"
        }
        tvOverlayStatus.setTextColor(if (hasOverlay) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))

        val hasListener = isNotificationListenerEnabled()
        tvNotificationStatus.text = if (hasListener) {
            "Notification Access: GRANTED"
        } else {
            "Notification Access: MISSING (Required to detect notifications)"
        }
        tvNotificationStatus.setTextColor(if (hasListener) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledPackages.contains(packageName)
    }

    private fun setupSizeControl() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedSize = prefs.getInt(LedOverlayService.KEY_SIZE, 22).coerceIn(2, 100)

        tvSizeLabel.text = "LED Dot Size: ${savedSize}dp"
        seekBarSize.max = 98 // range 0 to 98 corresponds to 2 to 100 dp
        seekBarSize.progress = (savedSize - 2).coerceAtLeast(0)

        updatePreviewDot(savedSize, prefs.getInt(LedOverlayService.KEY_COLOR, 0xFF00FF00.toInt()))

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentSize = (progress + 2).coerceIn(2, 100)
                tvSizeLabel.text = "LED Dot Size: ${currentSize}dp"
                prefs.edit().putInt(LedOverlayService.KEY_SIZE, currentSize).apply()
                val currentColor = prefs.getInt(LedOverlayService.KEY_COLOR, 0xFF00FF00.toInt())
                updatePreviewDot(currentSize, currentColor)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupColorPicker() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        var currentColor = prefs.getInt(LedOverlayService.KEY_COLOR, 0xFF00FF00.toInt())

        layoutColors.removeAllViews()
        colorViews.clear()

        val density = resources.displayMetrics.density
        val circleSizePx = (38 * density).toInt()
        val marginPx = (6 * density).toInt()

        for (color in colorOptions) {
            val colorView = View(this).apply {
                val params = LinearLayout.LayoutParams(circleSizePx, circleSizePx).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params
                background = createColorCircleDrawable(color, color == currentColor)
                setOnClickListener {
                    currentColor = color
                    prefs.edit().putInt(LedOverlayService.KEY_COLOR, color).apply()
                    refreshColorSelection(currentColor)
                    val currentSize = prefs.getInt(LedOverlayService.KEY_SIZE, 22)
                    updatePreviewDot(currentSize, color)
                }
            }
            colorViews.add(colorView)
            layoutColors.addView(colorView)
        }
    }

    private fun refreshColorSelection(selectedColor: Int) {
        for (i in colorOptions.indices) {
            val color = colorOptions[i]
            val view = colorViews[i]
            view.background = createColorCircleDrawable(color, color == selectedColor)
        }
    }

    private fun createColorCircleDrawable(color: Int, isSelected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (isSelected) {
                setStroke((3 * density).toInt(), Color.WHITE)
            } else {
                setStroke((1 * density).toInt(), Color.parseColor("#444444"))
            }
        }
    }

    private fun updatePreviewDot(sizeDp: Int, color: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val params = previewLedDot.layoutParams
        params.width = sizePx
        params.height = sizePx
        previewLedDot.layoutParams = params

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        previewLedDot.background = drawable
    }

    private fun setupActionButtons() {
        btnTestLed.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please allow Overlay Permission first", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return@setOnClickListener
            }
            requestNotificationPermissionIfNeeded()
            LedOverlayService.start(this)
            Toast.makeText(this, "LED overlay started! Tap black screen to dismiss.", Toast.LENGTH_SHORT).show()
        }

        btnStopLed.setOnClickListener {
            LedOverlayService.stop(this)
            Toast.makeText(this, "LED overlay stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
