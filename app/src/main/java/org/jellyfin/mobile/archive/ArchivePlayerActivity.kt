package org.jellyfin.mobile.archive

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * Full-screen player for Archive Mode. Streams directly from archive.org
 * using the app's existing ExoPlayer stack.
 */
class ArchivePlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        setContentView(playerView)

        // Immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        if (player != null) return
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "No stream to play", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AeonTV")
            .setAllowCrossProtocolRedirects(true)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                Toast.makeText(this@ArchivePlayerActivity, "Couldn't play this file", Toast.LENGTH_LONG).show()
                finish()
            }
        })
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        player = exoPlayer
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_STREAM_URL = "stream_url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, streamUrl: String, title: String) {
            context.startActivity(
                Intent(context, ArchivePlayerActivity::class.java).apply {
                    putExtra(EXTRA_STREAM_URL, streamUrl)
                    putExtra(EXTRA_TITLE, title)
                },
            )
        }
    }
}
