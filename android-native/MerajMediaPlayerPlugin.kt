package com.meraj.mediaplayer

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@CapacitorPlugin(name = "MerajMediaPlayer")
class MerajMediaPlayerPlugin : Plugin() {
    private val handler = Handler(Looper.getMainLooper())
    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private data class Track(val uri: String, val title: String, val artist: String, val id: String)
    private val queue = mutableListOf<Track>()
    private var currentIndex = -1
    private var repeatOne = false
    private var speed = 1.0f

    override fun load() {
        super.load()
        handler.post {
            try {
                libVLC = LibVLC(context, arrayListOf("--no-video-title-show", "--audio-time-stretch"))
                player = MediaPlayer(libVLC).also { p ->
                    p.setEventListener { event ->
                        when (event.type) {
                            MediaPlayer.Event.Playing, MediaPlayer.Event.Paused,
                            MediaPlayer.Event.Stopped, MediaPlayer.Event.TimeChanged,
                            MediaPlayer.Event.LengthChanged -> emitState()
                            MediaPlayer.Event.EndReached -> {
                                notifyListeners("trackEnded", JSObject())
                                if (repeatOne) playCurrent() else playNext()
                            }
                            MediaPlayer.Event.EncounteredError -> android.util.Log.e("MerajMediaPlayer", "LibVLC encountered playback error at index=$currentIndex uri=${queue.getOrNull(currentIndex)?.uri}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MerajMediaPlayer", "LibVLC initialization failed", e)
            }
        }
    }

    private fun emitState() {
        val p = player ?: return
        val media = p.media
        val state = JSObject().apply {
            put("isPlaying", p.isPlaying)
            put("position", p.time.coerceAtLeast(0L))
            put("duration", (media?.duration ?: 0L).coerceAtLeast(0L))
            put("playbackState", if (p.isPlaying) 3 else if (p.time > 0) 2 else 1)
            put("currentIndex", currentIndex)
            put("mediaId", queue.getOrNull(currentIndex)?.id ?: "")
        }
        notifyListeners("playbackState", state)
    }

    private fun openTrack(index: Int, autoplay: Boolean = true) {
        if (index !in queue.indices) return
        currentIndex = index
        val p = player ?: return
        val track = queue[index]
        val resolved = try { LibVlcUriResolver.resolve(context, track.uri) } catch (e: Exception) {
            android.util.Log.e("MerajMediaPlayer", "Cannot resolve media URI=${track.uri}", e)
            notifyListeners("playbackError", JSObject().put("message", e.message ?: "Cannot access selected media"))
            return
        }
        android.util.Log.i("MerajMediaPlayer", "Opening audio: scheme=${resolved.scheme}, path=${resolved.lastPathSegment}, index=$index")
        val media = Media(libVLC, resolved)
        media.setEventListener { event -> android.util.Log.i("MerajMediaPlayer", "Media event type=${event.type}, uri=${track.uri}") }
        p.media = media
        media.release()
        p.setRate(speed)
        if (autoplay) p.play()
        notifyListeners("trackChanged", JSObject().apply {
            put("mediaId", track.id); put("title", track.title); put("reason", 0)
        })
        emitState()
    }

    private fun playCurrent() { if (currentIndex in queue.indices) openTrack(currentIndex) }
    private fun playNext() {
        if (queue.isEmpty()) return
        val next = if (currentIndex + 1 < queue.size) currentIndex + 1 else if (repeatOne) currentIndex else -1
        if (next >= 0) openTrack(next) else { player?.stop(); emitState() }
    }
    private fun withPlayer(call: PluginCall, action: (MediaPlayer) -> Unit) {
        handler.post {
            val p = player
            if (p == null) call.reject("LibVLC player is not ready.")
            else try { action(p) } catch (e: Exception) { call.reject(e.message ?: "LibVLC operation failed") }
        }
    }

    @PluginMethod
    fun setQueue(call: PluginCall) {
        val items = call.getArray("items")
        if (items == null || items.length() == 0) { call.reject("A non-empty queue is required."); return }
        val parsed = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val uri = item.optString("uri", "")
            if (uri.isBlank()) continue
            parsed.add(Track(uri, item.optString("title", "Local audio"),
                item.optString("artist", "Unknown artist"), item.optString("id", uri)))
        }
        if (parsed.isEmpty()) { call.reject("No playable local media URIs were supplied."); return }
        handler.post {
            queue.clear(); queue.addAll(parsed)
            val index = (call.getInt("startIndex", 0) ?: 0).coerceIn(0, queue.lastIndex)
            openTrack(index, false)
            call.resolve(JSObject().put("count", queue.size))
        }
    }

    @PluginMethod fun playAt(call: PluginCall) {
        val index = call.getInt("index")
        if (index == null || index < 0) { call.reject("A valid queue index is required."); return }
        handler.post {
            if (index !in queue.indices) call.reject("Queue index is out of range.")
            else { openTrack(index); call.resolve(JSObject().put("playing", true).put("index", index)) }
        }
    }
    @PluginMethod fun play(call: PluginCall) {
        val uri = call.getString("uri")
        if (uri.isNullOrBlank()) { call.reject("A local content URI is required."); return }
        handler.post {
            queue.clear()
            queue.add(Track(uri, call.getString("title") ?: "Local media", "", uri))
            openTrack(0)
            call.resolve(JSObject().put("accepted", true))
        }
    }
    @PluginMethod fun getState(call: PluginCall) = withPlayer(call) { p ->
        call.resolve(JSObject().apply {
            put("isPlaying", p.isPlaying); put("position", p.time.coerceAtLeast(0L))
            put("duration", (p.media?.duration ?: 0L).coerceAtLeast(0L))
            put("playbackState", if (p.isPlaying) 3 else 1); put("currentIndex", currentIndex)
            put("mediaId", queue.getOrNull(currentIndex)?.id ?: "")
        })
    }
    @PluginMethod fun setRepeatOne(call: PluginCall) {
        repeatOne = call.getBoolean("enabled", false) ?: false
        call.resolve(JSObject().put("enabled", repeatOne))
    }
    @PluginMethod fun pause(call: PluginCall) = withPlayer(call) { it.pause(); call.resolve(JSObject().put("paused", true)) }
    @PluginMethod fun resume(call: PluginCall) = withPlayer(call) { it.play(); call.resolve(JSObject().put("playing", true)) }
    @PluginMethod fun seekTo(call: PluginCall) = withPlayer(call) { p ->
        val raw = call.data.opt("positionMs")
        val pos = when (raw) { is Number -> raw.toLong(); is String -> raw.toLongOrNull(); else -> null }
        if (pos == null || pos < 0) call.reject("positionMs must be a non-negative number.")
        else { p.time = pos; call.resolve(JSObject().put("positionMs", pos).put("accepted", true)) }
    }
    @PluginMethod fun setPlaybackSpeed(call: PluginCall) = withPlayer(call) { p ->
        val v = call.getDouble("speed")
        if (v == null || !v.isFinite() || v < 0.5 || v > 2.0) call.reject("Playback speed must be between 0.5 and 2.0.")
        else { speed = v.toFloat(); p.setRate(speed); call.resolve(JSObject().put("speed", v).put("accepted", true)) }
    }
    @PluginMethod fun getEqualizerPresets(call: PluginCall) {
        call.resolve(JSObject().put("presets", JSArray()))
    }
    @PluginMethod fun setEqualizerPreset(call: PluginCall) {
        call.reject("Equalizer presets are not implemented in this LibVLC migration yet.")
    }
    override fun handleOnDestroy() {
        handler.post {
            player?.stop()
            player?.release(); player = null
            libVLC?.release(); libVLC = null
            queue.clear()
        }
        super.handleOnDestroy()
    }
}
