
package com.spampafy.music

import android.media.audiofx.Equalizer

object EqualizerEngine {

    data class BandInfo(
        val index: Int,
        val frequencyHz: Int,
        val level: Int,
        val minLevel: Int,
        val maxLevel: Int
    )

    private var equalizer: Equalizer? = null

    fun initialize(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        release()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Exception) {
            equalizer = null
        }
    }

    fun isAvailable(): Boolean = equalizer != null

    fun isEnabled(): Boolean {
        return try {
            equalizer?.enabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()

        return try {
            (0 until eq.numberOfPresets.toInt()).map {
                eq.getPresetName(it.toShort()).toString()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun currentPreset(): Int {
        return try {
            equalizer?.currentPreset?.toInt() ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    fun setPreset(index: Short) {
        try {
            val eq = equalizer ?: return

            if (index.toInt() in 0 until eq.numberOfPresets.toInt()) {
                eq.usePreset(index)
                eq.enabled = true
            }
        } catch (_: Exception) {
        }
    }

    fun bands(): List<BandInfo> {
        val eq = equalizer ?: return emptyList()

        return try {
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()

            (0 until eq.numberOfBands.toInt()).map { index ->
                val band = index.toShort()

                BandInfo(
                    index = index,
                    frequencyHz = eq.getCenterFreq(band) / 1000,
                    level = eq.getBandLevel(band).toInt(),
                    minLevel = min,
                    maxLevel = max
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setBandLevel(index: Int, level: Int) {
        try {
            val eq = equalizer ?: return

            if (index !in 0 until eq.numberOfBands.toInt()) return

            val range = eq.bandLevelRange
            val value = level.coerceIn(
                range[0].toInt(),
                range[1].toInt()
            )

            eq.setBandLevel(index.toShort(), value.toShort())
            eq.enabled = true
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) {
        }

        equalizer = null
    }
}
