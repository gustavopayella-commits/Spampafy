
package com.spampafy.music

import android.content.Context
import android.net.Uri
import java.io.File

class OpusConverter(
    private val context: Context
) {

    companion object {
        val BITRATES = listOf(
            32000,
            48000,
            64000,
            96000
        )
    }

    fun getOutputFile(
        originalName: String
    ): File {

        val directory = File(
            context.getExternalFilesDir(null),
            "Spampafy/Converted"
        )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val cleanName = originalName
            .substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        return File(
            directory,
            "$cleanName.opus"
        )
    }
}
