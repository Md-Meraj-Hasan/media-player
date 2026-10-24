package com.meraj.mediaplayer

import android.media.audiofx.Equalizer

internal object MerajAudioEffects {
    @Volatile private var sessionId: Int = 0
    @Volatile private var effect: Equalizer? = null

    @Synchronized
    fun attach(id: Int) {
        try {
            effect?.release()
            effect = null
            sessionId = id
            if (id > 0) {
                effect = Equalizer(0, id).apply { enabled = true }
            }
        } catch (e: Exception) {
            android.util.Log.e("MerajMediaPlayer", "Could not attach Equalizer", e)
        }
    }

    @Synchronized
    fun presets(): List<Pair<String, String>> {
        val eq = effect ?: return emptyList()
        return try {
            (0 until eq.numberOfPresets).map { i ->
                val name = eq.getPresetName(i.toShort()).toString()
                val key = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                key to name
            }
        } catch (e: Exception) { emptyList() }
    }

    @Synchronized
    fun apply(key: String): Boolean {
        val eq = effect ?: return false
        return try {
            val idx = (0 until eq.numberOfPresets).firstOrNull { i ->
                eq.getPresetName(i.toShort()).toString().lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-").trim('-') == key
            } ?: return false
            eq.usePreset(idx.toShort())
            eq.enabled = true
            true
        } catch (e: Exception) { false }
    }

    @Synchronized
    fun release() {
        try { effect?.release() } catch (_: Exception) {}
        effect = null
        sessionId = 0
    }
}
