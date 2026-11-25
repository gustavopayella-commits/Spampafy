
package com.spampafy.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

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
                "El archivo no contiene una pista de audio"
            )

        } finally {
            extractor.release()
        }
    }
}
