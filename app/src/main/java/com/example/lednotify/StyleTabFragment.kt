package com.example.lednotify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lednotify.databinding.FragmentStyleBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.util.Locale

class StyleTabFragment : Fragment() {

    private var _binding: FragmentStyleBinding? = null
    private val binding get() = _binding!!

    private var selectedColor: Int = 0xFF00E676.toInt()
    private lateinit var colorsAdapter: StyleColorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSizeAndShape()
        setupInteractivePositioning()
        setupAnimationPatterns()
        setupColorPalette()
    }

    override fun onResume() {
        super.onResume()
        syncStateFromPreferences()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun syncStateFromPreferences() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        // Size
        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16).coerceIn(10, 100)
        binding.sliderSize.value = sizeDp.toFloat()
        binding.tvSizeLabel.text = "LED Dot Size: ${sizeDp}dp"

        // Shape
        val style = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT)
        when (style) {
            LedOverlayService.STYLE_RING -> binding.chipShapeRing.isChecked = true
            else -> binding.chipShapeDot.isChecked = true
        }

        // Animation
        val pattern = prefs.getString(LedOverlayService.KEY_ANIMATION_PATTERN, LedOverlayService.PATTERN_BLINK)
        when (pattern) {
            LedOverlayService.PATTERN_PULSE -> binding.chipAnimBreathe.isChecked = true
            LedOverlayService.PATTERN_HEARTBEAT -> binding.chipAnimPulse.isChecked = true
            LedOverlayService.PATTERN_SOLID -> binding.chipAnimRainbow.isChecked = true
            else -> binding.chipAnimBlink.isChecked = true
        }

        // Color
        selectedColor = AppColorManager.getDefaultColor(ctx)
        updateColorDisplay(selectedColor)
        colorsAdapter.notifyDataSetChanged()

        updateMiniDotAppearance()
        positionMiniDotFromPrefs()
    }

    private fun setupSizeAndShape() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        binding.sliderSize.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                val sizeDp = value.toInt()
                prefs.edit().putInt(LedOverlayService.KEY_SIZE, sizeDp).apply()
                binding.tvSizeLabel.text = "LED Dot Size: ${sizeDp}dp"
                updateMiniDotAppearance()
                LedOverlayService.updateLiveSize(sizeDp)
            }
        }

        binding.chipGroupShape.setOnCheckedStateChangeListener { _, checkedIds ->
            val newStyle = when {
                checkedIds.contains(R.id.chipShapeRing) -> LedOverlayService.STYLE_RING
                else -> LedOverlayService.STYLE_DOT
            }
            prefs.edit().putString(LedOverlayService.KEY_VISUAL_STYLE, newStyle).apply()
            updateMiniDotAppearance()
            LedOverlayService.updateLiveStyle()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInteractivePositioning() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        binding.frameInteractivePhone.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val frameW = v.width.toFloat()
                    val frameH = v.height.toFloat()
                    if (frameW <= 0 || frameH <= 0) return@setOnTouchListener false

                    val dotW = binding.viewDraggableDot.width.toFloat()
                    val dotH = binding.viewDraggableDot.height.toFloat()

                    val targetX = (event.x - dotW / 2).coerceIn(0f, frameW - dotW)
                    val targetY = (event.y - dotH / 2).coerceIn(0f, frameH - dotH)

                    binding.viewDraggableDot.x = targetX
                    binding.viewDraggableDot.y = targetY

                    // Map to real screen coordinates
                    val (screenW, screenH) = LedOverlayService.getRealScreenSize(requireContext())
                    val density = resources.displayMetrics.density
                    val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16)
                    val sizePx = (sizeDp * density).toInt()

                    val ratioX = targetX / (frameW - dotW)
                    val ratioY = targetY / (frameH - dotH)

                    val realX = ratioX * (screenW - sizePx)
                    val realY = ratioY * (screenH - sizePx)

                    prefs.edit()
                        .putFloat(LedOverlayService.KEY_POS_X, realX)
                        .putFloat(LedOverlayService.KEY_POS_Y, realY)
                        .apply()

                    binding.tvPositionCoords.text = "Position: X=${realX.toInt()}px, Y=${realY.toInt()}px"
                    LedOverlayService.updateLivePosition(realX, realY)
                    true
                }
                else -> true
            }
        }

        binding.btnResetPosition.setOnClickListener {
            val (screenW, _) = LedOverlayService.getRealScreenSize(requireContext())
            val density = resources.displayMetrics.density
            val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16)
            val sizePx = (sizeDp * density).toInt()

            val posX = (screenW - sizePx).toFloat().coerceAtLeast(0f)
            val posY = 0f

            prefs.edit()
                .putFloat(LedOverlayService.KEY_POS_X, posX)
                .putFloat(LedOverlayService.KEY_POS_Y, posY)
                .apply()

            positionMiniDotFromPrefs()
            LedOverlayService.updateLivePosition(posX, posY)
            Toast.makeText(requireContext(), "Position reset to Top-Right", Toast.LENGTH_SHORT).show()
        }

        binding.btnPositionScreenOverlay.setOnClickListener {
            val ctx = requireContext()
            if (!Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Please allow Overlay Permission first", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                startActivity(intent)
                return@setOnClickListener
            }
            LedOverlayService.startPositioning(ctx)
            Toast.makeText(ctx, "Overlay dot active! Drag it anywhere on screen.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun positionMiniDotFromPrefs() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedX = prefs.getFloat(LedOverlayService.KEY_POS_X, -1f)
        val savedY = prefs.getFloat(LedOverlayService.KEY_POS_Y, -1f)

        binding.frameInteractivePhone.post {
            if (_binding == null) return@post
            val frameW = binding.frameInteractivePhone.width.toFloat()
            val frameH = binding.frameInteractivePhone.height.toFloat()
            val (screenW, screenH) = LedOverlayService.getRealScreenSize(ctx)

            val density = resources.displayMetrics.density
            val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16)
            val sizePx = (sizeDp * density).toInt()

            val dotW = binding.viewDraggableDot.width.toFloat()
            val dotH = binding.viewDraggableDot.height.toFloat()

            if (savedX >= 0f && savedY >= 0f && screenW > 0 && screenH > 0) {
                val ratioX = (savedX / (screenW - sizePx)).coerceIn(0f, 1f)
                val ratioY = (savedY / (screenH - sizePx)).coerceIn(0f, 1f)
                binding.viewDraggableDot.x = ratioX * (frameW - dotW)
                binding.viewDraggableDot.y = ratioY * (frameH - dotH)
                binding.tvPositionCoords.text = "Position: X=${savedX.toInt()}px, Y=${savedY.toInt()}px"
            } else {
                binding.viewDraggableDot.x = frameW - dotW
                binding.viewDraggableDot.y = 0f
                binding.tvPositionCoords.text = "Position: Top-Right Corner"
            }
        }
    }

    private fun updateMiniDotAppearance() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16)
        val style = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT)
        val density = resources.displayMetrics.density

        val miniSizePx = ((sizeDp / 100f) * (36 - 14) + 14) * density

        val params = binding.viewDraggableDot.layoutParams
        params.width = miniSizePx.toInt()
        params.height = miniSizePx.toInt()
        binding.viewDraggableDot.layoutParams = params

        val drawable = if (style == LedOverlayService.STYLE_RING) {
            val strokePx = (2 * density).toInt().coerceAtLeast(1)
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(strokePx, selectedColor)
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(selectedColor)
            }
        }
        binding.viewDraggableDot.background = drawable
    }

    private fun setupAnimationPatterns() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        binding.chipGroupAnim.setOnCheckedStateChangeListener { _, checkedIds ->
            val pattern = when {
                checkedIds.contains(R.id.chipAnimBreathe) -> LedOverlayService.PATTERN_PULSE
                checkedIds.contains(R.id.chipAnimPulse) -> LedOverlayService.PATTERN_HEARTBEAT
                checkedIds.contains(R.id.chipAnimRainbow) -> LedOverlayService.PATTERN_SOLID
                else -> LedOverlayService.PATTERN_BLINK
            }
            prefs.edit().putString(LedOverlayService.KEY_ANIMATION_PATTERN, pattern).apply()
            LedOverlayService.updateLiveAnimationPattern()
        }

        binding.sliderSpeed.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                val speedMs = value.toInt()
                binding.tvSpeedLabel.text = "Speed / Interval: ${speedMs}ms"
            }
        }
    }

    private fun setupColorPalette() {
        colorsAdapter = StyleColorsAdapter(AppColorManager.PALETTE_12) { color ->
            selectedColor = color
            val ctx = requireContext()
            AppColorManager.setDefaultColor(ctx, color)
            updateColorDisplay(color)
            updateMiniDotAppearance()
            LedOverlayService.updateLiveColor(color)
            colorsAdapter.notifyDataSetChanged()
        }

        binding.recyclerColors.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerColors.adapter = colorsAdapter

        binding.btnCustomHexColor.setOnClickListener {
            showCustomHexColorDialog()
        }
    }

    private fun updateColorDisplay(color: Int) {
        val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and color)
        binding.tvSelectedColorHex.text = "Selected: $hex"
    }

    private fun showCustomHexColorDialog() {
        val ctx = requireContext()

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_custom_color_picker, null)
        val previewCircle = dialogView.findViewById<View>(R.id.viewColorPreview)
        val etHex = dialogView.findViewById<EditText>(R.id.etHexCode)
        val sbRed = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val sbGreen = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val sbBlue = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)
        val btnApply = dialogView.findViewById<android.widget.Button>(R.id.btnApplyCustomColor)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancelCustomColor)

        var currentColor = selectedColor
        var isInternalUpdate = false

        fun updateFromRgb() {
            if (isInternalUpdate) return
            isInternalUpdate = true
            currentColor = Color.rgb(sbRed.progress, sbGreen.progress, sbBlue.progress)
            val hex = String.format(Locale.US, "%06X", 0xFFFFFF and currentColor)
            etHex.setText(hex)
            previewCircle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(currentColor)
            }
            isInternalUpdate = false
        }

        sbRed.progress = Color.red(currentColor)
        sbGreen.progress = Color.green(currentColor)
        sbBlue.progress = Color.blue(currentColor)
        etHex.setText(String.format(Locale.US, "%06X", 0xFFFFFF and currentColor))
        previewCircle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(currentColor)
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) updateFromRgb()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbRed.setOnSeekBarChangeListener(seekListener)
        sbGreen.setOnSeekBarChangeListener(seekListener)
        sbBlue.setOnSeekBarChangeListener(seekListener)

        etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isInternalUpdate) return
                val text = s?.toString()?.trim() ?: ""
                try {
                    val parsed = Color.parseColor(if (text.startsWith("#")) text else "#$text")
                    isInternalUpdate = true
                    currentColor = parsed
                    sbRed.progress = Color.red(parsed)
                    sbGreen.progress = Color.green(parsed)
                    sbBlue.progress = Color.blue(parsed)
                    previewCircle.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(parsed)
                    }
                    isInternalUpdate = false
                } catch (e: Exception) {
                    // Ignore while typing incomplete hex
                }
            }
        })

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            selectedColor = currentColor
            AppColorManager.setDefaultColor(ctx, currentColor)
            updateColorDisplay(currentColor)
            updateMiniDotAppearance()
            LedOverlayService.updateLiveColor(currentColor)
            colorsAdapter.notifyDataSetChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    inner class StyleColorsAdapter(
        private val colors: List<Int>,
        private val onColorSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<StyleColorsAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(
            val root: FrameLayout,
            val ringView: View,
            val dotView: View
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val root = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    (48 * density).toInt(),
                    (48 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
            }
            val ring = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
                    gravity = Gravity.CENTER
                }
            }
            val dot = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt()).apply {
                    gravity = Gravity.CENTER
                }
            }
            root.addView(ring)
            root.addView(dot)
            return ColorViewHolder(root, ring, dot)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val color = colors[position]
            val density = holder.itemView.resources.displayMetrics.density

            holder.dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            if (color == selectedColor) {
                holder.ringView.visibility = View.VISIBLE
                holder.ringView.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((3 * density).toInt(), Color.WHITE)
                }
            } else {
                holder.ringView.visibility = View.GONE
            }

            holder.root.setOnClickListener {
                onColorSelect(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }
}
