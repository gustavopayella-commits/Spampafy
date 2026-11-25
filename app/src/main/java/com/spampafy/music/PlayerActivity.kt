
package com.spampafy.music

import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class PlayerActivity : AppCompatActivity() {

    private var controller: MediaController? = null

    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playButton: Button
    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val updateProgress = object : Runnable {
        override fun run() {
            updatePlayerInfo()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createScreen()
        connectPlayer()
    }

    private fun createScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(25, 30, 25, 30)
            setBackgroundColor(Color.rgb(16, 16, 24))
        }

        val back = Button(this).apply {
            text = "← VOLVER"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 35, 65))
            setOnClickListener {
                finish()
            }
        }

        root.addView(
            back,
            LinearLayout.LayoutParams(-1, 50)
        )

        val logo = TextView(this).apply {
            text = "♫ SPAMPAFY"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(167, 139, 250))
            setPadding(0, 20, 0, 25)
        }

        root.addView(logo)

        val cover = TextView(this).apply {
            text = "♫"
            textSize = 115f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(167, 139, 250))
            setBackgroundColor(Color.rgb(38, 30, 58))
        }

        root.addView(
            cover,
            LinearLayout.LayoutParams(-1, 280)
        )

        title = TextView(this).apply {
            text = "Seleccioná una canción"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 25, 0, 5)
        }

        root.addView(title)

        artist = TextView(this).apply {
            text = "Spampafy"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        root.addView(artist)

        progress = SeekBar(this).apply {
            max = 1000
            progressTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(167, 139, 250)
                )

            thumbTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.WHITE
                )

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val duration =
                                controller?.duration ?: 0L

                            if (duration > 0) {
                                controller?.seekTo(
                                    duration * value / 1000
                                )
                            }
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        val progressParams =
            LinearLayout.LayoutParams(-1, 45)

        progressParams.topMargin = 20

        root.addView(progress, progressParams)

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTime = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.LTGRAY)
        }

        totalTime = TextView(this).apply {
            text = "0:00"
            gravity = Gravity.END
            setTextColor(Color.LTGRAY)
        }

        times.addView(
            currentTime,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        times.addView(
            totalTime,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(times)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 0)
        }

        val previous = Button(this).apply {
            text = "⏮"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 35, 65))
            setOnClickListener {
                controller?.seekToPreviousMediaItem()
            }
        }

        playButton = Button(this).apply {
            text = "▶"
            textSize = 25f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(109, 40, 217))
            setOnClickListener {
                val player = controller ?: return@setOnClickListener

                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }

                updatePlayerInfo()
            }
        }

        val next = Button(this).apply {
            text = "⏭"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(45, 35, 65))
            setOnClickListener {
                controller?.seekToNextMediaItem()
            }
        }

        controls.addView(
            previous,
            LinearLayout.LayoutParams(75, 60)
        )

        controls.addView(
            playButton,
            LinearLayout.LayoutParams(100, 65)
        )

        controls.addView(
            next,
            LinearLayout.LayoutParams(75, 60)
        )

        root.addView(controls)

        val queueInfo = TextView(this).apply {
            text = "♫  Reproducción en cola"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(167, 139, 250))
            setPadding(0, 30, 0, 0)
        }

        root.addView(queueInfo)

        setContentView(root)
    }

    private fun connectPlayer() {

        val token = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        val future = MediaController.Builder(
            this,
            token
        ).buildAsync()

        future.addListener({

            try {
                controller = future.get()

                controller?.addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            updatePlayerInfo()
                        }

                        override fun onIsPlayingChanged(
                            isPlaying: Boolean
                        ) {
                            updatePlayerInfo()
                        }
                    }
                )

                updatePlayerInfo()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "No se pudo conectar el reproductor",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updatePlayerInfo() {

        val player = controller ?: return

        val metadata = player.mediaMetadata

        title.text =
            metadata.title?.toString()
                ?: "Reproduciendo música"

        artist.text =
            metadata.artist?.toString()
                ?: "Artista no disponible"

        playButton.text =
            if (player.isPlaying) "Ⅱ" else "▶"

        val duration = player.duration
            .takeIf { it != androidx.media3.common.C.TIME_UNSET }
            ?: 0L

        val position = player.currentPosition

        progress.progress =
            if (duration > 0) {
                (position * 1000 / duration)
                    .toInt()
                    .coerceIn(0, 1000)
            } else {
                0
            }

        currentTime.text = formatTime(position)
        totalTime.text = formatTime(duration)
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).coerceAtLeast(0)
        return String.format(
            "%d:%02d",
            seconds / 60,
            seconds % 60
        )
    }

    override fun onStart() {
        super.onStart()
        handler.post(updateProgress)
    }

    override fun onStop() {
        handler.removeCallbacks(updateProgress)
        super.onStop()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
