package com.spampafy.music

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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

    private val handler = Handler(Looper.getMainLooper())

    private val backgroundColor = Color.rgb(7, 11, 9)
    private val cardColor = Color.rgb(17, 25, 20)
    private val green = Color.rgb(57, 255, 120)
    private val mutedGreen = Color.rgb(25, 75, 45)
    private val secondaryText = Color.rgb(165, 180, 170)

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        color: Int,
        radius: Int = 16,
        strokeColor: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun styleButton(
        label: String,
        background: Int,
        textSize: Float = 15f
    ): Button {
        return Button(this).apply {
            text = label
            this.textSize = textSize
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), dp(8), dp(12), dp(8))
            stateListAnimator = null
            background = roundedBackground(background, 14)
        }
    }

    private fun createScreen() {
        val scroll = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(backgroundColor)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(backgroundColor)
        }

        val back = styleButton("‹  VOLVER", cardColor).apply {
            textSize = 14f
            setTextColor(green)
            gravity = Gravity.CENTER
            background = roundedBackground(cardColor, 14, mutedGreen)
            setOnClickListener { finish() }
        }
        root.addView(
            back,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(22)
            }
        )

        val logo = TextView(this).apply {
            text = "♫  SPAMPAFY"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(22))
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, -2))

        val coverContainer = FrameLayout(this).apply {
            background = roundedBackground(cardColor, 20, mutedGreen)
            clipToOutline = true
        }

        coverImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        coverPlaceholder = TextView(this).apply {
            text = "♫"
            textSize = 112f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            background = roundedBackground(cardColor, 20)
        }

        coverContainer.addView(
            coverImage,
            FrameLayout.LayoutParams(-1, -1)
        )
        coverContainer.addView(
            coverPlaceholder,
            FrameLayout.LayoutParams(-1, -1)
        )

        root.addView(
            coverContainer,
            LinearLayout.LayoutParams(-1, dp(280)).apply {
                bottomMargin = dp(22)
            }
        )

        title = TextView(this).apply {
            text = "Seleccioná una canción"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            includeFontPadding = false
            maxLines = 2
            setPadding(dp(4), dp(4), dp(4), dp(7))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        artist = TextView(this).apply {
            text = "Spampafy"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(secondaryText)
            includeFontPadding = false
        }
        root.addView(
            artist,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(18)
            }
        )

        progress = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(green)
            progressBackgroundTintList = ColorStateList.valueOf(
                Color.rgb(38, 48, 41)
            )
            thumbTintList = ColorStateList.valueOf(green)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val duration = controller?.duration ?: 0L
                            if (duration > 0) {
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
            LinearLayout.LayoutParams(-1, dp(42))
        )

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTime = TextView(this).apply {
            text = "0:00"
            textSize = 12f
            setTextColor(secondaryText)
        }

        totalTime = TextView(this).apply {
            text = "0:00"
            textSize = 12f
            gravity = Gravity.END
            setTextColor(secondaryText)
        }

        times.addView(currentTime, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(totalTime, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(
            times,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(24)
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val previous = styleButton("⏮", cardColor, 22f).apply {
            setOnClickListener { controller?.seekToPreviousMediaItem() }
        }

        playButton = styleButton("▶", green, 25f).apply {
            setTextColor(Color.rgb(4, 18, 9))
            background = roundedBackground(green, 16)
            setOnClickListener {
                val player = controller ?: return@setOnClickListener
                if (player.isPlaying) player.pause() else player.play()
                updatePlayerInfo()
            }
        }

        val next = styleButton("⏭", cardColor, 22f).apply {
            setOnClickListener { controller?.seekToNextMediaItem() }
        }

        controls.addView(
            previous,
            LinearLayout.LayoutParams(dp(72), dp(58)).apply {
                marginEnd = dp(7)
            }
        )
        controls.addView(
            playButton,
            LinearLayout.LayoutParams(dp(94), dp(62)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )
        controls.addView(
            next,
            LinearLayout.LayoutParams(dp(72), dp(58)).apply {
                marginStart = dp(7)
            }
        )
        root.addView(
            controls,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(24)
            }
        )

        val queueInfo = TextView(this).apply {
            text = "♫  Reproducción en cola"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(green)
            includeFontPadding = false
            setPadding(0, dp(5), 0, dp(12))
        }
        root.addView(queueInfo, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(
            root,
            ScrollView.LayoutParams(-1, -2)
        )
        setContentView(scroll)
    }

    private fun connectPlayer() {
        val token = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        val future = MediaController.Builder(this, token).buildAsync()

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

        progress.progress = if (duration > 0) {
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
            coverImage.visibility = View.GONE
            coverPlaceholder.visibility = View.VISIBLE
            lastArtworkData = null
            return
        }

        if (lastArtworkData?.contentEquals(artworkData) == true) return

        try {
            val bitmap = BitmapFactory.decodeByteArray(
                artworkData,
                0,
                artworkData.size
            )

            if (bitmap != null) {
                coverImage.setImageBitmap(bitmap)
                coverImage.visibility = View.VISIBLE
                coverPlaceholder.visibility = View.GONE
                lastArtworkData = artworkData
            } else {
                coverImage.setImageDrawable(null)
                coverImage.visibility = View.GONE
                coverPlaceholder.visibility = View.VISIBLE
                lastArtworkData = artworkData
            }
        } catch (e: Exception) {
            coverImage.setImageDrawable(null)
            coverImage.visibility = View.GONE
            coverPlaceholder.visibility = View.VISIBLE
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
