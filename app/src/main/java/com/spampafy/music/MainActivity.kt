
package com.spampafy.music

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    private lateinit var songTitle: TextView
    private lateinit var playButton: Button

    private val selectMusic =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            uri?.let {
                playMusic(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()

        createInterface()
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 35, 24, 24)
            setBackgroundColor(Color.rgb(16, 16, 24))
        }

        val logo = TextView(this).apply {
            text = "♫ SPAMPAFY"
            textSize = 29f
            setTextColor(Color.rgb(167, 139, 250))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(
                -1, 65
            )
        )

        val subtitle = TextView(this).apply {
            text = "Tu música, a tu manera"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        root.addView(subtitle)

        val section = TextView(this).apply {
            text = "Escuchá tu música"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 45, 0, 20)
        }

        root.addView(section)

        val album = TextView(this).apply {
            text = "♫"
            textSize = 100f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(139, 92, 246))
            setBackgroundColor(Color.rgb(32, 27, 49))
        }

        root.addView(
            album,
            LinearLayout.LayoutParams(-1, 220)
        )

        songTitle = TextView(this).apply {
            text = "Ninguna canción seleccionada"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 8)
        }

        root.addView(songTitle)

        val artist = TextView(this).apply {
            text = "Tu biblioteca musical"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        root.addView(artist)

        playButton = Button(this).apply {
            text = "▶  REPRODUCIR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(109, 40, 217))

            setOnClickListener {
                togglePlayback()
            }
        }

        val playParams = LinearLayout.LayoutParams(
            -1, 60
        ).apply {
            setMargins(0, 30, 0, 12)
        }

        root.addView(playButton, playParams)

        val libraryButton = Button(this).apply {
            text = "＋  AGREGAR MÚSICA"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 35, 49))

            setOnClickListener {
                selectMusic.launch(arrayOf("audio/*"))
            }
        }

        root.addView(
            libraryButton,
            LinearLayout.LayoutParams(-1, 55)
        )

        val bottom = TextView(this).apply {
            text = "INICIO       BUSCAR       BIBLIOTECA"
            textSize = 13f
            setTextColor(Color.rgb(0, 194, 255))
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 15)
        }

        root.addView(bottom)

        setContentView(root)
    }

    private fun playMusic(uri: Uri) {

        try {
            player?.apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                play()
            }

            songTitle.text =
                uri.lastPathSegment ?: "Reproduciendo música"

            playButton.text = "Ⅱ  PAUSAR"

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "No se pudo reproducir el archivo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun togglePlayback() {

        val musicPlayer = player ?: return

        if (musicPlayer.mediaItemCount == 0) {
            selectMusic.launch(arrayOf("audio/*"))
            return
        }

        if (musicPlayer.isPlaying) {
            musicPlayer.pause()
            playButton.text = "▶  CONTINUAR"
        } else {
            musicPlayer.play()
            playButton.text = "Ⅱ  PAUSAR"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
