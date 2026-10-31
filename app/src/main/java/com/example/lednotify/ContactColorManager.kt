package com.example.lednotify

import android.content.Context

data class ContactColorItem(
    val contactName: String,
    val color: Int
)

object ContactColorManager {
    const val PREFS_NAME = "contact_colors"
    private const val KEY_INITIALIZED = "is_initialized"

    fun initializeDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                // Default entries requested: Mom = #FF69B4 (pink), Boss = #FF0000 (red)
                .putInt("Mom", 0xFFFF69B4.toInt())
                .putInt("Boss", 0xFFFF0000.toInt())
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }
    }

    fun getColorForContact(context: Context, contactName: String?): Int? {
        if (contactName.isNullOrBlank()) return null
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val cleanName = contactName.trim()

        // Exact match first
        if (prefs.contains(cleanName)) {
            return prefs.getInt(cleanName, 0)
        }

        // Case-insensitive fallback match
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key != KEY_INITIALIZED && value is Int && key.equals(cleanName, ignoreCase = true)) {
                return value
            }
        }

        return null
    }

    fun saveContactColor(context: Context, contactName: String, color: Int) {
        val cleanName = contactName.trim()
        if (cleanName.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(cleanName, color).apply()
    }

    fun deleteContactColor(context: Context, contactName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(contactName).apply()
    }

    fun getAllContactColors(context: Context): List<ContactColorItem> {
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<ContactColorItem>()
        for ((key, value) in prefs.all) {
            if (key != KEY_INITIALIZED && value is Int) {
                list.add(ContactColorItem(key, value))
            }
        }
        return list.sortedBy { it.contactName.lowercase() }
    }
}
