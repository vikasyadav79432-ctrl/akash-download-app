package com.example.data.repository

import android.media.MediaMetadataRetriever
import com.example.BuildConfig
import com.example.data.remote.AuthorizedMediaManifest
import com.example.data.remote.YouTubeDataApiService
import com.example.data.remote.YouTubeOEmbedService
import com.example.domain.model.AnalyzedVideo
import com.example.domain.model.ParsedUrlResult
import com.example.domain.model.UrlKind
import com.example.domain.model.VideoQualityOption
import com.example.domain.parser.IsoDurationParser
import com.example.domain.parser.VideoUrlParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class MediaAnalyzerRepository(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val moshi: Moshi = defaultMoshi(),
    private val dataApiService: YouTubeDataApiService = createDataApiService(okHttpClient, moshi),
    private val oEmbedService: YouTubeOEmbedService = createOEmbedService(okHttpClient, moshi),
    private val apiKeyProvider: () -> String? = {
        BuildConfig.YOUTUBE_API_KEY.takeIf { it.isNotBlank() && it != "MY_YOUTUBE_API_KEY" }
    }
) {

    fun hasConfiguredYouTubeApiKey(): Boolean = apiKeyProvider() != null

    suspend fun analyzeUrl(rawInput: String): Result<AnalyzedVideo> = withContext(Dispatchers.IO) {
        when (val parsed = VideoUrlParser.parse(rawInput)) {
            is ParsedUrlResult.Invalid -> Result.failure(IllegalArgumentException(parsed.reason))
            is ParsedUrlResult.ValidYouTube -> analyzeYouTubeVideo(parsed)
            is ParsedUrlResult.ValidDirectMedia -> analyzeDirectMedia(parsed)
        }
    }

    private suspend fun analyzeYouTubeVideo(parsed: ParsedUrlResult.ValidYouTube): Result<AnalyzedVideo> {
        val apiKey = apiKeyProvider()
        if (apiKey != null) {
            val dataApiResult = runCatching {
                dataApiService.getVideoDetails(id = parsed.videoId, apiKey = apiKey)
            }.getOrNull()

            if (dataApiResult != null && dataApiResult.isSuccessful) {
                val item = dataApiResult.body()?.items?.firstOrNull()
                if (item != null) {
                    val snippet = item.snippet
                    val durationIso = item.contentDetails?.duration
                    val totalSeconds = IsoDurationParser.parseTotalSeconds(durationIso)
                    val formattedDuration = IsoDurationParser.formatDuration(durationIso)
                    val licenseRaw = item.status?.license
                    val licenseLabel = when (licenseRaw) {
                        "creativeCommon" -> "Creative Commons (YouTube)"
                        "youtube" -> "Standard YouTube License"
                        else -> licenseRaw
                    }
                    val privacy = item.status?.privacyStatus
                    if (privacy == "private") {
                        return Result.failure(
                            IllegalStateException("This YouTube video is private and cannot be accessed.")
                        )
                    }

                    val detectedShort = parsed.isShort || (totalSeconds != null && totalSeconds <= 60 && parsed.originalUrl.contains("/shorts/", ignoreCase = true))
                    return Result.success(
                        AnalyzedVideo(
                            id = parsed.videoId,
                            sourceUrl = parsed.originalUrl,
                            officialWatchUrl = parsed.normalizedUrl,
                            urlKind = if (detectedShort) UrlKind.YOUTUBE_SHORT else UrlKind.YOUTUBE_VIDEO,
                            isShort = detectedShort,
                            title = snippet?.title?.ifBlank { "YouTube Video (${parsed.videoId})" }
                                ?: "YouTube Video (${parsed.videoId})",
                            channelName = snippet?.channelTitle?.ifBlank { "YouTube Channel" }
                                ?: "YouTube Channel",
                            thumbnailUrl = snippet?.thumbnails?.bestUrl()
                                ?: "https://i.ytimg.com/vi/${parsed.videoId}/hqdefault.jpg",
                            durationFormatted = formattedDuration,
                            metadataSource = "YouTube Data API v3",
                            isYouTube = true,
                            licenseLabel = licenseLabel,
                            isOfficialDownloadRestricted = true,
                            restrictionReason = "YouTube Data API v3 provides metadata only. Direct third-party MP4 extraction is disabled to respect YouTube Terms of Service & DRM.",
                            availableQualities = emptyList()
                        )
                    )
                } else {
                    return Result.failure(
                        IllegalStateException("Video not found on YouTube. It may have been deleted or set to private.")
                    )
                }
            }
        }

        // Official YouTube oEmbed API fallback (no API key required)
        return try {
            val response = oEmbedService.getOEmbed(videoUrl = parsed.normalizedUrl)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(IllegalStateException("Empty response from YouTube oEmbed service."))
                Result.success(
                    AnalyzedVideo(
                        id = parsed.videoId,
                        sourceUrl = parsed.originalUrl,
                        officialWatchUrl = parsed.normalizedUrl,
                        urlKind = if (parsed.isShort) UrlKind.YOUTUBE_SHORT else UrlKind.YOUTUBE_VIDEO,
                        isShort = parsed.isShort,
                        title = body.title.ifBlank { "YouTube Video (${parsed.videoId})" },
                        channelName = body.authorName.ifBlank { "YouTube Creator" },
                        thumbnailUrl = body.thumbnailUrl
                            ?: "https://i.ytimg.com/vi/${parsed.videoId}/hqdefault.jpg",
                        durationFormatted = null,
                        metadataSource = "Official YouTube oEmbed",
                        isYouTube = true,
                        licenseLabel = "Standard / Creator Controlled",
                        isOfficialDownloadRestricted = true,
                        restrictionReason = "Direct MP4 stream downloading is not officially provided by YouTube APIs. Open in YouTube for official offline viewing, or use the Creator Export workflow if you own this video.",
                        availableQualities = emptyList()
                    )
                )
            } else {
                val msg = when (response.code()) {
                    401, 403 -> "Embedding or metadata access is restricted by this video's creator."
                    404 -> "YouTube video not found (404). Verify the URL and try again."
                    else -> "YouTube metadata service returned HTTP ${response.code()}."
                }
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: IOException) {
            Result.failure(
                IOException("Network error while contacting YouTube metadata API. Check your internet connection.", e)
            )
        }
    }

    private suspend fun analyzeDirectMedia(parsed: ParsedUrlResult.ValidDirectMedia): Result<AnalyzedVideo> {
        return try {
            val probe = probeRemoteResource(parsed.url)
                ?: return Result.failure(IOException("Unable to reach the media URL. Verify the address and network."))

            if (!probe.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Server rejected the media request with HTTP ${probe.code}.")
                )
            }

            val contentType = probe.contentType.lowercase()
            if (contentType.contains("application/json") || parsed.isManifestHint) {
                return analyzeAuthorizedManifest(parsed.url)
            }

            if (contentType.startsWith("text/html")) {
                return Result.failure(
                    IllegalArgumentException(
                        "This URL returns an HTML webpage (text/html) rather than an authorized video file. Paste a YouTube link or a direct MP4/WebM media link."
                    )
                )
            }

            if (!contentType.startsWith("video/") && !contentType.contains("octet-stream") && !hasVideoExtension(parsed.url)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Unsupported media content type \"$contentType\". Expected an authorized video file (MP4, WebM, MOV)."
                    )
                )
            }

            val mediaInfo = extractRemoteVideoMetadata(parsed.url)
            val height = mediaInfo.height ?: 0
            val qualityLabel = resolutionLabelForHeight(height)
            val formatLabel = formatLabelFromMimeOrUrl(contentType, parsed.url)
            val normalizedMime = if (contentType.startsWith("video/")) {
                contentType.substringBefore(';').trim()
            } else {
                mimeForFormat(formatLabel)
            }

            val qualityOption = VideoQualityOption(
                id = "direct_${height}",
                label = qualityLabel,
                height = height,
                format = formatLabel,
                mimeType = normalizedMime,
                downloadUrl = parsed.url,
                sizeBytes = probe.contentLength,
                supportsRange = probe.supportsRange
            )

            val hostLabel = runCatching { java.net.URI(parsed.url).host ?: "Authorized Host" }
                .getOrDefault("Authorized Host")

            Result.success(
                AnalyzedVideo(
                    id = UUID.randomUUID().toString(),
                    sourceUrl = parsed.url,
                    officialWatchUrl = parsed.url,
                    urlKind = UrlKind.AUTHORIZED_DIRECT_VIDEO,
                    isShort = (mediaInfo.height != null && mediaInfo.width != null && mediaInfo.height > mediaInfo.width),
                    title = parsed.suggestedFileName.substringBeforeLast('.').replace('_', ' '),
                    channelName = hostLabel,
                    thumbnailUrl = null,
                    durationFormatted = mediaInfo.durationFormatted,
                    metadataSource = "Direct HTTP Probe",
                    isYouTube = false,
                    licenseLabel = "Authorized Direct Media",
                    isOfficialDownloadRestricted = false,
                    restrictionReason = null,
                    availableQualities = listOf(qualityOption)
                )
            )
        } catch (e: IOException) {
            Result.failure(
                IOException("Network error while inspecting media URL: ${e.localizedMessage ?: "Connection failed"}", e)
            )
        }
    }

    private suspend fun analyzeAuthorizedManifest(manifestUrl: String): Result<AnalyzedVideo> {
        val request = Request.Builder().url(manifestUrl).get().build()
        val bodyText = okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                return Result.failure(IllegalStateException("Authorized manifest returned HTTP ${resp.code}."))
            }
            resp.body?.string()
                ?: return Result.failure(IllegalStateException("Authorized manifest was empty."))
        }

        val adapter = moshi.adapter(AuthorizedMediaManifest::class.java)
        val manifest = runCatching { adapter.fromJson(bodyText) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid authorized media manifest JSON."))

        val probedQualities = manifest.variants.mapNotNull { variant ->
            val probe = runCatching { probeRemoteResource(variant.url) }.getOrNull()
            if (probe != null && probe.isSuccessful && probe.contentLength > 0L) {
                val formatLabel = variant.format?.uppercase()
                    ?: formatLabelFromMimeOrUrl(probe.contentType, variant.url)
                val mime = if (probe.contentType.startsWith("video/")) {
                    probe.contentType.substringBefore(';').trim()
                } else {
                    mimeForFormat(formatLabel)
                }
                VideoQualityOption(
                    id = "manifest_${variant.height}_${variant.url.hashCode()}",
                    label = variant.label ?: resolutionLabelForHeight(variant.height),
                    height = variant.height,
                    format = formatLabel,
                    mimeType = mime,
                    downloadUrl = variant.url,
                    sizeBytes = probe.contentLength,
                    supportsRange = probe.supportsRange
                )
            } else {
                null
            }
        }.sortedBy { it.height }

        if (probedQualities.isEmpty()) {
            return Result.failure(
                IllegalStateException("No downloadable video qualities were available in the authorized manifest.")
            )
        }

        return Result.success(
            AnalyzedVideo(
                id = UUID.randomUUID().toString(),
                sourceUrl = manifestUrl,
                officialWatchUrl = manifestUrl,
                urlKind = UrlKind.AUTHORIZED_DIRECT_VIDEO,
                isShort = false,
                title = manifest.title?.ifBlank { "Authorized Creator Video" } ?: "Authorized Creator Video",
                channelName = manifest.channel?.ifBlank { "Authorized Source" } ?: "Authorized Source",
                thumbnailUrl = manifest.thumbnailUrl,
                durationFormatted = manifest.duration,
                metadataSource = "Authorized Multi-Quality Manifest",
                isYouTube = false,
                licenseLabel = manifest.license ?: "Authorized Media",
                isOfficialDownloadRestricted = false,
                restrictionReason = null,
                availableQualities = probedQualities
            )
        )
    }

    private data class RemoteProbe(
        val isSuccessful: Boolean,
        val code: Int,
        val contentType: String,
        val contentLength: Long,
        val supportsRange: Boolean
    )

    private fun probeRemoteResource(url: String): RemoteProbe? {
        val headReq = Request.Builder().url(url).head().build()
        okHttpClient.newCall(headReq).execute().use { resp ->
            if (resp.code != 405 && resp.code != 501) {
                val length = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                return RemoteProbe(
                    isSuccessful = resp.isSuccessful,
                    code = resp.code,
                    contentType = resp.header("Content-Type") ?: "",
                    contentLength = length,
                    supportsRange = acceptRanges
                )
            }
        }

        // Fallback if server disallows HEAD: request first byte only
        val rangeReq = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        okHttpClient.newCall(rangeReq).execute().use { resp ->
            val contentRange = resp.header("Content-Range")
            val parsedTotal = contentRange?.substringAfterLast('/')?.toLongOrNull()
            val length = parsedTotal ?: resp.header("Content-Length")?.toLongOrNull() ?: -1L
            return RemoteProbe(
                isSuccessful = resp.isSuccessful || resp.code == 206,
                code = resp.code,
                contentType = resp.header("Content-Type") ?: "",
                contentLength = length,
                supportsRange = resp.code == 206 || resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
            )
        }
    }

    private data class RemoteMediaMeta(
        val width: Int? = null,
        val height: Int? = null,
        val durationFormatted: String? = null
    )

    private fun extractRemoteVideoMetadata(url: String): RemoteMediaMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, emptyMap())
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val formattedDuration = durationMs?.let { ms ->
                val totalSeconds = (ms / 1000L).toInt()
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
                else "%d:%02d".format(minutes, seconds)
            }
            RemoteMediaMeta(width = width, height = height, durationFormatted = formattedDuration)
        } catch (_: Exception) {
            RemoteMediaMeta()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hasVideoExtension(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".webm") ||
            path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".mkv")
    }

    private fun formatLabelFromMimeOrUrl(mime: String, url: String): String {
        val lowerMime = mime.lowercase()
        val lowerUrl = url.substringBefore('?').lowercase()
        return when {
            lowerMime.contains("webm") || lowerUrl.endsWith(".webm") -> "WEBM"
            lowerMime.contains("quicktime") || lowerUrl.endsWith(".mov") -> "MOV"
            lowerMime.contains("x-matroska") || lowerUrl.endsWith(".mkv") -> "MKV"
            else -> "MP4"
        }
    }

    private fun mimeForFormat(format: String): String = when (format.uppercase()) {
        "WEBM" -> "video/webm"
        "MOV" -> "video/quicktime"
        "MKV" -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun resolutionLabelForHeight(height: Int): String = when {
        height >= 2160 -> "2160p 4K"
        height >= 1440 -> "1440p 2K"
        height >= 1080 -> "1080p Full HD"
        height >= 720 -> "720p HD"
        height >= 480 -> "480p"
        height >= 360 -> "360p"
        height > 0 -> "${height}p"
        else -> "Original Quality"
    }

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun defaultMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun createDataApiService(client: OkHttpClient, moshi: Moshi): YouTubeDataApiService =
            Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(YouTubeDataApiService::class.java)

        fun createOEmbedService(client: OkHttpClient, moshi: Moshi): YouTubeOEmbedService =
            Retrofit.Builder()
                .baseUrl("https://www.youtube.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(YouTubeOEmbedService::class.java)
    }
}
