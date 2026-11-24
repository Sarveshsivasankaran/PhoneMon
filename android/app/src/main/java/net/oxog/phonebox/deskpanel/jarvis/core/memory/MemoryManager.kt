package net.oxog.phonebox.deskpanel.jarvis.core.memory

import net.oxog.phonebox.deskpanel.jarvis.database.ConversationEntity
import net.oxog.phonebox.deskpanel.jarvis.database.JarvisDao

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
}

