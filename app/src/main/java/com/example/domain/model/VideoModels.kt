package com.example.domain.model

enum class UrlKind {
    YOUTUBE_VIDEO,
    YOUTUBE_SHORT,
    AUTHORIZED_DIRECT_VIDEO,
    INVALID
}

sealed interface ParsedUrlResult {
    data class ValidYouTube(
        val videoId: String,
        val isShort: Boolean,
        val normalizedUrl: String,
        val originalUrl: String
    ) : ParsedUrlResult

    data class ValidDirectMedia(
        val url: String,
        val suggestedFileName: String,
        val isManifestHint: Boolean
    ) : ParsedUrlResult

    data class Invalid(
        val reason: String
    ) : ParsedUrlResult
}

data class VideoQualityOption(
    val id: String,
    val label: String,
    val height: Int,
    val format: String,
    val mimeType: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val supportsRange: Boolean
)

data class AnalyzedVideo(
    val id: String,
    val sourceUrl: String,
    val officialWatchUrl: String,
    val urlKind: UrlKind,
    val isShort: Boolean,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationFormatted: String?,
    val metadataSource: String,
    val isYouTube: Boolean,
    val licenseLabel: String?,
    val isOfficialDownloadRestricted: Boolean,
    val restrictionReason: String?,
    val availableQualities: List<VideoQualityOption>
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class StorageLocationMode(val label: String) {
    MEDIA_STORE_DOWNLOADS("Android Downloads (MediaStore)"),
    CUSTOM_SAF_FOLDER("Custom Folder (SAF)")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val storageLocationMode: StorageLocationMode = StorageLocationMode.MEDIA_STORE_DOWNLOADS,
    val customFolderUri: String? = null,
    val customFolderName: String? = null,
    val wifiOnlyDownloads: Boolean = false
)
