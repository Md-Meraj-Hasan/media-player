package com.meraj.mediaplayer

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.provider.MediaStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MediaLibraryScanner")
class MediaLibraryScannerPlugin : Plugin() {

    @PluginMethod
    fun shareMedia(call: PluginCall) {
        val rawUri = call.getString("uri")
        if (rawUri.isNullOrBlank()) { call.reject("Missing media URI"); return }
        try {
            val uri = Uri.parse(rawUri)
            val mimeType = call.getString("mimeType")?.takeIf { it.contains("/") } ?: "*/*"
            val name = call.getString("name", "Media file") ?: "Media file"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(context.contentResolver, name, uri)
            }
            activity.runOnUiThread { activity.startActivity(android.content.Intent.createChooser(intent, "Share")) }
            call.resolve(JSObject().put("opened", true))
        } catch (e: Exception) { call.reject("Share error: ${e.message}", e) }
    }

    @PluginMethod
    fun scan(call: PluginCall) {
        val type = call.getString("type", "audio") ?: "audio"
        val resolver = context.contentResolver
        val items = JSArray()
        val collection = if (type == "video") MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = if (type == "video") arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DURATION, MediaStore.MediaColumns.RELATIVE_PATH)
                         else arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.MediaColumns.RELATIVE_PATH)
        
        try {
            resolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val durCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val item = JSObject()
                    item.put("id", id.toString())
                    item.put("uri", ContentUris.withAppendedId(collection, id).toString())
                    item.put("name", cursor.getString(nameCol) ?: "Unknown")
                    item.put("relativePath", if (pathCol >= 0) cursor.getString(pathCol) else "Device")
                    item.put("duration", if (durCol >= 0) cursor.getLong(durCol) else 0L)
                    items.put(item)
                }
            }
            call.resolve(JSObject().put("items", items).put("type", type))
        } catch (e: Exception) { call.reject("Scan failed: ${e.message}", e) }
    }

    @PluginMethod
    fun getThumbnail(call: PluginCall) {
        val rawUri = call.getString("uri") ?: return call.resolve(JSObject().put("thumbnail", ""))
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(rawUri))
            val frame = retriever.getFrameAtTime(1_000_000L) ?: retriever.getFrameAtTime(0L)
            if (frame == null) { call.resolve(JSObject().put("thumbnail", "")); return }
            val bytes = ByteArrayOutputStream()
            frame.compress(Bitmap.CompressFormat.JPEG, 70, bytes)
            val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            frame.recycle()
            call.resolve(JSObject().put("thumbnail", "data:image/jpeg;base64,$encoded"))
        } catch (e: Exception) { call.resolve(JSObject().put("thumbnail", "")) } 
        finally { try { retriever.release() } catch (_: Exception) {} }
    }

    @PluginMethod
    fun launchVideo(call: PluginCall) {
        val rawUri = call.getString("uri") ?: return call.reject("Missing URI")
        try {
            val intent = android.content.Intent(context, NativeVideoPlayerActivity::class.java).apply {
                putExtra(NativeVideoPlayerActivity.EXTRA_URI, rawUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
            call.resolve(JSObject().put("opened", true))
        } catch (e: Exception) { call.reject("Launch failed: ${e.message}", e) }
    }
}
