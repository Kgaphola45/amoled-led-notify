package com.example.lednotify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
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
    private lateinit var switchHideWhileUnlocked: android.widget.Switch
    private lateinit var switchCycleColors: android.widget.Switch
    private lateinit var switchAutoDismiss: android.widget.Switch
    private lateinit var switchAodMode: android.widget.Switch
    private lateinit var btnPreviewAod: Button
    private lateinit var switchBurnInProtection: android.widget.Switch
    private lateinit var btnTimeoutOption: Button
    private lateinit var btnBatteryCutoffOption: Button
    private lateinit var switchPocketMode: android.widget.Switch
    private lateinit var switchDnd: android.widget.Switch
    private lateinit var layoutDndTimes: LinearLayout
    private lateinit var btnDndStartTime: Button
    private lateinit var btnDndEndTime: Button
    private lateinit var btnTestLed: Button
    private lateinit var btnStopLed: Button

    // Visual styles & animation pattern views
    private lateinit var btnCustomHexPicker: Button
    private lateinit var btnStyleDot: Button
    private lateinit var btnStyleRing: Button
    private lateinit var tvStyleDescription: TextView
    private lateinit var btnAutoAlignCamera: Button
    private lateinit var btnAnimBlink: Button
    private lateinit var btnAnimPulse: Button
    private lateinit var btnAnimHeartbeat: Button
    private lateinit var btnAnimSolid: Button
    private lateinit var tvPatternDescription: TextView

    // System & Hardware Trigger views
    private lateinit var switchBatteryIndicator: android.widget.Switch
    private lateinit var switchLowBatteryWarning: android.widget.Switch
    private lateinit var btnMissedCallColor: Button
    private lateinit var btnSmsColor: Button

    // Convenience & UX views
    private lateinit var switchMasterEnable: android.widget.Switch
    private lateinit var btnSimulateNotification: Button
    private lateinit var btnExportConfig: Button
    private lateinit var btnImportConfig: Button

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
        setupVisualStyles()
        setupAnimationPatterns()
        setupPositionControls()
        setupLifecycleSwitches()
        setupHealthAndBatteryControls()
        setupSystemAndHardwareTriggers()
        setupConvenienceAndUx()
        setupActionButtons()

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val size = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
        val color = AppColorManager.getDefaultColor(this)
        updatePreviewDot(size, color)
        syncPositionSliders()
        switchMasterEnable.isChecked = LedTileService.isMasterEnabled(this)

        // Sync sliders in real time when dot is dragged on screen
        LedOverlayService.onPositionChangedListener = { x, y ->
            runOnUiThread {
                syncPositionSlidersFromCoords(x, y)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        LedOverlayService.onPositionChangedListener = null
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
        switchHideWhileUnlocked = findViewById(R.id.switchHideWhileUnlocked)
        switchCycleColors = findViewById(R.id.switchCycleColors)
        switchAutoDismiss = findViewById(R.id.switchAutoDismiss)
        switchAodMode = findViewById(R.id.switchAodMode)
        btnPreviewAod = findViewById(R.id.btnPreviewAod)
        switchBurnInProtection = findViewById(R.id.switchBurnInProtection)
        btnTimeoutOption = findViewById(R.id.btnTimeoutOption)
        btnBatteryCutoffOption = findViewById(R.id.btnBatteryCutoffOption)
        switchPocketMode = findViewById(R.id.switchPocketMode)
        switchDnd = findViewById(R.id.switchDnd)
        layoutDndTimes = findViewById(R.id.layoutDndTimes)
        btnDndStartTime = findViewById(R.id.btnDndStartTime)
        btnDndEndTime = findViewById(R.id.btnDndEndTime)
        btnTestLed = findViewById(R.id.btnTestLed)
        btnStopLed = findViewById(R.id.btnStopLed)

        btnCustomHexPicker = findViewById(R.id.btnCustomHexPicker)
        btnStyleDot = findViewById(R.id.btnStyleDot)
        btnStyleRing = findViewById(R.id.btnStyleRing)
        tvStyleDescription = findViewById(R.id.tvStyleDescription)
        btnAutoAlignCamera = findViewById(R.id.btnAutoAlignCamera)
        btnAnimBlink = findViewById(R.id.btnAnimBlink)
        btnAnimPulse = findViewById(R.id.btnAnimPulse)
        btnAnimHeartbeat = findViewById(R.id.btnAnimHeartbeat)
        btnAnimSolid = findViewById(R.id.btnAnimSolid)
        tvPatternDescription = findViewById(R.id.tvPatternDescription)

        switchBatteryIndicator = findViewById(R.id.switchBatteryIndicator)
        switchLowBatteryWarning = findViewById(R.id.switchLowBatteryWarning)
        btnMissedCallColor = findViewById(R.id.btnMissedCallColor)
        btnSmsColor = findViewById(R.id.btnSmsColor)

        switchMasterEnable = findViewById(R.id.switchMasterEnable)
        btnSimulateNotification = findViewById(R.id.btnSimulateNotification)
        btnExportConfig = findViewById(R.id.btnExportConfig)
        btnImportConfig = findViewById(R.id.btnImportConfig)
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
        val savedSize = prefs.getInt(LedOverlayService.KEY_SIZE, 2).coerceIn(2, 100)

        tvSizeLabel.text = "LED Dot Size: ${savedSize}dp"
        seekBarSize.max = 98 // range 0 to 98 corresponds to 2 to 100 dp
        seekBarSize.progress = (savedSize - 2).coerceAtLeast(0)

        updatePreviewDot(savedSize, AppColorManager.getDefaultColor(this))

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentSize = (progress + 2).coerceIn(2, 100)
                tvSizeLabel.text = "LED Dot Size: ${currentSize}dp"
                prefs.edit().putInt(LedOverlayService.KEY_SIZE, currentSize).apply()
                val currentColor = AppColorManager.getDefaultColor(this@MainActivity)
                updatePreviewDot(currentSize, currentColor)
                LedOverlayService.updateLiveSize(currentSize)
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
                    val currentSize = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
                    updatePreviewDot(currentSize, color)
                    LedOverlayService.updateLiveColor(color)
                }
            }
            colorViews.add(colorView)
            layoutColors.addView(colorView)
        }

        btnCustomHexPicker.setOnClickListener {
            showCustomHexColorPicker()
        }
    }

    private fun showCustomHexColorPicker() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        var currentColor = prefs.getInt(LedOverlayService.KEY_COLOR, 0xFF00FF00.toInt())

        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)
        val viewColorPreview = dialogView.findViewById<View>(R.id.viewColorPreview)
        val etHexCode = dialogView.findViewById<android.widget.EditText>(R.id.etHexCode)
        val tvRedLabel = dialogView.findViewById<TextView>(R.id.tvRedLabel)
        val seekBarRed = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val tvGreenLabel = dialogView.findViewById<TextView>(R.id.tvGreenLabel)
        val seekBarGreen = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val tvBlueLabel = dialogView.findViewById<TextView>(R.id.tvBlueLabel)
        val seekBarBlue = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)
        val layoutNeonPresets = dialogView.findViewById<LinearLayout>(R.id.layoutNeonPresets)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelCustomColor)
        val btnApply = dialogView.findViewById<Button>(R.id.btnApplyCustomColor)

        val neonPresets = listOf(
            Pair("Neon Lime", 0xFF00FF00.toInt()),
            Pair("Neon Cyan", 0xFF00E5FF.toInt()),
            Pair("Electric Blue", 0xFF0066FF.toInt()),
            Pair("Hot Pink", 0xFFFF007F.toInt()),
            Pair("Ultra Red", 0xFFFF0000.toInt()),
            Pair("Amber Glow", 0xFFFF9100.toInt()),
            Pair("Neon Purple", 0xFFD500F9.toInt()),
            Pair("Pure White", 0xFFFFFFFF.toInt())
        )

        var isUpdatingInternally = false

        fun formatHex(color: Int): String {
            return String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color)
        }

        fun updatePreviewAndText(color: Int) {
            currentColor = color
            viewColorPreview.setBackgroundColor(color)
            tvRedLabel.text = "Red: ${Color.red(color)}"
            tvGreenLabel.text = "Green: ${Color.green(color)}"
            tvBlueLabel.text = "Blue: ${Color.blue(color)}"
        }

        // Initialize state
        var r = Color.red(currentColor)
        var g = Color.green(currentColor)
        var b = Color.blue(currentColor)
        seekBarRed.progress = r
        seekBarGreen.progress = g
        seekBarBlue.progress = b
        etHexCode.setText(formatHex(currentColor))
        updatePreviewAndText(currentColor)

        val density = resources.displayMetrics.density
        val swatchSizePx = (34 * density).toInt()
        val swatchMarginPx = (4 * density).toInt()

        for ((_, presetColor) in neonPresets) {
            val swatch = View(this).apply {
                val lp = LinearLayout.LayoutParams(swatchSizePx, swatchSizePx).apply {
                    setMargins(swatchMarginPx, swatchMarginPx, swatchMarginPx, swatchMarginPx)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(presetColor)
                    setStroke((1 * density).toInt(), Color.parseColor("#444444"))
                }
                setOnClickListener {
                    isUpdatingInternally = true
                    r = Color.red(presetColor)
                    g = Color.green(presetColor)
                    b = Color.blue(presetColor)
                    seekBarRed.progress = r
                    seekBarGreen.progress = g
                    seekBarBlue.progress = b
                    etHexCode.setText(formatHex(presetColor))
                    updatePreviewAndText(presetColor)
                    isUpdatingInternally = false
                }
            }
            layoutNeonPresets.addView(swatch)
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUpdatingInternally) return
                r = seekBarRed.progress
                g = seekBarGreen.progress
                b = seekBarBlue.progress
                val color = Color.rgb(r, g, b)
                isUpdatingInternally = true
                etHexCode.setText(formatHex(color))
                updatePreviewAndText(color)
                isUpdatingInternally = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekBarRed.setOnSeekBarChangeListener(seekListener)
        seekBarGreen.setOnSeekBarChangeListener(seekListener)
        seekBarBlue.setOnSeekBarChangeListener(seekListener)

        etHexCode.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdatingInternally) return
                val text = s?.toString()?.trim() ?: ""
                try {
                    val parsed = Color.parseColor(if (text.startsWith("#")) text else "#$text")
                    isUpdatingInternally = true
                    r = Color.red(parsed)
                    g = Color.green(parsed)
                    b = Color.blue(parsed)
                    seekBarRed.progress = r
                    seekBarGreen.progress = g
                    seekBarBlue.progress = b
                    updatePreviewAndText(parsed)
                    isUpdatingInternally = false
                } catch (e: Exception) {
                    // Ignore while typing incomplete hex
                }
            }
        })

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            prefs.edit().putInt(LedOverlayService.KEY_COLOR, currentColor).apply()
            AppColorManager.setDefaultColor(this, currentColor)
            refreshColorSelection(currentColor)
            val currentSize = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
            updatePreviewDot(currentSize, currentColor)
            LedOverlayService.updateLiveColor(currentColor)
            Toast.makeText(this, "Color applied: ${formatHex(currentColor)}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupVisualStyles() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        fun updateStyleUi(style: String) {
            val isDot = style == LedOverlayService.STYLE_DOT
            btnStyleDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (isDot) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )
            btnStyleRing.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (!isDot) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )
            tvStyleDescription.text = if (isDot) {
                "Solid LED Dot: Displays a vibrant, solid circular indicator."
            } else {
                "Camera Notch Ring: Draws an illuminated hollow ring around your camera hole (transparent center for lens)."
            }
        }

        val currentStyle = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT) ?: LedOverlayService.STYLE_DOT
        updateStyleUi(currentStyle)

        btnStyleDot.setOnClickListener {
            prefs.edit().putString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT).apply()
            updateStyleUi(LedOverlayService.STYLE_DOT)
            val size = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
            val color = AppColorManager.getDefaultColor(this)
            updatePreviewDot(size, color)
            LedOverlayService.updateLiveStyle()
            Toast.makeText(this, "Visual Style: Solid LED Dot", Toast.LENGTH_SHORT).show()
        }

        btnStyleRing.setOnClickListener {
            prefs.edit().putString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_RING).apply()
            updateStyleUi(LedOverlayService.STYLE_RING)
            val size = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
            val color = AppColorManager.getDefaultColor(this)
            updatePreviewDot(size, color)
            LedOverlayService.updateLiveStyle()
            Toast.makeText(this, "Visual Style: Camera Notch Ring", Toast.LENGTH_SHORT).show()
        }

        btnAutoAlignCamera.setOnClickListener {
            autoAlignRingToCameraHole()
        }
    }

    private fun autoAlignRingToCameraHole() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val (screenW, screenH) = LedOverlayService.getRealScreenSize(this)

        var detectedCenterX: Float? = null
        var detectedCenterY: Float? = null
        var detectedDiameterPx: Int? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val insets = window.decorView.rootWindowInsets
            val cutout = insets?.displayCutout
            val rects = cutout?.boundingRects

            if (!rects.isNullOrEmpty()) {
                val cameraRect = rects.firstOrNull { it.top < screenH / 3 && it.height() > 0 && it.width() > 0 }
                    ?: rects.first()

                detectedCenterX = cameraRect.exactCenterX()
                detectedCenterY = cameraRect.exactCenterY()
                val baseSize = maxOf(cameraRect.width(), cameraRect.height())
                detectedDiameterPx = baseSize + (6 * density).toInt()
            }
        }

        val finalDiameterDp: Int
        val finalDiameterPx: Int
        val finalPosX: Float
        val finalPosY: Float

        if (detectedCenterX != null && detectedCenterY != null && detectedDiameterPx != null) {
            finalDiameterDp = (detectedDiameterPx / density).toInt().coerceIn(16, 80)
            finalDiameterPx = (finalDiameterDp * density).toInt()
            finalPosX = (detectedCenterX - finalDiameterPx / 2f).coerceIn(0f, (screenW - finalDiameterPx).toFloat())
            finalPosY = (detectedCenterY - finalDiameterPx / 2f).coerceIn(0f, (screenH - finalDiameterPx).toFloat())

            Toast.makeText(
                this,
                "Camera punch-hole detected! Aligned ${finalDiameterDp}dp ring around camera.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            finalDiameterDp = 30
            finalDiameterPx = (finalDiameterDp * density).toInt()
            finalPosX = ((screenW - finalDiameterPx) / 2f).coerceAtLeast(0f)
            finalPosY = 6 * density

            Toast.makeText(
                this,
                "Punch-hole not reported by OS. Positioned at top-center. You can drag to align with your camera!",
                Toast.LENGTH_LONG
            ).show()
        }

        prefs.edit()
            .putString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_RING)
            .putInt(LedOverlayService.KEY_SIZE, finalDiameterDp)
            .putFloat(LedOverlayService.KEY_POS_X, finalPosX)
            .putFloat(LedOverlayService.KEY_POS_Y, finalPosY)
            .apply()

        btnStyleDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
        btnStyleRing.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#5E35B1"))
        tvStyleDescription.text = "Camera Notch Ring: Draws an illuminated hollow ring around your camera hole (transparent center for lens)."

        tvSizeLabel.text = "LED Dot Size: ${finalDiameterDp}dp"
        seekBarSize.progress = (finalDiameterDp - 2).coerceAtLeast(0)
        syncPositionSliders()

        val color = AppColorManager.getDefaultColor(this)
        updatePreviewDot(finalDiameterDp, color)

        if (Settings.canDrawOverlays(this)) {
            if (LedOverlayService.isRunning()) {
                LedOverlayService.updateLiveSize(finalDiameterDp)
                LedOverlayService.updateLivePosition(finalPosX, finalPosY)
                LedOverlayService.updateLiveStyle()
            } else {
                LedOverlayService.startPositioning(this)
            }
        }
    }

    private fun setupAnimationPatterns() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        fun updatePatternUi(pattern: String) {
            btnAnimBlink.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (pattern == LedOverlayService.PATTERN_BLINK) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )
            btnAnimPulse.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (pattern == LedOverlayService.PATTERN_PULSE) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )
            btnAnimHeartbeat.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (pattern == LedOverlayService.PATTERN_HEARTBEAT) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )
            btnAnimSolid.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (pattern == LedOverlayService.PATTERN_SOLID) Color.parseColor("#5E35B1") else Color.parseColor("#333333")
            )

            tvPatternDescription.text = when (pattern) {
                LedOverlayService.PATTERN_PULSE -> "Pattern: Breathing / Pulse (Smooth sinusoidal glow)"
                LedOverlayService.PATTERN_HEARTBEAT -> "Pattern: Heartbeat (Double-blink: thump-thump ... pause)"
                LedOverlayService.PATTERN_SOLID -> "Pattern: Solid Glow (Constant subtle illumination, no blink)"
                else -> "Pattern: Blink (Standard on-off flashing)"
            }
        }

        val currentPattern = prefs.getString(LedOverlayService.KEY_ANIMATION_PATTERN, LedOverlayService.PATTERN_BLINK) ?: LedOverlayService.PATTERN_BLINK
        updatePatternUi(currentPattern)

        val patternButtons = listOf(
            Pair(btnAnimBlink, LedOverlayService.PATTERN_BLINK),
            Pair(btnAnimPulse, LedOverlayService.PATTERN_PULSE),
            Pair(btnAnimHeartbeat, LedOverlayService.PATTERN_HEARTBEAT),
            Pair(btnAnimSolid, LedOverlayService.PATTERN_SOLID)
        )

        for ((button, patternKey) in patternButtons) {
            button.setOnClickListener {
                prefs.edit().putString(LedOverlayService.KEY_ANIMATION_PATTERN, patternKey).apply()
                updatePatternUi(patternKey)
                LedOverlayService.updateLiveAnimationPattern()
                Toast.makeText(this, "Pattern: ${button.text}", Toast.LENGTH_SHORT).show()
            }
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
            val (screenW, screenH) = LedOverlayService.getRealScreenSize(this).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }
            val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
            val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)

            val posX = when (cornerGravity) {
                Gravity.TOP or Gravity.START, Gravity.BOTTOM or Gravity.START -> 0f
                else -> (screenW - sizePx).coerceAtLeast(0f)
            }
            val posY = when (cornerGravity) {
                Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END -> 0f
                else -> (screenH - sizePx).coerceAtLeast(0f)
            }

            prefs.edit()
                .putFloat(LedOverlayService.KEY_POS_X, posX)
                .putFloat(LedOverlayService.KEY_POS_Y, posY)
                .apply()

            val currentColor = AppColorManager.getDefaultColor(this)
            updatePreviewDot(sizeDp, currentColor)
            syncPositionSliders()
            LedOverlayService.updateLivePosition(posX, posY)
            Toast.makeText(this, "Position set to $name edge", Toast.LENGTH_SHORT).show()
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
                    val (screenW, _) = LedOverlayService.getRealScreenSize(this@MainActivity).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }
                    val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
                    val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
                    val maxX = (screenW - sizePx).coerceAtLeast(0f)
                    val newX = (progress / 100f) * maxX

                    prefs.edit().putFloat(LedOverlayService.KEY_POS_X, newX).apply()
                    tvPosXLabel.text = "Horizontal Position (X): $progress%"
                    updatePreviewDot(sizeDp, AppColorManager.getDefaultColor(this@MainActivity))

                    val currentY = prefs.getFloat(LedOverlayService.KEY_POS_Y, 0f)
                    LedOverlayService.updateLivePosition(newX, currentY)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Vertical Slider (Y)
        seekBarPosY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val (_, screenH) = LedOverlayService.getRealScreenSize(this@MainActivity).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }
                    val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
                    val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)
                    val maxY = (screenH - sizePx).coerceAtLeast(0f)
                    val newY = (progress / 100f) * maxY

                    prefs.edit().putFloat(LedOverlayService.KEY_POS_Y, newY).apply()
                    tvPosYLabel.text = "Vertical Position (Y): $progress%"
                    updatePreviewDot(sizeDp, AppColorManager.getDefaultColor(this@MainActivity))

                    val currentX = prefs.getFloat(LedOverlayService.KEY_POS_X, 0f)
                    LedOverlayService.updateLivePosition(currentX, newY)
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
        val (screenW, screenH) = LedOverlayService.getRealScreenSize(this).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }
        val density = resources.displayMetrics.density
        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)

        val maxX = (screenW - sizePx).coerceAtLeast(1f)
        val maxY = (screenH - sizePx).coerceAtLeast(1f)

        val percentX = if (savedPosX >= 0f) ((savedPosX / maxX) * 100).toInt().coerceIn(0, 100) else 100
        val percentY = if (savedPosY >= 0f) ((savedPosY / maxY) * 100).toInt().coerceIn(0, 100) else 0

        seekBarPosX.progress = percentX
        tvPosXLabel.text = "Horizontal Position (X): $percentX%"

        seekBarPosY.progress = percentY
        tvPosYLabel.text = "Vertical Position (Y): $percentY%"
    }

    private fun syncPositionSlidersFromCoords(posX: Float, posY: Float) {
        val (screenW, screenH) = LedOverlayService.getRealScreenSize(this).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(2)

        val maxX = (screenW - sizePx).coerceAtLeast(1f)
        val maxY = (screenH - sizePx).coerceAtLeast(1f)

        val percentX = ((posX / maxX) * 100).toInt().coerceIn(0, 100)
        val percentY = ((posY / maxY) * 100).toInt().coerceIn(0, 100)

        seekBarPosX.progress = percentX
        tvPosXLabel.text = "Horizontal Position (X): $percentX%"

        seekBarPosY.progress = percentY
        tvPosYLabel.text = "Vertical Position (Y): $percentY%"

        val color = AppColorManager.getDefaultColor(this)
        updatePreviewDot(sizeDp, color)
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
            val (screenW, screenH) = LedOverlayService.getRealScreenSize(this).let { (w, h) -> Pair(w.toFloat(), h.toFloat()) }

            if (savedPosX >= 0f && savedPosY >= 0f && screenW > 0f && screenH > 0f) {
                val maxDotX = (screenW - sizePx).coerceAtLeast(1f)
                val maxDotY = (screenH - sizePx).coerceAtLeast(1f)
                val ratioX = (savedPosX / maxDotX).coerceIn(0f, 1f)
                val ratioY = (savedPosY / maxDotY).coerceIn(0f, 1f)
                params.gravity = Gravity.NO_GRAVITY
                previewLedDot.x = ratioX * (previewW - sizePx)
                previewLedDot.y = ratioY * (previewH - sizePx)
            } else {
                params.gravity = Gravity.TOP or Gravity.END
                params.setMargins(0, 0, 0, 0)
            }
            previewLedDot.layoutParams = params
        }

        val style = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT) ?: LedOverlayService.STYLE_DOT
        val drawable = if (style == LedOverlayService.STYLE_RING) {
            val strokePx = (3 * density).toInt().coerceIn(2, 6)
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
        previewLedDot.background = drawable
    }

    private fun setupLifecycleSwitches() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        switchHideWhileUnlocked.isChecked = prefs.getBoolean(LedOverlayService.KEY_HIDE_WHILE_UNLOCKED, true)
        switchCycleColors.isChecked = prefs.getBoolean(LedOverlayService.KEY_CYCLE_COLORS, true)
        switchAutoDismiss.isChecked = prefs.getBoolean(LedOverlayService.KEY_AUTO_DISMISS, true)
        switchAodMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_AOD_MODE, true)

        switchHideWhileUnlocked.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_HIDE_WHILE_UNLOCKED, isChecked).apply()
        }

        switchCycleColors.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_CYCLE_COLORS, isChecked).apply()
        }

        switchAutoDismiss.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_AUTO_DISMISS, isChecked).apply()
        }

        switchAodMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_AOD_MODE, isChecked).apply()
        }

        btnPreviewAod.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please allow Overlay Permission first", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return@setOnClickListener
            }
            Toast.makeText(this, "Starting AOD Preview...\nDouble-tap anywhere on screen to exit!", Toast.LENGTH_LONG).show()
            btnPreviewAod.postDelayed({
                LedOverlayService.previewAod(this)
            }, 800)
        }
    }

    private fun setupHealthAndBatteryControls() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Burn-in Protection
        switchBurnInProtection.isChecked = prefs.getBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, true)
        switchBurnInProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, isChecked).apply()
        }

        // 2. Timeout Option Dialog
        fun updateTimeoutButtonText(minutes: Int) {
            btnTimeoutOption.text = if (minutes == 0) "Never" else if (minutes == 60) "1 hour" else "$minutes min"
        }
        val currentTimeout = prefs.getInt(LedOverlayService.KEY_TIMEOUT_MINUTES, 15)
        updateTimeoutButtonText(currentTimeout)

        btnTimeoutOption.setOnClickListener {
            val timeoutLabels = arrayOf("5 minutes", "15 minutes (Default)", "30 minutes", "1 hour", "Never (Always On)")
            val timeoutValues = intArrayOf(5, 15, 30, 60, 0)
            val selectedIndex = timeoutValues.indexOf(prefs.getInt(LedOverlayService.KEY_TIMEOUT_MINUTES, 15)).coerceAtLeast(0)

            android.app.AlertDialog.Builder(this)
                .setTitle("Auto-Off Timeout")
                .setSingleChoiceItems(timeoutLabels, selectedIndex) { dialog, which ->
                    val chosen = timeoutValues[which]
                    prefs.edit().putInt(LedOverlayService.KEY_TIMEOUT_MINUTES, chosen).apply()
                    updateTimeoutButtonText(chosen)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 3. Low Battery Cutoff Dialog
        fun updateBatteryButtonText(cutoff: Int) {
            btnBatteryCutoffOption.text = if (cutoff == 0) "Disabled" else "Below $cutoff%"
        }
        val currentCutoff = prefs.getInt(LedOverlayService.KEY_LOW_BATTERY_CUTOFF, 15)
        updateBatteryButtonText(currentCutoff)

        btnBatteryCutoffOption.setOnClickListener {
            val batteryLabels = arrayOf("Disabled", "Below 10%", "Below 15% (Default)", "Below 20%")
            val batteryValues = intArrayOf(0, 10, 15, 20)
            val selectedIndex = batteryValues.indexOf(prefs.getInt(LedOverlayService.KEY_LOW_BATTERY_CUTOFF, 15)).coerceAtLeast(0)

            android.app.AlertDialog.Builder(this)
                .setTitle("Low Battery Cutoff")
                .setSingleChoiceItems(batteryLabels, selectedIndex) { dialog, which ->
                    val chosen = batteryValues[which]
                    prefs.edit().putInt(LedOverlayService.KEY_LOW_BATTERY_CUTOFF, chosen).apply()
                    updateBatteryButtonText(chosen)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 4. Pocket Mode
        switchPocketMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_POCKET_MODE, true)
        switchPocketMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_POCKET_MODE, isChecked).apply()
        }

        // 5. Quiet Hours / DND Schedule
        val dndEnabled = prefs.getBoolean(LedOverlayService.KEY_DND_ENABLED, false)
        switchDnd.isChecked = dndEnabled
        layoutDndTimes.visibility = if (dndEnabled) View.VISIBLE else View.GONE

        switchDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_DND_ENABLED, isChecked).apply()
            layoutDndTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        fun formatTime12H(hour: Int, minute: Int): String {
            val amPm = if (hour < 12) "AM" else "PM"
            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            return String.format(java.util.Locale.US, "%02d:%02d %s", h12, minute, amPm)
        }

        fun updateDndTimeButtons() {
            val startH = prefs.getInt(LedOverlayService.KEY_DND_START_HOUR, 23)
            val startM = prefs.getInt(LedOverlayService.KEY_DND_START_MINUTE, 0)
            val endH = prefs.getInt(LedOverlayService.KEY_DND_END_HOUR, 7)
            val endM = prefs.getInt(LedOverlayService.KEY_DND_END_MINUTE, 0)
            btnDndStartTime.text = formatTime12H(startH, startM)
            btnDndEndTime.text = formatTime12H(endH, endM)
        }
        updateDndTimeButtons()

        btnDndStartTime.setOnClickListener {
            val startH = prefs.getInt(LedOverlayService.KEY_DND_START_HOUR, 23)
            val startM = prefs.getInt(LedOverlayService.KEY_DND_START_MINUTE, 0)
            android.app.TimePickerDialog(this, { _, hour, minute ->
                prefs.edit()
                    .putInt(LedOverlayService.KEY_DND_START_HOUR, hour)
                    .putInt(LedOverlayService.KEY_DND_START_MINUTE, minute)
                    .apply()
                updateDndTimeButtons()
            }, startH, startM, false).show()
        }

        btnDndEndTime.setOnClickListener {
            val endH = prefs.getInt(LedOverlayService.KEY_DND_END_HOUR, 7)
            val endM = prefs.getInt(LedOverlayService.KEY_DND_END_MINUTE, 0)
            android.app.TimePickerDialog(this, { _, hour, minute ->
                prefs.edit()
                    .putInt(LedOverlayService.KEY_DND_END_HOUR, hour)
                    .putInt(LedOverlayService.KEY_DND_END_MINUTE, minute)
                    .apply()
                updateDndTimeButtons()
            }, endH, endM, false).show()
        }
    }

    private fun setupSystemAndHardwareTriggers() {
        val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        switchBatteryIndicator.isChecked = prefs.getBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, true)
        switchBatteryIndicator.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, isChecked).apply()
        }

        switchLowBatteryWarning.isChecked = prefs.getBoolean(LedOverlayService.KEY_LOW_BATTERY_WARNING, true)
        switchLowBatteryWarning.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_LOW_BATTERY_WARNING, isChecked).apply()
        }

        val density = resources.displayMetrics.density

        fun updateColorButton(btn: Button, color: Int) {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            btn.background = drawable
        }

        val currentMissedColor = prefs.getInt(LedOverlayService.KEY_MISSED_CALL_COLOR, 0xFFFF1744.toInt())
        updateColorButton(btnMissedCallColor, currentMissedColor)

        btnMissedCallColor.setOnClickListener {
            val colorChoices = intArrayOf(
                0xFFFF1744.toInt(), // Electric Crimson
                0xFFFF0000.toInt(), // Vivid Red
                0xFF00E5FF.toInt(), // Cyan
                0xFFD500F9.toInt(), // Electric Purple
                0xFFFF9100.toInt(), // Amber
                0xFF00FF00.toInt()  // Lime
            )
            val names = arrayOf("Electric Crimson (Default)", "Vivid Red", "Cyan Blue", "Electric Purple", "Amber", "Green")
            val current = prefs.getInt(LedOverlayService.KEY_MISSED_CALL_COLOR, 0xFFFF1744.toInt())
            val selectedIdx = colorChoices.indexOf(current).coerceAtLeast(0)

            android.app.AlertDialog.Builder(this)
                .setTitle("Missed Call Alert Color")
                .setSingleChoiceItems(names, selectedIdx) { dialog, which ->
                    val chosen = colorChoices[which]
                    prefs.edit().putInt(LedOverlayService.KEY_MISSED_CALL_COLOR, chosen).apply()
                    updateColorButton(btnMissedCallColor, chosen)
                    dialog.dismiss()
                    Toast.makeText(this, "Missed call color updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val currentSmsColor = prefs.getInt(LedOverlayService.KEY_SMS_COLOR, 0xFFFFD600.toInt())
        updateColorButton(btnSmsColor, currentSmsColor)

        btnSmsColor.setOnClickListener {
            val colorChoices = intArrayOf(
                0xFFFFD600.toInt(), // Vibrant Gold / Amber
                0xFF00E5FF.toInt(), // Cyan
                0xFF00FF00.toInt(), // Lime
                0xFFFF007F.toInt(), // Hot Pink
                0xFF0066FF.toInt(), // Blue
                0xFFFFFFFF.toInt()  // Pure White
            )
            val names = arrayOf("Vibrant Gold (Default)", "Cyan Blue", "Lime Green", "Hot Pink", "Electric Blue", "Pure White")
            val current = prefs.getInt(LedOverlayService.KEY_SMS_COLOR, 0xFFFFD600.toInt())
            val selectedIdx = colorChoices.indexOf(current).coerceAtLeast(0)

            android.app.AlertDialog.Builder(this)
                .setTitle("SMS Message Alert Color")
                .setSingleChoiceItems(names, selectedIdx) { dialog, which ->
                    val chosen = colorChoices[which]
                    prefs.edit().putInt(LedOverlayService.KEY_SMS_COLOR, chosen).apply()
                    updateColorButton(btnSmsColor, chosen)
                    dialog.dismiss()
                    Toast.makeText(this, "SMS alert color updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupConvenienceAndUx() {
        switchMasterEnable.isChecked = LedTileService.isMasterEnabled(this)

        switchMasterEnable.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LedTileService.KEY_MASTER_ENABLED, isChecked).apply()
            LedTileService.requestTileUpdate(this)

            if (!isChecked) {
                LedOverlayService.stop(this)
                Toast.makeText(this, "LedNotify disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "LedNotify enabled", Toast.LENGTH_SHORT).show()
            }
        }

        btnSimulateNotification.setOnClickListener {
            showSimulateNotificationDialog()
        }

        btnExportConfig.setOnClickListener {
            showExportConfigDialog()
        }

        btnImportConfig.setOnClickListener {
            showImportConfigDialog()
        }
    }

    private fun showSimulateNotificationDialog() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow Overlay Permission first", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val testPresets = arrayOf(
            Triple("WhatsApp: Sarah", "com.whatsapp", 0xFF00FF00.toInt()),
            Triple("Telegram: Project Devs", "org.telegram.messenger", 0xFF00E5FF.toInt()),
            Triple("Missed Call: Boss", "com.google.android.dialer", 0xFFFF1744.toInt()),
            Triple("SMS: Bank Alert", "com.google.android.apps.messaging", 0xFFFFD600.toInt())
        )
        val names = testPresets.map { it.first }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Simulate Notification")
            .setItems(names) { _, which ->
                val (label, pkg, color) = testPresets[which]
                val senderName = label.substringAfter(": ")
                startSimulatorCountdown(label, pkg, senderName, color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSimulatorCountdown(label: String, pkg: String, senderName: String, color: Int) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Lock Your Screen Now!")
            .setMessage("Simulating $label in 5 seconds...\n\nTurn off or lock your screen now to test the AMOLED notification LED!")
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .create()

        var timer: CountDownTimer? = null
        dialog.setOnShowListener {
            val cancelBtn = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            cancelBtn.setOnClickListener {
                timer?.cancel()
                dialog.dismiss()
            }
        }
        dialog.show()

        timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                dialog.setMessage("Simulating $label in $seconds seconds...\n\nTurn off or lock your screen now to test the AMOLED notification LED!")
            }

            override fun onFinish() {
                try {
                    dialog.dismiss()
                } catch (e: Exception) {
                    // ignore
                }
                triggerSimulatedNotification(label, pkg, senderName, color)
            }
        }.start()
    }

    private fun triggerSimulatedNotification(label: String, pkg: String, senderName: String, color: Int) {
        val channelId = "simulated_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Simulated Test Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels for simulated test notifications"
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val notifIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            999,
            notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText("This is a simulated message alert to test the AMOLED LED.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(888, notification)
        } catch (e: SecurityException) {
            // ignore
        }

        val intent = Intent(this, LedOverlayService::class.java).apply {
            action = LedOverlayService.ACTION_START_LED
            putExtra(LedOverlayService.EXTRA_ALERT_COLORS, intArrayOf(color))
            putExtra(LedOverlayService.EXTRA_ALERT_SPEEDS, intArrayOf(ContactColorManager.DEFAULT_BLINK_SPEED_MS))
            putExtra(LedOverlayService.EXTRA_ALERT_NAMES, arrayOf(label))
            putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LedOverlayService.EXTRA_SENDER_NAME, senderName)
            putExtra(LedOverlayService.EXTRA_IS_TEST, false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Simulated alert sent for $senderName!", Toast.LENGTH_SHORT).show()
    }

    private fun showExportConfigDialog() {
        val jsonStr = ConfigBackupManager.exportConfigToJson(this)

        val density = resources.displayMetrics.density
        val padPx = (16 * density).toInt()

        val scrollView = ScrollView(this)
        val tv = TextView(this).apply {
            text = jsonStr
            setPadding(padPx, padPx, padPx, padPx)
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(tv)

        android.app.AlertDialog.Builder(this)
            .setTitle("Export Configuration")
            .setView(scrollView)
            .setPositiveButton("Share") { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "LedNotify_Config_Backup.json")
                    putExtra(Intent.EXTRA_TEXT, jsonStr)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Backup"))
            }
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("LedNotify Config", jsonStr)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Configuration copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showImportConfigDialog() {
        val density = resources.displayMetrics.density
        val padPx = (16 * density).toInt()

        val input = EditText(this).apply {
            hint = "Paste JSON configuration here..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            setPadding(padPx, padPx, padPx, padPx)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Import Configuration")
            .setMessage("Paste exported JSON configuration below to restore your custom contacts, per-app colors, and LED preferences.")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "No configuration provided", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val result = ConfigBackupManager.importConfigFromJson(this, text)
                if (result.success) {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
                    val size = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
                    val color = AppColorManager.getDefaultColor(this)
                    updatePreviewDot(size, color)
                    syncPositionSliders()
                    LedOverlayService.updateLiveColor(color)
                    LedOverlayService.updateLiveSize(size)
                    LedOverlayService.updateLiveStyle()
                    LedOverlayService.updateLiveAnimationPattern()
                    switchMasterEnable.isChecked = LedTileService.isMasterEnabled(this)
                    switchBurnInProtection.isChecked = prefs.getBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, true)
                    switchPocketMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_POCKET_MODE, true)
                    switchDnd.isChecked = prefs.getBoolean(LedOverlayService.KEY_DND_ENABLED, false)
                    switchBatteryIndicator.isChecked = prefs.getBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, true)
                    switchLowBatteryWarning.isChecked = prefs.getBoolean(LedOverlayService.KEY_LOW_BATTERY_WARNING, true)
                } else {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Import Failed")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            LedOverlayService.start(this, isTest = true)
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
