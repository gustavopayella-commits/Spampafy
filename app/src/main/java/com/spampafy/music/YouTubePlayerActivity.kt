
package com.spampafy.music

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
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

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra("VIDEO_ID") ?: ""

        if (!videoId.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            Toast.makeText(this, "Video no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val title = intent.getStringExtra("VIDEO_TITLE") ?: "Reproduciendo"
        val channel = intent.getStringExtra("VIDEO_CHANNEL") ?: "YouTube"

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

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

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
        videoParams.bottomMargin = 18

        root.addView(videoContainer, videoParams)

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
            setPadding(0, 8, 0, 20)
        }

        root.addView(artist)

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
                54
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
            <meta name="viewport"
                content="width=device-width, initial-scale=1.0">
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
            </style>
            </head>
            <body>
            <div id="player"></div>

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
                            'rel': 0
                        },
                        events: {
                            'onReady': function(event) {
                                event.target.playVideo();
                            }
                        }
                    });
                }
            </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
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
