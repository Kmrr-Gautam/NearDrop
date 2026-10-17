package com.neardrop.app.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.neardrop.app.core.FileEntry
import java.io.InputStream

/** A user-selected item before it is given a manifest index. */
data class SendItem(val uri: Uri, val relativePath: String, val size: Long, val mime: String?)

class UriSource(private val ctx: Context, private val uri: Uri, override val entry: FileEntry) : SendSource {
    override fun open(): InputStream = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open ${entry.path}")
}

object FileResolver {
    fun resolveFiles(ctx: Context, uris: List<Uri>): List<SendItem> = uris.mapNotNull { uri ->
        runCatching {
            var name = "file"; var size = -1L
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!c.isNull(it)) size = c.getLong(it) }
                }
            }
            if (size < 0) size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size < 0) return@runCatching null
            SendItem(uri, name.replace('/', '_'), size, ctx.contentResolver.getType(uri))
        }.getOrNull()
    }

    /** Walks a Storage Access Framework tree and preserves the folder structure as relative paths. */
    fun resolveTree(ctx: Context, tree: Uri): List<SendItem> {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        val out = ArrayList<SendItem>()
        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            if (depth > 30) return
            for (f in dir.listFiles()) {
                val n = (f.name ?: continue).replace('/', '_')
                if (f.isDirectory) walk(f, "$prefix$n/", depth + 1)
                else if (f.isFile) out.add(SendItem(f.uri, "$prefix$n", f.length(), f.type))
            }
        }
        walk(root, (root.name ?: "folder").replace('/', '_') + "/", 0)
        return out
    }

    fun toSources(ctx: Context, items: List<SendItem>): List<SendSource> =
        items.mapIndexed { i, it -> UriSource(ctx, it.uri, FileEntry(i, it.relativePath, it.size)) }
}
