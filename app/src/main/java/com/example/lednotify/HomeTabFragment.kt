package com.example.lednotify

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.example.lednotify.databinding.FragmentHomeBinding

class HomeTabFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var previewAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPermissions()
        setupPreviewAnimation()
        setupActionButtons()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updatePreviewAppearance()
        startPreviewBlink()
    }

    override fun onPause() {
        super.onPause()
        previewAnimator?.cancel()
    }

    override fun onDestroyView() {
        previewAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun setupPermissions() {
        binding.layoutOverlayStatus.setOnClickListener {
            val ctx = requireContext()
            if (!Settings.canDrawOverlays(ctx)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                startActivity(intent)
            } else {
                Toast.makeText(ctx, "Overlay Permission is already granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.layoutNotificationStatus.setOnClickListener {
            val ctx = requireContext()
            val isGranted = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            if (!isGranted) {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Could not open Notification Listener settings", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(ctx, "Notification Access is already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePermissionStatuses() {
        val ctx = context ?: return

        // 1. Overlay Permission
        val overlayGranted = Settings.canDrawOverlays(ctx)
        if (overlayGranted) {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_check_circle)
            binding.ivOverlayStatus.setColorFilter(Color.parseColor("#00E676"))
            binding.tvOverlayAction.text = "Granted"
            binding.tvOverlayAction.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_warning)
            binding.ivOverlayStatus.setColorFilter(Color.parseColor("#FF5252"))
            binding.tvOverlayAction.text = "Grant ↗"
            binding.tvOverlayAction.setTextColor(Color.parseColor("#FF5252"))
        }

        // 2. Notification Listener Permission
        val notifGranted = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (notifGranted) {
            binding.ivNotificationStatus.setImageResource(R.drawable.ic_check_circle)
            binding.ivNotificationStatus.setColorFilter(Color.parseColor("#00E676"))
            binding.tvNotificationAction.text = "Granted"
            binding.tvNotificationAction.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.ivNotificationStatus.setImageResource(R.drawable.ic_warning)
            binding.ivNotificationStatus.setColorFilter(Color.parseColor("#FF5252"))
            binding.tvNotificationAction.text = "Grant ↗"
            binding.tvNotificationAction.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun setupPreviewAnimation() {
        previewAnimator = ObjectAnimator.ofFloat(binding.previewLedDot, "alpha", 0.05f, 1f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
    }

    private fun startPreviewBlink() {
        previewAnimator?.let {
            if (!it.isRunning) {
                it.start()
            }
        }
    }

    private fun updatePreviewAppearance() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16)
        val color = AppColorManager.getDefaultColor(ctx)
        val style = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT) ?: LedOverlayService.STYLE_DOT
        val density = resources.displayMetrics.density

        val sizePx = (sizeDp * density).toInt().coerceIn((10 * density).toInt(), (48 * density).toInt())

        val params = binding.previewLedDot.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(sizePx, sizePx)
        params.width = sizePx
        params.height = sizePx
        params.gravity = Gravity.CENTER
        binding.previewLedDot.layoutParams = params

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
        binding.previewLedDot.background = drawable
    }

    private fun setupActionButtons() {
        binding.btnTestLed.setOnClickListener {
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

            LedOverlayService.start(ctx, isTest = true)
            Toast.makeText(ctx, "Test LED started! Check your screen.", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopLed.setOnClickListener {
            val ctx = requireContext()
            LedOverlayService.stop(ctx)
            Toast.makeText(ctx, "LED stopped", Toast.LENGTH_SHORT).show()
        }
    }
}
