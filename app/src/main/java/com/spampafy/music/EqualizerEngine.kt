
package com.spampafy.music

import android.media.audiofx.Equalizer

object EqualizerEngine {

    private var equalizer: Equalizer? = null

    fun initialize(audioSessionId: Int) {
        release()

        if (audioSessionId <= 0) return

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            equalizer = null
        }
    }

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()

        return try {
            (0 until eq.numberOfPresets.toInt()).map {
                eq.getPresetName(it.toShort()).toString()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setPreset(index: Short) {
        try {
            equalizer?.usePreset(index)
            equalizer?.enabled = true
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }

        equalizer = null
    }
}
