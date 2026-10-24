package com.meraj.mediaplayer

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class NativeVideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var audioManager: AudioManager

    // UI Elements
    private lateinit var controlsLayout: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnLock: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnShare: ImageButton

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    // Gesture Variables
    private var startY = 0f
    private var startVolume = 0
    private var startBrightness = 0f
    private var isVolumeControl = false
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide Action Bar
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Programmatically create the whole UI
        setupUI()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        startBrightness = window.attributes.screenBrightness

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUri = intent.getStringExtra(EXTRA_URI) ?: ""
        if (videoUri.isNotEmpty()) {
            player.setMediaItem(MediaItem.fromUri(videoUri))
            player.prepare()
            player.playWhenReady = true
        }

        setupListeners()
    }

    private fun setupUI() {
        // Root Layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // PlayerView
        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = false // Disable default controller
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(playerView)

        // Overlay Layout for Custom Controls
        controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#40000000")) // Semi-transparent black
            gravity = android.view.Gravity.CENTER
            visibility = View.VISIBLE
        }

        // Center Controls (Play, Rewind, Forward)
        val centerControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnRewind = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_rew)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }
        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply { setMargins(40, 0, 40, 0) }
        }
        btnForward = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_ff)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }

        centerControls.addView(btnRewind)
        centerControls.addView(btnPlayPause)
        centerControls.addView(btnForward)
        controlsLayout.addView(centerControls)

        // Bottom Controls (Seekbar, Time, Tools)
        bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.BOTTOM }
            setPadding(30, 30, 30, 50)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
        }

        // Seekbar
        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        bottomControls.addView(seekBar)

        // Time & Tools Layout
        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 0) }
        }

        tvCurrentTime = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); text = "00:00" }
        tvTotalTime = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); text = "00:00" }
        
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        
        btnLock = ImageButton(this).apply { setImageResource(android.R.drawable.ic_lock_lock); setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        btnRotate = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_rotate); setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        btnShare = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_share); setBackgroundColor(android.graphics.Color.TRANSPARENT) }

        toolsRow.addView(tvCurrentTime)
        toolsRow.addView(spacer)
        toolsRow.addView(btnLock)
        toolsRow.addView(btnRotate)
        toolsRow.addView(btnShare)
        toolsRow.addView(tvTotalTime)
        
        bottomControls.addView(toolsRow)
        controlsLayout.addView(bottomControls)
        rootLayout.addView(controlsLayout)

        setContentView(rootLayout)
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            showControls()
        }

        btnRewind.setOnClickListener {
            player.seekTo(player.currentPosition - 10000)
            showControls()
        }

        btnForward.setOnClickListener {
            player.seekTo(player.currentPosition + 10000)
            showControls()
        }

        btnLock.setOnClickListener {
            isLocked = !isLocked
            if (isLocked) {
                controlsLayout.visibility = View.GONE
                btnLock.setImageResource(android.R.drawable.ic_lock_lock)
            } else {
                controlsLayout.visibility = View.VISIBLE
                btnLock.setImageResource(android.R.drawable.ic_lock_lock)
            }
        }

        btnRotate.setOnClickListener {
            // Rotation logic (handled by manifest config changes for simplicity here)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                    tvCurrentTime.text = formatTime(progress.toLong())
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

        // Gestures for Volume & Brightness
        playerView.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener false
            
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

        playerView.setOnClickListener {
            if (controlsLayout.visibility == View.VISIBLE) hideControls() else showControls()
        }
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
        if (isLocked) return
        controlsLayout.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun hideControls() {
        if (isLocked) return
        controlsLayout.visibility = View.GONE
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        hideHandler.removeCallbacks(hideRunnable)
    }

    companion object {
        const val EXTRA_URI = "EXTRA_URI"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_QUEUE_URIS = "EXTRA_QUEUE_URIS"
        const val EXTRA_QUEUE_TITLES = "EXTRA_QUEUE_TITLES"
        const val EXTRA_QUEUE_MIMES = "EXTRA_QUEUE_MIMES"
    }
}
