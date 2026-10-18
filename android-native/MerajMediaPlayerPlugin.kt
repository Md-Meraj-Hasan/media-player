package com.meraj.mediaplayer

import android.content.ComponentName
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.common.util.concurrent.ListenableFuture

@CapacitorPlugin(name = "MerajMediaPlayer")
class MerajMediaPlayerPlugin : Plugin() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile private var controller: MediaController? = null
    private var listener: Player.Listener? = null

    override fun load() {
        super.load()
        mainHandler.post {
            val token = SessionToken(context, ComponentName(context, MerajPlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val c = future.get()
                    controller = c
                    val l = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            notifyListeners("trackChanged", JSObject().apply {
                                put("mediaId", mediaItem?.mediaId ?: "")
                                put("title", mediaItem?.mediaMetadata?.title?.toString() ?: "")
                                put("reason", reason)
                            })
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val p = controller
                            notifyListeners("playbackState", JSObject().apply {
                                put("state", playbackState)
                                put("isPlaying", p?.isPlaying == true)
                                put("position", p?.currentPosition ?: 0L)
                                put("duration", (p?.duration ?: 0L).coerceAtLeast(0L))
                            })
                            if (playbackState == Player.STATE_ENDED) notifyListeners("trackEnded", JSObject())
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            notifyListeners("playbackState", JSObject().apply {
                                put("state", controller?.playbackState ?: Player.STATE_IDLE)
                                put("isPlaying", isPlaying)
                                put("position", controller?.currentPosition ?: 0L)
                                put("duration", (controller?.duration ?: 0L).coerceAtLeast(0L))
                            })
                        }
                    }
                    listener = l
                    c.addListener(l)
                } catch (e: Exception) {
                    android.util.Log.e("MerajMediaPlayer", "Media session connection failed", e)
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        }
    }

    private fun withController(call: PluginCall, action: (MediaController) -> Unit) {
        mainHandler.post {
            val c = controller
            if (c == null) call.reject("Background media session is not ready. Please try again.")
            else try { action(c) } catch (e: Exception) { call.reject(e.message ?: "Media operation failed") }
        }
    }

    @PluginMethod
    fun setQueue(call: PluginCall) {
        val items = call.getArray("items")
        val startIndex = call.getInt("startIndex", 0) ?: 0
        if (items == null || items.length() == 0) { call.reject("A non-empty queue is required."); return }
        withController(call) { c ->
            val mediaItems = ArrayList<MediaItem>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val uri = item.optString("uri", "")
                if (uri.isBlank()) continue
                val title = item.optString("title", "Local audio")
                val artist = item.optString("artist", "Unknown artist")
                mediaItems.add(MediaItem.Builder().setMediaId(item.optString("id", uri)).setUri(Uri.parse(uri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build())
            }
            if (mediaItems.isEmpty()) { call.reject("No playable local media URIs were supplied."); return@withController }
            c.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            c.prepare()
            call.resolve(JSObject().put("count", mediaItems.size))
        }
    }

    @PluginMethod
    fun playAt(call: PluginCall) {
        val index = call.getInt("index")
        if (index == null || index < 0) { call.reject("A valid queue index is required."); return }
        withController(call) { c ->
            if (index >= c.mediaItemCount) { call.reject("Queue index is out of range."); return@withController }
            c.seekToDefaultPosition(index)
            c.play()
            call.resolve(JSObject().put("playing", true).put("index", index))
        }
    }

    @PluginMethod
    fun play(call: PluginCall) {
        val uri = call.getString("uri")
        if (uri.isNullOrBlank()) { call.reject("A local content URI is required."); return }
        withController(call) { c ->
            c.setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).build())
            c.prepare(); c.play()
            call.resolve(JSObject().put("accepted", true))
        }
    }

    @PluginMethod fun getState(call: PluginCall) = withController(call) { p ->
        call.resolve(JSObject().apply { put("isPlaying", p.isPlaying); put("position", p.currentPosition); put("duration", p.duration.coerceAtLeast(0L)); put("playbackState", p.playbackState); put("currentIndex", p.currentMediaItemIndex); put("mediaId", p.currentMediaItem?.mediaId ?: "") })
    }
    @PluginMethod fun setRepeatOne(call: PluginCall) = withController(call) { p ->
        val enabled = call.getBoolean("enabled", false) ?: false
        p.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        call.resolve(JSObject().put("enabled", enabled))
    }
    @PluginMethod fun pause(call: PluginCall) = withController(call) { p -> p.pause(); call.resolve(JSObject().put("paused", true)) }
    @PluginMethod fun resume(call: PluginCall) = withController(call) { p -> p.play(); call.resolve(JSObject().put("playing", true)) }
    @PluginMethod fun seekTo(call: PluginCall) = withController(call) { p ->
        val position = call.getLong("positionMs")
        if (position == null || position < 0) call.reject("positionMs must be non-negative.")
        else { p.seekTo(position); call.resolve(JSObject().put("positionMs", position)) }
    }

    override fun handleOnDestroy() {
        mainHandler.post {
            listener?.let { controller?.removeListener(it) }
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            controller = null
        }
        super.handleOnDestroy()
    }
}
