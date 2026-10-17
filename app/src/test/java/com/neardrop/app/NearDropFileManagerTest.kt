package com.neardrop.app

import com.neardrop.app.core.NearDropFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearDropFileManagerTest {

    @Test
    fun folderNameIsNearDrop() {
        assertEquals("NearDrop", NearDropFileManager.FOLDER_NAME)
    }

    @Test
    fun testPhotoMimeTypes() {
        assertEquals("image/jpeg", NearDropFileManager.getMimeType("vacation.jpg"))
        assertEquals("image/jpeg", NearDropFileManager.getMimeType("photo.JPEG"))
        assertEquals("image/png", NearDropFileManager.getMimeType("screenshot.png"))
        assertEquals("image/webp", NearDropFileManager.getMimeType("banner.webp"))
        assertEquals("image/gif", NearDropFileManager.getMimeType("animation.gif"))
        assertEquals("image/heic", NearDropFileManager.getMimeType("portrait.heic"))
    }

    @Test
    fun testDocumentMimeTypes() {
        assertEquals("application/pdf", NearDropFileManager.getMimeType("contract.pdf"))
        assertEquals("application/msword", NearDropFileManager.getMimeType("notes.doc"))
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", NearDropFileManager.getMimeType("resume.docx"))
        assertEquals("text/plain", NearDropFileManager.getMimeType("readme.txt"))
        assertEquals("text/csv", NearDropFileManager.getMimeType("data.csv"))
    }

    @Test
    fun testZipAndArchiveMimeTypes() {
        assertEquals("application/zip", NearDropFileManager.getMimeType("bundle.zip"))
        assertEquals("application/x-rar-compressed", NearDropFileManager.getMimeType("archive.rar"))
        assertEquals("application/x-7z-compressed", NearDropFileManager.getMimeType("backup.7z"))
        assertEquals("application/x-tar", NearDropFileManager.getMimeType("files.tar"))
        assertEquals("application/gzip", NearDropFileManager.getMimeType("archive.tar.gz"))
    }

    @Test
    fun testMusicMimeTypes() {
        assertEquals("audio/mpeg", NearDropFileManager.getMimeType("track01.mp3"))
        assertEquals("audio/wav", NearDropFileManager.getMimeType("recording.wav"))
        assertEquals("audio/flac", NearDropFileManager.getMimeType("song.flac"))
        assertEquals("audio/mp4", NearDropFileManager.getMimeType("voice.m4a"))
        assertEquals("audio/ogg", NearDropFileManager.getMimeType("effect.ogg"))
    }

    @Test
    fun testVideoMimeTypes() {
        assertEquals("video/mp4", NearDropFileManager.getMimeType("clip.mp4"))
        assertEquals("video/x-matroska", NearDropFileManager.getMimeType("movie.mkv"))
    }
}
