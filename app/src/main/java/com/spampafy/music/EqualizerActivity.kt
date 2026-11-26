package com.spampafy.music

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EqualizerActivity : AppCompatActivity() {

    private val bgColor = Color.rgb(7, 10, 8)
    private val panel = Color.rgb(19, 25, 21)
    private val panelSelected = Color.rgb(20, 65, 38)
    private val green = Color.rgb(57, 255, 120)
    private val greenDark = Color.rgb(25, 150, 75)
    private val white = Color.rgb(245, 250, 246)
    private val muted = Color.rgb(165, 180, 170)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
            if (strokeWidth > 0) {
                setStroke(dp(strokeWidth), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(22))
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(bgColor)
            setPadding(0, 0, 0, dp(8))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "♫  ECUALIZADOR"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(-1, dp(62)))

        root.addView(TextView(this).apply {
            text = "PERSONALIZÁ TU SONIDO"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(22))
        })

        if (!EqualizerEngine.isAvailable()) {
            addMessage(
                "El ecualizador todavía no está disponible.\n\n" +
                    "Reproducí una canción y volvé a entrar."
            )
            addBackButton()
            return
        }

        addPowerButton()
        addPresets()
        addBands()
        addBackButton()
    }

    private fun addPowerButton() {
        val enabled = EqualizerEngine.isEnabled()

        addButton(
            if (enabled) "●  ECUALIZADOR ACTIVADO" else "○  ECUALIZADOR DESACTIVADO",
            selected = enabled
        ) {
            EqualizerEngine.setEnabled(!EqualizerEngine.isEnabled())
            render()
        }
    }

    private fun addPresets() {
        addSection("PERFILES DE SONIDO")

        val presets = EqualizerEngine.presetNames()
        val selected = EqualizerEngine.currentPreset()

        if (presets.isEmpty()) {
            addMessage("No hay perfiles disponibles.")
            return
        }

        presets.forEachIndexed { index, name ->
            addButton(
                if (index == selected) "✓  $name" else name,
                selected = index == selected
            ) {
                EqualizerEngine.setPreset(index.toShort())

                Toast.makeText(
                    this,
                    "$name aplicado",
                    Toast.LENGTH_SHORT
                ).show()

                render()
            }
        }
    }

    private fun addBands() {
        addSection("AJUSTE MANUAL")

        val bands = EqualizerEngine.bands()

        if (bands.isEmpty()) {
            addMessage("No se encontraron bandas de sonido.")
            return
        }

        bands.forEach { band ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(12))
                background = roundedBackground(panel, Color.rgb(37, 54, 43), 1)
            }

            val frequency = if (band.frequencyHz >= 1000) {
                String.format(
                    Locale.getDefault(),
                    "%.1f kHz",
                    band.frequencyHz / 1000f
                )
            } else {
                "${band.frequencyHz} Hz"
            }

            val title = TextView(this).apply {
                text = frequency
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(green)
            }

            val level = TextView(this).apply {
                text = formatLevel(band.level)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            val seek = SeekBar(this).apply {
                max = (band.maxLevel - band.minLevel).coerceAtLeast(1)
                progress = (band.level - band.minLevel).coerceIn(0, max)
                progressTintList = ColorStateList.valueOf(green)
                thumbTintList = ColorStateList.valueOf(green)
                progressBackgroundTintList = ColorStateList.valueOf(
                    Color.rgb(55, 75, 61)
                )

                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (fromUser) {
                                val value = band.minLevel + progress
                                EqualizerEngine.setBandLevel(band.index, value)
                                level.text = formatLevel(value)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    }
                )
            }

            container.addView(title)
            container.addView(seek)
            container.addView(level)

            root.addView(
                container,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                }
            )
        }
    }

    private fun formatLevel(value: Int): String {
        return String.format(
            Locale.getDefault(),
            "%+.1f dB",
            value / 100f
        )
    }

    private fun addSection(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            letterSpacing = 0.08f
            setPadding(dp(2), dp(22), dp(2), dp(12))
        })
    }

    private fun addMessage(message: String) {
        root.addView(TextView(this).apply {
            text = message
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(18), dp(25), dp(18), dp(25))
            background = roundedBackground(panel)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
    }

    private fun addButton(
        label: String,
        selected: Boolean = false,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            isSingleLine = false
            maxLines = 2
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minHeight = dp(54)
            minimumHeight = dp(54)
            setTextColor(if (selected) Color.BLACK else white)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(
                if (selected) green else panel,
                if (selected) green else Color.rgb(42, 60, 47),
                1
            )
            stateListAnimator = null
            setOnClickListener { action() }
        }

        root.addView(
            button,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(9)
            }
        )
    }

    private fun addBackButton() {
        addButton("←  VOLVER") {
            finish()
        }
    }
}
