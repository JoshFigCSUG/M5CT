package com.csugprojects.m5ct.data.remote

import com.csugprojects.m5ct.data.model.UnsplashResponse
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit Interface for fetching external data
interface UnsplashApi {
    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String = "nature",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        // API Key handling is typically done via an Interceptor or Query annotation
        @Query("client_id") clientId: String = "5yGLJ6FLVF0ST0QcVxwE3YIv3h9-NvCCEQZAsVKR4FI"
    ): UnsplashResponse
}