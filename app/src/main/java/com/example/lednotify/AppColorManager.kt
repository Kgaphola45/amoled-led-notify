package com.example.lednotify

import android.content.Context

object AppColorManager {
    const val PREFS_NAME = "app_colors"
    const val KEY_DEFAULT_COLOR = "default_color"
    private const val KEY_INITIALIZED = "is_initialized"
    private const val PREFIX_ENABLED = "enabled_"

    // 12 vibrant colors for LED notifications
    val PALETTE_12 = listOf(
        0xFF00FF00.toInt(), // Green (#00FF00)
        0xFF76FF03.toInt(), // Lime (#76FF03)
        0xFFFF0000.toInt(), // Red (#FF0000)
        0xFFDC143C.toInt(), // Crimson (#DC143C)
        0xFF0000FF.toInt(), // Blue (#0000FF)
        0xFF00BFFF.toInt(), // Deep Sky Blue (#00BFFF)
        0xFF00FFFF.toInt(), // Cyan (#00FFFF)
        0xFFFFFF00.toInt(), // Yellow (#FFFF00)
        0xFFFF00FF.toInt(), // Magenta (#FF00FF)
        0xFF9C27B0.toInt(), // Purple (#9C27B0)
        0xFFFFA500.toInt(), // Orange (#FFA500)
        0xFFFFFFFF.toInt()  // White (#FFFFFF)
    )

    fun initializeDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DEFAULT_COLOR, 0xFF00FF00.toInt())
                // Pre-fill colors
                .putInt("com.whatsapp", 0xFF00FF00.toInt())           // Green #00FF00
                .putInt("com.instagram.android", 0xFFFF00FF.toInt())  // Magenta #FF00FF
                .putInt("com.facebook.katana", 0xFF00BFFF.toInt())    // Blue #00BFFF
                .putInt("com.google.android.gm", 0xFFFF0000.toInt())  // Red #FF0000
                // Pre-enable only popular apps by default; all others are disabled until user chooses
                .putBoolean(PREFIX_ENABLED + "com.whatsapp", true)
                .putBoolean(PREFIX_ENABLED + "com.instagram.android", true)
                .putBoolean(PREFIX_ENABLED + "com.facebook.katana", true)
                .putBoolean(PREFIX_ENABLED + "com.google.android.gm", true)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }
    }

    fun isAppEnabled(context: Context, packageName: String?): Boolean {
        if (packageName == null) return false
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREFIX_ENABLED + packageName, false)
    }

    fun setAppEnabled(context: Context, packageName: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFIX_ENABLED + packageName, enabled).apply()
    }

    fun getColorForPackage(context: Context, packageName: String?): Int {
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultColor = prefs.getInt(KEY_DEFAULT_COLOR, 0xFF00FF00.toInt())
        return if (packageName != null && prefs.contains(packageName)) {
            prefs.getInt(packageName, defaultColor)
        } else {
            defaultColor
        }
    }

    fun setColorForPackage(context: Context, packageName: String, color: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(packageName, color).apply()
    }

    fun getDefaultColor(context: Context): Int {
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DEFAULT_COLOR, 0xFF00FF00.toInt())
    }

    fun setDefaultColor(context: Context, color: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DEFAULT_COLOR, color).apply()
    }
}
