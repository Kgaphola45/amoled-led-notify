package com.example.lednotify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ImportResult(
    val success: Boolean,
    val contactsCount: Int,
    val appsCount: Int,
    val message: String
)

object ConfigBackupManager {

    fun exportConfigToJson(context: Context): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Export Contacts
        val contactsArray = JSONArray()
        val contactPrefs = context.getSharedPreferences(ContactColorManager.PREFS_NAME, Context.MODE_PRIVATE)
        val speedPrefs = context.getSharedPreferences(ContactColorManager.PREFS_SPEEDS, Context.MODE_PRIVATE)

        for ((key, value) in contactPrefs.all) {
            if (key != "is_initialized" && value is Int) {
                val item = JSONObject()
                item.put("name", key)
                item.put("color", value)
                item.put("speedMs", speedPrefs.getInt(key, ContactColorManager.DEFAULT_BLINK_SPEED_MS))
                contactsArray.put(item)
            }
        }
        root.put("contacts", contactsArray)

        // 2. Export App Colors & Enabled Apps
        val appsObject = JSONObject()
        val appPrefs = context.getSharedPreferences(AppColorManager.PREFS_NAME, Context.MODE_PRIVATE)
        for ((key, value) in appPrefs.all) {
            if (value is Int) {
                appsObject.put(key, value)
            }
        }
        root.put("appColors", appsObject)

        // 3. Export Global LED Settings
        val settingsObject = JSONObject()
        val ledPrefs = context.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        for ((key, value) in ledPrefs.all) {
            when (value) {
                is Int -> settingsObject.put(key, value)
                is Float -> settingsObject.put(key, value.toDouble())
                is Boolean -> settingsObject.put(key, value)
                is String -> settingsObject.put(key, value)
            }
        }
        root.put("ledSettings", settingsObject)

        return root.toString(2)
    }

    fun importConfigFromJson(context: Context, jsonStr: String): ImportResult {
        return try {
            val root = JSONObject(jsonStr)

            var contactsCount = 0
            var appsCount = 0

            // 1. Import Contacts
            if (root.has("contacts")) {
                val contactsArray = root.getJSONArray("contacts")
                val contactPrefs = context.getSharedPreferences(ContactColorManager.PREFS_NAME, Context.MODE_PRIVATE)
                val speedPrefs = context.getSharedPreferences(ContactColorManager.PREFS_SPEEDS, Context.MODE_PRIVATE)
                val cEdit = contactPrefs.edit()
                val sEdit = speedPrefs.edit()

                for (i in 0 until contactsArray.length()) {
                    val obj = contactsArray.getJSONObject(i)
                    val name = obj.optString("name")
                    val color = obj.optInt("color", 0)
                    val speed = obj.optInt("speedMs", ContactColorManager.DEFAULT_BLINK_SPEED_MS)
                    if (name.isNotEmpty() && color != 0) {
                        cEdit.putInt(name, color)
                        sEdit.putInt(name, speed)
                        contactsCount++
                    }
                }
                cEdit.apply()
                sEdit.apply()
            }

            // 2. Import App Colors
            if (root.has("appColors")) {
                val appsObj = root.getJSONObject("appColors")
                val appPrefs = context.getSharedPreferences(AppColorManager.PREFS_NAME, Context.MODE_PRIVATE)
                val aEdit = appPrefs.edit()

                val keys = appsObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val color = appsObj.getInt(pkg)
                    aEdit.putInt(pkg, color)
                    appsCount++
                }
                aEdit.apply()
            }

            // 3. Import LED Settings
            if (root.has("ledSettings")) {
                val settingsObj = root.getJSONObject("ledSettings")
                val ledPrefs = context.getSharedPreferences(LedOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
                val lEdit = ledPrefs.edit()

                val keys = settingsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = settingsObj.get(key)
                    when (value) {
                        is Int -> lEdit.putInt(key, value)
                        is Double -> lEdit.putFloat(key, value.toFloat())
                        is Boolean -> lEdit.putBoolean(key, value)
                        is String -> lEdit.putString(key, value)
                    }
                }
                lEdit.apply()
            }

            ImportResult(
                success = true,
                contactsCount = contactsCount,
                appsCount = appsCount,
                message = "Successfully imported $contactsCount contacts and $appsCount app color profiles!"
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                contactsCount = 0,
                appsCount = 0,
                message = "Failed to import: ${e.localizedMessage ?: "Invalid configuration format"}"
            )
        }
    }
}
