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
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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
    private lateinit var btnSetColorsPerApp: Button
    private lateinit var btnWhatsAppContactColors: Button
    private lateinit var framePreviewContainer: FrameLayout
    private lateinit var previewLedDot: View
    private lateinit var tvSizeLabel: TextView
    private lateinit var seekBarSize: SeekBar
    private lateinit var layoutColors: LinearLayout
    private lateinit var btnCornerTopLeft: Button
    private lateinit var btnCornerTopRight: Button
    private lateinit var btnCornerBottomLeft: Button
    private lateinit var btnCornerBottomRight: Button
    private lateinit var tvPosXLabel: TextView
    private lateinit var seekBarPosX: SeekBar
    private lateinit var tvPosYLabel: TextView
    private lateinit var seekBarPosY: SeekBar
    private lateinit var btnPositionScreen: Button
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

        AppColorManager.initializeDefaultsIfNeeded(this)
        ContactColorManager.initializeDefaultsIfNeeded(this)

        bindViews()
        setupPermissions()
        setupPerAppColorsButton()
        setupWhatsAppContactColorsButton()
        setupSizeControl()
        setupColorPicker()
        setupPositionControls()
        setupActionButtons()

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val size = prefs.getInt(LedOverlayService.KEY_SIZE, 22)
        val color = AppColorManager.getDefaultColor(this)
        updatePreviewDot(size, color)
        syncPositionSliders()
    }

    private fun bindViews() {
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnNotificationAccess = findViewById(R.id.btnNotificationAccess)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        btnSetColorsPerApp = findViewById(R.id.btnSetColorsPerApp)
        btnWhatsAppContactColors = findViewById(R.id.btnWhatsAppContactColors)
        framePreviewContainer = findViewById(R.id.framePreviewContainer)
        previewLedDot = findViewById(R.id.previewLedDot)
        tvSizeLabel = findViewById(R.id.tvSizeLabel)
        seekBarSize = findViewById(R.id.seekBarSize)
        layoutColors = findViewById(R.id.layoutColors)
        btnCornerTopLeft = findViewById(R.id.btnCornerTopLeft)
        btnCornerTopRight = findViewById(R.id.btnCornerTopRight)
        btnCornerBottomLeft = findViewById(R.id.btnCornerBottomLeft)
        btnCornerBottomRight = findViewById(R.id.btnCornerBottomRight)
        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        seekBarPosX = findViewById(R.id.seekBarPosX)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        seekBarPosY = findViewById(R.id.seekBarPosY)
        btnPositionScreen = findViewById(R.id.btnPositionScreen)
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

    private fun setupPerAppColorsButton() {
        btnSetColorsPerApp.setOnClickListener {
            val intent = Intent(this, AppColorSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupWhatsAppContactColorsButton() {
        btnWhatsAppContactColors.setOnClickListener {
            val intent = Intent(this, ContactColorActivity::class.java)
            startActivity(intent)
        }
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
        val savedSize = prefs.getInt(LedOverlayService.KEY_SIZE, 22).coerceIn(10, 100)

        tvSizeLabel.text = "LED Dot Size: ${savedSize}dp"
        seekBarSize.max = 90 // range 0 to 90 corresponds to 10 to 100 dp
        seekBarSize.progress = (savedSize - 10).coerceAtLeast(0)

        updatePreviewDot(savedSize, AppColorManager.getDefaultColor(this))

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentSize = (progress + 10).coerceIn(10, 100)
                tvSizeLabel.text = "LED Dot Size: ${currentSize}dp"
                prefs.edit().putInt(LedOverlayService.KEY_SIZE, currentSize).apply()
                val currentColor = AppColorManager.getDefaultColor(this@MainActivity)
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
                    AppColorManager.setDefaultColor(this@MainActivity, color)
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

    private fun setupPositionControls() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density

        fun setCorner(name: String, cornerGravity: Int) {
            val screenW = resources.displayMetrics.widthPixels.toFloat()
            val screenH = resources.displayMetrics.heightPixels.toFloat()
            val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 22)
            val sizePx = sizeDp * density
            val margin = 8f * density

            val posX = when (cornerGravity) {
                Gravity.TOP or Gravity.START, Gravity.BOTTOM or Gravity.START -> margin
                else -> (screenW - sizePx - margin).coerceAtLeast(0f)
            }
            val posY = when (cornerGravity) {
                Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END -> margin
                else -> (screenH - sizePx - margin).coerceAtLeast(0f)
            }

            prefs.edit()
                .putFloat(LedOverlayService.KEY_POS_X, posX)
                .putFloat(LedOverlayService.KEY_POS_Y, posY)
                .apply()

            val currentColor = AppColorManager.getDefaultColor(this)
            updatePreviewDot(sizeDp, currentColor)
            syncPositionSliders()
            Toast.makeText(this, "Position set to $name", Toast.LENGTH_SHORT).show()
        }

        btnCornerTopLeft.setOnClickListener {
            setCorner("Top-Left", Gravity.TOP or Gravity.START)
        }

        btnCornerTopRight.setOnClickListener {
            setCorner("Top-Right", Gravity.TOP or Gravity.END)
        }

        btnCornerBottomLeft.setOnClickListener {
            setCorner("Bottom-Left", Gravity.BOTTOM or Gravity.START)
        }

        btnCornerBottomRight.setOnClickListener {
            setCorner("Bottom-Right", Gravity.BOTTOM or Gravity.END)
        }

        // Horizontal Slider (X)
        seekBarPosX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 22)
                    val sizePx = sizeDp * density
                    val newX = (progress / 100f) * (screenW - sizePx).coerceAtLeast(0f)

                    prefs.edit().putFloat(LedOverlayService.KEY_POS_X, newX).apply()
                    tvPosXLabel.text = "Horizontal Position (X): $progress%"
                    updatePreviewDot(sizeDp, AppColorManager.getDefaultColor(this@MainActivity))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Vertical Slider (Y)
        seekBarPosY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val screenH = resources.displayMetrics.heightPixels.toFloat()
                    val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 22)
                    val sizePx = sizeDp * density
                    val newY = (progress / 100f) * (screenH - sizePx).coerceAtLeast(0f)

                    prefs.edit().putFloat(LedOverlayService.KEY_POS_Y, newY).apply()
                    tvPosYLabel.text = "Vertical Position (Y): $progress%"
                    updatePreviewDot(sizeDp, AppColorManager.getDefaultColor(this@MainActivity))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnPositionScreen.setOnClickListener {
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
            LedOverlayService.startPositioning(this)
            Toast.makeText(this, "LED dot is active on screen! Touch and drag it anywhere you want.", Toast.LENGTH_SHORT).show()
        }

        syncPositionSliders()
    }

    private fun syncPositionSliders() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPosX = prefs.getFloat(LedOverlayService.KEY_POS_X, -1f)
        val savedPosY = prefs.getFloat(LedOverlayService.KEY_POS_Y, -1f)
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()

        if (screenW > 0f && screenH > 0f) {
            val percentX = if (savedPosX >= 0f) ((savedPosX / screenW) * 100).toInt().coerceIn(0, 100) else 90
            val percentY = if (savedPosY >= 0f) ((savedPosY / screenH) * 100).toInt().coerceIn(0, 100) else 5

            seekBarPosX.progress = percentX
            tvPosXLabel.text = "Horizontal Position (X): $percentX%"

            seekBarPosY.progress = percentY
            tvPosYLabel.text = "Vertical Position (Y): $percentY%"
        }
    }

    private fun updatePreviewDot(sizeDp: Int, color: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
        val params = previewLedDot.layoutParams as FrameLayout.LayoutParams
        params.width = sizePx
        params.height = sizePx

        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPosX = prefs.getFloat(LedOverlayService.KEY_POS_X, -1f)
        val savedPosY = prefs.getFloat(LedOverlayService.KEY_POS_Y, -1f)

        framePreviewContainer.post {
            val previewW = framePreviewContainer.width.toFloat()
            val previewH = framePreviewContainer.height.toFloat()
            val screenW = resources.displayMetrics.widthPixels.toFloat()
            val screenH = resources.displayMetrics.heightPixels.toFloat()

            if (savedPosX >= 0f && savedPosY >= 0f && screenW > 0f && screenH > 0f) {
                val ratioX = (savedPosX / screenW).coerceIn(0f, 1f)
                val ratioY = (savedPosY / screenH).coerceIn(0f, 1f)
                params.gravity = Gravity.NO_GRAVITY
                previewLedDot.x = ratioX * (previewW - sizePx)
                previewLedDot.y = ratioY * (previewH - sizePx)
            } else {
                params.gravity = Gravity.TOP or Gravity.END
                params.setMargins(0, (12 * density).toInt(), (12 * density).toInt(), 0)
            }
            previewLedDot.layoutParams = params
        }

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
            Toast.makeText(this, "LED overlay started! You can drag the dot anywhere on screen.", Toast.LENGTH_SHORT).show()
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
