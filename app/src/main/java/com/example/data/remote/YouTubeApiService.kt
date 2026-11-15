package com.example.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeDataApiService {
    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails,status",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): Response<YouTubeVideosResponse>
}

interface YouTubeOEmbedService {
    @GET("oembed")
    suspend fun getOEmbed(
        @Query("url") videoUrl: String,
        @Query("format") format: String = "json"
    ): Response<YouTubeOEmbedResponse>
}
