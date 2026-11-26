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
import androidx.documentfile.provider.DocumentFile
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
    private var selectedFolders: MutableList<MusicFolder> = mutableListOf()
    private var folderTracks: MutableMap<String, MutableList<Track>> = mutableMapOf()
    private var searchQuery = ""
    private var conversionStatus: TextView? = null

    data class MusicFolder(
        val uri: Uri,
        val name: String
    )

    data class Track(
        val uri: Uri,
        val title: String,
        val artist: String,
        val album: String,
        val artwork: ByteArray? = null,
        val folderName: String = ""
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

    // Selector de canciones individuales.
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

    // Nuevo selector de carpetas de música.
    private val chooseMusicFolder =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    val alreadyAdded = selectedFolders.any {
                        it.uri.toString() == uri.toString()
                    }

                    if (!alreadyAdded) {
                        val documentFolder =
                            DocumentFile.fromTreeUri(this, uri)

                        val folderName =
                            documentFolder?.name ?: "Carpeta de música"

                        selectedFolders.add(
                            MusicFolder(uri, folderName)
                        )

                        saveSelectedFolders()
                        loadLibrary()

                        Toast.makeText(
                            this,
                            "Carpeta agregada: $folderName",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Esta carpeta ya está agregada",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "No se pudo agregar la carpeta",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
        selectedFolders = loadSelectedFolders()

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

        homeActionCard(
            "♫",
            "MÚSICA LOCAL",
            "Tus canciones y carpetas",
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

        actionButton("📁  Agregar carpeta de música") {
            chooseMusicFolder.launch(null)
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
    // CARPETAS DE MÚSICA
    // =========================================================

    private fun addFolderList() {
        heading("Mis carpetas de música")

        actionButton("＋  Agregar carpeta de música") {
            chooseMusicFolder.launch(null)
        }

        if (selectedFolders.isEmpty()) {
            emptyMessage(
                "Todavía no agregaste carpetas. Tocá el botón para elegir una carpeta de música."
            )
            return
        }

        selectedFolders.forEach { folder ->

            val tracks = folderTracks[folder.uri.toString()]
                ?: mutableListOf()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(13, 13, 9, 13)

                background = roundedBackground(
                    cardColor,
                    15f,
                    greenBorder
                )
            }

            val icon = TextView(this).apply {
                text = "📁"
                textSize = 29f
                gravity = Gravity.CENTER
            }

            row.addView(
                icon,
                LinearLayout.LayoutParams(48, 52)
            )

            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 0, 4, 0)
            }

            labels.addView(
                TextView(this).apply {
                    text = folder.name
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textMain)
                    maxLines = 1
                }
            )

            labels.addView(
                TextView(this).apply {
                    text = "${tracks.size} canciones"
                    textSize = 12f
                    setTextColor(greenNeon)
                    setPadding(0, 4, 0, 0)
                }
            )

            labels.setOnClickListener {
                showFolder(folder)
            }

            row.addView(
                labels,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val play = TextView(this).apply {
                text = "▶"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                background = roundedBackground(greenNeon, 25f)
                setPadding(11, 8, 11, 8)

                setOnClickListener {
                    if (tracks.isNotEmpty()) {
                        playTracks(tracks, 0)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Esta carpeta no contiene canciones compatibles",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            row.addView(play)

            val more = TextView(this).apply {
                text = "⋮"
                textSize = 23f
                setTextColor(textMain)
                setPadding(10, 5, 5, 5)

                setOnClickListener {
                    showFolderOptions(folder)
                }
            }

            row.addView(more)

            row.setOnClickListener {
                showFolder(folder)
            }

            content.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 9
                }
            )
        }
    }

    private fun showFolder(folder: MusicFolder) {
        content.removeAllViews()

        val tracks = folderTracks[folder.uri.toString()]
            ?: mutableListOf()

        heading("📁 ${folder.name}")
        subtitle("${tracks.size} canciones encontradas")

        actionButton("←  Volver a mis carpetas") {
            showTab("Biblioteca")
        }

        actionButton("▶  Reproducir toda la carpeta") {
            playTracks(tracks, 0)
        }

        if (tracks.isEmpty()) {
            emptyMessage(
                "No se encontraron canciones en esta carpeta."
            )
            return
        }

        tracks.forEach {
            addTrackRow(it)
        }
    }

    private fun showFolderOptions(folder: MusicFolder) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(
                arrayOf(
                    "Abrir carpeta",
                    "Reproducir carpeta",
                    "Quitar carpeta de Spampafy"
                )
            ) { _, which ->

                when (which) {
                    0 -> showFolder(folder)

                    1 -> {
                        val tracks =
                            folderTracks[folder.uri.toString()]
                                ?: mutableListOf()

                        playTracks(tracks, 0)
                    }

                    2 -> {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Quitar carpeta")
                            .setMessage(
                                "Se quitará de Spampafy, pero no se borrarán las canciones originales del teléfono."
                            )
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Quitar") { _, _ ->

                                selectedFolders.removeAll {
                                    it.uri.toString() ==
                                        folder.uri.toString()
                                }

                                saveSelectedFolders()
                                loadLibrary()
                            }
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
                    it.album.contains(q, true) ||
                    it.folderName.contains(q, true)
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

        actionButton("📁  Agregar carpeta de música") {
            chooseMusicFolder.launch(null)
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

        // En la pantalla principal se muestran solamente las carpetas.
        // Los temas se listan al abrir una carpeta desde addFolderList().
        addFolderList()

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
    // ================= FUNCIONES RECUPERADAS =================

    private fun connectPlayer() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (currentTab == "Inicio") showTab("Inicio")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (currentTab == "Inicio") showTab("Inicio")
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo conectar el reproductor", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveSelectedUris() {
        prefs.edit().putString("selected_uris", JSONArray(selectedUris.map { it.toString() }).toString()).apply()
    }

    private fun loadSelectedUris(): MutableList<Uri> {
        val json = prefs.getString("selected_uris", "[]") ?: "[]"
        return try { val arr = JSONArray(json); MutableList(arr.length()) { Uri.parse(arr.getString(it)) } } catch (_: Exception) { mutableListOf() }
    }

    private fun saveSelectedFolders() {
        val arr = JSONArray()
        selectedFolders.forEach { arr.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name)) }
        prefs.edit().putString("selected_folders", arr.toString()).apply()
    }

    private fun loadSelectedFolders(): MutableList<MusicFolder> {
        val json = prefs.getString("selected_folders", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i -> val obj = arr.getJSONObject(i); MusicFolder(Uri.parse(obj.getString("uri")), obj.optString("name", "Carpeta")) }
        } catch (_: Exception) { mutableListOf() }
    }

    private fun loadLibrary() {
        executor.execute {
            val tracks = LinkedHashMap<String, Track>()
            val folders = mutableMapOf<String, MutableList<Track>>()
            selectedUris.forEach { uri -> runCatching { makeTrack(uri, "") }.getOrNull()?.let { tracks[it.key()] = it } }
            selectedFolders.forEach { folder ->
                val list = mutableListOf<Track>()
                runCatching { scanFolder(DocumentFile.fromTreeUri(this, folder.uri), folder.name, list) }
                list.forEach { tracks[it.key()] = it }
                folders[folder.uri.toString()] = list
            }
            runOnUiThread {
                allTracks = tracks.values.toMutableList()
                folderTracks = folders
                if (currentTab == "Inicio" || currentTab == "Biblioteca" || currentTab == "Favoritos") showTab(currentTab)
            }
        }
    }

    private fun scanFolder(dir: DocumentFile?, folderName: String, out: MutableList<Track>) {
        if (dir == null || !dir.isDirectory) return
        dir.listFiles().forEach { file ->
            if (file.isDirectory) scanFolder(file, folderName, out)
            else if (file.isFile && (file.type?.startsWith("audio/") == true || file.name?.matches(Regex("(?i).*\\.(mp3|m4a|aac|wav|flac|ogg|opus|wma)$")) == true)) {
                runCatching { makeTrack(file.uri, folderName) }.getOrNull()?.let(out::add)
            }
        }
    }

    private fun makeTrack(uri: Uri, folder: String): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: queryDisplayName(uri)?.substringBeforeLast('.') ?: "Audio"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artista desconocido"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Álbum desconocido"
            val art = retriever.embeddedPicture
            Track(uri, title, artist, album, art, folder)
        } catch (_: Exception) { null } finally { runCatching { retriever.release() } }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun findTrack(uri: Uri): Track? = allTracks.firstOrNull { it.uri.toString() == uri.toString() } ?: makeTrack(uri, "")

    private fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) { Toast.makeText(this, "No hay canciones para reproducir", Toast.LENGTH_SHORT).show(); return }
        val c = controller
        if (c == null) { Toast.makeText(this, "El reproductor todavía está iniciando", Toast.LENGTH_SHORT).show(); return }
        c.setMediaItems(tracks.map { it.mediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
        Toast.makeText(this, "Reproduciendo ${tracks[startIndex.coerceIn(0, tracks.lastIndex)].title}", Toast.LENGTH_SHORT).show()
    }

    private fun addTrackRow(track: Track, target: LinearLayout = content) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 10, 8, 10); background = roundedBackground(cardColor, 14f, greenBorder) }
        val icon = TextView(this).apply { text = "♫"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(greenNeon) }
        row.addView(icon, LinearLayout.LayoutParams(40, 48))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 0, 5, 0) }
        labels.addView(TextView(this).apply { text = track.title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(textMain); maxLines = 1 })
        labels.addView(TextView(this).apply { text = track.artist + if (track.folderName.isNotBlank()) " • ${track.folderName}" else ""; textSize = 11f; setTextColor(textSecondary); maxLines = 1; setPadding(0, 4, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val play = TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = roundedBackground(greenNeon, 24f)
            setPadding(10, 8, 10, 8)
            setOnClickListener {
                val playlist = allTracks.ifEmpty { listOf(track) }
                val index = playlist.indexOf(track).coerceAtLeast(0)
                playTracks(playlist, index)
            }
        }
        row.addView(play)
        val fav = TextView(this).apply {
            val set = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
            text = if (set.contains(track.key())) "♥" else "♡"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.RED); setPadding(8, 5, 5, 5)
            setOnClickListener { val f = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet(); if (!f.add(track.key())) f.remove(track.key()); prefs.edit().putStringSet("favorites", f).apply(); text = if (f.contains(track.key())) "♥" else "♡" }
        }
        row.addView(fav)
        target.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
    }

    private fun emptyMessage(message: String) = addEmpty(content, message)
    private fun addEmpty(target: LinearLayout, message: String) {
        target.addView(TextView(this).apply { text = message; textSize = 14f; gravity = Gravity.CENTER; setTextColor(textSecondary); setPadding(20, 24, 20, 24) }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showQueue() {
        content.removeAllViews(); heading("Cola de reproducción"); subtitle("Canciones cargadas en el reproductor")
        actionButton("← Volver") { showTab("Inicio") }
        val c = controller
        val queue = if (c != null) (0 until c.mediaItemCount).mapNotNull { i ->
            val item = c.getMediaItemAt(i); val md = item.mediaMetadata
            Track(item.localConfiguration?.uri ?: return@mapNotNull null, md.title?.toString() ?: "Audio", md.artist?.toString() ?: "Artista desconocido", md.albumTitle?.toString() ?: "", folderName = "")
        } else emptyList()
        if (queue.isEmpty()) emptyMessage("La cola está vacía. Elegí una canción para reproducir.") else queue.forEach { addTrackRow(it, content) }
    }

    private fun getPlaylists(): List<Pair<String, MutableList<Track>>> {
        val raw = prefs.getString("playlists", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i); val name = o.optString("name", "Lista")
                val uris = o.optJSONArray("uris") ?: JSONArray()
                val tracks = (0 until uris.length()).mapNotNull { findTrack(Uri.parse(uris.getString(it))) }.toMutableList()
                name to tracks
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun createPlaylistDialog() {
        val input = EditText(this).apply { hint = "Nombre de la lista"; setTextColor(textMain); setHintTextColor(textSecondary) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Nueva lista de reproducción").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim(); if (name.isBlank()) return@setPositiveButton
                val arr = JSONArray(prefs.getString("playlists", "[]") ?: "[]")
                arr.put(JSONObject().put("name", name).put("uris", JSONArray()))
                prefs.edit().putString("playlists", arr.toString()).apply(); showTab("Biblioteca")
            }.show()
    }

    private fun showPlaylist(name: String, tracks: List<Track>) {
        content.removeAllViews(); heading(name); subtitle("${tracks.size} canciones")
        actionButton("← Volver a biblioteca") { showTab("Biblioteca") }
        actionButton("▶ Reproducir lista") { playTracks(tracks, 0) }
        if (tracks.isEmpty()) emptyMessage("Esta lista está vacía.") else tracks.forEach { addTrackRow(it, content) }
        actionButton("Eliminar lista") {
            val arr = JSONArray(); getPlaylists().filterNot { it.first == name }.forEach { (n, ts) -> arr.put(JSONObject().put("name", n).put("uris", JSONArray(ts.map { it.uri.toString() }))) }
            prefs.edit().putString("playlists", arr.toString()).apply(); showTab("Biblioteca")
        }
    }

    private fun showConverter() {
        content.removeAllViews(); heading("Convertir a OPUS"); subtitle("Conversión de audio local. Seleccioná un archivo de tu biblioteca.")
        actionButton("← Volver") { showTab("Biblioteca") }
        val bitrateSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, PcmToOpus.BITRATES.map { "${it / 1000} kbps" }) }
        content.addView(bitrateSpinner, LinearLayout.LayoutParams(-1, 48))
        conversionStatus = TextView(this).apply { text = "Los archivos convertidos se guardan en la carpeta Spampafy/Converted."; setTextColor(textSecondary); setPadding(8, 10, 8, 10) }
        content.addView(conversionStatus)
        if (allTracks.isEmpty()) emptyMessage("Agregá canciones primero para convertirlas.")
        allTracks.forEach { track ->
            val b = TextView(this).apply {
                text = "⇄  Convertir: ${track.title}"
                setTextColor(textMain)
                setPadding(12, 12, 12, 12)
                background = roundedBackground(cardColor, 12f, greenBorder)
                setOnClickListener {
                    val bitrate = PcmToOpus.BITRATES.getOrElse(bitrateSpinner.selectedItemPosition) { 64000 }
                    conversionStatus?.text = "Convirtiendo ${track.title}..."
                    executor.execute {
                        try {
                            val output = PcmToOpus(this@MainActivity).convert(track.uri, bitrate) { status ->
                                runOnUiThread { conversionStatus?.text = status }
                            }
                            runOnUiThread {
                                conversionStatus?.text = "Listo: ${output.name}"
                                Toast.makeText(this@MainActivity, "Archivo OPUS guardado", Toast.LENGTH_LONG).show()
                                loadLibrary()
                            }
                        } catch (e: Exception) {
                            runOnUiThread { conversionStatus?.text = "Error: ${e.message}" }
                        }
                    }
                }
            }
            content.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
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
}
