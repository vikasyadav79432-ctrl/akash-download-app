package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeVideosResponse(
    val items: List<YouTubeVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    val id: String,
    val snippet: YouTubeSnippet? = null,
    val contentDetails: YouTubeContentDetails? = null,
    val status: YouTubeStatus? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val publishedAt: String? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    val maxres: YouTubeThumbnail? = null,
    val standard: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    @Json(name = "default") val defaultThumb: YouTubeThumbnail? = null
) {
    fun bestUrl(): String? =
        maxres?.url ?: standard?.url ?: high?.url ?: medium?.url ?: defaultThumb?.url
}

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeContentDetails(
    val duration: String? = null,
    val definition: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeStatus(
    val privacyStatus: String? = null,
    val license: String? = null,
    val embeddable: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeOEmbedResponse(
    val title: String = "",
    @Json(name = "author_name") val authorName: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class AuthorizedMediaManifest(
    val title: String? = null,
    val channel: String? = null,
    val thumbnailUrl: String? = null,
    val duration: String? = null,
    val license: String? = null,
    val variants: List<AuthorizedManifestVariant> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AuthorizedManifestVariant(
    val height: Int,
    val label: String? = null,
    val format: String? = null,
    val url: String
)
