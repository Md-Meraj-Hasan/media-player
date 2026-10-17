package com.meraj.mediaplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Native playback core using Media3/ExoPlayer.
 * Format/container detection is automatic: callers provide a URI, not a manually selected format.
 *
 * Audio-only playback bridge. The web UI controls this player through Capacitor methods.
 */
@CapacitorPlugin(name = "MerajMediaPlayer")
class MerajMediaPlayerPlugin : Plugin() {
    private var player: ExoPlayer? = null

    override fun load() {
        super.load()
        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    notifyListeners("playbackError", JSObject().apply {
                        put("message", error.message ?: "This file's codec/container is not supported on this device.")
                        put("errorCode", error.errorCode)
                    })
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    notifyListeners("playbackState", JSObject().apply {
                        put("state", playbackState)
                        put("isPlaying", this@MerajMediaPlayerPlugin.player?.isPlaying == true)
                        put("position", this@MerajMediaPlayerPlugin.player?.currentPosition ?: 0L)
                        put("duration", (this@MerajMediaPlayerPlugin.player?.duration ?: 0L).coerceAtLeast(0L))
                    })
                }
            })
        }
    }

    @PluginMethod
    fun play(call: PluginCall) {
        val uriText = call.getString("uri")
        if (uriText.isNullOrBlank()) {
            call.reject("A local content URI is required.")
            return
        }
        try {
            val item = MediaItem.fromUri(Uri.parse(uriText))
            player?.setMediaItem(item)
            player?.prepare()
            player?.playWhenReady = true
            call.resolve(JSObject().put("accepted", true))
        } catch (e: Exception) {
            call.reject("Unable to open media: ${e.message}")
        }
    }

    @PluginMethod
    fun getState(call: PluginCall) {
        val p = player
        call.resolve(JSObject().apply {
            put("isPlaying", p?.isPlaying == true)
            put("position", p?.currentPosition ?: 0L)
            put("duration", (p?.duration ?: 0L).coerceAtLeast(0L))
            put("playbackState", p?.playbackState ?: Player.STATE_IDLE)
        })
    }

    @PluginMethod
    fun setRepeatOne(call: PluginCall) {
        val enabled = call.getBoolean("enabled", false) ?: false
        player?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        call.resolve(JSObject().put("enabled", enabled))
    }

    @PluginMethod
    fun pause(call: PluginCall) {
        player?.pause()
        call.resolve(JSObject().put("paused", true))
    }

    @PluginMethod
    fun resume(call: PluginCall) {
        player?.play()
        call.resolve(JSObject().put("playing", true))
    }

    @PluginMethod
    fun seekTo(call: PluginCall) {
        val position = call.getLong("positionMs")
        if (position == null || position < 0) {
            call.reject("positionMs must be a non-negative number.")
            return
        }
        player?.seekTo(position)
        call.resolve(JSObject().put("positionMs", position))
    }

    override fun handleOnDestroy() {
        player?.release()
        player = null
        super.handleOnDestroy()
    }
}
