package com.example.domain.parser

import com.example.domain.model.ParsedUrlResult
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object VideoUrlParser {

    private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL_EXTRACT_REGEX = Regex("(https?://\\S+)")
    private val DIRECT_MEDIA_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "mkv")

    fun parse(rawInput: String): ParsedUrlResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedUrlResult.Invalid("Please paste a YouTube video, Short, or authorized video URL.")
        }

        // Handle YouTube share sheet text such as "Amazing video https://youtu.be/..."
        val candidateUrl = URL_EXTRACT_REGEX.find(trimmed)?.value ?: trimmed

        val uri = try {
            URI(candidateUrl)
        } catch (_: Exception) {
            return ParsedUrlResult.Invalid("Malformed URL format. Make sure the link starts with https://")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null) {
            return ParsedUrlResult.Invalid("Missing URL scheme. Please enter a full link starting with https://")
        }
        if (scheme != "https" && scheme != "http") {
            return ParsedUrlResult.Invalid("Unsupported URL scheme \"$scheme\". Only HTTPS/HTTP links are supported.")
        }

        val host = uri.host?.lowercase()?.removePrefix("www.") ?: ""
        if (host.isBlank()) {
            return ParsedUrlResult.Invalid("Invalid URL host. Please check the pasted link.")
        }

        return when {
            isYouTubeHost(host) -> parseYouTubeUri(uri, host, candidateUrl)
            scheme == "http" -> ParsedUrlResult.Invalid(
                "Insecure HTTP direct downloads are blocked on Android. Use an HTTPS authorized media URL."
            )
            else -> parseDirectMediaUri(uri, candidateUrl)
        }
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host == "youtube.com" ||
            host == "m.youtube.com" ||
            host == "music.youtube.com" ||
            host == "youtube-nocookie.com" ||
            host == "youtu.be"
    }

    private fun parseYouTubeUri(uri: URI, host: String, originalUrl: String): ParsedUrlResult {
        val pathSegments = (uri.rawPath ?: "")
            .split('/')
            .filter { it.isNotBlank() }

        val queryParams = parseQueryParameters(uri.rawQuery)

        if (host == "youtu.be") {
            val id = pathSegments.firstOrNull()?.substringBefore('?')
            if (id.isNullOrBlank()) {
                return ParsedUrlResult.Invalid("Missing YouTube video ID in youtu.be link.")
            }
            if (!YOUTUBE_ID_REGEX.matches(id)) {
                return ParsedUrlResult.Invalid("Invalid YouTube video ID \"$id\". Expected 11 characters.")
            }
            return ParsedUrlResult.ValidYouTube(
                videoId = id,
                isShort = false,
                normalizedUrl = "https://www.youtube.com/watch?v=$id",
                originalUrl = originalUrl
            )
        }

        val firstSegment = pathSegments.firstOrNull()?.lowercase()
        when (firstSegment) {
            "shorts" -> {
                val id = pathSegments.getOrNull(1)
                if (id.isNullOrBlank()) {
                    return ParsedUrlResult.Invalid("Missing video ID in YouTube Shorts URL.")
                }
                if (!YOUTUBE_ID_REGEX.matches(id)) {
                    return ParsedUrlResult.Invalid("Invalid YouTube Shorts ID \"$id\".")
                }
                return ParsedUrlResult.ValidYouTube(
                    videoId = id,
                    isShort = true,
                    normalizedUrl = "https://www.youtube.com/shorts/$id",
                    originalUrl = originalUrl
                )
            }
            "embed", "live", "v" -> {
                val id = pathSegments.getOrNull(1)
                if (id.isNullOrBlank() || !YOUTUBE_ID_REGEX.matches(id)) {
                    return ParsedUrlResult.Invalid("Invalid or missing YouTube video ID in /$firstSegment/ link.")
                }
                return ParsedUrlResult.ValidYouTube(
                    videoId = id,
                    isShort = false,
                    normalizedUrl = "https://www.youtube.com/watch?v=$id",
                    originalUrl = originalUrl
                )
            }
            "watch" -> {
                val id = queryParams["v"]
                if (id.isNullOrBlank()) {
                    return ParsedUrlResult.Invalid("Missing \"v\" video ID parameter in YouTube watch URL.")
                }
                if (!YOUTUBE_ID_REGEX.matches(id)) {
                    return ParsedUrlResult.Invalid("Invalid YouTube video ID \"$id\". Expected 11 characters.")
                }
                return ParsedUrlResult.ValidYouTube(
                    videoId = id,
                    isShort = false,
                    normalizedUrl = "https://www.youtube.com/watch?v=$id",
                    originalUrl = originalUrl
                )
            }
            "playlist" -> {
                return ParsedUrlResult.Invalid(
                    "Playlist URLs cannot be analyzed without a specific video ID. Paste a video or Shorts URL."
                )
            }
            else -> {
                val fallbackV = queryParams["v"]
                if (!fallbackV.isNullOrBlank() && YOUTUBE_ID_REGEX.matches(fallbackV)) {
                    return ParsedUrlResult.ValidYouTube(
                        videoId = fallbackV,
                        isShort = false,
                        normalizedUrl = "https://www.youtube.com/watch?v=$fallbackV",
                        originalUrl = originalUrl
                    )
                }
                return ParsedUrlResult.Invalid(
                    "Unrecognized YouTube URL path. Supported formats: watch?v=, youtu.be/, shorts/, live/, or embed/."
                )
            }
        }
    }

    private fun parseDirectMediaUri(uri: URI, originalUrl: String): ParsedUrlResult {
        val lastSegment = (uri.rawPath ?: "")
            .split('/')
            .lastOrNull { it.isNotBlank() }
            ?: "authorized_video.mp4"

        val cleanName = URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = cleanName.substringAfterLast('.', "").lowercase()
        val isManifest = ext == "json"
        val suggested = when {
            ext in DIRECT_MEDIA_EXTENSIONS -> cleanName
            isManifest -> cleanName.substringBeforeLast('.') + ".mp4"
            else -> "${cleanName.ifBlank { "authorized_video" }}.mp4"
        }

        return ParsedUrlResult.ValidDirectMedia(
            url = originalUrl,
            suggestedFileName = suggested,
            isManifestHint = isManifest
        )
    }

    private fun parseQueryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                result[key] = value
            }
        }
        return result
    }
}
