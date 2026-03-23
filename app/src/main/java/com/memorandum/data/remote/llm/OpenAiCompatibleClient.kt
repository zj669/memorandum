package com.memorandum.data.remote.llm

import android.util.Log
import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.util.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(
    private val config: LlmConfigEntity,
    private val okHttpClient: OkHttpClient,
    private val cryptoHelper: CryptoHelper,
    private val json: Json,
) : LlmClient {

    companion object {
        private const val TAG = "OpenAiCompatibleClient"
        private const val CONNECT_TIMEOUT_S = 30L
        private const val READ_TIMEOUT_S = 120L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun chat(request: LlmRequest): Result<LlmResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = try {
                    cryptoHelper.decrypt(config.apiKeyEncrypted)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt API key for provider=${config.providerName}")
                    throw IllegalStateException("API key decryption failed", e)
                }

                val body = buildRequestBody(request)
                val url = "${config.baseUrl.trimEnd('/')}/v1/chat/completions"

                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = try {
                    client.newCall(httpRequest).execute()
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Timeout calling LLM: url=$url")
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Network error calling LLM: ${e.message}")
                    throw e
                }

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string().orEmpty().take(200)
                        Log.e(TAG, "LLM API error: code=${resp.code}, body=$errorBody")
                        when (resp.code) {
                            401, 403 -> throw SecurityException("Auth failed (${resp.code}): $errorBody")
                            429 -> throw IOException("Rate limited (429): $errorBody")
                            else -> throw IOException("HTTP ${resp.code}: $errorBody")
                        }
                    }

                    val contentType = resp.header("Content-Type").orEmpty()
                    if (contentType.contains("text/event-stream")) {
                        readSseStream(resp.body!!.source())
                    } else {
                        // Fallback: provider returned non-streaming response
                        parseResponse(resp.body?.string().orEmpty())
                    }
                }
            }
        }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val testRequest = LlmRequest(
                systemPrompt = "You are a test assistant.",
                userMessage = "Reply with exactly: ok",
                temperature = 0f,
                maxTokens = 10,
                responseFormat = ResponseFormat.TEXT,
            )
            chat(testRequest).getOrThrow()
            true
        }
    }

    private fun buildRequestBody(request: LlmRequest): String {
        val messagesArray = buildJsonArray {
            // System message
            add(buildJsonObject {
                put("role", "system")
                put("content", request.systemPrompt)
            })

            // User message (with optional images)
            if (request.images.isEmpty()) {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", request.userMessage)
                })
            } else {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", request.userMessage)
                        })
                        for (image in request.images) {
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:${image.mimeType};base64,${image.base64Data}")
                                })
                            })
                        }
                    })
                })
            }
        }

        val bodyObj = buildJsonObject {
            put("model", config.modelName)
            put("messages", messagesArray)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
        }

        return bodyObj.toString()
    }

    private fun readSseStream(source: BufferedSource): LlmResponse {
        val contentBuilder = StringBuilder()
        var finishReason: String? = null
        var usage: TokenUsage? = null

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val choices = chunk["choices"]?.jsonArray ?: continue
                    val firstChoice = choices.firstOrNull()?.jsonObject ?: continue
                    firstChoice["delta"]?.jsonObject?.get("content")
                        ?.jsonPrimitive?.content?.let { contentBuilder.append(it) }
                    firstChoice["finish_reason"]?.jsonPrimitive?.content
                        ?.let { finishReason = it }
                    chunk["usage"]?.jsonObject?.let { u ->
                        usage = TokenUsage(
                            promptTokens = u["prompt_tokens"]?.jsonPrimitive?.int ?: 0,
                            completionTokens = u["completion_tokens"]?.jsonPrimitive?.int ?: 0,
                            totalTokens = u["total_tokens"]?.jsonPrimitive?.int ?: 0,
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse SSE chunk: ${data.take(100)}")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "SSE stream interrupted: ${e.message}")
            throw e
        }

        if (contentBuilder.isEmpty()) {
            throw IOException("SSE stream completed with no content")
        }

        Log.d(TAG, "SSE stream completed: ${contentBuilder.length} chars")
        return LlmResponse(
            content = contentBuilder.toString(),
            usage = usage,
            finishReason = finishReason,
        )
    }

    private fun parseResponse(responseBody: String): LlmResponse {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val content = firstChoice?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.content.orEmpty()
            val finishReason = firstChoice?.get("finish_reason")
                ?.jsonPrimitive?.content

            val usageObj = root["usage"]?.jsonObject
            val usage = usageObj?.let {
                TokenUsage(
                    promptTokens = it["prompt_tokens"]?.jsonPrimitive?.int ?: 0,
                    completionTokens = it["completion_tokens"]?.jsonPrimitive?.int ?: 0,
                    totalTokens = it["total_tokens"]?.jsonPrimitive?.int ?: 0,
                )
            }

            LlmResponse(
                content = content,
                usage = usage,
                finishReason = finishReason,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM response: ${responseBody.take(200)}")
            throw IllegalStateException("Failed to parse LLM response", e)
        }
    }
}
