package com.saaddev.safeid.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomWatermarkPreset(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val templateText: String
)

/**
 * Persists user-created custom watermark presets locally in SharedPreferences.
 */
object CustomPresetRepository {

    private const val PREFS_NAME = "safeid_custom_presets"
    private const val KEY_PRESETS = "presets_json"

    fun loadPresets(context: Context): List<CustomWatermarkPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    val label = obj.optString("label")
                    val text = obj.optString("templateText")
                    if (id.isNotBlank() && label.isNotBlank() && text.isNotBlank()) {
                        add(CustomWatermarkPreset(id = id, label = label, templateText = text))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePresets(context: Context, presets: List<CustomWatermarkPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("label", preset.label)
                put("templateText", preset.templateText)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, array.toString())
            .apply()
    }
}
