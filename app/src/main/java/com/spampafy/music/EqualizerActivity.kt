
package com.spampafy.music

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EqualizerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 22)
            setBackgroundColor(Color.rgb(13, 13, 20))
        }

        root.addView(TextView(this).apply {
            text = "🎚  ECUALIZADOR"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(167, 139, 250))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 65))

        root.addView(TextView(this).apply {
            text = "Elegí un perfil de sonido para la música que está reproduciéndose."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 18)
        })

        val presets = EqualizerEngine.presetNames()

        if (presets.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "El ecualizador estará disponible cuando el reproductor esté activo en este dispositivo."
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(8, 20, 8, 20)
            })
        } else {
            presets.forEachIndexed { index, name ->
                val button = Button(this).apply {
                    text = name
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(75, 45, 125))

                    setOnClickListener {
                        EqualizerEngine.setPreset(index.toShort())
                        Toast.makeText(
                            this@EqualizerActivity,
                            "Perfil $name aplicado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                root.addView(
                    button,
                    LinearLayout.LayoutParams(-1, 50).apply {
                        bottomMargin = 7
                    }
                )
            }
        }

        root.addView(Button(this).apply {
            text = "VOLVER"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 50))

        setContentView(root)
    }
}
