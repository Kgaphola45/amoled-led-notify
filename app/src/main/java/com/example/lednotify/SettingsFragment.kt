package com.example.lednotify

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.lednotify.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTimeoutControl()
        setupSwitches()
        setupAodPreview()
        setupBackupAndExport()
        setupAboutSection()
    }

    override fun onResume() {
        super.onResume()
        syncSwitchStates()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupTimeoutControl() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        fun updateTimeoutUi(minutes: Int) {
            val label = when (minutes) {
                0 -> "Never"
                60 -> "1 hour"
                else -> "$minutes min"
            }
            binding.btnTimeout.text = label
            binding.tvTimeoutSubtitle.text = if (minutes == 0) {
                "LED remains active until cleared"
            } else {
                "Stops LED after $label of inactivity"
            }
        }

        val currentTimeout = prefs.getInt(LedOverlayService.KEY_TIMEOUT_MINUTES, 15)
        updateTimeoutUi(currentTimeout)

        val clickListener = View.OnClickListener {
            val timeoutLabels = arrayOf("5 minutes", "10 minutes", "15 minutes (Default)", "30 minutes", "1 hour", "Never (Always On)")
            val timeoutValues = intArrayOf(5, 10, 15, 30, 60, 0)
            val selectedIndex = timeoutValues.indexOf(prefs.getInt(LedOverlayService.KEY_TIMEOUT_MINUTES, 15)).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle("Auto-Off Timeout")
                .setSingleChoiceItems(timeoutLabels, selectedIndex) { dialog, which ->
                    val chosen = timeoutValues[which]
                    prefs.edit().putInt(LedOverlayService.KEY_TIMEOUT_MINUTES, chosen).apply()
                    updateTimeoutUi(chosen)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnTimeout.setOnClickListener(clickListener)
        binding.rowTimeout.setOnClickListener(clickListener)
    }

    private fun setupSwitches() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

        // Pocket Mode
        binding.switchPocketMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_POCKET_MODE, true)
        binding.switchPocketMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_POCKET_MODE, isChecked).apply()
        }

        // Respect DND
        binding.switchRespectDnd.isChecked = prefs.getBoolean(LedOverlayService.KEY_RESPECT_SYSTEM_DND, true)
        binding.switchRespectDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_RESPECT_SYSTEM_DND, isChecked).apply()
        }

        // Burn-in Protection
        binding.switchBurnInProtection.isChecked = prefs.getBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, true)
        binding.switchBurnInProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, isChecked).apply()
        }

        // Hide while unlocked
        binding.switchHideWhileUnlocked.isChecked = prefs.getBoolean(LedOverlayService.KEY_HIDE_WHILE_UNLOCKED, true)
        binding.switchHideWhileUnlocked.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_HIDE_WHILE_UNLOCKED, isChecked).apply()
        }

        // AOD Mode
        binding.switchAodMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_AOD_MODE, true)
        binding.switchAodMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_AOD_MODE, isChecked).apply()
        }

        // Suppress in app
        binding.switchSuppressInApp.isChecked = prefs.getBoolean(LedOverlayService.KEY_SUPPRESS_WHEN_APP_FOREGROUND, true)
        binding.switchSuppressInApp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_SUPPRESS_WHEN_APP_FOREGROUND, isChecked).apply()
            if (isChecked && !ForegroundAppDetector.hasUsageStatsPermission(requireContext())) {
                Toast.makeText(requireContext(), "Please grant Usage Access to detect open apps", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        // Privacy Mode
        binding.switchPrivacyMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_PRIVACY_MODE, false)
        binding.switchPrivacyMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_PRIVACY_MODE, isChecked).apply()
        }

        // Battery Charging Indicator
        binding.switchBatteryIndicator.isChecked = prefs.getBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, true)
        binding.switchBatteryIndicator.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, isChecked).apply()
        }
    }

    private fun syncSwitchStates() {
        val prefs = requireContext().getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        binding.switchPocketMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_POCKET_MODE, true)
        binding.switchRespectDnd.isChecked = prefs.getBoolean(LedOverlayService.KEY_RESPECT_SYSTEM_DND, true)
        binding.switchBurnInProtection.isChecked = prefs.getBoolean(LedOverlayService.KEY_BURN_IN_PROTECTION, true)
        binding.switchHideWhileUnlocked.isChecked = prefs.getBoolean(LedOverlayService.KEY_HIDE_WHILE_UNLOCKED, true)
        binding.switchAodMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_AOD_MODE, true)
        binding.switchSuppressInApp.isChecked = prefs.getBoolean(LedOverlayService.KEY_SUPPRESS_WHEN_APP_FOREGROUND, true)
        binding.switchPrivacyMode.isChecked = prefs.getBoolean(LedOverlayService.KEY_PRIVACY_MODE, false)
        binding.switchBatteryIndicator.isChecked = prefs.getBoolean(LedOverlayService.KEY_BATTERY_INDICATOR_ENABLED, true)
    }

    private fun setupAodPreview() {
        binding.btnPreviewAod.setOnClickListener {
            val ctx = requireContext()
            if (!Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Please allow Overlay Permission first", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                )
                startActivity(intent)
                return@setOnClickListener
            }
            Toast.makeText(ctx, "Starting AOD Preview...\nDouble-tap anywhere on screen to exit!", Toast.LENGTH_LONG).show()
            binding.btnPreviewAod.postDelayed({
                if (isAdded) {
                    LedOverlayService.previewAod(requireContext())
                }
            }, 800)
        }
    }

    private fun setupBackupAndExport() {
        binding.btnExportSettings.setOnClickListener {
            val ctx = requireContext()
            val jsonStr = ConfigBackupManager.exportConfigToJson(ctx)
            val density = resources.displayMetrics.density
            val padPx = (16 * density).toInt()

            val scrollView = ScrollView(ctx)
            val tv = TextView(ctx).apply {
                text = jsonStr
                setPadding(padPx, padPx, padPx, padPx)
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
            scrollView.addView(tv)

            AlertDialog.Builder(ctx)
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
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("LedNotify Config", jsonStr)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(ctx, "Configuration copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnImportSettings.setOnClickListener {
            val ctx = requireContext()
            val density = resources.displayMetrics.density
            val padPx = (16 * density).toInt()

            val input = EditText(ctx).apply {
                hint = "Paste JSON configuration here..."
                setHintTextColor(Color.parseColor("#888888"))
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                minLines = 8
                gravity = Gravity.TOP or Gravity.START
                setPadding(padPx, padPx, padPx, padPx)
            }

            AlertDialog.Builder(ctx)
                .setTitle("Import Configuration")
                .setMessage("Paste exported JSON configuration below to restore your custom contacts, per-app colors, and LED preferences.")
                .setView(input)
                .setPositiveButton("Import") { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.isEmpty()) {
                        Toast.makeText(ctx, "No configuration provided", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val result = ConfigBackupManager.importConfigFromJson(ctx, text)
                    if (result.success) {
                        Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                        syncSwitchStates()
                        val prefs = ctx.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
                        val color = AppColorManager.getDefaultColor(ctx)
                        val size = prefs.getInt(LedOverlayService.KEY_SIZE, 2)
                        LedOverlayService.updateLiveColor(color)
                        LedOverlayService.updateLiveSize(size)
                        LedOverlayService.updateLiveStyle()
                        LedOverlayService.updateLiveAnimationPattern()
                    } else {
                        Toast.makeText(ctx, "Import error: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupAboutSection() {
        binding.btnGithubLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
