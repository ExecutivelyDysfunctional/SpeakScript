package com.example.api

import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import retrofit2.http.Path

object AiConstants {
    const val GEMINI_DEFAULT_MODEL = "gemini-3.6-flash"
    const val GEMINI_PRO_MODEL = "gemini-3.6-flash"
    const val OPENROUTER_DEFAULT_MODEL = "google/gemini-2.5-flash"
    const val GROQ_AUDIO_MODEL = "whisper-large-v3"
    const val GROQ_TEXT_MODEL = "llama-3.3-70b-versatile"
}

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float? = null,
    val stream: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiMultimodalChatRequest(
    val model: String,
    val messages: List<OpenAiMultimodalMessage>,
    val temperature: Float? = null,
    val stream: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiMultimodalMessage(
    val role: String,
    val content: List<OpenAiContentPart>
)

@JsonClass(generateAdapter = true)
data class OpenAiContentPart(
    val type: String, // "text" or "input_audio"
    val text: String? = null,
    val input_audio: OpenAiInputAudio? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiInputAudio(
    val data: String, // base64 string
    val format: String // "mp3", "wav", "m4a", "aac", "ogg", "flac"
)

@JsonClass(generateAdapter = true)
data class GroqTranscriptionResponse(
    val text: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val message: OpenAiMessage? = null,
    val delta: OpenAiMessage? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    @Streaming
    suspend fun generateContentStream(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): ResponseBody
}

interface OpenAiCompatibleApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referrer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse

    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletionsStream(
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referrer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: OpenAiChatRequest
    ): ResponseBody

    @POST("v1/chat/completions")
    suspend fun chatCompletionsMultimodal(
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referrer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: OpenAiMultimodalChatRequest
    ): OpenAiChatResponse

    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletionsMultimodalStream(
        @Header("Authorization") authHeader: String,
        @Header("HTTP-Referer") referrer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: OpenAiMultimodalChatRequest
    ): ResponseBody
}

interface GroqAudioApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Part file: MultipartBody.Part,
        @retrofit2.http.Part("model") model: RequestBody,
        @retrofit2.http.Part("response_format") responseFormat: RequestBody? = null,
        @retrofit2.http.Part("prompt") prompt: RequestBody? = null,
        @retrofit2.http.Part("language") language: RequestBody? = null
    ): GroqTranscriptionResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object OpenRouterClient {
    private const val BASE_URL = "https://openrouter.ai/api/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val service: OpenAiCompatibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiCompatibleApiService::class.java)
    }
}

object GroqClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val service: OpenAiCompatibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAiCompatibleApiService::class.java)
    }
}

object GroqAudioClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val service: GroqAudioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GroqAudioApiService::class.java)
    }
}

