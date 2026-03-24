package com.memorandum.data.remote.mcp

import android.util.Log
import com.memorandum.data.local.room.entity.McpServerEntity
import com.memorandum.util.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpMcpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cryptoHelper: CryptoHelper,
    private val json: Json,
) : McpClient {

    companion object {
        private const val TAG = "HttpMcpClient"
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val requestIdCounter = AtomicInteger(1)
    private val initializationMutex = Mutex()
    private val initializedServers = mutableSetOf<String>()

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun listTools(server: McpServerEntity): Result<List<McpTool>> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInitialized(server)
                val body = buildJsonRpcRequest("tools/list")
                val responseBody = executeRequest(server, body)
                parseToolsList(responseBody)
            }
        }

    override suspend fun callTool(
        server: McpServerEntity,
        toolName: String,
        arguments: Map<String, JsonElement>,
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized(server)
            val params = buildJsonObject {
                put("name", toolName)
                put("arguments", buildJsonObject {
                    for ((key, value) in arguments) {
                        put(key, value)
                    }
                })
            }
            val body = buildJsonRpcRequest("tools/call", params)
            val responseBody = executeRequest(server, body)
            parseToolResult(responseBody)
        }
    }

    override suspend fun testConnection(server: McpServerEntity): Result<List<McpTool>> =
        withContext(Dispatchers.IO) {
            runCatching {
                listTools(server).getOrThrow()
            }
        }

    private suspend fun ensureInitialized(server: McpServerEntity) {
        if (initializedServers.contains(server.id)) return

        initializationMutex.withLock {
            if (initializedServers.contains(server.id)) return

            val initParams = buildJsonObject {
                put("protocolVersion", "2025-06-18")
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {})
                })
                put("clientInfo", buildJsonObject {
                    put("name", "Memorandum")
                    put("version", "1.0")
                })
            }
            val initializeBody = buildJsonRpcRequest("initialize", initParams)
            val initializeResponse = executeRequest(server, initializeBody)
            validateInitializeResponse(server, initializeResponse)

            val initializedBody = buildJsonRpcNotification("notifications/initialized")
            executeRequest(server, initializedBody, expectsResponse = false)

            initializedServers.add(server.id)
            Log.i(TAG, "MCP initialized: server=${server.name}")
        }
    }

    private fun validateInitializeResponse(server: McpServerEntity, responseBody: String) {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val result = root["result"]?.jsonObject
            ?: throw IOException("Invalid initialize response from ${server.name}")
        val protocolVersion = result["protocolVersion"]?.jsonPrimitive?.content.orEmpty()
        if (protocolVersion.isBlank()) {
            throw IOException("Missing protocolVersion in initialize response from ${server.name}")
        }
    }

    private fun buildJsonRpcRequest(method: String, params: JsonObject? = null): String {
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIdCounter.getAndIncrement())
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }
        return obj.toString()
    }

    private fun buildJsonRpcNotification(method: String, params: JsonObject? = null): String {
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }
        return obj.toString()
    }

    private fun executeRequest(server: McpServerEntity, jsonBody: String, expectsResponse: Boolean = true): String {
        val requestBuilder = Request.Builder()
            .url(server.baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        applyAuth(requestBuilder, server)

        val request = requestBuilder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout: server=${server.name}")
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Network error: server=${server.name}, error=${e.message}")
            throw e
        }

        return response.use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = rawBody.take(200)
                Log.e(TAG, "MCP error: server=${server.name}, code=${resp.code}, body=$snippet")
                when (resp.code) {
                    401, 403 -> throw SecurityException("Auth failed for ${server.name}")
                    else -> throw IOException("HTTP ${resp.code} from ${server.name}: $snippet")
                }
            }

            if (!expectsResponse) {
                return@use rawBody
            }

            val body = extractJsonFromSse(rawBody)
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.jsonObject
            if (error != null) {
                val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown JSON-RPC error"
                Log.e(TAG, "JSON-RPC error: server=${server.name}, error=$errorMsg")
                throw IOException("JSON-RPC error from ${server.name}: $errorMsg")
            }

            body
        }
    }

    /**
     * Extract JSON from SSE-formatted response.
     * SSE format: "event: message\ndata: {json}\n\n"
     * If the body is already plain JSON, return as-is.
     */
    private fun extractJsonFromSse(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val dataLines = trimmed.lines()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
        return dataLines.lastOrNull() ?: trimmed
    }

    private fun applyAuth(builder: Request.Builder, server: McpServerEntity) {
        when (server.authType.uppercase()) {
            "NONE" -> { /* no auth */ }
            "BEARER" -> {
                val token = server.authValueEncrypted?.let {
                    try {
                        cryptoHelper.decrypt(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decrypt auth for server=${server.name}")
                        null
                    }
                }
                if (token != null) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            "HEADER" -> {
                val headerValue = server.authValueEncrypted?.let {
                    try {
                        cryptoHelper.decrypt(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decrypt auth for server=${server.name}")
                        null
                    }
                }
                if (headerValue != null) {
                    val parts = headerValue.split(":", limit = 2)
                    if (parts.size == 2) {
                        builder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }
        }
    }

    private fun parseToolsList(responseBody: String): List<McpTool> {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val result = root["result"]?.jsonObject ?: return emptyList()
        val tools = result["tools"]?.jsonArray ?: return emptyList()

        return tools.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                McpTool(
                    name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                    inputSchema = obj["inputSchema"]?.jsonObject ?: JsonObject(emptyMap()),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool: ${e.message}")
                null
            }
        }
    }

    private fun parseToolResult(responseBody: String): McpToolResult {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val result = root["result"]?.jsonObject
            ?: return McpToolResult(content = "", isError = true)

        val contentArray = result["content"]?.jsonArray
        val isError = result["isError"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        val contentText = contentArray?.joinToString("\n") { element ->
            val obj = element.jsonObject
            obj["text"]?.jsonPrimitive?.content ?: obj.toString()
        }.orEmpty()

        return McpToolResult(content = contentText, isError = isError)
    }
}
