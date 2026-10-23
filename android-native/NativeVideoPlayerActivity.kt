package com.meraj.mediaplayer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class NativeVideoPlayerActivity : Activity() {
    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_QUEUE_URIS = "queueUris"
        const val EXTRA_QUEUE_TITLES = "queueTitles"
        const val EXTRA_QUEUE_MIMES = "queueMimes"
    }
    private var vlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var status: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private var dragging = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progress = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.length.coerceAtLeast(0L)
            if (!dragging && duration > 0) {
                seek.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seek.progress = p.time.coerceIn(0L, duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            status.text = "${time(p.time)} / ${time(duration)}"
            handler.postDelayed(this, 400)
        }
    }
    private fun time(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000).toInt()
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(12, 6, 12, 6) }
        val title = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
            setTextColor(Color.WHITE); textSize = 16f
        }
        top.addView(title, LinearLayout.LayoutParams(0, 44, 1f))
        val close = TextView(this).apply { text = "✕"; setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER; setPadding(12, 0, 12, 0); setOnClickListener { finish() } }
        top.addView(close)
        root.addView(top)
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 4, 12, 12) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playButton = TextView(this).apply {
            text = "Ⅱ"; setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
            setPadding(18, 8, 18, 8)
            setOnClickListener { val p = player ?: return@setOnClickListener; if (p.isPlaying) { p.pause(); text = "▶" } else { p.play(); text = "Ⅱ" } }
        }
        row.addView(playButton)
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER_VERTICAL }
        row.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val speed = TextView(this).apply {
            text = "1×"; setTextColor(Color.WHITE); textSize = 16f; setPadding(12, 8, 12, 8)
            var idx = 0
            setOnClickListener { val rates = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f); idx = (idx + 1) % rates.size; player?.setRate(rates[idx]); text = "${rates[idx]}×" }
        }
        row.addView(speed)
        controls.addView(row)
        seek = SeekBar(this)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(bar: SeekBar) { player?.time = bar.progress.toLong(); dragging = false }
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) status.text = time(value.toLong()) }
        })
        controls.addView(seek)
        root.addView(controls)
        setContentView(root)
        try {
            vlc = LibVLC(this, arrayListOf("--no-video-title-show", "--audio-time-stretch"))
            player = MediaPlayer(vlc).also { p ->
                p.attachViews(videoLayout, null, false, false)
                p.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> runOnUiThread { playButton.text = "Ⅱ" }
                        MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> runOnUiThread { playButton.text = "▶" }
                        MediaPlayer.Event.EncounteredError -> runOnUiThread { Toast.makeText(this, "LibVLC could not play this media/codec.", Toast.LENGTH_LONG).show() }
                    }
                }
                val uri = intent.getStringExtra(EXTRA_URI) ?: intent.dataString
                if (uri.isNullOrBlank()) { Toast.makeText(this, "Video URI missing", Toast.LENGTH_LONG).show(); finish(); return }
                val media = Media(vlc, Uri.parse(uri))
                p.media = media
                media.release()
                p.play()
            }
            handler.post(progress)
        } catch (e: Exception) {
            android.util.Log.e("NiceMultimediaVideo", "LibVLC video startup failed", e)
            Toast.makeText(this, "Could not start LibVLC video player", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacks(progress)
        player?.stop()
        player?.detachViews()
        player?.release(); player = null
        vlc?.release(); vlc = null
        super.onDestroy()
    }
}
