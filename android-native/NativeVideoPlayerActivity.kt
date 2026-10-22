package com.meraj.mediaplayer

import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

/** Custom Web GUI Video Player with Native Volume/Brightness Gestures & JS Bridge */
class NativeVideoPlayerActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var webView: WebView
    private lateinit var gestureIndicator: View
    private lateinit var scaleDetector: ScaleGestureDetector

    private var pinchScale = 1f
    private var isPinching = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listener: Player.Listener? = null

    private var previousQueue: List<MediaItem> = emptyList()
    private var previousIndex = 0
    private var previousPosition = 0L
    private var previousPlayWhenReady = false
    private var queueSnapshotTaken = false
    private var videoUri: Uri? = null
    private var videoTitle = "Video"

    // Side-swipe gestures: Left half = Screen Brightness; Right half = Volume
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureStartVolume = 1f
    private var gestureStartBrightness = 0.5f
    private var activeSideGesture = 0 // 0=none, 1=brightness, 2=volume
    private var sideGestureAdjusted = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or 
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or 
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Setup ExoPlayer View with native controls DISABLED
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setKeepContentOnPlayerReset(true)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // 2. Setup WebView for HTML/CSS Custom GUI Controls with JS Bridge
        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            addJavascriptInterface(WebAppInterface(), "AndroidBridge")
            loadUrl("file:///android_asset/video-player.html")
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        // 3. Gesture Indicator for Volume/Brightness
        gestureIndicator = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(74, 224, 205))
                cornerRadius = dp(3).toFloat()
            }
            visibility = View.GONE
            alpha = 0.95f
        }
        root.addView(gestureIndicator, FrameLayout.LayoutParams(dp(4), dp(72), Gravity.CENTER_VERTICAL or Gravity.LEFT))

        // 4. Zoom / Pinch Detector
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pinchScale = (pinchScale * detector.scaleFactor).coerceIn(1f, 2f)
                playerView.scaleX = pinchScale
                playerView.scaleY = pinchScale
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                if (pinchScale <= 1.02f) {
                    pinchScale = 1f
                    playerView.scaleX = 1f
                    playerView.scaleY = 1f
                }
            }
        })

        // 5. Brightness & Volume Swipe Gesture Handler
        webView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.x
                    gestureStartY = event.y
                    gestureStartVolume = controller?.volume ?: 1f
                    val currentBrightness = window.attributes.screenBrightness
                    gestureStartBrightness = if (currentBrightness in 0.01f..1f) currentBrightness else 0.5f
                    activeSideGesture = 0
                    sideGestureAdjusted = false
                    gestureIndicator.visibility = View.GONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isPinching && !sideGestureAdjusted) {
                        val dx = event.x - gestureStartX
                        val dy = event.y - gestureStartY
                        if (kotlin.math.abs(dy) > dp(24) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                            activeSideGesture = if (gestureStartX < root.width / 2f) 1 else 2
                            sideGestureAdjusted = true
                        }
                    }
                    if (!isPinching && sideGestureAdjusted) {
                        val delta = ((gestureStartY - event.y) / root.height.coerceAtLeast(1).toFloat()).coerceIn(-1f, 1f)
                        if (activeSideGesture == 1) { // Brightness adjustment
                            val brightness = (gestureStartBrightness + delta).coerceIn(0.05f, 1f)
                            val attrs = window.attributes
                            attrs.screenBrightness = brightness
                            window.attributes = attrs
                            updateGestureIndicator(brightness, activeSideGesture)
                        } else if (activeSideGesture == 2) { // Volume adjustment
                            val newVolume = (gestureStartVolume + delta).coerceIn(0f, 1f)
                            controller?.volume = newVolume
                            updateGestureIndicator(newVolume, activeSideGesture)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (sideGestureAdjusted && !isPinching) {
                        gestureIndicator.visibility = View.GONE
                    }
                    activeSideGesture = 0
                    sideGestureAdjusted = false
                }
            }
            false
        }

        setContentView(root)

        val rawUri = intent.getStringExtra(EXTRA_URI)
        if (rawUri.isNullOrBlank()) { finish(); return }
        videoUri = Uri.parse(rawUri)
        videoTitle = intent.getStringExtra(EXTRA_TITLE) ?: videoUri?.lastPathSegment ?: "Video"

        setupMediaController()
    }

    // JavaScript Bridge to let HTML/CSS controls command the ExoPlayer
    inner class WebAppInterface {
        @JavascriptInterface
        fun play() {
            handler.post { controller?.play() }
        }

        @JavascriptInterface
        fun pause() {
            handler.post { controller?.pause() }
        }

        @JavascriptInterface
        fun seekTo(positionMs: Long) {
            handler.post { controller?.seekTo(positionMs) }
        }

        @JavascriptInterface
        fun next() {
            handler.post { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
        }

        @JavascriptInterface
        fun previous() {
            handler.post { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
        }
    }

    private fun setupMediaController() {
        val token = SessionToken(this, ComponentName(this, MerajPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val c = future.get()
                controller = c
                playerView.player = c
                snapshotQueue(c)
                val uri = videoUri ?: return@addListener

                val providerMime = try { contentResolver.getType(uri) } catch (_: Exception) { null }
                val fallbackMime = when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.US)) {
                    "mp4", "m4v" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    "3gp" -> "video/3gpp"
                    "ts", "m2ts" -> "video/mp2t"
                    "avi" -> "video/avi"
                    else -> null
                }
                val mime = providerMime?.takeIf { it.startsWith("video/") } ?: fallbackMime

                val queueUris = intent.getStringArrayListExtra(EXTRA_QUEUE_URIS).orEmpty()
                val queueTitles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES).orEmpty()
                val queueMimes = intent.getStringArrayListExtra(EXTRA_QUEUE_MIMES).orEmpty()
                val mediaItems = ArrayList<MediaItem>()

                for (i in queueUris.indices) {
                    val qUri = Uri.parse(queueUris[i])
                    val qTitle = queueTitles.getOrNull(i) ?: qUri.lastPathSegment ?: "Video"
                    val qMime = queueMimes.getOrNull(i)?.takeIf { it.startsWith("video/") }
                        ?: try { contentResolver.getType(qUri)?.takeIf { it.startsWith("video/") } } catch (_: Exception) { null }
                    val builder = MediaItem.Builder().setMediaId(qUri.toString()).setUri(qUri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(qTitle).build())
                    if (qMime != null && qMime != "video/*") builder.setMimeType(qMime)
                    mediaItems.add(builder.build())
                }

                if (mediaItems.isEmpty()) {
                    val builder = MediaItem.Builder().setMediaId(uri.toString()).setUri(uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(videoTitle).build())
                    if (mime != null) builder.setMimeType(mime)
                    mediaItems.add(builder.build())
                }

                val selectedIndex = mediaItems.indexOfFirst { it.mediaId == uri.toString() }.coerceAtLeast(0)
                c.setMediaItems(mediaItems, selectedIndex, 0L)
                c.prepare()
                c.playWhenReady = true
                c.play()

                val l = object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        runOnUiThread { Toast.makeText(this@NativeVideoPlayerActivity, "Video playback failed: ${error.errorCodeName}", Toast.LENGTH_LONG).show() }
                    }
                }
                listener = l
                c.addListener(l)

            } catch (e: Exception) {
                Toast.makeText(this, "Could not open video player", Toast.LENGTH_LONG).show()
                finish()
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }

    private fun updateGestureIndicator(level: Float, side: Int) {
        val value = level.coerceIn(0f, 1f)
        val height = dp(24) + (dp(112) * value).toInt()
        val params = (gestureIndicator.layoutParams as FrameLayout.LayoutParams)
        params.width = dp(4)
        params.height = height
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == 1) Gravity.LEFT else Gravity.RIGHT)
        params.leftMargin = if (side == 1) dp(28) else 0
        params.rightMargin = if (side == 2) dp(28) else 0
        gestureIndicator.layoutParams = params
        gestureIndicator.visibility = View.VISIBLE
    }

    private fun snapshotQueue(p: MediaController) {
        if (queueSnapshotTaken) return
        previousQueue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
        previousIndex = p.currentMediaItemIndex.coerceAtLeast(0)
        previousPosition = p.currentPosition.coerceAtLeast(0L)
        previousPlayWhenReady = p.playWhenReady
        queueSnapshotTaken = true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        val c = controller
        listener?.let { c?.removeListener(it) }
        if (isFinishing && queueSnapshotTaken && c != null) {
            try {
                if (previousQueue.isNotEmpty()) {
                    c.setMediaItems(previousQueue, previousIndex.coerceIn(0, previousQueue.lastIndex), previousPosition)
                    c.prepare()
                    if (previousPlayWhenReady) c.play() else c.pause()
                } else c.clearMediaItems()
            } catch (_: Exception) {}
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFinishing) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "com.meraj.mediaplayer.VIDEO_URI"
        const val EXTRA_TITLE = "com.meraj.mediaplayer.VIDEO_TITLE"
        const val EXTRA_QUEUE_URIS = "com.meraj.mediaplayer.VIDEO_QUEUE_URIS"
        const val EXTRA_QUEUE_TITLES = "com.meraj.mediaplayer.VIDEO_QUEUE_TITLES"
        const val EXTRA_QUEUE_MIMES = "com.meraj.mediaplayer.VIDEO_QUEUE_MIMES"
    }
}
