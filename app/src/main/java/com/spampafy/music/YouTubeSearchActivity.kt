
package com.spampafy.music

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class YouTubeSearchActivity : Activity() {

    private val green = Color.rgb(0, 255, 120)
    private val black = Color.rgb(8, 8, 8)
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var container: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var status: TextView

    private var filter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun background(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(18, 20, 18, 12)
        }

        val header = TextView(this).apply {
            text = "SPAMPAFY"
            textSize = 27f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "YOUTUBE MUSIC & VIDEOS"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        root.addView(subtitle)

        searchInput = EditText(this).apply {
            hint = "Buscar canciones o videos..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(16, 12, 16, 12)
            background = background(Color.rgb(28, 28, 28), 24f)
        }

        val searchButton = Button(this).apply {
            text = "BUSCAR"
            setTextColor(Color.BLACK)
            textSize = 12f
            setBackgroundColor(green)
            setOnClickListener {
                search(searchInput.text.toString())
            }
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(searchInput, LinearLayout.LayoutParams(0, 54, 1f))
            addView(searchButton, LinearLayout.LayoutParams(
                105, 54
            ).apply {
                leftMargin = 8
            })
        }

        root.addView(searchRow)

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 15)
        }

        val options = listOf(
            "Todos" to "all",
            "Videos" to "video",
            "Shorts" to "shorts",
            "Música" to "music",
            "En vivo" to "live"
        )

        options.forEach { (label, value) ->
            val button = TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (filter == value) green else Color.WHITE)
                setPadding(9, 10, 9, 10)
                setOnClickListener {
                    filter = value
                    options.forEach { }
                    search(searchInput.text.toString())
                    for (i in 0 until filters.childCount) {
                        val child = filters.getChildAt(i) as TextView
                        child.setTextColor(
                            if (child.text == label) green else Color.WHITE
                        )
                    }
                }
            }
            filters.addView(button)
        }

        root.addView(filters)

        status = TextView(this).apply {
            text = "Buscá tu música favorita"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 8)
        }

        root.addView(status)

        val scroll = ScrollView(this)
        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(
            -1, 0, 1f
        ))

        val back = Button(this).apply {
            text = "VOLVER A SPAMPAFY"
            setTextColor(green)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }

        root.addView(back)

        setContentView(root)
    }

    private fun search(query: String) {
        if (query.isBlank()) {
            status.text = "Escribí qué querés escuchar"
            return
        }

        val apiKey = BuildConfig.YOUTUBE_API_KEY

        if (apiKey.isBlank()) {
            status.text = "Falta configurar la clave API"
            return
        }

        status.text = "Buscando en YouTube..."
        results.removeAllViews()

        executor.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")

                val type = when (filter) {
                    "video" -> "video"
                    "shorts" -> "video"
                    "music" -> "video"
                    "live" -> "video"
                    else -> "video"
                }

                val extra = when (filter) {
                    "music" -> "&videoCategoryId=10"
                    "live" -> "&eventType=live"
                    else -> ""
                }

                val url = URL(
                    "https://www.googleapis.com/youtube/v3/search" +
                    "?part=snippet&type=$type&maxResults=20" +
                    "&q=$encoded$extra&key=$apiKey"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream.bufferedReader().use {
                    it.readText()
                }

                connection.disconnect()

                val json = JSONObject(response)
                val items = json.getJSONArray("items")

                runOnUiThread {
                    if (items.length() == 0) {
                        status.text = "No se encontraron resultados"
                    } else {
                        status.text = "${items.length()} resultados encontrados"
                    }
                }

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getJSONObject("id")
                    val videoId = id.optString("videoId")

                    if (videoId.isBlank()) continue

                    val snippet = item.getJSONObject("snippet")
                    val title = snippet.optString("title")
                    val channel = snippet.optString("channelTitle")
                    val thumbnails = snippet.optJSONObject("thumbnails")
                    val medium = thumbnails?.optJSONObject("medium")
                    val imageUrl = medium?.optString("url") ?: ""

                    runOnUiThread {
                        addResult(videoId, title, channel, imageUrl)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error al buscar. Revisá tu conexión y API."
                }
            }
        }
    }

    private fun addResult(
        videoId: String,
        title: String,
        channel: String,
        imageUrl: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 10, 8, 10)
            background = background(Color.rgb(22, 22, 22), 16f)
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
        }

        row.addView(image, LinearLayout.LayoutParams(115, 78))

        if (imageUrl.isNotBlank()) {
            executor.execute {
                try {
                    val bitmap = URL(imageUrl).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }
                    runOnUiThread {
                        image.setImageBitmap(bitmap)
                    }
                } catch (_: Exception) {}
            }
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 0, 4, 0)
        }

        val name = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 2
            typeface = Typeface.DEFAULT_BOLD
        }

        val artist = TextView(this).apply {
            text = channel
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, 5, 0, 5)
        }

        val play = TextView(this).apply {
            text = "▶ REPRODUCIR"
            textSize = 11f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                openPlayer(videoId, title, channel)
            }
        }

        details.addView(name)
        details.addView(artist)
        details.addView(play)

        row.addView(details, LinearLayout.LayoutParams(
            0, -2, 1f
        ))

        row.setOnClickListener {
            openPlayer(videoId, title, channel)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = 9
        results.addView(row, params)
    }

    private fun openPlayer(
        videoId: String,
        title: String,
        channel: String
    ) {
        val intent = android.content.Intent(
            this,
            YouTubePlayerActivity::class.java
        )
        intent.putExtra("VIDEO_ID", videoId)
        intent.putExtra("VIDEO_TITLE", title)
        intent.putExtra("VIDEO_CHANNEL", channel)
        startActivity(intent)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
