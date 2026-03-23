package com.memorandum.navigation

sealed class Route(val route: String) {
    // Bottom tabs
    data object Today : Route("today")
    data object Tasks : Route("tasks")
    data object Memory : Route("memory")
    data object Settings : Route("settings")

    // Secondary pages
    data object Entry : Route("entry")
    data object TaskDetail : Route("task_detail/{taskId}") {
        fun create(taskId: String) = "task_detail/$taskId"
    }
    data object Notifications : Route("notifications")
    data object ModelConfig : Route("model_config?configId={configId}") {
        fun create(configId: String? = null): String =
            if (configId != null) "model_config?configId=$configId" else "model_config"
    }
    data object McpConfig : Route("mcp_config?serverId={serverId}") {
        fun create(serverId: String? = null): String =
            if (serverId != null) "mcp_config?serverId=$serverId" else "mcp_config"
    }
}
