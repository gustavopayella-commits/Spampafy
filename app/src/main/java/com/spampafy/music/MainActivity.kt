
package com.spampafy.music

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
class MusicService : MediaSessionService() {

    private var controller: MediaController? = null
private var controllerFuture: ListenableFuture<MediaController>? = null
private var pendingUri: Uri? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(
            this,
            player!!
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
    }
}
private fun connectToMusicService() {
    val token = SessionToken(
        this,
        ComponentName(this, MusicService::class.java)
    )

    val future = MediaController.Builder(this, token)
        .buildAsync()

    controllerFuture = future

    future.addListener({
        try {
            controller = future.get()

            pendingUri?.let { uri ->
                pendingUri = null
                playMusic(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo conectar el reproductor",
                Toast.LENGTH_LONG
            ).show()
        }
    }, ContextCompat.getMainExecutor(this))
}