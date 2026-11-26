
package com.spampafy.music

import androidx.media3.common.AudioListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val audioListener = object : AudioListener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            EqualizerEngine.initialize(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer

        exoPlayer.addAudioListener(audioListener)

        // Inicializa si la sesión ya está disponible.
        if (exoPlayer.audioSessionId > 0) {
            EqualizerEngine.initialize(exoPlayer.audioSessionId)
        }

        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val currentPlayer = player

        if (currentPlayer == null ||
            !currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player?.removeAudioListener(audioListener)

        EqualizerEngine.release()

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
    }
}
