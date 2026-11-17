package com.example.contacts.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Les URI de galerie sont temporaires : on copie toujours l'image dans
 * filesDir/photos pour qu'elle survive au redémarrage.
 */
object PhotoStorage {

    private const val PHOTOS_DIR = "photos"

    private fun photosDir(context: Context): File =
        File(context.filesDir, PHOTOS_DIR).apply {
            if (!exists() && !mkdirs()) throw IOException("Impossible de créer le dossier photos")
        }

    fun createImageFile(context: Context, prefix: String): File {
        val suffix = UUID.randomUUID().toString().take(8)
        return File(photosDir(context), "${prefix}_${System.currentTimeMillis()}_$suffix.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun copyFromUri(context: Context, source: Uri, prefix: String): String? {
        return try {
            val target = createImageFile(context, prefix)
            val resolver: ContentResolver = context.contentResolver
            resolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteIfInternal(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        val root = context.filesDir.absolutePath + File.separator
        if (!path.startsWith(root)) return
        try {
            File(path).takeIf { it.exists() }?.delete()
        } catch (_: Exception) {
            // Nettoyage best-effort : une photo orpheline n'empêche pas l'enregistrement.
        }
    }

    /** Décodage avec sous-échantillonnage pour éviter les OOM sur de grandes photos. */
    fun loadBitmap(path: String?, maxSize: Int = 512): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > maxSize) sample *= 2

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
