
package com.spampafy.music

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.Toast

class YouTubePlayerActivity : Activity() {

    private val green = Color.rgb(0, 255, 120)
    private val black = Color.rgb(8, 8, 8)

    // Origen utilizado para identificar el reproductor integrado.
    private val appOrigin = "https://www.youtube-nocookie.com"

    private lateinit var webView: WebView
    private lateinit var playerStatus: TextView

    private var videoId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoId = intent.getStringExtra("VIDEO_ID") ?: ""

        if (!videoId.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            Toast.makeText(
                this,
                "Video no válido",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val title = intent.getStringExtra("VIDEO_TITLE")
            ?: "Reproduciendo"

        val channel = intent.getStringExtra("VIDEO_CHANNEL")
            ?: "YouTube"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(16, 20, 16, 16)
        }

        val logo = TextView(this).apply {
            text = "SPAMPAFY"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        root.addView(logo)

        val subtitle = TextView(this).apply {
            text = "YOUTUBE PLAYER"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        root.addView(subtitle)

        val videoContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode =
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW

                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }

            CookieManager.getInstance()
                .setAcceptCookie(true)

            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, true)

            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }

        videoContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val videoParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        videoParams.topMargin = 24
        videoParams.bottomMargin = 12

        root.addView(videoContainer, videoParams)

        playerStatus = TextView(this).apply {
            text = "Preparando reproductor..."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 12)
        }

        root.addView(playerStatus)

        val name = TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 3
        }

        root.addView(name)

        val artist = TextView(this).apply {
            text = channel
            textSize = 14f
            setTextColor(green)
            setPadding(0, 8, 0, 12)
        }

        root.addView(artist)

        val externalButton = Button(this).apply {
            text = "ABRIR EN YOUTUBE"
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(green)

            setOnClickListener {
                openYouTube()
            }
        }

        root.addView(
            externalButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                50
            ).apply {
                bottomMargin = 8
            }
        )

        val back = Button(this).apply {
            text = "VOLVER A SPAMPAFY"
            textSize = 13f
            setTextColor(green)
            setBackgroundColor(Color.rgb(25, 25, 25))

            setOnClickListener {
                finish()
            }
        }

        root.addView(
            back,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                50
            )
        )

        setContentView(root)

        loadYouTubePlayer(videoId)
    }

    private fun loadYouTubePlayer(videoId: String) {

        val html = """
            <!DOCTYPE html>
            <html>
            <head>

            <meta charset="UTF-8">

            <meta name="viewport"
                content="width=device-width, initial-scale=1.0">

            <meta name="referrer"
                content="strict-origin-when-cross-origin">

            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: #000000;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                }

                #player {
                    width: 100%;
                    height: 100%;
                }

                #message {
                    color: #00ff78;
                    text-align: center;
                    font-family: Arial;
                    padding: 10px;
                }
            </style>

            </head>

            <body>

            <div id="player"></div>
            <div id="message"></div>

            <script>

                var tag = document.createElement('script');

                tag.src = "https://www.youtube.com/iframe_api";

                document.head.appendChild(tag);

                var player;

                function onYouTubeIframeAPIReady() {

                    player = new YT.Player('player', {

                        width: '100%',
                        height: '100%',

                        videoId: '$videoId',

                        playerVars: {
                            'playsinline': 1,
                            'rel': 0,
                            'autoplay': 0,
                            'origin': '$appOrigin'
                        },

                        events: {

                            'onReady': function(event) {

                                var iframe = event.target.getIframe();

                                iframe.setAttribute(
                                    'referrerpolicy',
                                    'strict-origin-when-cross-origin'
                                );

                                document.getElementById('message')
                                    .innerText = 'Tocá PLAY para reproducir';

                            },

                            'onStateChange': function(event) {

                                if (event.data === 1) {
                                    document.getElementById('message')
                                        .innerText = '';
                                }

                                if (event.data === 0) {
                                    document.getElementById('message')
                                        .innerText = 'Video finalizado';
                                }
                            },

                            'onError': function(event) {

                                document.getElementById('message')
                                    .innerText =
                                    'YouTube no permite reproducir este video. Código: '
                                    + event.data;

                            }
                        }
                    });
                }

            </script>

            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            appOrigin,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun openYouTube() {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

            startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo abrir YouTube",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
        }

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }

        super.onDestroy()
    }
}
