package com.meraj.mediaplayer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Converts granted content:// URIs into app-owned files that LibVLC can reliably read. */
object LibVlcUriResolver {
    private const val TAG = "MerajVlcUri"
    fun resolve(context: Context, raw: String): Uri {
        val uri = Uri.parse(raw)
        if (uri.scheme != "content") return uri
        val resolver = context.contentResolver
        var displayName = "media"
        var mime: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) displayName = c.getString(i) ?: displayName
                }
            }
        } catch (e: Exception) { Log.w(TAG, "Could not query display name for $uri", e) }
        try { mime = resolver.getType(uri) } catch (e: Exception) { Log.w(TAG, "Could not query MIME type for $uri", e) }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val ext = displayName.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: when (mime) {
                "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/3gpp" -> "3gp"
                "audio/mpeg" -> "mp3"; "audio/mp4" -> "m4a"; "audio/aac" -> "aac"
                "audio/ogg" -> "ogg"; "audio/flac" -> "flac"; "audio/wav", "audio/x-wav" -> "wav"
                else -> "bin"
            }
        val dir = File(context.cacheDir, "vlc-media").apply { mkdirs() }
        val target = File(dir, "$digest.$ext")
        if (target.isFile && target.length() > 0) {
            Log.i(TAG, "Using cached media file ${target.name}, bytes=${target.length()}, mime=$mime")
            return Uri.fromFile(target)
        }
        val temp = File(dir, "$digest.tmp")
        Log.i(TAG, "Copying content URI to app cache: name=$displayName mime=$mime uri=$uri")
        resolver.openInputStream(uri)?.use { input -> FileOutputStream(temp).use { output -> input.copyTo(output) } }
            ?: throw IllegalStateException("Android could not open selected media URI: $uri")
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        if (!target.isFile || target.length() == 0L) throw IllegalStateException("Selected media copied as an empty file")
        Log.i(TAG, "Resolved media file ${target.absolutePath}, bytes=${target.length()}")
        return Uri.fromFile(target)
    }
}
