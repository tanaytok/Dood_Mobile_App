package com.example.dodoprojectv2.api

import com.example.dodoprojectv2.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Gemini API isteği için data class
 */
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 1024,
    val topP: Double = 0.95
)

/**
 * Gemini API yanıtı için data class
 */
data class GeminiResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: ResponseContent
)

data class ResponseContent(
    val parts: List<ResponsePart>
)

data class ResponsePart(
    val text: String
)

/**
 * TaskResponse, JSON yanıtından parse edilen tek görev için model
 */
data class TaskResponse(
    val title: String,
    val totalCount: Int = 1
)

/**
 * Gemini API servisi
 */
interface GeminiService {
    @Headers("Content-Type: application/json")
    @POST("v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent")
    suspend fun generateContent(
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

/**
 * Gemini API Client
 */
object GeminiClient {
    private const val API_URL = "https://generativelanguage.googleapis.com/"
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val originalUrl = original.url
            
            val url = originalUrl.newBuilder()
                .addQueryParameter("key", BuildConfig.GEMINI_API_KEY)
                .build()
                
            val request = original.newBuilder()
                .url(url)
                .build()
                
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(API_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val service: GeminiService = retrofit.create(GeminiService::class.java)
} 