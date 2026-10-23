package com.meraj.mediaplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
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
        private const val TAG = "MerajLibVLCVideo"
    }
    private var vlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var pfd: ParcelFileDescriptor? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var status: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private lateinit var titleView: TextView
    private var dragging = false
    private val handler = Handler(Looper.getMainLooper())

    private val progress = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.length.coerceAtLeast(0L)
            if (!dragging && duration > 0L) {
                seek.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                seek.progress = p.time.coerceIn(0L, duration).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            status.text = "${time(p.time)} / ${time(duration)}"
            handler.postDelayed(this, 300)
        }
    }
    private fun time(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1000).toInt()
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
    private fun text(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(12, 8, 12, 8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        videoLayout.elevation = 0f
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))

        // Overlay controls are siblings above VLC's video surface so the surface cannot cover them.
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            elevation = 100f
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 4)
            setBackgroundColor(0x99000000.toInt())
        }
        titleView = text(intent.getStringExtra(EXTRA_TITLE) ?: "Video", 16f).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(titleView, LinearLayout.LayoutParams(0, 48, 1f))
        val close = text("✕", 23f).apply {
            contentDescription = "Close video"
            isClickable = true
            setOnClickListener { finish() }
        }
        top.addView(close, LinearLayout.LayoutParams(52, 48))
        overlay.addView(top)

        val spacer = View(this)
        overlay.addView(spacer, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 4, 10, 8)
            setBackgroundColor(0xCC000000.toInt())
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playButton = text("Ⅱ", 24f).apply {
            contentDescription = "Play or pause"
            isClickable = true
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                if (p.isPlaying) p.pause() else p.play()
            }
        }
        row.addView(playButton, LinearLayout.LayoutParams(54, 48))
        val back10 = text("↶ 10s", 14f).apply {
            contentDescription = "Back 10 seconds"
            isClickable = true
            setOnClickListener { player?.let { it.time = (it.time - 10_000L).coerceAtLeast(0L) } }
        }
        row.addView(back10, LinearLayout.LayoutParams(70, 48))
        status = text("0:00 / 0:00", 13f).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(status, LinearLayout.LayoutParams(0, 48, 1f))
        val speed = text("1×", 15f).apply {
            contentDescription = "Playback speed"
            isClickable = true
            var idx = 2
            val rates = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            setOnClickListener {
                idx = (idx + 1) % rates.size
                player?.setRate(rates[idx])
                text = "${rates[idx]}×"
            }
        }
        row.addView(speed, LinearLayout.LayoutParams(64, 48))
        val forward10 = text("10s ↷", 14f).apply {
            contentDescription = "Forward 10 seconds"
            isClickable = true
            setOnClickListener { player?.let { p -> p.time = (p.time + 10_000L).coerceAtMost(p.length.coerceAtLeast(0L)) } }
        }
        row.addView(forward10, LinearLayout.LayoutParams(76, 48))
        controls.addView(row)
        val optionsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val fit = text("FIT", 13f).apply {
            contentDescription = "Change video fit mode"
            isClickable = true
            var idx = 0
            val ratios: Array<String?> = arrayOf(null, "16:9", "4:3")
            val labels = arrayOf("FIT", "16:9", "4:3")
            setOnClickListener {
                idx = (idx + 1) % ratios.size
                player?.setAspectRatio(ratios[idx])
                text = labels[idx]
            }
        }
        optionsRow.addView(fit, LinearLayout.LayoutParams(64, 40))
        val rotate = text("ROTATE", 13f).apply {
            contentDescription = "Toggle orientation"
            isClickable = true
            setOnClickListener {
                requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        optionsRow.addView(rotate, LinearLayout.LayoutParams(88, 40))
        val stop = text("STOP", 13f).apply {
            contentDescription = "Stop playback"
            isClickable = true
            setOnClickListener { player?.stop(); finish() }
        }
        optionsRow.addView(stop, LinearLayout.LayoutParams(68, 40))
        controls.addView(optionsRow)
        seek = SeekBar(this).apply { contentDescription = "Seek video" }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                player?.time = bar.progress.toLong()
                dragging = false
            }
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) status.text = "${time(value.toLong())} / ${time(player?.length ?: 0L)}"
            }
        })
        controls.addView(seek)
        overlay.addView(controls)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1, Gravity.TOP))
        setContentView(root)
        overlay.bringToFront()

        val rawUri = intent.getStringExtra(EXTRA_URI) ?: intent.dataString
        if (rawUri.isNullOrBlank()) {
            Toast.makeText(this, "Video URI missing", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            vlc = LibVLC(this, arrayListOf("--no-video-title-show", "--audio-time-stretch", "--verbose=2"))
            player = MediaPlayer(vlc).also { p ->
                p.attachViews(videoLayout, null, false, false)
                // VLC's SurfaceView can otherwise visually cover sibling controls on some devices.
                window.decorView.post {
                    overlay.bringToFront()
                    overlay.invalidate()
                }
                p.setEventListener { event ->
                    runOnUiThread {
                        when (event.type) {
                            MediaPlayer.Event.Playing -> playButton.text = "Ⅱ"
                            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playButton.text = "▶"
                            MediaPlayer.Event.EncounteredError -> {
                                android.util.Log.e(TAG, "LibVLC encountered a playback error for $rawUri")
                                Toast.makeText(this, "LibVLC could not open this media. Check file access/codec.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                val uri = Uri.parse(rawUri)
                val media = if (uri.scheme == "content") {
                    pfd = contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("Android could not open this file. Re-select it and grant access.")
                    Media(vlc, pfd!!.fileDescriptor)
                } else Media(vlc, uri)
                p.media = media
                media.release()
                p.play()
            }
            handler.post(progress)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "LibVLC video startup failed for $rawUri", e)
            Toast.makeText(this, "Video open failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(progress)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.detachViews() } catch (_: Exception) {}
        player?.release(); player = null
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        vlc?.release(); vlc = null
        super.onDestroy()
    }
}
