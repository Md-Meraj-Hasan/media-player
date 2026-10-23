package com.meraj.mediaplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.max

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
    private lateinit var overlay: LinearLayout
    private lateinit var playButton: TextView
    private lateinit var status: TextView
    private lateinit var seek: SeekBar
    private lateinit var thumbnailRow: LinearLayout
    private var dragging = false
    private var controlsVisible = true
    private var currentRate = 1f
    private var currentUri = ""
    private val queueUris by lazy { intent.getStringArrayListExtra(EXTRA_QUEUE_URIS) ?: arrayListOf() }
    private val queueTitles by lazy { intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES) ?: arrayListOf() }
    private val handler = Handler(Looper.getMainLooper())
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
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun time(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000).toInt()
        val h = s / 3600
        return if (h > 0) String.format("%02d:%02d:%02d", h, (s % 3600) / 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60)
    }
    private fun label(text: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = size; gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private fun toolButton(symbol: String, description: String, action: () -> Unit): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), 0, dp(3), 0) }
        val icon = label(symbol, 25f)
        icon.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xCC171719.toInt()) }
        outer.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        outer.addView(label(description, 10f))
        outer.setOnClickListener { action() }
        outer.contentDescription = description
        return outer
    }
    private fun seekBy(delta: Long) { val p = player ?: return; p.time = (p.time + delta).coerceIn(0L, p.length.coerceAtLeast(0L)) }
    private fun loadVideo(uriString: String, title: String? = null) {
        try {
            currentUri = uriString
            val media = Media(vlc ?: return, Uri.parse(uriString))
            player?.media = media
            media.release()
            player?.play()
            playButton.text = "Ⅱ"
            title?.let { (findViewById<TextView>(1001))?.text = it }
        } catch (e: Exception) { Toast.makeText(this, "Unable to open video", Toast.LENGTH_SHORT).show() }
    }
    private fun makeThumbnail(uriString: String): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uriString))
            val bmp = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bmp
        } catch (_: Exception) { null }
    }
    private fun addThumbnail(uri: String, title: String, selected: Boolean) {
        val holder = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.BLACK)
                setStroke(dp(if (selected) 3 else 1), if (selected) Color.WHITE else 0xFF343434.toInt())
            }
            setPadding(dp(if (selected) 4 else 2), dp(if (selected) 4 else 2), dp(if (selected) 4 else 2), dp(if (selected) 4 else 2))
            contentDescription = title
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true }
        holder.addView(image, FrameLayout.LayoutParams(-1, -1))
        thumbnailRow.addView(holder, LinearLayout.LayoutParams(dp(142), dp(82)).apply { marginEnd = dp(10) })
        Thread {
            val bitmap = makeThumbnail(uri)
            runOnUiThread {
                if (bitmap != null) image.setImageBitmap(bitmap) else {
                    image.setBackgroundColor(0xFF292929.toInt())
                    image.setImageResource(android.R.drawable.ic_media_play)
                    image.setColorFilter(Color.WHITE)
                }
            }
        }.start()
        holder.setOnClickListener { if (uri != currentUri) loadVideo(uri, title) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))
        overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0x66000000); setPadding(dp(14), dp(8), dp(14), dp(10)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val down = label("⌄", 30f)
        top.addView(down, LinearLayout.LayoutParams(dp(44), dp(46)))
        val title = label(intent.getStringExtra(EXTRA_TITLE) ?: "Video", 18f, true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; id = 1001; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        top.addView(title, LinearLayout.LayoutParams(0, dp(46), 1f))
        top.addView(label("CC", 15f, true).apply { setPadding(dp(10), 0, dp(10), 0); contentDescription = "Subtitles" })
        val close = label("⋮", 28f).apply { setOnClickListener { finish() } }
        top.addView(close, LinearLayout.LayoutParams(dp(38), dp(46)))
        overlay.addView(top)
        val spacer = View(this)
        overlay.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val center = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4)) }
        val back = label("⟲\n10", 21f, true).apply { setOnClickListener { seekBy(-10_000) } }
        center.addView(back, LinearLayout.LayoutParams(dp(76), dp(72)))
        playButton = label("Ⅱ", 42f, true).apply { setOnClickListener { val p = player ?: return@setOnClickListener; if (p.isPlaying) { p.pause(); text = "▶" } else { p.play(); text = "Ⅱ" } } }
        center.addView(playButton, LinearLayout.LayoutParams(dp(86), dp(76)))
        val forward = label("⟳\n10", 21f, true).apply { setOnClickListener { seekBy(10_000) } }
        center.addView(forward, LinearLayout.LayoutParams(dp(76), dp(72)))
        overlay.addView(center)
        overlay.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolButton("♙", "LOCK") { Toast.makeText(this@NativeVideoPlayerActivity, "Tap screen to show controls", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("◖))", "VOLUME") { Toast.makeText(this@NativeVideoPlayerActivity, "Use device volume buttons", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("▣", "SCREEN") { requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("‹ ›", "ASPECT") { Toast.makeText(this@NativeVideoPlayerActivity, "Aspect ratio is managed by LibVLC", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("◉", "REPEAT") { Toast.makeText(this@NativeVideoPlayerActivity, "Repeat mode", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("♫", "AUDIO") { Toast.makeText(this@NativeVideoPlayerActivity, "Audio track selection", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(66), 1f))
        tools.addView(toolButton("▯↻", "ROTATE") { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR }, LinearLayout.LayoutParams(0, dp(66), 1f))
        overlay.addView(tools)
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(5), 0, dp(2)) }
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFE13C83.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFE13C83.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x66FFFFFF)
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(bar: SeekBar) { player?.time = bar.progress.toLong(); dragging = false }
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) status.text = "${time(value.toLong())} / ${time(player?.length ?: 0L)}" }
            })
        }
        timeline.addView(seek, LinearLayout.LayoutParams(-1, dp(28)))
        val times = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        status = label("00:00 / 00:00", 13f).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
        times.addView(status, LinearLayout.LayoutParams(0, dp(22), 1f))
        val speed = label("1.0×", 15f, true).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { val rates = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f); val next = (rates.indexOfFirst { it == currentRate }.coerceAtLeast(0) + 1) % rates.size; currentRate = rates[next]; player?.setRate(currentRate); text = "${currentRate}×" }
        }
        times.addView(speed)
        timeline.addView(times)
        overlay.addView(timeline)
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(0, dp(5), 0, 0) }
        thumbnailRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        scroll.addView(thumbnailRow, LinearLayout.LayoutParams(-2, -2))
        overlay.addView(scroll, LinearLayout.LayoutParams(-1, dp(94)))
        val overlayParams = FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM)
        root.addView(overlay, overlayParams)
        videoLayout.setOnClickListener { controlsVisible = !controlsVisible; overlay.visibility = if (controlsVisible) View.VISIBLE else View.GONE }
        setContentView(root)
        try {
            vlc = LibVLC(this, arrayListOf("--no-video-title-show", "--audio-time-stretch"))
            val uri = intent.getStringExtra(EXTRA_URI) ?: intent.dataString
            if (uri.isNullOrBlank()) { Toast.makeText(this, "Video URI missing", Toast.LENGTH_LONG).show(); finish(); return }
            currentUri = uri
            player = MediaPlayer(vlc).also { p ->
                p.attachViews(videoLayout, null, false, false)
                p.setEventListener { event -> when (event.type) {
                    MediaPlayer.Event.Playing -> runOnUiThread { playButton.text = "Ⅱ" }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> runOnUiThread { playButton.text = "▶" }
                    MediaPlayer.Event.EncounteredError -> runOnUiThread { Toast.makeText(this, "LibVLC could not play this media/codec.", Toast.LENGTH_LONG).show() }
                } }
                val media = Media(vlc, Uri.parse(uri)); p.media = media; media.release(); p.play()
            }
            val allUris = if (queueUris.isNotEmpty()) queueUris else arrayListOf(uri)
            val allTitles = if (queueTitles.isNotEmpty()) queueTitles else arrayListOf(intent.getStringExtra(EXTRA_TITLE) ?: "Video")
            allUris.forEachIndexed { i, itemUri -> addThumbnail(itemUri, allTitles.getOrElse(i) { "Video" }, itemUri == uri) }
            handler.post(progress)
        } catch (e: Exception) {
            android.util.Log.e("NiceMultimediaVideo", "LibVLC video startup failed", e)
            Toast.makeText(this, "Could not start LibVLC video player", Toast.LENGTH_LONG).show(); finish()
        }
    }
    override fun onDestroy() {
        handler.removeCallbacks(progress)
        player?.stop(); player?.detachViews(); player?.release(); player = null
        vlc?.release(); vlc = null
        super.onDestroy()
    }
}
