package com.mesha.mobile.data.local.chat

import com.mesha.mobile.domain.ai.LocalChatMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatMessageDao,
) {

    suspend fun loadMessages(): List<LocalChatMessage> =
        dao.getAll().map { entity ->
            LocalChatMessage(
                role = LocalChatMessage.Role.valueOf(entity.role),
                content = entity.content,
            )
        }

    suspend fun saveMessage(message: LocalChatMessage) {
        dao.insert(
            ChatMessageEntity(
                role = message.role.name,
                content = message.content,
            )
        )
    }

    suspend fun clearSession() {
        dao.deleteAll()
    }
}
