package com.meraj.mediaplayer

import android.media.audiofx.Equalizer

/**
 * Android system Equalizer controller for the active audio session.
 *
 * Note: callers in this project use the object name MerajAudioEffects (plural).
 * Attach it to the active player's audioSessionId before requesting presets.
 */
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
            android.util.Log.e(
                "MerajMediaPlayer",
                "Could not attach Equalizer to session $id",
                e
            )
        }
    }

    @Synchronized
    fun presets(): List<Pair<String, String>> {
        val eq = effect ?: return emptyList()
        return try {
            (0 until eq.numberOfPresets).map { i ->
                val name = eq.getPresetName(i.toShort()).toString()
                val key = name.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                key to name
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "MerajMediaPlayer",
                "Could not read Equalizer presets",
                e
            )
            emptyList()
        }
    }

    @Synchronized
    fun apply(key: String): Boolean {
        val eq = effect ?: return false
        return try {
            val idx = (0 until eq.numberOfPresets).firstOrNull { i ->
                eq.getPresetName(i.toShort()).toString().lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-') == key
            } ?: return false

            eq.usePreset(idx.toShort())
            eq.enabled = true
            true
        } catch (e: Exception) {
            android.util.Log.e(
                "MerajMediaPlayer",
                "Could not apply Equalizer preset $key",
                e
            )
            false
        }
    }

    @Synchronized
    fun release() {
        try {
            effect?.release()
        } catch (_: Exception) {
            // Ignore release errors while shutting down the playback service.
        }
        effect = null
        sessionId = 0
    }
}
