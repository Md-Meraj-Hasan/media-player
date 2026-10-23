package com.meraj.mediaplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class NativeVideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var controlsLayout: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Yahan apni layout file ka naam check karein (jaise R.layout.activity_main)
        setContentView(R.layout.activity_native_video_player) 

        playerView = findViewById(R.id.playerView)
        controlsLayout = findViewById(R.id.controlsLayout)
        bottomControls = findViewById(R.id.bottomControls)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUri = intent.getStringExtra("VIDEO_URI") ?: "" 
        if (videoUri.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        btnPlayPause.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            showControls()
        }

        btnForward.setOnClickListener {
            player.seekTo(player.currentPosition + 10000)
            showControls()
        }

        btnRewind.setOnClickListener {
            val newPos = player.currentPosition - 10000
            if (newPos > 0) {
                player.seekTo(newPos)
            } else {
                player.seekTo(0)
            }
            showControls()
        }

        playerView.setOnClickListener {
            if (controlsLayout.visibility == View.VISIBLE) {
                hideControls()
            } else {
                showControls()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                hideHandler.removeCallbacks(hideRunnable)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                showControls()
            }
        })

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    updateProgress()
                    hideControls()
                } else {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    showControls()
                }
            }
        })
    }

    private fun updateProgress() {
        if (player.isPlaying) {
            seekBar.max = player.duration.toInt()
            seekBar.progress = player.currentPosition.toInt()
            tvCurrentTime.text = formatTime(player.currentPosition)
            tvTotalTime.text = formatTime(player.duration)
            hideHandler.postDelayed({ updateProgress() }, 1000)
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    private fun showControls() {
        controlsLayout.visibility = View.VISIBLE
        bottomControls.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun hideControls() {
        controlsLayout.visibility = View.GONE
        bottomControls.visibility = View.GONE
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        hideHandler.removeCallbacks(hideRunnable)
    }
}
