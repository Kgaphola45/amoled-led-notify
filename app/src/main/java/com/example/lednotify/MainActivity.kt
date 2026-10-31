package com.example.lednotify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.lednotify.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val postNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize defaults
        AppColorManager.initializeDefaultsIfNeeded(this)
        ContactColorManager.initializeDefaultsIfNeeded(this)

        setupNavigation()
        setupMasterSwitch()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateMasterSwitchUi(LedTileService.isMasterEnabled(this))
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment
        val navController = navHostFragment?.navController

        if (navController != null) {
            binding.bottomNavigation.setupWithNavController(navController)
        }
    }

    private fun setupMasterSwitch() {
        val isMasterOn = LedTileService.isMasterEnabled(this)
        updateMasterSwitchUi(isMasterOn)

        binding.switchTopMaster.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LedTileService.KEY_MASTER_ENABLED, isChecked).apply()
            LedTileService.requestTileUpdate(this)

            updateMasterSwitchUi(isChecked)

            if (!isChecked) {
                LedOverlayService.stop(this)
                Toast.makeText(this, "AMOLED LED notifications paused", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "AMOLED LED notifications active", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMasterSwitchUi(isEnabled: Boolean) {
        binding.switchTopMaster.setOnCheckedChangeListener(null)
        binding.switchTopMaster.isChecked = isEnabled
        if (isEnabled) {
            binding.tvMasterStatus.text = "Active & Monitoring"
            binding.tvMasterStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.tvMasterStatus.text = "Paused / Disabled"
            binding.tvMasterStatus.setTextColor(Color.parseColor("#FF5252"))
        }
        binding.switchTopMaster.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LedTileService.KEY_MASTER_ENABLED, isChecked).apply()
            LedTileService.requestTileUpdate(this)

            updateMasterSwitchUi(isChecked)

            if (!isChecked) {
                LedOverlayService.stop(this)
                Toast.makeText(this, "AMOLED LED notifications paused", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "AMOLED LED notifications active", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
