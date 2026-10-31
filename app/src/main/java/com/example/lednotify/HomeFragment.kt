package com.example.lednotify

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.example.lednotify.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var blinkAnimator: ObjectAnimator? = null

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

        setupPermissionsClicks()
        setupActionButtons()
        startPreviewBlink()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updatePreviewAppearance()
    }

    override fun onDestroyView() {
        blinkAnimator?.cancel()
        blinkAnimator = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupPermissionsClicks() {
        binding.layoutOverlayStatus.setOnClickListener {
            val ctx = requireContext()
            if (Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Overlay permission is already granted", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                startActivity(intent)
            }
        }

        binding.layoutNotificationStatus.setOnClickListener {
            val ctx = requireContext()
            if (isNotificationServiceEnabled(ctx)) {
                Toast.makeText(ctx, "Notification access is already granted", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }

    private fun updatePermissionStatuses() {
        val ctx = context ?: return
        val hasOverlay = Settings.canDrawOverlays(ctx)
        val hasNotification = isNotificationServiceEnabled(ctx)

        if (hasOverlay) {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_check_circle)
            binding.tvOverlayAction.text = "Granted"
            binding.tvOverlayAction.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_warning)
            binding.tvOverlayAction.text = "Grant Access"
            binding.tvOverlayAction.setTextColor(Color.parseColor("#FF5252"))
        }

        if (hasNotification) {
            binding.ivNotificationStatus.setImageResource(R.drawable.ic_check_circle)
            binding.tvNotificationAction.text = "Granted"
            binding.tvNotificationAction.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.ivNotificationStatus.setImageResource(R.drawable.ic_warning)
            binding.tvNotificationAction.text = "Grant Access"
            binding.tvNotificationAction.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkg = context.packageName
        val listeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return listeners.contains(pkg)
    }

    private fun updatePreviewAppearance() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt(LedOverlayService.KEY_SIZE, 16).coerceIn(10, 100)
        val color = prefs.getInt(LedOverlayService.KEY_COLOR, Color.parseColor("#00E676"))
        val style = prefs.getString(LedOverlayService.KEY_VISUAL_STYLE, LedOverlayService.STYLE_DOT) ?: LedOverlayService.STYLE_DOT

        val density = resources.displayMetrics.density
        val previewSizePx = ((sizeDp.coerceIn(10, 48)) * density).toInt()

        binding.previewLedDot.layoutParams = binding.previewLedDot.layoutParams.apply {
            width = previewSizePx
            height = previewSizePx
        }

        val drawable = if (style == LedOverlayService.STYLE_RING) {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((3 * density).toInt(), color)
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        binding.previewLedDot.background = drawable
    }

    private fun startPreviewBlink() {
        blinkAnimator?.cancel()
        blinkAnimator = ObjectAnimator.ofFloat(binding.previewLedDot, View.ALPHA, 1f, 0.1f).apply {
            duration = 500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
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

            val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            val color = prefs.getInt(LedOverlayService.KEY_COLOR, Color.parseColor("#00E676"))
            val speed = prefs.getInt(LedOverlayService.KEY_SPEED, 400)
            val pattern = prefs.getString(LedOverlayService.KEY_ANIMATION_PATTERN, LedOverlayService.PATTERN_BLINK) ?: LedOverlayService.PATTERN_BLINK

            val testIntent = Intent(ctx, LedOverlayService::class.java).apply {
                action = LedOverlayService.ACTION_START_LED
                putExtra(LedOverlayService.EXTRA_PACKAGE_NAME, "test.notification")
                putExtra(LedOverlayService.EXTRA_SENDER_NAME, "Test Simulation")
                putExtra(LedOverlayService.EXTRA_ALERT_COLORS, intArrayOf(color))
                putExtra(LedOverlayService.EXTRA_ALERT_SPEEDS, intArrayOf(speed))
                putExtra(LedOverlayService.EXTRA_ALERT_NAMES, arrayOf("Test Alert"))
                putExtra(LedOverlayService.EXTRA_ALERT_PATTERNS, arrayOf(pattern))
                putExtra(LedOverlayService.EXTRA_IS_TEST, true)
            }
            ctx.startService(testIntent)
            Toast.makeText(ctx, "Test LED Active on Screen", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopLed.setOnClickListener {
            val ctx = requireContext()
            LedOverlayService.stop(ctx)
            Toast.makeText(ctx, "LED Overlay Stopped", Toast.LENGTH_SHORT).show()
        }
    }
}
