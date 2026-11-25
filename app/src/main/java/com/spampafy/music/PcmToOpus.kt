
package com.spampafy.music

import android.content.Context
import android.net.Uri
import io.element.android.opusencoder.OggOpusEncoder
import io.element.android.opusencoder.configuration.SampleRate
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt

class PcmToOpus(
    private val context: Context
) {

    companion object {
        val BITRATES = listOf(32000, 48000, 64000, 96000)
    }

    private val decoder = PcmDecoder(context)

    // Remuestreo lineal por bloques, sin cargar toda la canción.
    private class StreamingResampler(
        inputRate: Int,
        private val outputRate: Int
    ) {
        private val step = inputRate.toDouble() / outputRate
        private var position = 0.0
        private var buffer = ShortArray(0)

        init {
            require(inputRate > 0) {
                "Frecuencia de audio no válida"
            }
        }

        fun process(
            input: ShortArray,
            onOutput: (ShortArray) -> Unit
        ) {
            if (input.isEmpty()) return

            val combined = ShortArray(
                buffer.size + input.size
            )

            buffer.copyInto(combined)
            input.copyInto(combined, buffer.size)

            val output = ShortArray(
                (combined.size / step + 2).toInt()
            )

            var count = 0

            while (position + 1.0 < combined.size) {
                val index = floor(position).toInt()
                val fraction = position - index

                val first = combined[index].toDouble()
                val second = combined[index + 1].toDouble()

                val value = (
                    first + (second - first) * fraction
                ).roundToInt().coerceIn(-32768, 32767)

                output[count++] = value.toShort()
                position += step
            }

            val discard = floor(position)
                .toInt()
                .coerceAtMost(combined.size - 1)

            buffer = combined.copyOfRange(
                discard,
                combined.size
            )

            position -= discard

            if (count > 0) {
                onOutput(output.copyOf(count))
            }
        }

        fun finish(onOutput: (ShortArray) -> Unit) {
            if (buffer.isEmpty()) return

            val output = ArrayList<Short>()

            while (position < buffer.size) {
                val index = floor(position)
                    .toInt()
                    .coerceIn(0, buffer.lastIndex)

                val next = (index + 1)
                    .coerceAtMost(buffer.lastIndex)

                val fraction = position - index

                val first = buffer[index].toDouble()
                val second = buffer[next].toDouble()

                val value = (
                    first + (second - first) * fraction
                ).roundToInt().coerceIn(-32768, 32767)

                output.add(value.toShort())
                position += step
            }

            if (output.isNotEmpty()) {
                onOutput(
                    ShortArray(output.size) { output[it] }
                )
            }

            buffer = ShortArray(0)
        }
    }

    fun convert(
        uri: Uri,
        bitrate: Int,
        onProgress: (String) -> Unit = {}
    ): File {

        require(bitrate in BITRATES) {
            "Calidad Opus no válida"
        }

        val inputInfo = decoder.inspect(uri)

        require(inputInfo.sampleRate > 0) {
            "Frecuencia de audio no válida"
        }

        require(inputInfo.channels in 1..2) {
            "Solo se admite audio mono o estéreo"
        }

        val outputDirectory = File(
            context.getExternalFilesDir(null),
            "Spampafy/Converted"
        )

        if (!outputDirectory.exists() &&
            !outputDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "No se pudo crear la carpeta de destino"
            )
        }

        val originalName = uri.lastPathSegment
            ?.substringAfterLast("/")
            ?.substringBeforeLast(".")
            ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            ?.take(80)
            ?.ifBlank { "musica" }
            ?: "musica"

        val outputFile = File(
            outputDirectory,
            "${originalName}_${bitrate / 1000}kbps.opus"
        )

        var encoder: OggOpusEncoder? = null

        try {
            onProgress("Preparando conversión...")

            val activeEncoder = OggOpusEncoder.create()
            encoder = activeEncoder

            val initResult = activeEncoder.init(
                outputFile.absolutePath,
                SampleRate.Rate48kHz
            )

            check(initResult == 0) {
                "No se pudo iniciar Opus: $initResult"
            }

            val bitrateResult =
                activeEncoder.setBitrate(bitrate)

            check(bitrateResult == 0) {
                "No se pudo configurar la calidad Opus"
            }

            val resampler = StreamingResampler(
                inputRate = inputInfo.sampleRate,
                outputRate = 48000
            )

            fun encodeBlock(samples: ShortArray) {
                if (samples.isEmpty()) return

                val result = activeEncoder.encode(
                    samples,
                    samples.size
                )

                check(result == 0) {
                    "Error al codificar audio: $result"
                }
            }

            onProgress("Convirtiendo audio...")

            decoder.decode(
                uri,
                onFormat = { info ->
                    check(info.channels in 1..2) {
                        "Cantidad de canales no compatible"
                    }

                    check(info.sampleRate == inputInfo.sampleRate) {
                        "Frecuencia inesperada"
                    }
                },
                onPcm = { samples, rate, channels ->

                    check(rate == inputInfo.sampleRate) {
                        "Frecuencia inesperada"
                    }

                    val mono = when (channels) {
                        1 -> samples

                        2 -> {
                            check(samples.size % 2 == 0) {
                                "Muestras estéreo incompletas"
                            }

                            ShortArray(samples.size / 2) { index ->
                                val left =
                                    samples[index * 2].toInt()

                                val right =
                                    samples[index * 2 + 1].toInt()

                                ((left + right) / 2).toShort()
                            }
                        }

                        else -> throw IllegalArgumentException(
                            "Solo se admite audio mono o estéreo"
                        )
                    }

                    resampler.process(mono) { converted ->
                        encodeBlock(converted)
                    }
                }
            )

            onProgress("Finalizando archivo...")

            resampler.finish { converted ->
                encodeBlock(converted)
            }

            check(
                outputFile.exists() &&
                outputFile.length() > 0
            ) {
                "No se generó el archivo Opus"
            }

            onProgress("Conversión terminada")

            return outputFile

        } catch (e: Exception) {
            outputFile.delete()
            throw e

        } finally {
            encoder?.release()
        }
    }
}
