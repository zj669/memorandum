package com.memorandum.data.remote.llm

import android.util.Log
import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.util.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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

    override suspend fun getCapabilities(): Result<LlmCapabilities> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.baseUrl.lowercase()
            val supportsTools = !baseUrl.contains("chatgpt.com/backend-api/codex/responses")
            LlmCapabilities(
                supportsTools = supportsTools,
                supportsRequiredToolChoice = supportsTools,
            )
        }
    }

    private fun buildRequestBody(request: LlmRequest): String {
        val messagesArray = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", request.systemPrompt)
            })

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
                                putJsonObject("image_url") {
                                    put("url", "data:${image.mimeType};base64,${image.base64Data}")
                                }
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
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            }
                        })
                    }
                }
                put("tool_choice", buildToolChoiceJson(request.toolChoice))
            }
        }

        return bodyObj.toString()
    }

    private fun buildToolChoiceJson(toolChoice: LlmToolChoice): JsonElement {
        return when (toolChoice) {
            LlmToolChoice.Auto -> JsonPrimitive("auto")
            LlmToolChoice.None -> JsonPrimitive("none")
            LlmToolChoice.Required -> JsonPrimitive("required")
            is LlmToolChoice.Specific -> buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", toolChoice.toolName)
                }
            }
        }
    }

    private fun readSseStream(source: BufferedSource): LlmResponse {
        val contentBuilder = StringBuilder()
        var finishReason: String? = null
        var usage: TokenUsage? = null
        val toolCalls = linkedMapOf<String, ToolCallAccumulator>()

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
                    val delta = firstChoice["delta"]?.jsonObject
                    delta?.get("content")?.jsonPrimitive?.content?.let { contentBuilder.append(it) }
                    mergeToolCalls(toolCalls, delta)
                    firstChoice["finish_reason"]?.jsonPrimitive?.content?.let { finishReason = it }
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

        if (contentBuilder.isEmpty() && toolCalls.isEmpty()) {
            throw IOException("SSE stream completed with no content")
        }

        Log.d(TAG, "SSE stream completed: contentChars=${contentBuilder.length}, toolCalls=${toolCalls.size}")
        return LlmResponse(
            content = contentBuilder.toString(),
            usage = usage,
            finishReason = finishReason,
            toolCalls = toolCalls.values.mapNotNull { it.toToolCall() },
        )
    }

    private fun parseResponse(responseBody: String): LlmResponse {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content.orEmpty()
            val finishReason = firstChoice?.get("finish_reason")?.jsonPrimitive?.content
            val toolCalls = parseToolCalls(message?.get("tool_calls")?.jsonArray)

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
                toolCalls = toolCalls,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM response: ${responseBody.take(200)}")
            throw IllegalStateException("Failed to parse LLM response", e)
        }
    }

    private fun parseToolCalls(toolCallsArray: JsonArray?): List<LlmToolCall> {
        return toolCallsArray?.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                val function = obj["function"]?.jsonObject ?: return@runCatching null
                val argumentsRaw = function["arguments"]?.jsonPrimitive?.content ?: "{}"
                LlmToolCall(
                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                    name = function["name"]?.jsonPrimitive?.content.orEmpty(),
                    arguments = json.parseToJsonElement(argumentsRaw).jsonObject,
                )
            }.getOrElse {
                Log.w(TAG, "Failed to parse tool call from response")
                null
            }
        } ?: emptyList()
    }

    private fun mergeToolCalls(
        accumulators: MutableMap<String, ToolCallAccumulator>,
        delta: JsonObject?,
    ) {
        val toolCalls = delta?.get("tool_calls")?.jsonArray ?: return
        toolCalls.forEach { toolElement ->
            val toolObj = toolElement.jsonObject
            val index = toolObj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
            val key = index.toString()
            val accumulator = accumulators.getOrPut(key) { ToolCallAccumulator() }
            toolObj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { accumulator.id = it }
            val function = toolObj["function"]?.jsonObject
            function?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { accumulator.name = it }
            function?.get("arguments")?.jsonPrimitive?.content?.let { accumulator.arguments.append(it) }
        }
    }

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun toToolCall(): LlmToolCall? {
            if (name.isBlank()) return null
            return try {
                LlmToolCall(
                    id = id,
                    name = name,
                    arguments = Json.parseToJsonElement(arguments.toString().ifBlank { "{}" }).jsonObject,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
