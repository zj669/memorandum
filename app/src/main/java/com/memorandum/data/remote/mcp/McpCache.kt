package com.memorandum.data.remote.mcp

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpCache @Inject constructor() {

    companion object {
        private const val TAG = "McpCache"
        private const val MAX_ENTRIES = 50
        private const val TTL_MS = 30L * 60 * 1000 // 30 minutes
    }

    private data class CacheEntry(
        val result: String,
        val timestamp: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(serverName: String, toolName: String, query: String): String? {
        val key = buildKey(serverName, toolName, query)
        val entry = cache[key] ?: return null
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > TTL_MS) {
            cache.remove(key)
            Log.d(TAG, "Cache expired: key=$key")
            return null
        }
        Log.d(TAG, "Cache hit: key=$key")
        return entry.result
    }

    fun put(serverName: String, toolName: String, query: String, result: String) {
        val key = buildKey(serverName, toolName, query)
        // Evict oldest if at capacity
        if (cache.size >= MAX_ENTRIES && !cache.containsKey(key)) {
            evictOldest()
        }
        cache[key] = CacheEntry(result = result, timestamp = System.currentTimeMillis())
        Log.d(TAG, "Cache put: key=$key")
    }

    fun clear() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }

    private fun buildKey(serverName: String, toolName: String, query: String): String {
        return "$serverName|$toolName|$query".hashCode().toString()
    }

    private fun evictOldest() {
        val oldest = cache.entries.minByOrNull { it.value.timestamp }
        if (oldest != null) {
            cache.remove(oldest.key)
        }
    }
}
