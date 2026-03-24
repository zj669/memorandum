package com.memorandum.ai.schema

import com.memorandum.data.remote.llm.LlmToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object StructuredOutputTools {
    private fun required(vararg names: String) = buildJsonArray {
        names.forEach { add(JsonPrimitive(it)) }
    }

    val clarifier = LlmToolDefinition(
        name = "submit_clarifier_output",
        description = "Return the clarification decision as structured arguments.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("ask") { put("type", "boolean") }
                putJsonObject("question") { put("type", "string") }
                putJsonObject("reason") { put("type", "string") }
            }
            put("required", required("ask"))
            put("additionalProperties", false)
        },
    )

    val planner = LlmToolDefinition(
        name = "submit_planner_output",
        description = "Return the task planning result as structured arguments.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("needs_clarification") { put("type", "boolean") }
                putJsonObject("clarification_question") { put("type", "string") }
                putJsonObject("clarification_reason") { put("type", "string") }
                putJsonObject("should_use_mcp") { put("type", "boolean") }
                putJsonObject("mcp_queries") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("task_title") { put("type", "string") }
                putJsonObject("summary") { put("type", "string") }
                putJsonObject("steps") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("index") { put("type", "integer") }
                            putJsonObject("title") { put("type", "string") }
                            putJsonObject("description") { put("type", "string") }
                            putJsonObject("needs_mcp") { put("type", "boolean") }
                        }
                        put("required", required("index", "title", "description", "needs_mcp"))
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("schedule_blocks") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("date") { put("type", "string") }
                            putJsonObject("start") { put("type", "string") }
                            putJsonObject("end") { put("type", "string") }
                            putJsonObject("reason") { put("type", "string") }
                        }
                        put("required", required("date", "start", "end", "reason"))
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("prep_items") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("risks") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("notification_candidates") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("type") { put("type", "string") }
                            putJsonObject("title") { put("type", "string") }
                            putJsonObject("body") { put("type", "string") }
                            putJsonObject("action_type") { put("type", "string") }
                        }
                        put("required", required("type", "title", "body", "action_type"))
                        put("additionalProperties", false)
                    }
                }
            }
            put(
                "required",
                required(
                    "needs_clarification",
                    "should_use_mcp",
                    "mcp_queries",
                    "steps",
                    "schedule_blocks",
                    "prep_items",
                    "risks",
                    "notification_candidates",
                ),
            )
            put("additionalProperties", false)
        },
    )

    val heartbeat = LlmToolDefinition(
        name = "submit_heartbeat_output",
        description = "Return the heartbeat decision as structured arguments.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("should_use_mcp") { put("type", "boolean") }
                putJsonObject("mcp_queries") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("should_notify") { put("type", "boolean") }
                putJsonObject("notification") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("type") { put("type", "string") }
                        putJsonObject("action_type") { put("type", "string") }
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("body") { put("type", "string") }
                    }
                    put("required", required("type", "action_type", "title", "body"))
                    put("additionalProperties", false)
                }
                putJsonObject("reason") { put("type", "string") }
                putJsonObject("task_ref") { put("type", "string") }
                putJsonObject("cooldown_hours") { put("type", "integer") }
            }
            put(
                "required",
                required(
                    "should_use_mcp",
                    "mcp_queries",
                    "should_notify",
                    "reason",
                    "cooldown_hours",
                ),
            )
            put("additionalProperties", false)
        },
    )

    val memory = LlmToolDefinition(
        name = "submit_memory_output",
        description = "Return the memory updates as structured arguments.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("new_memories") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("type") { put("type", "string") }
                            putJsonObject("subject") { put("type", "string") }
                            putJsonObject("content") { put("type", "string") }
                            putJsonObject("confidence") { put("type", "number") }
                            putJsonObject("source_refs") {
                                put("type", "array")
                                putJsonObject("items") { put("type", "string") }
                            }
                        }
                        put("required", required("type", "subject", "content", "confidence", "source_refs"))
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("updates") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("memory_id") { put("type", "string") }
                            putJsonObject("content") { put("type", "string") }
                            putJsonObject("confidence") { put("type", "number") }
                            putJsonObject("new_source_refs") {
                                put("type", "array")
                                putJsonObject("items") { put("type", "string") }
                            }
                        }
                        put("required", required("memory_id"))
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("downgrades") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("memory_id") { put("type", "string") }
                            putJsonObject("new_confidence") { put("type", "number") }
                            putJsonObject("reason") { put("type", "string") }
                        }
                        put("required", required("memory_id", "new_confidence", "reason"))
                        put("additionalProperties", false)
                    }
                }
            }
            put("required", required("new_memories", "updates", "downgrades"))
            put("additionalProperties", false)
        },
    )
}
