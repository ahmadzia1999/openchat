package com.openchat.app

import com.openchat.app.data.AppData
import com.openchat.app.data.Conversation
import com.openchat.app.data.ModelInfo
import com.openchat.app.data.Provider

data class ModelOption(
    val provider: Provider,
    val model: ModelInfo
)

data class ModelSelection(
    val provider: Provider,
    val modelId: String,
    val info: ModelInfo?
)

data class AppUiState(
    val ready: Boolean = false,
    val data: AppData = AppData(),
    val activeConversationId: String? = null,
    val fetchingProviderIds: Set<String> = emptySet(),
    val testingModelKeys: Set<String> = emptySet(),
    val streamingConversationId: String? = null
) {
    val activeConversation: Conversation?
        get() = activeConversationId?.let { id ->
            data.conversations.firstOrNull { it.id == id }
        }

    val isStreaming: Boolean
        get() = streamingConversationId != null

    fun enabledOptions(): List<ModelOption> {
        val providersById = data.providers.associateBy { it.id }
        return data.models
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { model ->
                providersById[model.providerId]?.let { provider ->
                    ModelOption(provider, model)
                }
            }
            .toList()
    }

    fun resolveSelection(): ModelSelection? {
        val conversation = activeConversation
        if (conversation != null) {
            val provider = data.providers.firstOrNull { it.id == conversation.providerId }
                ?: return null
            if (conversation.modelId.isBlank()) return null
            val info = data.models.firstOrNull {
                it.providerId == provider.id && it.modelId == conversation.modelId
            }
            return ModelSelection(provider, conversation.modelId, info)
        }

        val options = enabledOptions()
        val last = options.firstOrNull {
            it.provider.id == data.settings.lastProviderId &&
                it.model.modelId == data.settings.lastModelId
        }
        val option = last ?: options.firstOrNull() ?: return null
        return ModelSelection(option.provider, option.model.modelId, option.model)
    }

    fun currentModelLabel(): String {
        val selection = resolveSelection() ?: return "Choose model"
        return selection.info?.modelId ?: selection.modelId
    }

    fun currentProviderLabel(): String? = resolveSelection()?.provider?.name
}
