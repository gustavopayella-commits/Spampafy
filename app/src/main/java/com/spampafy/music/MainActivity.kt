
package com.spampafy.music

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var songTitle: TextView
    private lateinit var playButton: Button
    private lateinit var conversionStatus: TextView
    private lateinit var qualitySpinner: Spinner

    private var selectedUri: Uri? = null

    private val selectMusic =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                selectedUri = it
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

        root.addView(logo, LinearLayout.LayoutParams(-1, 65))

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
            setPadding(0, 35, 0, 20)
        }
        root.addView(section)

        val album = TextView(this).apply {
            text = "♫"
            textSize = 100f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(139, 92, 246))
            setBackgroundColor(Color.rgb(32, 27, 49))
        }
        root.addView(album, LinearLayout.LayoutParams(-1, 180))

        songTitle = TextView(this).apply {
            text = "Ninguna canción seleccionada"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }
        root.addView(songTitle)

        playButton = Button(this).apply {
            text = "▶  REPRODUCIR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(109, 40, 217))
            setOnClickListener { togglePlayback() }
        }
        root.addView(playButton, LinearLayout.LayoutParams(-1, 55))

        val libraryButton = Button(this).apply {
            text = "＋  AGREGAR MÚSICA"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 35, 49))
            setOnClickListener {
                selectMusic.launch(arrayOf("audio/*"))
            }
        }
        root.addView(libraryButton, LinearLayout.LayoutParams(-1, 50))

        val separator = TextView(this).apply {
            text = "━━━━━━━━━━━━━━━━━━━━"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(109, 40, 217))
            setPadding(0, 18, 0, 8)
        }
        root.addView(separator)

        val converterTitle = TextView(this).apply {
            text = "CONVERTIR A OPUS"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(167, 139, 250))
        }
        root.addView(converterTitle)

        val qualityLabel = TextView(this).apply {
            text = "Calidad de audio"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 5)
        }
        root.addView(qualityLabel)

        qualitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("32 kbps", "48 kbps", "64 kbps", "96 kbps")
            )
        }
        root.addView(qualitySpinner)

        val convertButton = Button(this).apply {
            text = "CONVERTIR A OPUS"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(109, 40, 217))
            setOnClickListener { convertSelectedMusic(this) }
        }

        root.addView(
            convertButton,
            LinearLayout.LayoutParams(-1, 55)
        )

        conversionStatus = TextView(this).apply {
            text = "Seleccioná una canción para convertir"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        root.addView(conversionStatus)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun convertSelectedMusic(button: Button) {
        val uri = selectedUri

        if (uri == null) {
            Toast.makeText(
                this,
                "Primero seleccioná una canción",
                Toast.LENGTH_SHORT
            ).show()
            selectMusic.launch(arrayOf("audio/*"))
            return
        }

        val bitrates = listOf(32000, 48000, 64000, 96000)
        val bitrate = bitrates[qualitySpinner.selectedItemPosition]

        button.isEnabled = false
        conversionStatus.text = "Preparando conversión..."

        executor.execute {
            try {
                val output = PcmToOpus(this).convert(
                    uri,
                    bitrate
                ) { message ->
                    runOnUiThread {
                        conversionStatus.text = message
                    }
                }

                runOnUiThread {
                    conversionStatus.text =
                        "¡Conversión terminada!\n${output.absolutePath}"
                    button.isEnabled = true
                    Toast.makeText(
                        this,
                        "Archivo Opus creado",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    conversionStatus.text =
                        "Error: ${e.message ?: "No se pudo convertir"}"
                    button.isEnabled = true
                }
            }
        }
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
        executor.shutdown()
        super.onDestroy()
    }
}
