
package com.spampafy.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // Paleta Spampafy Premium: negro profundo + verde neón.
    private val bgColor = Color.rgb(7, 10, 8)
    private val panelColor = Color.rgb(19, 26, 21)
    private val cardColor = Color.rgb(25, 34, 27)
    private val greenNeon = Color.rgb(57, 255, 20)
    private val greenDark = Color.rgb(23, 53, 28)
    private val greenBorder = Color.rgb(42, 92, 48)
    private val textMain = Color.WHITE
    private val textSecondary = Color.rgb(167, 176, 168)

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy {
        getSharedPreferences("spampafy_data", MODE_PRIVATE)
    }

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout

    private var currentTab = "Inicio"
    private var allTracks: MutableList<Track> = mutableListOf()
    private var selectedUris: MutableList<Uri> = mutableListOf()
    private var searchQuery = ""
    private var conversionStatus: TextView? = null

    data class Track(
        val uri: Uri,
        val title: String,
        val artist: String,
        val album: String,
        val artwork: ByteArray? = null
    ) {
        fun key() = uri.toString()

        fun mediaItem(): MediaItem {
            val builder = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)

            artwork?.let {
                if (it.isNotEmpty()) {
                    builder.setArtworkData(
                        it,
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER
                    )
                }
            }

            return MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(builder.build())
                .build()
        }
    }

    private val chooseAudio =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }

                    if (selectedUris.none {
                            it.toString() == uri.toString()
                        }) {
                        selectedUris.add(uri)
                    }
                }

                saveSelectedUris()
                loadLibrary()

                Toast.makeText(
                    this,
                    "${uris.size} archivo(s) agregados",
                    Toast.LENGTH_SHORT
                ).show()

                playTracks(
                    uris.mapNotNull { findTrack(it) },
                    0
                )
            }
        }

    private val requestAudioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            loadLibrary()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedUris = loadSelectedUris()

        buildShell()
        connectPlayer()
        loadLibrary()
        requestLibraryPermissionIfNeeded()
    }

    private fun requestLibraryPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission.launch(permission)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float = 16f,
        strokeColor: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius

            if (strokeColor != null) {
                setStroke(1, strokeColor)
            }
        }
    }

    // =========================================================
    // DISEÑO PRINCIPAL
    // =========================================================

    private fun buildShell() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 5, 4))
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                0,
                bars.top,
                0,
                bars.bottom
            )

            insets
        }

        // Encabezado
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 5, 20, 5)
            setBackgroundColor(Color.rgb(3, 5, 4))
        }

        val logo = TextView(this).apply {
            text = "♫"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(greenNeon)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }

        val brand = TextView(this).apply {
            text = "SPAMPAFY"
            textSize = 25f
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            letterSpacing = 0.12f
            setTextColor(greenNeon)
            includeFontPadding = false
            setShadowLayer(18f, 0f, 0f, greenNeon)
            setPadding(10, 0, 0, 0)
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(43, -2)
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val status = TextView(this).apply {
            text = "●"
            textSize = 13f
            setTextColor(greenNeon)
            gravity = Gravity.CENTER
        }

        header.addView(status)

        root.addView(
            header,
            LinearLayout.LayoutParams(-1, 65)
        )

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 20)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(8, 13, 9))
            setPadding(5, 7, 5, 7)
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(-1, 70)
        )

        setContentView(root)

        ViewCompat.requestApplyInsets(root)

        renderNavigation()
        showTab("Inicio")
    }

    private fun renderNavigation() {
        nav.removeAllViews()

        listOf(
            "Inicio" to "⌂",
            "Buscar" to "⌕",
            "Biblioteca" to "▤",
            "Favoritos" to "♥"
        ).forEach { (label, symbol) ->

            val selected = currentTab == label

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(3, 5, 3, 5)

                if (selected) {
                    background = roundedBackground(
                        greenDark,
                        15f,
                        greenNeon
                    )
                }
            }

            val icon = TextView(this).apply {
                text = symbol
                textSize = 22f
                gravity = Gravity.CENTER
                includeFontPadding = false

                setTextColor(
                    if (selected) greenNeon
                    else textSecondary
                )
            }

            val name = TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                includeFontPadding = false

                typeface = if (selected) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }

                setTextColor(
                    if (selected) greenNeon
                    else textSecondary
                )
            }

            item.addView(icon)
            item.addView(name)

            item.setOnClickListener {
                showTab(label)
            }

            nav.addView(
                item,
                LinearLayout.LayoutParams(0, -1, 1f)
            )
        }
    }

    private fun showTab(tab: String) {
        currentTab = tab

        renderNavigation()
        content.removeAllViews()

        when (tab) {
            "Inicio" -> showHome()
            "Buscar" -> showSearch()
            "Biblioteca" -> showLibrary()
            "Favoritos" -> showFavorites()
        }
    }

    private fun heading(text: String) {
        content.addView(
            TextView(this).apply {
                this.text = text
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(greenNeon)
                setPadding(0, 14, 0, 13)
            }
        )
    }

    private fun subtitle(text: String) {
        content.addView(
            TextView(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(textSecondary)
                setPadding(0, 0, 0, 12)
            }
        )
    }

    private fun actionButton(
        label: String,
        action: () -> Unit
    ) {
        val button = TextView(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.BLACK)
            setPadding(14, 12, 14, 12)
            background = roundedBackground(greenNeon, 16f)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(-1, 55).apply {
                bottomMargin = 11
            }
        )
    }

    // =========================================================
    // NUEVA PANTALLA DE INICIO SPAMPAFY
    // =========================================================

    private fun showHome() {

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 22)
        }

        val heroIcon = TextView(this).apply {
            text = "🎧"
            textSize = 65f
            gravity = Gravity.CENTER
            setTextColor(greenNeon)
            setShadowLayer(25f, 0f, 0f, greenNeon)
        }

        val heroTitle = TextView(this).apply {
            text = "SPAMPAFY"
            textSize = 34f
            gravity = Gravity.CENTER
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            letterSpacing = 0.08f
            setTextColor(greenNeon)
            setShadowLayer(20f, 0f, 0f, greenNeon)
        }

        val heroSubtitle = TextView(this).apply {
            text = "MÚSICA  •  VIDEOS  •  SIN LÍMITES"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            setTextColor(textSecondary)
            setPadding(0, 7, 0, 5)
        }

        hero.addView(heroIcon)
        hero.addView(heroTitle)
        hero.addView(heroSubtitle)

        content.addView(hero)

        val divider = TextView(this).apply {
            text = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(greenNeon)
            setPadding(0, 0, 0, 17)
        }

        content.addView(divider)

        val count = TextView(this).apply {
            text = "♫   ${allTracks.size} canciones en tu biblioteca"
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(greenNeon)
            setPadding(16, 13, 16, 13)

            background = roundedBackground(
                Color.rgb(9, 18, 11),
                15f,
                greenBorder
            )
        }

        content.addView(
            count,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 18
            }
        )

        // Accesos principales
        homeActionCard(
            "♫",
            "MÚSICA LOCAL",
            "Tus canciones",
            greenNeon
        ) {
            showTab("Biblioteca")
        }

        homeActionCard(
            "▶",
            "YOUTUBE",
            "Buscá y reproducí videos",
            Color.RED
        ) {
            startActivity(
                Intent(
                    this,
                    YouTubeSearchActivity::class.java
                )
            )
        }

        homeActionCard(
            "▤",
            "BIBLIOTECA OPUS",
            "Tus archivos convertidos",
            greenNeon
        ) {
            showOpusLibrary()
        }

        val toolsTitle = TextView(this).apply {
            text = "HERRAMIENTAS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(greenNeon)
            setPadding(3, 18, 0, 12)
        }

        content.addView(toolsTitle)

        actionButton("＋  Agregar música") {
            chooseAudio.launch(arrayOf("audio/*"))
        }

        actionButton("▶  Reproducir toda mi música") {
            playTracks(allTracks, 0)
        }

        actionButton("♫  Cola de reproducción") {
            showQueue()
        }

        actionButton("🎚  Ecualizador") {
            startActivity(
                Intent(this, EqualizerActivity::class.java)
            )
        }

        actionButton("⇄  Convertir a OPUS") {
            showConverter()
        }

        // Reproductor compacto
        addCompactPlayer()

        val recentTitle = TextView(this).apply {
            text = "TU MÚSICA"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
            setTextColor(greenNeon)
            setPadding(3, 22, 0, 12)
        }

        content.addView(recentTitle)

        allTracks.take(5).forEach {
            addTrackRow(it)
        }

        if (allTracks.isEmpty()) {
            emptyMessage(
                "Todavía no hay canciones. Agregá música para comenzar."
            )
        }
    }

    private fun homeActionCard(
        symbol: String,
        title: String,
        description: String,
        iconColor: Int,
        action: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(17, 17, 15, 17)

            background = roundedBackground(
                Color.rgb(4, 10, 6),
                22f,
                greenNeon
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }

        val icon = TextView(this).apply {
            text = symbol
            textSize = 36f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(iconColor)
            setShadowLayer(15f, 0f, 0f, iconColor)
        }

        card.addView(
            icon,
            LinearLayout.LayoutParams(75, 65)
        )

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 5, 0)
        }

        labels.addView(
            TextView(this).apply {
                text = title
                textSize = 17f
                typeface = Typeface.create(
                    "sans-serif-black",
                    Typeface.BOLD
                )
                setTextColor(textMain)
                maxLines = 1
            }
        )

        labels.addView(
            TextView(this).apply {
                text = description
                textSize = 13f
                setTextColor(textSecondary)
                setPadding(0, 5, 0, 0)
                maxLines = 1
            }
        )

        card.addView(
            labels,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        card.addView(
            TextView(this).apply {
                text = "›"
                textSize = 37f
                setTextColor(greenNeon)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(30, -1)
        )

        content.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 15
            }
        )
    }

    private fun addCompactPlayer() {
        val player = controller
        val current = player?.currentMediaItem
        val metadata = current?.mediaMetadata

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 14, 15, 13)

            background = roundedBackground(
                Color.rgb(8, 15, 10),
                18f,
                greenBorder
            )
        }

        card.addView(
            TextView(this).apply {
                text = "EN REPRODUCCIÓN"
                textSize = 11f
                letterSpacing = 0.12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(greenNeon)
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 8)
        }

        val musicIcon = TextView(this).apply {
            text = "♫"
            textSize = 35f
            gravity = Gravity.CENTER
            setTextColor(greenNeon)
            setShadowLayer(12f, 0f, 0f, greenNeon)
        }

        info.addView(
            musicIcon,
            LinearLayout.LayoutParams(55, 55)
        )

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 0, 5, 0)
        }

        labels.addView(
            TextView(this).apply {
                text = metadata?.title?.toString()
                    ?: "Nada reproduciéndose"

                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMain)
                maxLines = 1
            }
        )

        labels.addView(
            TextView(this).apply {
                text = metadata?.artist?.toString()
                    ?: "Elegí una canción para comenzar"

                textSize = 12f
                setTextColor(textSecondary)
                maxLines = 1
            }
        )

        info.addView(
            labels,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val previous = TextView(this).apply {
            text = "◀"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            setPadding(8, 8, 8, 8)

            setOnClickListener {
                controller?.seekToPreviousMediaItem()
                showTab("Inicio")
            }
        }

        val playPause = TextView(this).apply {
            text = if (player?.isPlaying == true) "Ⅱ" else "▶"
            textSize = 23f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)

            background = roundedBackground(
                greenNeon,
                30f
            )

            setOnClickListener {
                val p = controller

                if (p != null) {
                    if (p.isPlaying) {
                        p.pause()
                    } else {
                        p.play()
                    }

                    showTab("Inicio")
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Todavía no hay música reproduciéndose",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val next = TextView(this).apply {
            text = "▶▶"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            setPadding(8, 8, 8, 8)

            setOnClickListener {
                controller?.seekToNextMediaItem()
                showTab("Inicio")
            }
        }

        controls.addView(
            previous,
            LinearLayout.LayoutParams(45, 45)
        )

        controls.addView(
            playPause,
            LinearLayout.LayoutParams(48, 48)
        )

        controls.addView(
            next,
            LinearLayout.LayoutParams(50, 45)
        )

        info.addView(controls)
        card.addView(info)

        val openPlayer = TextView(this).apply {
            text = "ABRIR REPRODUCTOR  ↗"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(greenNeon)
            setPadding(0, 7, 0, 2)

            setOnClickListener {
                if (controller?.currentMediaItem != null) {
                    startActivity(
                        Intent(
                            this@MainActivity,
                            PlayerActivity::class.java
                        )
                    )
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Primero reproducí una canción",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        card.addView(openPlayer)

        content.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 20
                bottomMargin = 8
            }
        )
    }

    private fun showOpusLibrary() {
        content.removeAllViews()

        heading("Biblioteca OPUS")
        subtitle(
            "Tus canciones convertidas y guardadas en formato OPUS."
        )

        actionButton("←  Volver a Inicio") {
            showTab("Inicio")
        }

        val opusTracks = allTracks.filter {
            it.uri.path?.endsWith(".opus", true) == true
        }

        val count = TextView(this).apply {
            text = "${opusTracks.size} archivos OPUS"
            textSize = 13f
            setTextColor(greenNeon)
            setPadding(13, 12, 13, 12)

            background = roundedBackground(
                panelColor,
                14f,
                greenBorder
            )
        }

        content.addView(
            count,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12
            }
        )

        if (opusTracks.isEmpty()) {
            emptyMessage(
                "Todavía no hay archivos OPUS. Convertí una canción para verla acá."
            )
            return
        }

        actionButton("▶  Reproducir toda la biblioteca OPUS") {
            playTracks(opusTracks, 0)
        }

        opusTracks.forEach {
            addTrackRow(it)
        }
    }

    // =========================================================
    // BÚSQUEDA
    // =========================================================

    private fun showSearch() {
        heading("Buscar")
        subtitle("Encontrá canciones, artistas y álbumes.")

        val input = EditText(this).apply {
            hint = "Canción, artista o álbum"
            setHintTextColor(textSecondary)
            setTextColor(textMain)
            textSize = 16f
            setSingleLine(true)
            setPadding(15, 8, 15, 8)

            background = roundedBackground(
                panelColor,
                14f,
                greenBorder
            )
        }

        content.addView(
            input,
            LinearLayout.LayoutParams(-1, 54)
        )

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(results)

        fun update(q: String) {
            results.removeAllViews()

            val matches = allTracks.filter {
                it.title.contains(q, true) ||
                    it.artist.contains(q, true) ||
                    it.album.contains(q, true)
            }

            matches.forEach {
                addTrackRow(it, results)
            }

            if (matches.isEmpty()) {
                addEmpty(
                    results,
                    if (q.isBlank()) {
                        "Escribí para buscar en tu música."
                    } else {
                        "No se encontraron canciones."
                    }
                )
            }
        }

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    searchQuery = s?.toString().orEmpty()
                    update(searchQuery)
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        update("")
    }

    // =========================================================
    // BIBLIOTECA
    // =========================================================

    private fun showLibrary() {
        heading("Tu biblioteca")
        subtitle("Toda tu música en un solo lugar.")

        actionButton("＋  Agregar canciones") {
            chooseAudio.launch(arrayOf("audio/*"))
        }

        actionButton("＋  Crear lista de reproducción") {
            createPlaylistDialog()
        }

        actionButton("♫  Ver cola de reproducción") {
            showQueue()
        }

        actionButton("⇄  Convertir música a OPUS") {
            showConverter()
        }

        heading("Canciones")

        allTracks.forEach {
            addTrackRow(it)
        }

        if (allTracks.isEmpty()) {
            emptyMessage(
                "No hay canciones. Tocá Agregar canciones."
            )
        }

        heading("Tus listas")

        getPlaylists().forEach { playlist ->

            val row = TextView(this).apply {
                text = "♫  ${playlist.first}  (${playlist.second.size})"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMain)
                setPadding(15, 16, 15, 16)

                background = roundedBackground(
                    cardColor,
                    14f,
                    greenBorder
                )

                setOnClickListener {
                    showPlaylist(
                        playlist.first,
                        playlist.second
                    )
                }
            }

            content.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 8
                }
            )
        }
    }

    private fun showFavorites() {
        heading("Tus favoritos ♥")
        subtitle("Las canciones que guardaste.")

        val keys = prefs.getStringSet(
            "favorites",
            emptySet()
        ) ?: emptySet()

        val favorites = allTracks.filter {
            keys.contains(it.key())
        }

        favorites.forEach {
            addTrackRow(it)
        }

        if (favorites.isEmpty()) {
            emptyMessage(
                "Todavía no agregaste favoritos. Usá ♡ en una canción."
            )
        }
    }

    // =========================================================
    // FILAS DE CANCIONES
    // =========================================================

    private fun addTrackRow(
        track: Track,
        parent: LinearLayout = content
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 11, 8, 11)

            background = roundedBackground(
                cardColor,
                14f,
                greenBorder
            )
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        labels.addView(
            TextView(this).apply {
                text = track.title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMain)
                maxLines = 1
            }
        )

        labels.addView(
            TextView(this).apply {
                text = "${track.artist}  •  ${track.album}"
                textSize = 12f
                setTextColor(textSecondary)
                maxLines = 1
            }
        )

        labels.setOnClickListener {
            playTracks(listOf(track), 0)
        }

        row.addView(
            labels,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val favorite = TextView(this).apply {
            textSize = 22f
            setTextColor(greenNeon)
            text = if (isFavorite(track)) "♥" else "♡"
            setPadding(8, 4, 8, 4)

            setOnClickListener {
                toggleFavorite(track)
                text = if (isFavorite(track)) "♥" else "♡"
            }
        }

        row.addView(favorite)

        val more = TextView(this).apply {
            text = "⋮"
            textSize = 24f
            setTextColor(textMain)
            setPadding(10, 2, 8, 2)

            setOnClickListener {
                showTrackOptions(track)
            }
        }

        row.addView(more)

        parent.addView(
            row,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 7
            }
        )
    }

    private fun showTrackOptions(track: Track) {
        val isOpusFile =
            track.uri.scheme == "file" &&
                track.uri.path?.endsWith(".opus", true) == true

        val options = if (isOpusFile) {
            arrayOf(
                "Reproducir",
                "Agregar a favoritos / quitar",
                "Agregar a una lista",
                "Agregar a la cola",
                "Eliminar archivo OPUS"
            )
        } else {
            arrayOf(
                "Reproducir",
                "Agregar a favoritos / quitar",
                "Agregar a una lista",
                "Agregar a la cola"
            )
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(options) { _, which ->

                when (which) {

                    0 -> playTracks(listOf(track), 0)

                    1 -> {
                        toggleFavorite(track)

                        Toast.makeText(
                            this,
                            "Favoritos actualizados",
                            Toast.LENGTH_SHORT
                        ).show()

                        if (currentTab == "Favoritos") {
                            showTab("Favoritos")
                        }
                    }

                    2 -> addTrackToPlaylistDialog(track)

                    3 -> {
                        appendToQueue(track)

                        Toast.makeText(
                            this,
                            "Agregada a la cola",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    4 -> {
                        val file = File(track.uri.path ?: "")

                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Eliminar canción")
                            .setMessage(
                                "¿Querés eliminar ${file.name}?"
                            )
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Eliminar") { _, _ ->

                                if (file.delete()) {
                                    loadLibrary()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "No se pudo eliminar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .show()
                    }
                }
            }
            .show()
    }

    // =========================================================
    // COLA DE REPRODUCCIÓN
    // =========================================================

    private fun showQueue() {
        content.removeAllViews()

        heading("Cola de reproducción")

        val player = controller

        if (player == null || player.mediaItemCount == 0) {
            emptyMessage("No hay canciones en la cola.")
            return
        }

        val current = player.currentMediaItemIndex

        for (i in 0 until player.mediaItemCount) {

            val metadata = player.getMediaItemAt(i).mediaMetadata

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 11, 8, 11)

                background = roundedBackground(
                    if (i == current) greenDark else cardColor,
                    13f,
                    if (i == current) greenNeon else greenBorder
                )
            }

            val name = TextView(this).apply {
                text = (if (i == current) "▶  " else "") +
                    (metadata.title?.toString() ?: "Canción")

                setTextColor(
                    if (i == current) greenNeon else textMain
                )

                typeface = if (i == current) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }

                textSize = 14f
            }

            row.addView(
                name,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            row.addView(
                smallButton("▶") {
                    player.seekToDefaultPosition(i)
                    player.play()
                    showQueue()
                }
            )

            row.addView(
                smallButton("↑") {
                    if (i > 0) {
                        moveQueueItem(i, i - 1)
                        showQueue()
                    }
                }
            )

            row.addView(
                smallButton("↓") {
                    if (i < player.mediaItemCount - 1) {
                        moveQueueItem(i, i + 1)
                        showQueue()
                    }
                }
            )

            content.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 7
                }
            )
        }

        actionButton("Volver a Inicio") {
            showTab("Inicio")
        }
    }

    private fun moveQueueItem(from: Int, to: Int) {
        val p = controller ?: return

        val items = (0 until p.mediaItemCount)
            .map { p.getMediaItemAt(it) }
            .toMutableList()

        val item = items.removeAt(from)
        items.add(to, item)

        val currentUri =
            p.currentMediaItem?.localConfiguration?.uri

        val newIndex = items.indexOfFirst {
            it.localConfiguration?.uri == currentUri
        }.coerceAtLeast(0)

        p.setMediaItems(
            items,
            newIndex,
            p.currentPosition
        )

        p.prepare()
        p.play()
    }

    private fun smallButton(
        label: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(9, 7, 9, 7)
            background = roundedBackground(greenNeon, 10f)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }
    }

    // =========================================================
    // LISTAS DE REPRODUCCIÓN
    // =========================================================

    private fun createPlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Nombre de la lista"
            setTextColor(textMain)
            setHintTextColor(textSecondary)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nueva lista")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear") { _, _ ->

                val name = input.text.toString().trim()

                if (name.isNotEmpty()) {
                    val playlists = readPlaylists()

                    if (!playlists.has(name)) {
                        playlists.put(name, JSONArray())
                    }

                    savePlaylists(playlists)

                    if (currentTab == "Biblioteca") {
                        showTab("Biblioteca")
                    }
                }
            }
            .show()
    }

    private fun addTrackToPlaylistDialog(track: Track) {
        val playlists = readPlaylists()

        val names = (0 until playlists.length()).map {
            playlists.names().getString(it)
        }

        if (names.isEmpty()) {
            createPlaylistDialog()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Agregar a lista")
            .setItems(names.toTypedArray()) { _, which ->

                val name = names[which]

                val arr = playlists.optJSONArray(name)
                    ?: JSONArray()

                if (
                    (0 until arr.length()).none {
                        arr.optString(it) == track.key()
                    }
                ) {
                    arr.put(track.key())
                }

                playlists.put(name, arr)
                savePlaylists(playlists)

                Toast.makeText(
                    this,
                    "Agregada a $name",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showPlaylist(
        name: String,
        keys: List<String>
    ) {
        content.removeAllViews()

        heading(name)

        val tracks = allTracks.filter {
            keys.contains(it.key())
        }

        actionButton("▶  Reproducir lista") {
            playTracks(tracks, 0)
        }

        tracks.forEach {
            addTrackRow(it)
        }

        actionButton("Eliminar lista") {
            val playlists = readPlaylists()
            playlists.remove(name)
            savePlaylists(playlists)
            showTab("Biblioteca")
        }
    }

    private fun getPlaylists(): List<Pair<String, List<String>>> {
        val playlists = readPlaylists()

        return (0 until playlists.length()).mapNotNull { i ->

            val name = playlists.names().optString(i)

            val arr = playlists.optJSONArray(name)
                ?: JSONArray()

            name to (0 until arr.length()).map {
                arr.optString(it)
            }
        }
    }

    private fun readPlaylists(): JSONObject {
        return try {
            JSONObject(
                prefs.getString("playlists", "{}") ?: "{}"
            )
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun savePlaylists(playlists: JSONObject) {
        prefs.edit()
            .putString("playlists", playlists.toString())
            .apply()
    }

    // =========================================================
    // FAVORITOS
    // =========================================================

    private fun isFavorite(track: Track): Boolean {
        return (
            prefs.getStringSet("favorites", emptySet())
                ?: emptySet()
            ).contains(track.key())
    }

    private fun toggleFavorite(track: Track) {
        val keys = (
            prefs.getStringSet("favorites", emptySet())
                ?: emptySet()
            ).toMutableSet()

        if (!keys.add(track.key())) {
            keys.remove(track.key())
        }

        prefs.edit()
            .putStringSet("favorites", keys)
            .apply()
    }

    // =========================================================
    // REPRODUCCIÓN
    // =========================================================

    private fun appendToQueue(track: Track) {
        val p = controller ?: return
        p.addMediaItem(track.mediaItem())
    }

    private fun playTracks(
        tracks: List<Track>,
        start: Int
    ) {
        val p = controller

        if (p == null) {
            Toast.makeText(
                this,
                "Conectando reproductor...",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (tracks.isEmpty()) {
            Toast.makeText(
                this,
                "No hay canciones para reproducir",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val items = tracks.map {
            it.mediaItem()
        }

        p.setMediaItems(
            items,
            start.coerceIn(0, items.lastIndex),
            C.TIME_UNSET
        )

        p.prepare()
        p.play()

        startActivity(
            Intent(this, PlayerActivity::class.java)
        )
    }

    private fun connectPlayer() {
        val token = SessionToken(
            this,
            ComponentName(
                this,
                MusicService::class.java
            )
        )

        val future = MediaController.Builder(
            this,
            token
        ).buildAsync()

        controllerFuture = future

        future.addListener({

            try {
                controller = future.get()

                controller?.addListener(
                    object : Player.Listener {

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            if (currentTab == "Inicio") {
                                showTab("Inicio")
                            }
                        }

                        override fun onIsPlayingChanged(
                            isPlaying: Boolean
                        ) {
                            if (currentTab == "Inicio") {
                                showTab("Inicio")
                            }
                        }
                    }
                )

                if (currentTab == "Inicio") {
                    showTab("Inicio")
                }

            } catch (_: Exception) {

                Toast.makeText(
                    this,
                    "No se pudo conectar el reproductor",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun findTrack(uri: Uri): Track? {
        return allTracks.firstOrNull {
            it.uri.toString() == uri.toString()
        }
    }

    // =========================================================
    // GUARDAR CANCIONES SELECCIONADAS
    // =========================================================

    private fun saveSelectedUris() {
        val array = JSONArray()

        selectedUris.forEach {
            array.put(it.toString())
        }

        prefs.edit()
            .putString("selected_uris", array.toString())
            .apply()
    }

    private fun loadSelectedUris(): MutableList<Uri> {
        val result = mutableListOf<Uri>()

        try {
            val array = JSONArray(
                prefs.getString("selected_uris", "[]") ?: "[]"
            )

            for (i in 0 until array.length()) {

                val uri = Uri.parse(array.getString(i))

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    result.add(uri)

                } catch (_: Exception) {
                    // Se omite si el permiso ya no está disponible.
                }
            }

        } catch (_: Exception) {
        }

        return result
    }

    // =========================================================
    // CARGAR BIBLIOTECA LOCAL Y OPUS
    // =========================================================

    private fun loadLibrary() {
        val tracks = mutableListOf<Track>()

        selectedUris.forEach { uri ->

            try {
                val retriever = MediaMetadataRetriever()

                retriever.setDataSource(this, uri)

                val title = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
                ) ?: getFileName(uri)

                val artist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
                ) ?: "Artista desconocido"

                val album = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM
                ) ?: "Álbum desconocido"

                val artwork = retriever.embeddedPicture

                retriever.release()

                tracks.add(
                    Track(
                        uri,
                        title,
                        artist,
                        album,
                        artwork
                    )
                )

            } catch (_: Exception) {
            }
        }

        val convertedDir = File(
            getExternalFilesDir(null),
            "Spampafy/Converted"
        )

        if (convertedDir.exists()) {

            convertedDir.listFiles()
                ?.filter {
                    it.isFile &&
                        it.extension.equals("opus", true)
                }
                ?.forEach { file ->

                    try {
                        val uri = Uri.fromFile(file)

                        if (tracks.none { it.uri == uri }) {

                            val retriever =
                                MediaMetadataRetriever()

                            retriever.setDataSource(
                                file.absolutePath
                            )

                            val title = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_TITLE
                            ) ?: file.nameWithoutExtension

                            val artist = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_ARTIST
                            ) ?: "Artista desconocido"

                            val album = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_ALBUM
                            ) ?: "OPUS"

                            val artwork = retriever.embeddedPicture

                            retriever.release()

                            tracks.add(
                                Track(
                                    uri,
                                    title,
                                    artist,
                                    album,
                                    artwork
                                )
                            )
                        }

                    } catch (_: Exception) {

                        tracks.add(
                            Track(
                                Uri.fromFile(file),
                                file.nameWithoutExtension,
                                "Artista desconocido",
                                "OPUS"
                            )
                        )
                    }
                }
        }

        allTracks = tracks

        if (::content.isInitialized) {
            showTab(currentTab)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Audio"

        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                    if (index >= 0) {
                        name = cursor.getString(index) ?: name
                    }
                }
            }

        } catch (_: Exception) {
        }

        return name
    }

    // =========================================================
    // MENSAJES VACÍOS
    // =========================================================

    private fun emptyMessage(message: String) {
        addEmpty(content, message)
    }

    private fun addEmpty(
        parent: LinearLayout,
        message: String
    ) {
        val box = TextView(this).apply {
            text = message
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textSecondary)
            setPadding(12, 25, 12, 25)
        }

        parent.addView(
            box,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    // =========================================================
    // CONVERSIÓN OPUS
    // =========================================================

    private fun showConverter() {
        if (allTracks.isEmpty()) {

            Toast.makeText(
                this,
                "Primero agregá música a tu biblioteca",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val tracks = allTracks.toList()
        val names = tracks.map { it.title }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Elegir canción para convertir")
            .setItems(names) { _, selected ->

                val track = tracks[selected]

                val bitrates = PcmToOpus.BITRATES

                val labels = bitrates.map {
                    "${it / 1000} kbps"
                }.toTypedArray()

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Calidad OPUS")
                    .setItems(labels) { _, quality ->
                        convertTrack(
                            track,
                            bitrates[quality]
                        )
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun convertTrack(
        track: Track,
        bitrate: Int
    ) {
        val status = TextView(this).apply {
            text = "Preparando conversión..."
            textSize = 14f
            setTextColor(greenNeon)
            setPadding(8, 12, 8, 12)
        }

        content.addView(status, 0)
        conversionStatus = status

        executor.execute {

            try {
                val converter = PcmToOpus(this)

                val output = converter.convert(
                    track.uri,
                    bitrate
                ) { message ->

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = message
                        }
                    }
                }

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {

                        status.text =
                            "Conversión terminada: ${output.name}"

                        Toast.makeText(
                            this,
                            "Archivo OPUS guardado correctamente",
                            Toast.LENGTH_LONG
                        ).show()

                        loadLibrary()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {

                        status.text =
                            "Error al convertir: ${e.message}"

                        Toast.makeText(
                            this,
                            "No se pudo convertir el audio",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // =========================================================
    // CIERRE
    // =========================================================

    override fun onDestroy() {
        controller?.release()
        controller = null
        controllerFuture = null

        executor.shutdownNow()

        super.onDestroy()
    }
}
