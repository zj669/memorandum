package com.memorandum.ai.schema

import kotlinx.serialization.Serializable

@Serializable
data class ClarifierOutput(
    val ask: Boolean = false,
    val question: String? = null,
    val reason: String? = null,
)
