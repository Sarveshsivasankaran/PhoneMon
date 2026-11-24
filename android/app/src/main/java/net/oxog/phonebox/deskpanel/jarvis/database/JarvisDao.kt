package net.oxog.phonebox.deskpanel.jarvis.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConversations(limit: Int = 50): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFactEntity): Long

    @Query("SELECT * FROM memory_facts WHERE category = :category")
    suspend fun getFactsByCategory(category: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts")
    fun getAllFacts(): Flow<List<MemoryFactEntity>>

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteFact(id: Long)

    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}

