package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
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
    const val GEMINI_DEFAULT_MODEL = "gemini-3.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-3.5-flash"
    const val OPENROUTER_DEFAULT_MODEL = "google/gemini-2.5-flash:free"
    const val GROQ_AUDIO_MODEL = "whisper-large-v3"
    const val GROQ_TEXT_MODEL = "llama-3.3-70b-versatile"
    const val GROQ_TRANSCRIPTION_PROMPT_MAX_LENGTH = 896
}

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generation_config") val generationConfig: GenerationConfig? = null,
    @Json(name = "system_instruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inline_data") val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mime_type") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "response_mime_type") val responseMimeType: String? = null,
    @Json(name = "thinking_config") val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    @Json(name = "thinking_level") val thinkingLevel: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null,
    @Json(name = "finishReason") val finishReason: String? = null
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

    @POST("v1beta/models/{model}:streamGenerateContent")
    @Streaming
    suspend fun generateContentStream(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Query("alt") alt: String = "sse",
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

object GeminiStreamParser {
    suspend fun parseSseStream(
        responseBody: ResponseBody,
        apiKeyToMask: String? = null,
        onChunkReceived: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val accumulated = StringBuilder()
        var hasReceivedChunk = false

        responseBody.byteStream().bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                val rawLine = line?.trim() ?: continue
                if (rawLine.isEmpty() || rawLine.startsWith(":")) {
                    continue // Blank SSE line or comment
                }

                if (rawLine.startsWith("data:")) {
                    val jsonPayload = rawLine.substring(5).trim()
                    if (jsonPayload.isEmpty()) continue
                    if (jsonPayload == "[DONE]") break

                    val textChunk = parseSseJsonPayload(jsonPayload, apiKeyToMask)
                    if (!textChunk.isNullOrEmpty()) {
                        accumulated.append(textChunk)
                        hasReceivedChunk = true
                        onChunkReceived(accumulated.toString())
                    }
                }
            }
        }

        if (!hasReceivedChunk && accumulated.isEmpty()) {
            throw IllegalStateException("Gemini API returned an empty response stream.")
        }

        accumulated.toString()
    }

    fun parseSseJsonPayload(jsonPayload: String, apiKeyToMask: String? = null): String? {
        if (jsonPayload.isBlank() || jsonPayload == "[DONE]") return null

        val json = try {
            JSONObject(jsonPayload)
        } catch (e: Exception) {
            return null // Skip malformed SSE chunk
        }

        if (json.has("error")) {
            val errorObj = json.optJSONObject("error")
            val rawMsg = errorObj?.optString("message") ?: json.optString("error")
            val status = errorObj?.optString("status") ?: ""
            val maskedMsg = maskApiKey(rawMsg, apiKeyToMask)
            val statusPart = if (status.isNotBlank()) " ($status)" else ""
            throw IllegalStateException("Gemini API Error$statusPart: $maskedMsg")
        }

        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val candidate = candidates.optJSONObject(0) ?: return null

        val finishReason = candidate.optString("finishReason", candidate.optString("finish_reason"))
        if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "BLOCKLIST") {
            throw IllegalStateException("Gemini stream stopped due to finish reason: $finishReason")
        }

        val content = candidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null

        val chunkText = StringBuilder()
        for (i in 0 until parts.length()) {
            val partObj = parts.optJSONObject(i) ?: continue
            val text = partObj.optString("text", "")
            if (text.isNotEmpty()) {
                chunkText.append(text)
            }
        }

        return if (chunkText.isNotEmpty()) chunkText.toString() else null
    }

    fun maskApiKey(text: String, apiKey: String? = null): String {
        var result = text
        if (!apiKey.isNullOrBlank() && apiKey.length > 5) {
            result = result.replace(apiKey, "***MASKED_KEY***")
        }
        result = result.replace(Regex("sk-or-v1-[a-zA-Z0-9_-]+"), "***MASKED_KEY***")
        result = result.replace(Regex("gsk_[a-zA-Z0-9_-]+"), "***MASKED_KEY***")
        result = result.replace(Regex("AIzaSy[a-zA-Z0-9_-]+"), "***MASKED_KEY***")
        return result
    }
}

