package com.neardrop.app.core

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object NearDropFileManager {
    const val FOLDER_NAME = "NearDrop"

    /**
     * Resolves the "NearDrop" folder inside the public Download directory
     * so that it is visible in the device's File Manager.
     */
    fun getNearDropFolder(context: Context): File {
        val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val nearDropDir = File(publicDownload, FOLDER_NAME)
        if (!nearDropDir.exists()) {
            nearDropDir.mkdirs()
        }
        return if (nearDropDir.exists()) {
            nearDropDir
        } else {
            val fallback = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(null)
                    ?: context.filesDir,
                FOLDER_NAME
            )
            fallback.mkdirs()
            fallback
        }
    }

    /**
     * Detects MIME type for photo, doc, zip, music, video, or generic files.
     */
    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mimeFromMap = runCatching {
            if (ext.isNotEmpty()) MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext) else null
        }.getOrNull()
        if (!mimeFromMap.isNullOrBlank()) return mimeFromMap

        return when (ext) {
            // Photos / Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"

            // Documents ("doz")
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "epub" -> "application/epub+zip"

            // Archives / Zip
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "bz2" -> "application/x-bzip2"
            "xz" -> "application/x-xz"

            // Music / Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"

            // Video
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"

            else -> "application/octet-stream"
        }
    }

    /**
     * Ensures the received file is saved in the "NearDrop" folder in the device's File Manager,
     * and triggers MediaScanner so photos, music, docs, zip etc. appear immediately in
     * the File Manager, Gallery, and Music apps.
     */
    fun saveToNearDropFolder(context: Context, sourceFile: File, relativePath: String): File {
        val nearDropFolder = getNearDropFolder(context)
        val cleanRel = PathSanitizer.sanitize(relativePath)
        val targetFile = FileNames.uniqueFile(nearDropFolder, cleanRel)

        var finalFile: File = targetFile
        var directSaveSuccess = false

        if (sourceFile.canonicalPath == targetFile.canonicalPath && targetFile.exists()) {
            directSaveSuccess = true
        } else {
            try {
                targetFile.parentFile?.mkdirs()
                if (sourceFile.exists()) {
                    sourceFile.copyTo(targetFile, overwrite = true)
                    sourceFile.delete()
                    directSaveSuccess = true
                }
            } catch (_: Exception) {
                directSaveSuccess = false
            }
        }

        // On Android 10+ (API 29+), if direct file write was restricted by Scoped Storage,
        // use MediaStore.Downloads with RELATIVE_PATH = "Download/NearDrop"
        if (!directSaveSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sourceFile.exists()) {
            try {
                val mime = getMimeType(targetFile.name)
                val relSubDir = cleanRel.substringBeforeLast('/', "").ifBlank { "" }
                val mediaRelPath = if (relSubDir.isNotEmpty()) "Download/$FOLDER_NAME/$relSubDir" else "Download/$FOLDER_NAME"

                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, targetFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, mediaRelPath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { inp -> inp.copyTo(out) }
                    }
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, cv, null, null)
                    sourceFile.delete()
                    finalFile = targetFile
                }
            } catch (_: Exception) {
                finalFile = sourceFile
            }
        } else if (!directSaveSuccess) {
            finalFile = sourceFile
        }

        // Trigger MediaScanner so Gallery, Music player, and File Manager index the file
        if (finalFile.exists()) {
            runCatching {
                val mime = getMimeType(finalFile.name)
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(finalFile.absolutePath),
                    arrayOf(mime),
                    null
                )
            }
        }

        return finalFile
    }

    /**
     * Intent to open the NearDrop folder in the user's File Manager.
     */
    fun getFolderIntent(context: Context): Intent? {
        val folder = getNearDropFolder(context)
        try {
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F$FOLDER_NAME")
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/root")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}

        return Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the "NearDrop" folder in the device's File Manager app.
     */
    fun openNearDropFolder(context: Context) {
        val folder = getNearDropFolder(context)
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F$FOLDER_NAME"),
                    "vnd.android.document/root"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                val contentUri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
                }.getOrNull()
                if (contentUri != null) {
                    setDataAndType(contentUri, "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        )

        for (intent in attempts) {
            try {
                if (intent.data != null || intent.action == DownloadManager.ACTION_VIEW_DOWNLOADS) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }

        Toast.makeText(context, "NearDrop folder: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
    }

    /**
     * Opens a specific received file with its default app (Gallery, Music, PDF reader, etc.).
     */
    fun openFile(context: Context, relativeOrFileName: String) {
        val folder = getNearDropFolder(context)
        val rawName = relativeOrFileName.trim().removePrefix("/")
        val file = File(folder, rawName).let {
            if (it.exists()) it
            else {
                val abs = File(rawName)
                if (abs.exists()) abs else it
            }
        }

        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
