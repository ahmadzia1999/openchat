package com.openchat.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ModelInfo(
    val modelId: String = "",
    val providerId: String = "",
    val enabled: Boolean = false,
    val tested: Boolean = false,
    val latencyMs: Long? = null,
    val testedAt: Long? = null,
    val isManual: Boolean = false
) {
    val key: String
        get() = "$providerId::$modelId"
}

@Serializable
data class Attachment(
    val name: String = "image.jpg",
    val mimeType: String = "image/jpeg",
    val base64: String = ""
)

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT
}

@Serializable
enum class MessageStatus {
    COMPLETE,
    STREAMING,
    ERROR,
    STOPPED
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String = "",
    val reasoning: String = "",
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val error: String? = null,
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val providerId: String = "",
    val modelId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val stream: Boolean = true,
    val lastProviderId: String = "",
    val lastModelId: String = ""
)

@Serializable
data class AppData(
    val schemaVersion: Int = 1,
    val providers: List<Provider> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val settings: AppSettings = AppSettings()
)

@Serializable
data class ExportedChats(
    val app: String = "OpenChat",
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<Conversation> = emptyList()
)
