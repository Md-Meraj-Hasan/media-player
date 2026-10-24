package com.meraj.mediaplayer

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class NativeVideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var audioManager: AudioManager
    private var startY = 0f
    private var startVolume = 0
    private var startBrightness = 0f
    private var isVolumeControl = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the ugly blue Action Bar
        supportActionBar?.hide()

        playerView = PlayerView(this)
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setContentView(playerView)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        startBrightness = window.attributes.screenBrightness

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUri = intent.getStringExtra(EXTRA_URI) ?: ""
        if (videoUri.isNotEmpty()) {
            player.setMediaItem(MediaItem.fromUri(videoUri))
            player.prepare()
            player.playWhenReady = true
        }

        // Swipe Gesture for Volume & Brightness
        playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startBrightness = window.attributes.screenBrightness
                    isVolumeControl = event.x > playerView.width / 2
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = startY - event.y
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (isVolumeControl) {
                        val newVol = (startVolume + (deltaY / 50)).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    } else {
                        val newBrightness = (startBrightness + (deltaY / 500f)).coerceIn(0.1f, 1.0f)
                        val lp = window.attributes
                        lp.screenBrightness = newBrightness
                        window.attributes = lp
                    }
                    true
                }
                else -> false
            }
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
