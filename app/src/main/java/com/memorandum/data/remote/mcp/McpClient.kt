package com.memorandum.data.remote.mcp

import com.memorandum.data.local.room.entity.McpServerEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface McpClient {
    suspend fun listTools(server: McpServerEntity): Result<List<McpTool>>
    suspend fun callTool(
        server: McpServerEntity,
        toolName: String,
        arguments: Map<String, JsonElement>,
    ): Result<McpToolResult>
    suspend fun testConnection(server: McpServerEntity): Result<List<McpTool>>
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class McpToolResult(
    val content: String,
    val isError: Boolean = false,
)
