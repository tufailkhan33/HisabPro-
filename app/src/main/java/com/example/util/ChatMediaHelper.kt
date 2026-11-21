package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class EncodedAttachment(
    val base64: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

object ChatMediaHelper {
    private const val MAX_RAW_ATTACHMENT_BYTES = 450_000L
    private const val MAX_BASE64_LENGTH = 700_000

    fun createCameraImageUri(context: Context): Uri {
        val camDir = File(context.cacheDir, "chat_camera").apply { mkdirs() }
        val photoFile = File(camDir, "IMG_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    fun decodeBase64ToBytes(base64: String): ByteArray {
        if (base64.isBlank()) return ByteArray(0)
        return Base64.decode(base64, Base64.NO_WRAP)
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = decodeBase64ToBytes(base64)
            if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeImageUriToBase64(context: Context, uri: Uri): Result<EncodedAttachment> = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val maxDim = 960
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: throw IllegalArgumentException("Could not decode selected image.")

        var quality = 78
        var jpegBytes: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            jpegBytes = baos.toByteArray()
            quality -= 12
        } while (jpegBytes.size > 320_000 && quality >= 30)

        bitmap.recycle()
        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        require(encoded.length <= MAX_BASE64_LENGTH) { "Image is too large even after compression." }

        EncodedAttachment(
            base64 = encoded,
            fileName = resolveDisplayName(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            sizeBytes = jpegBytes.size.toLong()
        )
    }

    fun readFileUriToBase64(context: Context, uri: Uri): Result<EncodedAttachment> = runCatching {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected file.")
        require(bytes.isNotEmpty()) { "Selected file is empty." }
        require(bytes.size <= MAX_RAW_ATTACHMENT_BYTES) {
            "File size (${bytes.size / 1024} KB) exceeds the 450 KB group chat upload limit."
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(encoded.length <= MAX_BASE64_LENGTH) { "Encoded file exceeds maximum message size." }

        val name = resolveDisplayName(context, uri) ?: "document_${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        EncodedAttachment(
            base64 = encoded,
            fileName = name,
            mimeType = mime,
            sizeBytes = bytes.size.toLong()
        )
    }

    fun saveChatMediaToDownloads(
        context: Context,
        base64: String,
        fileName: String,
        mimeType: String
    ): Result<String> = runCatching {
        val bytes = decodeBase64ToBytes(base64)
        require(bytes.isNotEmpty()) { "Attachment has no content." }
        val cleanName = fileName.ifBlank { "hisabpro_file_${System.currentTimeMillis()}" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeMime = mimeType.ifBlank { "application/octet-stream" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                put(MediaStore.Downloads.MIME_TYPE, safeMime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HisabPro/ChatFiles")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not save file to Downloads.")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Failed to write file bytes.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Downloads/HisabPro/ChatFiles/$cleanName"
        } else {
            val appDir = File(context.filesDir, "downloads/ChatFiles").apply { mkdirs() }
            val dest = File(appDir, cleanName)
            FileOutputStream(dest).use { it.write(bytes) }
            "Downloads/HisabPro/ChatFiles/$cleanName"
        }
    }

    fun openChatFile(context: Context, base64: String, fileName: String, mimeType: String) {
        val bytes = decodeBase64ToBytes(base64)
        if (bytes.isEmpty()) return
        val dir = File(context.cacheDir, "chat_media").apply { mkdirs() }
        val cleanName = fileName.ifBlank { "attachment_${System.currentTimeMillis()}" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(dir, cleanName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(share, cleanName).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

class AudioVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startMillis: Long = 0L

    fun startRecording(): Result<Unit> = runCatching {
        stopSilently()
        val dir = File(context.cacheDir, "chat_media").apply { mkdirs() }
        val file = File(dir, "VOICE_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(24000)
            setMaxDuration(120_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        outputFile = file
        startMillis = System.currentTimeMillis()
    }

    fun stopAndEncode(): Result<Triple<String, Int, Long>> = runCatching {
        val file = outputFile ?: throw IllegalStateException("No recording active.")
        val rec = recorder ?: throw IllegalStateException("Recorder not started.")
        val elapsedSec = (((System.currentTimeMillis() - startMillis) + 500L) / 1000L).toInt().coerceAtLeast(1)
        try {
            rec.stop()
        } finally {
            rec.release()
            recorder = null
            outputFile = null
        }
        val bytes = file.readBytes()
        file.delete()
        require(bytes.isNotEmpty()) { "Voice recording was empty." }
        require(bytes.size <= 450_000) { "Voice note is too long. Please keep voice notes under 90 seconds." }
        Triple(Base64.encodeToString(bytes, Base64.NO_WRAP), elapsedSec, bytes.size.toLong())
    }

    fun stopSilently() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class AudioVoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var activeMessageId: String? = null

    fun playOrToggle(
        messageId: String,
        base64: String,
        onStateChange: (playingId: String?) -> Unit
    ) {
        if (activeMessageId == messageId && player?.isPlaying == true) {
            stop()
            onStateChange(null)
            return
        }
        stop()
        try {
            val bytes = ChatMediaHelper.decodeBase64ToBytes(base64)
            if (bytes.isEmpty()) {
                onStateChange(null)
                return
            }
            val dir = File(context.cacheDir, "chat_media").apply { mkdirs() }
            val temp = File(dir, "play_${messageId}.m4a")
            FileOutputStream(temp).use { it.write(bytes) }

            val mp = MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                setOnCompletionListener {
                    stop()
                    onStateChange(null)
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onStateChange(null)
                    true
                }
                prepare()
                start()
            }
            player = mp
            activeMessageId = messageId
            onStateChange(messageId)
        } catch (_: Exception) {
            stop()
            onStateChange(null)
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        activeMessageId = null
    }
}
