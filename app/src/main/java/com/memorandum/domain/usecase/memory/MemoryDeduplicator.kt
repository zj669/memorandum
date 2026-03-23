package com.memorandum.domain.usecase.memory

import com.memorandum.ai.schema.NewMemory
import com.memorandum.data.local.room.entity.MemoryEntity
import javax.inject.Inject

class MemoryDeduplicator @Inject constructor() {

    companion object {
        private const val SIMILARITY_THRESHOLD = 0.8f
    }

    fun findDuplicate(
        newMemory: NewMemory,
        existing: List<MemoryEntity>,
    ): MemoryEntity? {
        return existing
            .filter { it.type.name == newMemory.type.uppercase() }
            .firstOrNull { subjectSimilarity(it.subject, newMemory.subject) > SIMILARITY_THRESHOLD }
    }

    private fun subjectSimilarity(a: String, b: String): Float {
        val setA = a.toSet()
        val setB = b.toSet()
        val intersection = setA.intersect(setB)
        val union = setA.union(setB)
        return if (union.isEmpty()) 0f else intersection.size.toFloat() / union.size
    }
}
