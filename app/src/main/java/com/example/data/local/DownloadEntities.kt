package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadStatus

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val mimeType: String,
    val qualityLabel: String,
    val isShort: Boolean,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val status: String,
    val supportsRange: Boolean,
    val localTempPath: String,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val downloadStatus: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED)

    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val fileName: String,
    val mimeType: String,
    val qualityLabel: String,
    val isShort: Boolean,
    val fileSizeBytes: Long,
    val contentUri: String,
    val sourceUrl: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
