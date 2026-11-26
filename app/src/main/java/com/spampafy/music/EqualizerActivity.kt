
package com.spampafy.music

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EqualizerActivity : AppCompatActivity() {

    private val background = Color.rgb(13, 13, 20)
    private val panel = Color.rgb(29, 27, 43)
    private val violet = Color.rgb(167, 139, 250)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(background)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "🎚  ECUALIZADOR"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(violet)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 65))

        root.addView(TextView(this).apply {
            text = "PERSONALIZÁ TU SONIDO"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
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
            if (enabled) "●  ECUALIZADOR ACTIVADO"
            else "○  ECUALIZADOR DESACTIVADO"
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
                if (index == selected) "✓  $name" else name
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
                setPadding(14, 12, 14, 12)
                setBackgroundColor(panel)
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
                setTextColor(violet)
            }

            val level = TextView(this).apply {
                text = formatLevel(band.level)
                textSize = 13f
                setTextColor(Color.LTGRAY)
            }

            val seek = SeekBar(this).apply {
                max = (band.maxLevel - band.minLevel).coerceAtLeast(1)
                progress = (band.level - band.minLevel)
                    .coerceIn(0, max)

                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (fromUser) {
                                val value = band.minLevel + progress

                                EqualizerEngine.setBandLevel(
                                    band.index,
                                    value
                                )

                                level.text = formatLevel(value)
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

            container.addView(title)
            container.addView(seek)
            container.addView(level)

            root.addView(
                container,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 9
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
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(violet)
            setPadding(0, 22, 0, 12)
        })
    }

    private fun addMessage(message: String) {
        root.addView(TextView(this).apply {
            text = message
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(15, 25, 15, 25)
        })
    }

    private fun addButton(
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 45, 125))
            setOnClickListener { action() }
        }

        root.addView(
            button,
            LinearLayout.LayoutParams(-1, 48).apply {
                bottomMargin = 7
            }
        )
    }

    private fun addBackButton() {
        addButton("←  VOLVER") {
            finish()
        }
    }
}
