package com.csugprojects.m5ct.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

private const val BASE_URL = "https://api.unsplash.com/"

/**
 * Singleton service responsible for configuring and providing the Retrofit client.
 * This object is part of the Model layer's remote data source implementation.
 */
object UnsplashApiService {

    // Configures the JSON parser for kotlinx.serialization.
    private val json = Json {
        // Allows the parser to safely skip any JSON fields not mapped in our Kotlin data classes (DTOs).
        ignoreUnknownKeys = true
    }

    // Builds the Retrofit HTTP client.
    private val retrofit = Retrofit.Builder()
        // Adds the serialization converter to handle JSON mapping automatically.
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .baseUrl(BASE_URL)
        .build()

    /**
     * Provides the concrete implementation of the UnsplashApi interface.
     * The 'lazy' keyword ensures the Retrofit object is only constructed once upon first access.
     */
    val api: UnsplashApi by lazy {
        retrofit.create(UnsplashApi::class.java)
    }
}