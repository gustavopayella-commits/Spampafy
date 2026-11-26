package com.spampafy.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("spampafy_data", MODE_PRIVATE) }

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
            artwork?.let { if (it.isNotEmpty()) builder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            return MediaItem.Builder().setUri(uri).setMediaMetadata(builder.build()).build()
        }
    }

    private val chooseAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                if (selectedUris.none { it.toString() == uri.toString() }) selectedUris.add(uri)
            }
            saveSelectedUris()
            loadLibrary()
            Toast.makeText(this, "${uris.size} archivo(s) agregados", Toast.LENGTH_SHORT).show()
            playTracks(uris.mapNotNull { findTrack(it) }, 0)
        }
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
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
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission.launch(permission)
        }
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 13, 20))
        }
        val header = TextView(this).apply {
            text = "♫  SPAMPAFY"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(167, 139, 250))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 12, 20, 8)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, 58))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 8, 18, 14)
        }
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(25, 23, 36))
            setPadding(4, 6, 4, 6)
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, 62))
        setContentView(root)
        renderNavigation()
        showTab("Inicio")
    }

    private fun renderNavigation() {
        nav.removeAllViews()
        listOf("Inicio" to "⌂", "Buscar" to "⌕", "Biblioteca" to "▤", "Favoritos" to "♥").forEach { (label, symbol) ->
            val b = TextView(this).apply {
                text = "$symbol\n$label"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (currentTab == label) Color.rgb(167,139,250) else Color.LTGRAY)
                setOnClickListener { showTab(label) }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, -1, 1f))
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
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 14)
        })
    }

    private fun actionButton(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(91, 40, 170))
            setOnClickListener { action() }
        }
        content.addView(b, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 8 })
    }

    private fun showHome() {
        heading("Tu música")
        content.addView(TextView(this).apply {
            text = "Escuchá tus canciones y descubrí tu biblioteca"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        })
        actionButton("＋  Agregar música", { chooseAudio.launch(arrayOf("audio/*")) })
        actionButton("▶  Reproducir toda mi música", { playTracks(allTracks, 0) })
        actionButton("♫  Cola de reproducción", { showQueue() })
        actionButton("🎚  Ecualizador", { startActivity(Intent(this, EqualizerActivity::class.java)) })
        actionButton("⇄  Convertir a OPUS", { showConverter() })
        heading("Reproducidas / Biblioteca")
        allTracks.take(8).forEach { addTrackRow(it) }
        if (allTracks.isEmpty()) emptyMessage("Agregá música para comenzar.")
    }

    private fun showSearch() {
        heading("Buscar")
        val input = EditText(this).apply {
            hint = "Canción, artista o álbum"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.rgb(35, 32, 48))
        }
        content.addView(input, LinearLayout.LayoutParams(-1, 52))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        fun update(q: String) {
            results.removeAllViews()
            val matches = allTracks.filter {
                it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
            }
            matches.forEach { addTrackRow(it, results) }
            if (matches.isEmpty()) addEmpty(results, if (q.isBlank()) "Escribí para buscar en tu música." else "No se encontraron canciones.")
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        update("")
    }

    private fun showLibrary() {
        heading("Tu biblioteca")
        actionButton("＋  Agregar canciones", { chooseAudio.launch(arrayOf("audio/*")) })
        actionButton("＋  Crear lista de reproducción", { createPlaylistDialog() })
        actionButton("♫  Ver cola de reproducción", { showQueue() })
        actionButton("⇄  Convertir música a OPUS", { showConverter() })
        heading("Canciones")
        allTracks.forEach { addTrackRow(it) }
        if (allTracks.isEmpty()) emptyMessage("No hay canciones. Tocá Agregar canciones.")
        heading("Tus listas")
        getPlaylists().forEach { playlist ->
            val row = TextView(this).apply {
                text = "♫  ${playlist.first}  (${playlist.second.size})"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(12, 14, 12, 14)
                setBackgroundColor(Color.rgb(32, 27, 49))
                setOnClickListener { showPlaylist(playlist.first, playlist.second) }
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
        }
    }

    private fun showFavorites() {
        heading("Tus favoritos ♥")
        val keys = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val favorites = allTracks.filter { keys.contains(it.key()) }
        favorites.forEach { addTrackRow(it) }
        if (favorites.isEmpty()) emptyMessage("Todavía no agregaste favoritos. Usá ☆ en una canción.")
    }

    private fun addTrackRow(track: Track, parent: LinearLayout = content) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 9, 6, 9)
            setBackgroundColor(Color.rgb(29, 27, 40))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = track.title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        labels.addView(TextView(this).apply {
            text = "${track.artist}  •  ${track.album}"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            maxLines = 1
        })
        labels.setOnClickListener { playTracks(listOf(track), 0) }
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val favorite = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.rgb(190, 120, 255))
            text = if (isFavorite(track)) "♥" else "♡"
            setPadding(8, 4, 8, 4)
            setOnClickListener { toggleFavorite(track); text = if (isFavorite(track)) "♥" else "♡" }
        }
        row.addView(favorite)
        val more = TextView(this).apply {
            text = "⋮"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(10, 2, 8, 2)
            setOnClickListener { showTrackOptions(track) }
        }
        row.addView(more)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6 })
    }

    private fun showTrackOptions(track: Track) {
        val options = if (track.uri.scheme == "file" && track.uri.path?.endsWith(".opus", true) == true) {
            arrayOf("Reproducir", "Agregar a favoritos / quitar", "Agregar a una lista", "Agregar a la cola", "Eliminar archivo OPUS")
        } else {
            arrayOf("Reproducir", "Agregar a favoritos / quitar", "Agregar a una lista", "Agregar a la cola")
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(track.title).setItems(options) { _, which ->
            when (which) {
                0 -> playTracks(listOf(track), 0)
                1 -> { toggleFavorite(track); Toast.makeText(this, "Favoritos actualizados", Toast.LENGTH_SHORT).show(); if (currentTab == "Favoritos") showTab("Favoritos") }
                2 -> addTrackToPlaylistDialog(track)
                3 -> { appendToQueue(track); Toast.makeText(this, "Agregada a la cola", Toast.LENGTH_SHORT).show() }
                4 -> {
                    val file = File(track.uri.path ?: "")
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Eliminar canción")
                        .setMessage("¿Querés eliminar ${file.name}?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar") { _, _ ->
                            if (file.delete()) loadLibrary()
                            else Toast.makeText(this, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                        }.show()
                }
            }
        }.show()
    }

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
                setPadding(8, 10, 8, 10)
                setBackgroundColor(if (i == current) Color.rgb(58, 42, 85) else Color.rgb(29,27,40))
            }
            val name = TextView(this).apply {
                text = (if (i == current) "▶  " else "") + (metadata.title?.toString() ?: "Canción")
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val play = smallButton("▶") { player.seekToDefaultPosition(i); player.play(); showQueue() }
            val up = smallButton("↑") { if (i > 0) { moveQueueItem(i, i - 1); showQueue() } }
            val down = smallButton("↓") { if (i < player.mediaItemCount - 1) { moveQueueItem(i, i + 1); showQueue() } }
            row.addView(play); row.addView(up); row.addView(down)
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 })
        }
        actionButton("Volver a Inicio", { showTab("Inicio") })
    }

    private fun moveQueueItem(from: Int, to: Int) {
        val p = controller ?: return
        val items = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }.toMutableList()
        val item = items.removeAt(from)
        items.add(to, item)
        val currentUri = p.currentMediaItem?.localConfiguration?.uri
        val newIndex = items.indexOfFirst { it.localConfiguration?.uri == currentUri }.coerceAtLeast(0)
        p.setMediaItems(items, newIndex, p.currentPosition)
        p.prepare()
        p.play()
    }

    private fun smallButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        minWidth = 38
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }

    private fun createPlaylistDialog() {
        val input = EditText(this).apply { hint = "Nombre de la lista"; setTextColor(Color.WHITE) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nueva lista").setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val playlists = readPlaylists()
                    if (!playlists.has(name)) playlists.put(name, JSONArray())
                    savePlaylists(playlists)
                    if (currentTab == "Biblioteca") showTab("Biblioteca")
                }
            }.show()
    }

    private fun addTrackToPlaylistDialog(track: Track) {
        val playlists = readPlaylists()
        val names = (0 until playlists.length()).map { playlists.names().getString(it) }
        if (names.isEmpty()) { createPlaylistDialog(); return }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Agregar a lista").setItems(names.toTypedArray()) { _, which ->
            val name = names[which]
            val arr = playlists.optJSONArray(name) ?: JSONArray()
            if ((0 until arr.length()).none { arr.optString(it) == track.key() }) arr.put(track.key())
            playlists.put(name, arr); savePlaylists(playlists)
            Toast.makeText(this, "Agregada a $name", Toast.LENGTH_SHORT).show()
        }.setPositiveButton("Cerrar", null).show()
    }

    private fun showPlaylist(name: String, keys: List<String>) {
        content.removeAllViews()
        heading(name)
        val tracks = allTracks.filter { keys.contains(it.key()) }
        actionButton("▶  Reproducir lista", { playTracks(tracks, 0) })
        tracks.forEach { addTrackRow(it) }
        actionButton("Eliminar lista", {
            val p = readPlaylists(); p.remove(name); savePlaylists(p); showTab("Biblioteca")
        })
    }

    private fun getPlaylists(): List<Pair<String, List<String>>> {
        val p = readPlaylists()
        return (0 until p.length()).mapNotNull { i ->
            val name = p.names().optString(i)
            val arr = p.optJSONArray(name) ?: JSONArray()
            name to (0 until arr.length()).map { arr.optString(it) }
        }
    }

    private fun readPlaylists(): JSONObject = try { JSONObject(prefs.getString("playlists", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    private fun savePlaylists(p: JSONObject) { prefs.edit().putString("playlists", p.toString()).apply() }

    private fun isFavorite(track: Track) = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).contains(track.key())
    private fun toggleFavorite(track: Track) {
        val keys = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()
        if (!keys.add(track.key())) keys.remove(track.key())
        prefs.edit().putStringSet("favorites", keys).apply()
    }

    private fun appendToQueue(track: Track) {
        val p = controller ?: return
        p.addMediaItem(track.mediaItem())
    }

    private fun playTracks(tracks: List<Track>, start: Int) {
        val p = controller
        if (p == null) { Toast.makeText(this, "Conectando reproductor...", Toast.LENGTH_SHORT).show(); return }
        if (tracks.isEmpty()) { Toast.makeText(this, "No hay canciones para reproducir", Toast.LENGTH_SHORT).show(); return }
        val items = tracks.map { it.mediaItem() }
        p.setMediaItems(items, start.coerceIn(0, items.lastIndex), C.TIME_UNSET)
        p.prepare()
        p.play()
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun connectPlayer() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
      