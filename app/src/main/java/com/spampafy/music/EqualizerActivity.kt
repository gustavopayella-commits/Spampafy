
package com.spampafy.music

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EqualizerActivity : AppCompatActivity() {

    private val background = Color.rgb(13, 13, 20)
    private val panel = Color.rgb(29, 27, 43)
    private val violet = Color.rgb(167, 139, 250)
    private val blue = Color.rgb(65, 145, 255)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            render()
        }
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

        addTitle()
        addDescription()

        if (!EqualizerEngine.isAvailable()) {
            addMessage(
                "El ecualizador todavía no está disponible.\n\n" +
                "Iniciá la reproducción de una canción y volvé a entrar."
            )
            addBackButton()
            return
        }

        addPowerSwitch()
        addPresets()
        addBands()
        addBackButton()
    }

    private fun addTitle() {
        root.addView(TextView(this).apply {
            text = "🎚  ECUALIZADOR"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(violet)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 65))
    }

    private fun addDescription() {
        root.addView(TextView(this).apply {
            text = "PERSONALIZÁ TU SONIDO"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })
    }

    private fun addPowerSwitch() {
        val power = Switch(this).apply {
            text = "Ecualizador activado"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = EqualizerEngine.isEnabled()

            thumbTintList = ColorStateList.valueOf(violet)
            trackTintList = ColorStateList.valueOf(blue)

            setOnCheckedChangeListener { _, checked ->
                EqualizerEngine.setEnabled(checked)

                Toast.makeText(
                    this@EqualizerActivity,
                    if (checked) "Ecualizador activado"
                    else "Ecualizador desactivado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            power,
            LinearLayout.LayoutParams(-1, 55)
        )
    }

    private fun addPresets() {
        addSectionTitle("PERFILES DE SONIDO")

        val presets = EqualizerEngine.presetNames()
        val selected = EqualizerEngine.currentPreset()

        if (presets.isEmpty()) {
            addMessage("No hay perfiles predefinidos disponibles.")
            return
        }

        presets.forEachIndexed { index, name ->

            val active = index == selected

            val button = Button(this).apply {
                text = if (active) "✓  $name" else name
                isAllCaps = false
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    if (active) Color.rgb(100, 55, 165)
                    else Color.rgb(48, 40, 68)
                )

                setOnClickListener {
                    EqualizerEngine.setPreset(index.toShort())

                    Toast.makeText(
                        this@EqualizerActivity,
                        "$name aplicado",
                        Toast.LENGTH_SHORT
                    ).show()

                    render()
                }
            }

            root.addView(
                button,
                LinearLayout.LayoutParams(-1, 48).apply {
                    bottomMargin = 6
                }
            )
        }
    }

    private fun addBands() {
        addSectionTitle("AJUSTE MANUAL")

        val bands = EqualizerEngine.bands()

        if (bands.isEmpty()) {
            addMessage("No se pudieron obtener las bandas de sonido.")
            return
        }

        bands.forEach { band ->

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
                setBackgroundColor(panel)
            }

            val frequency = if (band.frequencyHz >= 1000) {
                "${band.frequencyHz / 1000} kHz"
            } else {
                "${band.frequencyHz} Hz"
            }

            val label = TextView(this).apply {
                text = frequency
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(violet)
            }

            val level = TextView(this).apply {
                text = formatLevel(band.level)
                textSize = 12f
                setTextColor(Color.LTGRAY)
            }

            val seek = SeekBar(this).apply {
                max = (band.maxLevel - band.minLevel).coerceAtLeast(1)
                progress = (band.level - band.minLevel)
                    .coerceIn(0, max)

                progressTintList = ColorStateList.valueOf(violet)
                thumbTintList = ColorStateList.valueOf(blue)

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

            container.addView(label)
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

    private fun addSectionTitle(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
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

    private fun addBackButton() {
        root.addView(Button(this).apply {
            text = "←  VOLVER"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 45, 125))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 52).apply {
            topMargin = 24
        })
    }
}
