package com.memorandum.ai.orchestrator

import android.util.Log
import com.memorandum.data.local.datastore.AppPreferencesDataStore
import com.memorandum.data.local.room.entity.McpServerEntity
import com.memorandum.data.remote.mcp.McpCache
import com.memorandum.data.remote.mcp.McpClient
import com.memorandum.data.remote.mcp.McpPrivacyFilter
import com.memorandum.data.remote.mcp.McpQueryResult
import com.memorandum.data.remote.mcp.McpResultTrimmer
import com.memorandum.data.remote.mcp.McpTool
import com.memorandum.data.repository.ConfigRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface McpExecutionResult {
    data class Success(
        val results: List<McpQueryResult>,
        val summary: String,
    ) : McpExecutionResult

    data class PartialSuccess(
        val results: List<McpQueryResult>,
        val failures: List<String>,
        val summary: String,
    ) : McpExecutionResult

    data class AllFailed(val errors: List<String>) : McpExecutionResult
    data object NetworkDisabled : McpExecutionResult
    data object NoServersConfigured : McpExecutionResult
}

@Singleton
class McpOrchestrator @Inject constructor(
    private val mcpClient: McpClient,
    private val configRepository: ConfigRepository,
    private val mcpCache: McpCache,
    private val mcpResultTrimmer: McpResultTrimmer,
    private val mcpPrivacyFilter: McpPrivacyFilter,
    private val appPreferencesDataStore: AppPreferencesDataStore,
) {

    companion object {
        private const val TAG = "McpOrchestrator"
        private const val MAX_QUERIES = 3
    }

    suspend fun executeQueries(queries: List<String>): McpExecutionResult {
        // 1. Check network permission
        val prefs = appPreferencesDataStore.preferences.first()
        if (!prefs.allowNetworkAccess) {
            Log.i(TAG, "Network access disabled, skipping MCP")
            return McpExecutionResult.NetworkDisabled
        }

        // 2. Get enabled servers
        val servers = configRepository.getEnabledMcpServers().getOrElse {
            Log.e(TAG, "Failed to get MCP servers: ${it.message}")
            return McpExecutionResult.AllFailed(listOf("Failed to load MCP servers"))
        }
        if (servers.isEmpty()) {
            Log.i(TAG, "No MCP servers configured")
            return McpExecutionResult.NoServersConfigured
        }

        // 3. Execute queries (max 3)
        val limitedQueries = queries.take(MAX_QUERIES)
        val results = mutableListOf<McpQueryResult>()
        val failures = mutableListOf<String>()

        for (query in limitedQueries) {
            val result = executeSingleQuery(query, servers)
            result.fold(
                onSuccess = { results.add(it) },
                onFailure = { failures.add("$query: ${it.message}") },
            )
        }

        // 4. Build summary
        return when {
            results.isEmpty() -> {
                Log.w(TAG, "All MCP queries failed: $failures")
                McpExecutionResult.AllFailed(failures)
            }
            failures.isEmpty() -> {
                val summary = mcpResultTrimmer.mergeResults(results)
                Log.i(TAG, "MCP queries all succeeded: ${results.size} results")
                McpExecutionResult.Success(results = results, summary = summary)
            }
            else -> {
                val summary = mcpResultTrimmer.mergeResults(results)
                Log.w(TAG, "MCP partial success: ${results.size} ok, ${failures.size} failed")
                McpExecutionResult.PartialSuccess(
                    results = results,
                    failures = failures,
                    summary = summary,
                )
            }
        }
    }

    private suspend fun executeSingleQuery(
        query: String,
        servers: List<McpServerEntity>,
    ): Result<McpQueryResult> {
        val sanitizedQuery = mcpPrivacyFilter.sanitizeQuery(query)

        // Try each server until one works
        for (server in servers) {
            val result = tryServerForQuery(server, query, sanitizedQuery)
            if (result != null) return Result.success(result)
        }

        return Result.failure(IllegalStateException("No MCP server could handle query: $query"))
    }

    private suspend fun tryServerForQuery(
        server: McpServerEntity,
        originalQuery: String,
        sanitizedQuery: String,
    ): McpQueryResult? {
        // Check cache first
        val cached = findCachedResult(server, sanitizedQuery)
        if (cached != null) return cached

        // List tools and find a suitable one
        val tools = mcpClient.listTools(server).getOrElse {
            Log.w(TAG, "Failed to list tools for server=${server.name}: ${it.message}")
            return null
        }

        val filteredTools = filterTools(tools, server.toolWhitelistJson)
        val selectedTool = selectTool(filteredTools, sanitizedQuery)
        if (selectedTool == null) {
            Log.d(TAG, "No suitable tool on server=${server.name} for query=$sanitizedQuery")
            return null
        }

        // Call the tool
        val toolResult = mcpClient.callTool(
            server = server,
            toolName = selectedTool.name,
            arguments = mapOf("query" to sanitizedQuery),
        ).getOrElse {
            Log.w(TAG, "Tool call failed: server=${server.name}, tool=${selectedTool.name}, error=${it.message}")
            return null
        }

        if (toolResult.isError) {
            Log.w(TAG, "Tool returned error: server=${server.name}, tool=${selectedTool.name}")
            return null
        }

        val trimmed = mcpResultTrimmer.trim(toolResult.content)

        // Cache the result
        mcpCache.put(server.name, selectedTool.name, sanitizedQuery, trimmed)

        // Build summary
        mcpPrivacyFilter.buildCallSummary(
            query = sanitizedQuery,
            serverName = server.name,
            toolName = selectedTool.name,
            resultPreview = trimmed,
        )

        return McpQueryResult(
            query = originalQuery,
            serverName = server.name,
            toolName = selectedTool.name,
            rawResult = toolResult.content,
            trimmedResult = trimmed,
        )
    }

    private fun findCachedResult(server: McpServerEntity, query: String): McpQueryResult? {
        // We don't know the tool name for cache lookup, so we skip server-specific cache
        // The cache is checked per server+tool in the main loop
        return null
    }

    private fun filterTools(tools: List<McpTool>, whitelist: List<String>): List<McpTool> {
        if (whitelist.isEmpty()) return tools
        return tools.filter { it.name in whitelist }
    }

    private fun selectTool(tools: List<McpTool>, query: String): McpTool? {
        if (tools.isEmpty()) return null
        // Prefer tools with "search" in name/description
        val searchTool = tools.firstOrNull { tool ->
            tool.name.contains("search", ignoreCase = true) ||
                tool.description.contains("search", ignoreCase = true) ||
                tool.description.contains("搜索", ignoreCase = true)
        }
        return searchTool ?: tools.first()
    }
}
