package com.spampafy.music

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
    private lateinit var coverImage: ImageView
    private lateinit var coverPlaceholder: TextView

    private var lastArtworkData: ByteArray? = null

    private val backgroundColor = Color.rgb(7, 10, 8)
    private val panelColor = Color.rgb(20, 27, 22)
    private val green = Color.rgb(57, 255, 120)
    private val darkGreen = Color.rgb(20, 105, 52)
    private val textMuted = Color.rgb(165, 180, 170)

    private val handler = Handler(Looper.getMainLooper())

    private val updateProgress = object : Runnable {
        override fun run() {
            updatePlayerInfo()
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
        connectPlayer()
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(backgroundColor)
        scroll.setFillViewport(true)
        scroll.addView(
            root,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)

        val back = Button(this).apply {
            text = "←  VOLVER"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(panelColor)
            setOnClickListener { finish() }
        }
        root.addView(
            back,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val logo = TextView(this).apply {
            text = "♫  SPAMPAFY"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            letterSpacing = 0.04f
            setPadding(0, dp(18), 0, dp(20))
        }
        root.addView(
            logo,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Portada del álbum
        val coverContainer = FrameLayout(this).apply {
            setBackgroundColor(panelColor)
        }

        coverImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = ImageView.GONE
        }

        coverPlaceholder = TextView(this).apply {
            text = "♫"
            textSize = 112f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            setBackgroundColor(panelColor)
        }

        coverContainer.addView(
            coverImage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        coverContainer.addView(
            coverPlaceholder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            coverContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280)
            ).apply {
                bottomMargin = dp(20)
            }
        )

        title = TextView(this).apply {
            text = "Seleccioná una canción"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            maxLines = 2
            setPadding(dp(4), dp(4), dp(4), dp(5))
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        artist = TextView(this).apply {
            text = "Spampafy"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textMuted)
            maxLines = 1
        }
        root.addView(
            artist,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        progress = SeekBar(this).apply {
            max = 1000
            progressTintList =
                android.content.res.ColorStateList.valueOf(green)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(green)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(54, 75, 60)
                )

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val duration = controller?.duration ?: 0L
                            if (duration > 0L) {
                                controller?.seekTo(duration * value / 1000)
                            }
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        root.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(15)
            }
        )

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTime = TextView(this).apply {
            text = "0:00"
            textSize = 12f
            setTextColor(textMuted)
        }

        totalTime = TextView(this).apply {
            text = "0:00"
            textSize = 12f
            gravity = Gravity.END
            setTextColor(textMuted)
        }

        times.addView(
            currentTime,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        times.addView(
            totalTime,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(
            times,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        }

        val previous = Button(this).apply {
            text = "⏮"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(panelColor)
            setOnClickListener {
                controller?.seekToPreviousMediaItem()
            }
        }

        playButton = Button(this).apply {
            text = "▶"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
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
            setBackgroundColor(panelColor)
            setOnClickListener {
                controller?.seekToNextMediaItem()
            }
        }

        controls.addView(
            previous,
            LinearLayout.LayoutParams(dp(70), dp(58)).apply {
                marginEnd = dp(10)
            }
        )
        controls.addView(
            playButton,
            LinearLayout.LayoutParams(dp(94), dp(64))
        )
        controls.addView(
            next,
            LinearLayout.LayoutParams(dp(70), dp(58)).apply {
                marginStart = dp(10)
            }
        )
        root.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val queueInfo = TextView(this).apply {
            text = "♫  REPRODUCCIÓN EN COLA"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(green)
            setPadding(0, dp(25), 0, dp(8))
        }
        root.addView(
            queueInfo,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
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
                            lastArtworkData = null
                            updatePlayerInfo()
                        }

                        override fun onMediaMetadataChanged(
                            mediaMetadata: MediaMetadata
                        ) {
                            updatePlayerInfo()
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
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

        title.text = metadata.title?.toString() ?: "Reproduciendo música"
        artist.text = metadata.artist?.toString() ?: "Artista no disponible"

        updateArtwork(metadata.artworkData)

        playButton.text = if (player.isPlaying) "Ⅱ" else "▶"

        val duration = player.duration
            .takeIf { it != androidx.media3.common.C.TIME_UNSET }
            ?: 0L

        val position = player.currentPosition

        progress.progress = if (duration > 0L) {
            (position * 1000 / duration).toInt().coerceIn(0, 1000)
        } else {
            0
        }

        currentTime.text = formatTime(position)
        totalTime.text = formatTime(duration)
    }

    private fun updateArtwork(artworkData: ByteArray?) {
        if (artworkData == null || artworkData.isEmpty()) {
            coverImage.setImageDrawable(null)
            coverImage.visibility = ImageView.GONE
            coverPlaceholder.visibility = TextView.VISIBLE
            lastArtworkData = null
            return
        }

        if (lastArtworkData?.contentEquals(artworkData) == true) {
            return
        }

        try {
            val bitmap = BitmapFactory.decodeByteArray(
                artworkData,
                0,
                artworkData.size
            )

            if (bitmap != null) {
                coverImage.setImageBitmap(bitmap)
                coverImage.visibility = ImageView.VISIBLE
                coverPlaceholder.visibility = TextView.GONE
            } else {
                coverImage.setImageDrawable(null)
                coverImage.visibility = ImageView.GONE
                coverPlaceholder.visibility = TextView.VISIBLE
            }
            lastArtworkData = artworkData
        } catch (e: Exception) {
            coverImage.setImageDrawable(null)
            coverImage.visibility = ImageView.GONE
            coverPlaceholder.visibility = TextView.VISIBLE
            lastArtworkData = artworkData
        }
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
        handler.removeCallbacks(updateProgress)
        handler.post(updateProgress)
    }

    override fun onStop() {
        handler.removeCallbacks(updateProgress)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateProgress)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
