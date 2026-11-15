package com.example.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.DownloadTaskEntity
import com.example.domain.model.AnalyzedVideo
import com.example.domain.model.AppSettings
import com.example.domain.model.DownloadStatus
import com.example.domain.model.StorageLocationMode
import com.example.domain.model.VideoQualityOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadEngineRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pauseRequestedIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelRequestedIds = ConcurrentHashMap.newKeySet<String>()

    val activeTasksFlow: Flow<List<DownloadTaskEntity>> = dao.observeTasks()
    val historyFlow: Flow<List<DownloadHistoryEntity>> = dao.observeHistory()

    init {
        ensureNotificationChannel()
    }

    suspend fun startDownload(
        video: AnalyzedVideo,
        quality: VideoQualityOption,
        settings: AppSettings
    ): Result<String> {
        if (settings.wifiOnlyDownloads && !isOnUnmeteredWifi()) {
            return Result.failure(
                IllegalStateException("Wi-Fi Only Downloads is enabled in Settings. Connect to Wi-Fi or disable the restriction.")
            )
        }
        if (!isNetworkAvailable()) {
            return Result.failure(
                IOException("No active internet connection. Connect to a network and try again.")
            )
        }

        val taskId = UUID.randomUUID().toString()
        val safeBase = video.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "")
            .trim()
            .replace(' ', '_')
            .take(48)
            .ifBlank { "authorized_video" }
        val ext = quality.format.lowercase().ifBlank { "mp4" }
        val qualityTag = quality.label.replace(' ', '_')
        val fileName = "${safeBase}_${qualityTag}_${taskId.take(6)}.$ext"
        val tempDir = File(context.cacheDir, "vdp_downloads").apply { mkdirs() }
        val tempFile = File(tempDir, "$taskId.part")

        val task = DownloadTaskEntity(
            id = taskId,
            title = video.title,
            channelName = video.channelName,
            thumbnailUrl = video.thumbnailUrl,
            sourceUrl = video.sourceUrl,
            downloadUrl = quality.downloadUrl,
            fileName = fileName,
            mimeType = quality.mimeType,
            qualityLabel = quality.label,
            isShort = video.isShort,
            totalBytes = quality.sizeBytes,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            status = DownloadStatus.QUEUED.name,
            supportsRange = quality.supportsRange,
            localTempPath = tempFile.absolutePath,
            errorMessage = null
        )

        dao.upsertTask(task)
        launchDownloadJob(taskId, settings)
        return Result.success(taskId)
    }

    fun pauseDownload(taskId: String) {
        pauseRequestedIds.add(taskId)
        activeJobs[taskId]?.cancel()
    }

    suspend fun resumeDownload(taskId: String, settings: AppSettings): Result<Unit> {
        if (settings.wifiOnlyDownloads && !isOnUnmeteredWifi()) {
            return Result.failure(
                IllegalStateException("Wi-Fi Only Downloads is enabled. Connect to Wi-Fi to resume.")
            )
        }
        if (!isNetworkAvailable()) {
            return Result.failure(
                IOException("No active internet connection to resume download.")
            )
        }
        val task = dao.getTaskById(taskId)
            ?: return Result.failure(IllegalStateException("Download task not found."))
        pauseRequestedIds.remove(taskId)
        cancelRequestedIds.remove(taskId)
        dao.upsertTask(
            task.copy(
                status = DownloadStatus.QUEUED.name,
                errorMessage = null
            )
        )
        launchDownloadJob(taskId, settings)
        return Result.success(Unit)
    }

    fun cancelDownload(taskId: String) {
        cancelRequestedIds.add(taskId)
        pauseRequestedIds.remove(taskId)
        activeJobs[taskId]?.cancel()
        scope.launch {
            val task = dao.getTaskById(taskId)
            if (task != null) {
                runCatching { File(task.localTempPath).delete() }
                dao.deleteTaskById(taskId)
            }
            cancelRequestedIds.remove(taskId)
        }
    }

    private fun launchDownloadJob(taskId: String, settings: AppSettings) {
        activeJobs[taskId]?.cancel()
        val job = scope.launch {
            executeDownload(taskId, settings)
        }
        activeJobs[taskId] = job
        job.invokeOnCompletion {
            activeJobs.remove(taskId)
        }
    }

    private suspend fun executeDownload(taskId: String, settings: AppSettings) {
        val initialTask = dao.getTaskById(taskId) ?: return
        val tempFile = File(initialTask.localTempPath)
        tempFile.parentFile?.mkdirs()

        var existingBytes = if (initialTask.supportsRange && tempFile.exists()) {
            tempFile.length()
        } else {
            if (tempFile.exists()) tempFile.delete()
            0L
        }

        dao.upsertTask(
            initialTask.copy(
                status = DownloadStatus.DOWNLOADING.name,
                downloadedBytes = existingBytes,
                errorMessage = null
            )
        )

        try {
            val requestBuilder = Request.Builder().url(initialTask.downloadUrl)
            if (existingBytes > 0L && initialTask.supportsRange) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("Server returned HTTP ${response.code}")
                }

                val isPartial = response.code == 206
                if (!isPartial && existingBytes > 0L) {
                    // Server ignored Range and restarted from byte 0
                    existingBytes = 0L
                    if (tempFile.exists()) tempFile.delete()
                }

                val body = response.body ?: throw IOException("Empty media stream body.")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    isPartial && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    initialTask.totalBytes > 0L -> initialTask.totalBytes
                    else -> -1L
                }
                val serverSupportsRange = isPartial ||
                    response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true ||
                    initialTask.supportsRange

                var bytesWritten = existingBytes
                var windowBytes = 0L
                var windowStartMs = System.currentTimeMillis()
                var lastPersistMs = windowStartMs

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, isPartial).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (pauseRequestedIds.contains(taskId) || cancelRequestedIds.contains(taskId)) {
                                throw CancellationException("User paused or cancelled")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            windowBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastPersistMs >= 300L) {
                                val elapsedSec = ((now - windowStartMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (windowBytes / elapsedSec).toLong()
                                dao.upsertTask(
                                    initialTask.copy(
                                        totalBytes = totalBytes,
                                        downloadedBytes = bytesWritten,
                                        speedBytesPerSec = speed,
                                        status = DownloadStatus.DOWNLOADING.name,
                                        supportsRange = serverSupportsRange
                                    )
                                )
                                lastPersistMs = now
                                if (now - windowStartMs >= 1000L) {
                                    windowBytes = 0L
                                    windowStartMs = now
                                }
                            }
                        }
                        output.flush()
                    }
                }

                // Persist completed file to Android Storage (MediaStore or Custom SAF folder)
                val savedUri = saveCompletedFileToStorage(
                    tempFile = tempFile,
                    fileName = initialTask.fileName,
                    mimeType = initialTask.mimeType,
                    settings = settings
                )
                val finalSize = tempFile.length()
                tempFile.delete()

                val historyItem = DownloadHistoryEntity(
                    id = initialTask.id,
                    title = initialTask.title,
                    channelName = initialTask.channelName,
                    thumbnailUrl = initialTask.thumbnailUrl,
                    fileName = initialTask.fileName,
                    mimeType = initialTask.mimeType,
                    qualityLabel = initialTask.qualityLabel,
                    isShort = initialTask.isShort,
                    fileSizeBytes = finalSize,
                    contentUri = savedUri.toString(),
                    sourceUrl = initialTask.sourceUrl
                )

                dao.insertHistory(historyItem)
                dao.deleteTaskById(initialTask.id)
                postCompletionNotification(initialTask.title, initialTask.qualityLabel)
            }
        } catch (ce: CancellationException) {
            if (cancelRequestedIds.contains(taskId)) {
                tempFile.delete()
                dao.deleteTaskById(taskId)
                cancelRequestedIds.remove(taskId)
            } else if (pauseRequestedIds.contains(taskId)) {
                pauseRequestedIds.remove(taskId)
                val latest = dao.getTaskById(taskId) ?: initialTask
                dao.upsertTask(
                    latest.copy(
                        downloadedBytes = if (tempFile.exists()) tempFile.length() else latest.downloadedBytes,
                        speedBytesPerSec = 0L,
                        status = DownloadStatus.PAUSED.name
                    )
                )
            }
            throw ce
        } catch (e: Exception) {
            val latest = dao.getTaskById(taskId) ?: initialTask
            dao.upsertTask(
                latest.copy(
                    downloadedBytes = if (tempFile.exists()) tempFile.length() else latest.downloadedBytes,
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = e.localizedMessage ?: "Network interruption during download."
                )
            )
        }
    }

    private fun saveCompletedFileToStorage(
        tempFile: File,
        fileName: String,
        mimeType: String,
        settings: AppSettings
    ): Uri {
        if (settings.storageLocationMode == StorageLocationMode.CUSTOM_SAF_FOLDER &&
            !settings.customFolderUri.isNullOrBlank()
        ) {
            val treeUri = Uri.parse(settings.customFolderUri)
            val createdUri = runCatching {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    fileName
                )
            }.getOrNull()

            if (createdUri != null) {
                context.contentResolver.openOutputStream(createdUri)?.use { out ->
                    FileInputStream(tempFile).use { input -> input.copyTo(out) }
                } ?: throw IOException("Could not write to selected custom folder.")
                return createdUri
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VideoDownloaderPro")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore download entry.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempFile).use { input -> input.copyTo(out) }
                } ?: throw IOException("Failed to open MediaStore output stream.")

                val publish = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VideoDownloaderPro"
            ).apply { mkdirs() }
            val destFile = File(downloadsDir, fileName)
            FileInputStream(tempFile).use { input ->
                FileOutputStream(destFile).use { out -> input.copyTo(out) }
            }
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        }
    }

    suspend fun importCreatorExportedVideo(sourceUri: Uri): Result<DownloadHistoryEntity> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var displayName = "creator_export_${System.currentTimeMillis()}.mp4"
            var fileSize = 0L
            resolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }

            val mimeType = resolver.getType(sourceUri) ?: "video/mp4"
            var qualityLabel = "Creator Export"
            var isShort = false

            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, sourceUri)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                if (height != null) {
                    qualityLabel = when {
                        height >= 2160 -> "2160p 4K"
                        height >= 1440 -> "1440p 2K"
                        height >= 1080 -> "1080p Full HD"
                        height >= 720 -> "720p HD"
                        height >= 480 -> "480p"
                        else -> "${height}p"
                    }
                }
                if (height != null && width != null && height > width) {
                    isShort = true
                }
            }
            runCatching { retriever.release() }

            // Copy into app-managed video directory if temporary SAF read permission isn't persistable
            val localDir = File(context.filesDir, "videos").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(localDir, "${System.currentTimeMillis()}_$safeName")
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IOException("Unable to read selected creator video file."))

            if (fileSize <= 0L) fileSize = destFile.length()
            val providerUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )

            val historyItem = DownloadHistoryEntity(
                id = UUID.randomUUID().toString(),
                title = displayName.substringBeforeLast('.').replace('_', ' '),
                channelName = "Creator Export",
                thumbnailUrl = null,
                fileName = displayName,
                mimeType = mimeType,
                qualityLabel = qualityLabel,
                isShort = isShort,
                fileSizeBytes = fileSize,
                contentUri = providerUri.toString(),
                sourceUrl = "local://creator-export"
            )
            dao.insertHistory(historyItem)
            Result.success(historyItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHistoryItem(item: DownloadHistoryEntity, deleteFromStorage: Boolean) {
        withContext(Dispatchers.IO) {
            if (deleteFromStorage) {
                runCatching {
                    val uri = Uri.parse(item.contentUri)
                    if (uri.authority == "${context.packageName}.fileprovider") {
                        val fileName = uri.lastPathSegment
                        if (!fileName.isNullOrBlank()) {
                            File(context.filesDir, "videos/$fileName").delete()
                            @Suppress("DEPRECATION")
                            File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                "VideoDownloaderPro/$fileName"
                            ).delete()
                        }
                    } else {
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            }
            dao.deleteHistoryById(item.id)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isOnUnmeteredWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Authorized Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for completed authorized video downloads"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun postCompletionNotification(title: String, quality: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Complete ($quality)")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "vdp_downloads"
    }
}
