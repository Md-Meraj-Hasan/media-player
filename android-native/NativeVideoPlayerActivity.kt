package com.meraj.mediaplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout

class NativeVideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a PlayerView programmatically (No XML needed)
        playerView = PlayerView(this)
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setContentView(playerView)

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Get Video URI from Intent
        val videoUri = intent.getStringExtra(EXTRA_URI) ?: ""
        if (videoUri.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    companion object {
        const val EXTRA_URI = "EXTRA_URI"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_QUEUE_URIS = "EXTRA_QUEUE_URIS"
        const val EXTRA_QUEUE_TITLES = "EXTRA_QUEUE_TITLES"
        const val EXTRA_QUEUE_MIMES = "EXTRA_QUEUE_MIMES"
    }
}
