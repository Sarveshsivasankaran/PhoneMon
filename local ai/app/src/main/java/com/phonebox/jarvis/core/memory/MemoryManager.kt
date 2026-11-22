package com.phonebox.jarvis.core.memory

import com.phonebox.jarvis.database.ConversationEntity
import com.phonebox.jarvis.database.JarvisDao
import com.phonebox.jarvis.database.MemoryFactEntity
import kotlinx.coroutines.flow.Flow

/**
 * High-level memory coordinator handling persistent context,
 * fact retrieval, and history management.
 */
class MemoryManager(private val dao: JarvisDao) {

    suspend fun recordTurn(sender: String, message: String, isSpoken: Boolean = false) {
        dao.insertConversation(
            ConversationEntity(
                sender = sender,
                message = message,
                isSpoken = isSpoken
            )
        )
    }

    fun observeRecentConversations(limit: Int = 30): Flow<List<ConversationEntity>> {
        return dao.getRecentConversations(limit)
    }

    suspend fun storeFact(key: String, value: String, category: String = "PREFERENCE") {
        dao.insertFact(
            MemoryFactEntity(
                category = category,
                key = key,
                value = value
            )
        )
    }

    fun observeAllFacts(): Flow<List<MemoryFactEntity>> {
        return dao.getAllFacts()
    }

    suspend fun removeFact(id: Long) {
        dao.deleteFact(id)
    }

    suspend fun clearConversations() {
        dao.clearAllConversations()
    }
}

