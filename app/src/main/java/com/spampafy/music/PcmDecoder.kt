
package com.spampafy.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

class PcmDecoder(
    private val context: Context
) {

    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val mime: String
    )

    fun inspect(uri: Uri): AudioInfo {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(context, uri, null)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: continue

                if (mime.startsWith("audio/")) {
                    return AudioInfo(
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                        mime
                    )
                }
            }

            throw IllegalArgumentException("No se encontró audio")

        } finally {
            extractor.release()
        }
    }

    fun decode(
        uri: Uri,
        onFormat: (AudioInfo) -> Unit,
        onPcm: (ShortArray, Int, Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            var audioFormat: MediaFormat? = null
            var audioMime: String? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    audioFormat = format
                    audioMime = mime
                    break
                }
            }

            val format = audioFormat
                ?: throw IllegalArgumentException("No hay pista de audio")

            val mime = audioMime
                ?: throw IllegalArgumentException("Formato desconocido")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var inputFinished = false
            var outputFinished = false

            var sampleRate = format.getInteger(
                MediaFormat.KEY_SAMPLE_RATE
            )

            var channels = format.getInteger(
                MediaFormat.KEY_CHANNEL_COUNT
            )

            onFormat(AudioInfo(sampleRate, channels, mime))

            val info = MediaCodec.BufferInfo()

            while (!outputFinished) {

                if (!inputFinished) {
                    val inputIndex = codec.dequeueInputBuffer(10000)

                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Buffer de entrada vacío")

                        inputBuffer.clear()

                        val size = extractor.readSampleData(inputBuffer, 0)

                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputFinished = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex =
                    codec.dequeueOutputBuffer(info, 10000)) {

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat

                        sampleRate = outputFormat.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE
                        )

                        channels = outputFormat.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT
                        )

                        val encoding = if (
                            outputFormat.containsKey(
                                MediaFormat.KEY_PCM_ENCODING
                            )
                        ) {
                            outputFormat.getInteger(
                                MediaFormat.KEY_PCM_ENCODING
                            )
                        } else {
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        }

                        if (encoding !=
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        ) {
                            throw IllegalStateException(
                                "El decodificador no produjo PCM de 16 bits"
                            )
                        }

                        onFormat(
                            AudioInfo(sampleRate, channels, mime)
                        )
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Esperar más datos.
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuffer =
                                codec.getOutputBuffer(outputIndex)

                            if (outputBuffer != null && info.size > 0) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)

                                val pcmBuffer = outputBuffer.slice()
                                    .order(ByteOrder.LITTLE_ENDIAN)

                                val samples = ShortArray(
                                    pcmBuffer.remaining() / 2
                                )

                                pcmBuffer.asShortBuffer().get(samples)

                                onPcm(
                                    samples,
                                    sampleRate,
                                    channels
                                )
                            }

                            if (
                                info.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            ) {
                                outputFinished = true
                            }

                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }

        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }

            codec?.release()
            extractor.release()
        }
    }
}
