
package com.spampafy.music

import android.content.Context
import android.net.Uri
import io.element.android.opusencoder.OggOpusEncoder
import io.element.android.opusencoder.configuration.SampleRate
import java.io.File

class PcmToOpus(
    private val context: Context
) {

    companion object {
        val BITRATES = listOf(32000, 48000, 64000, 96000)
    }

    private val decoder = PcmDecoder(context)

    fun convert(
        uri: Uri,
        bitrate: Int,
        onProgress: (String) -> Unit = {}
    ): File {

        require(bitrate in BITRATES) {
            "Calidad Opus no válida"
        }

        val inputInfo = decoder.inspect(uri)

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

        val sampleRate = when (inputInfo.sampleRate) {
            48000 -> SampleRate.Rate48kHz
            24000 -> SampleRate.Rate24KHz
            16000 -> SampleRate.Rate16kHz
            12000 -> SampleRate.Rate12kHz
            8000 -> SampleRate.Rate8khz
            else -> throw IllegalArgumentException(
                "Frecuencia no compatible: ${inputInfo.sampleRate} Hz"
            )
        }

        var encoder: OggOpusEncoder? = null

        try {
            onProgress("Preparando conversión...")

            encoder = OggOpusEncoder.create()

            val initResult = encoder.init(
                outputFile.absolutePath,
                sampleRate
            )

            check(initResult == 0) {
                "No se pudo iniciar Opus: $initResult"
            }

            val bitrateResult = encoder.setBitrate(bitrate)

            check(bitrateResult == 0) {
                "No se pudo configurar la calidad Opus"
            }

            onProgress("Convirtiendo audio...")

            decoder.decode(
                uri,
                onFormat = { info ->
                    check(info.channels in 1..2) {
                        "Cantidad de canales no compatible"
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
                                val left = samples[index * 2].toInt()
                                val right = samples[index * 2 + 1].toInt()

                                ((left + right) / 2).toShort()
                            }
                        }

                        else -> throw IllegalArgumentException(
                            "Solo se admite audio mono o estéreo"
                        )
                    }

                    if (mono.isNotEmpty()) {
                        val result = encoder.encode(
                            mono,
                            mono.size
                        )

                        check(result == 0) {
                            "Error al codificar audio: $result"
                        }
                    }
                }
            )

            onProgress("Finalizando archivo...")

            check(outputFile.exists() && outputFile.length() > 0) {
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
