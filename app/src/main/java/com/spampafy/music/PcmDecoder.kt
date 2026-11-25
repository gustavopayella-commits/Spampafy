
package com.spampafy.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

data class AudioInfo(
    val sampleRate: Int,
    val channels: Int,
    val mime: String
)

class PcmDecoder(
    private val context: Context
) {

    fun inspect(uri: Uri): AudioInfo {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(
                context,
                uri,
                null
            )

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(
                    MediaFormat.KEY_MIME
                ) ?: continue

                if (mime.startsWith("audio/")) {
                    return AudioInfo(
                        sampleRate = format.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE
                        ),
                        channels = format.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT
                        ),
                        mime = mime
                    )
                }
            }

            throw IllegalArgumentException(
                "No se encontró una pista de audio"
            )

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
            extractor.setDataSource(
                context,
                uri,
                null
            )

            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(
                    MediaFormat.KEY_MIME
                ) ?: continue

                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    audioFormat = format
                    break
                }
            }

            val format = audioFormat
                ?: throw IllegalArgumentException(
                    "No se encontró audio"
                )

            val mime = format.getString(
                MediaFormat.KEY_MIME
            )!!

            val sampleRate = format.getInteger(
                MediaFormat.KEY_SAMPLE_RATE
            )

            val channels = format.getInteger(
                MediaFormat.KEY_CHANNEL_COUNT
            )

            onFormat(
                AudioInfo(sampleRate, channels, mime)
            )

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputFinished = false
            var outputFinished = false

            while (!outputFinished) {

                if (!inputFinished) {
                    val inputIndex = codec.dequeueInputBuffer(10000)

                    if (inputIndex >= 0) {
                        val inputBuffer =
                            codec.getInputBuffer(inputIndex)
                                ?: throw IllegalStateException(
                                    "No se pudo obtener el buffer"
                                )

                        val size = extractor.readSampleData(
                            inputBuffer,
                            0
                        )

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

                val outputIndex = codec.dequeueOutputBuffer(
                    info,
                    10000
                )

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

                        if (samples.isNotEmpty()) {
                            onPcm(
                                samples,
                                sampleRate,
                                channels
                            )
                        }
                    }

                    outputFinished =
                        (info.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                    codec.releaseOutputBuffer(outputIndex, false)
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
