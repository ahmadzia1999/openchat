package com.openchat.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openchat.app.data.AppData
import com.openchat.app.data.AppRepository
import com.openchat.app.data.AppSettings
import com.openchat.app.data.Attachment
import com.openchat.app.data.ChatMessage
import com.openchat.app.data.ChatRole
import com.openchat.app.data.Conversation
import com.openchat.app.data.ExportedChats
import com.openchat.app.data.MessageStatus
import com.openchat.app.data.ModelInfo
import com.openchat.app.data.Provider
import com.openchat.app.data.ThemeMode
import com.openchat.app.network.ApiClient
import com.openchat.app.network.ApiMessage
import com.openchat.app.network.ApiUsage
import com.openchat.app.network.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val apiClient = ApiClient()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var streamingJob: Job? = null
    private var streamToken = 0L

    private val whitespaceRegex = Regex("\\s+")

    init {
        viewModelScope.launch {
            val result = repository.load()
            val normalized = normalizeLoadedData(result.data)
            _uiState.update {
                it.copy(ready = true, data = normalized)
            }
            result.error?.let(::notify)
        }
    }

    fun notify(message: String) {
        if (message.isNotBlank()) eventChannel.trySend(message)
    }

    // ---------------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------------

    fun saveProvider(
        providerId: String?,
        name: String,
        baseUrl: String,
        apiKey: String
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            notify("Provider name is required.")
            return null
        }
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        if (normalizedUrl == null) {
            notify("Enter a valid Base URL, for example https://api.openai.com/v1")
            return null
        }

        val targetId = providerId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        var wasExisting = false
        mutateData { data ->
            val existing = data.providers.firstOrNull { it.id == targetId }
            wasExisting = existing != null
            val provider = existing?.copy(
                name = cleanName,
                baseUrl = normalizedUrl,
                apiKey = apiKey.trim()
            ) ?: Provider(
                id = targetId,
                name = cleanName,
                baseUrl = normalizedUrl,
                apiKey = apiKey.trim()
            )
            val providers = if (existing == null) {
                data.providers + provider
            } else {
                data.providers.map { if (it.id == targetId) provider else it }
            }
            data.copy(providers = providers)
        }
        notify(if (wasExisting) "Provider updated." else "Provider added.")
        return targetId
    }

    fun deleteProvider(providerId: String) {
        val state = _uiState.value
        val streamingConversation = state.data.conversations.firstOrNull {
            it.id == state.streamingConversationId
        }
        if (streamingConversation?.providerId == providerId) {
            streamingJob?.cancel()
        }
        mutateData { data ->
            data.copy(
                providers = data.providers.filterNot { it.id == providerId },
                models = data.models.filterNot { it.providerId == providerId }
            ).withValidDefaultSelection()
        }
        notify("Provider removed. Existing chats were kept.")
    }

    // ---------------------------------------------------------------------
    // Models
    // ---------------------------------------------------------------------

    fun fetchModels(providerId: String) {
        val state = _uiState.value
        val provider = state.data.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            notify("Provider not found.")
            return
        }
        if (providerId in state.fetchingProviderIds) return

        _uiState.update {
            it.copy(fetchingProviderIds = it.fetchingProviderIds + providerId)
        }
        viewModelScope.launch {
            try {
                val remoteModels = apiClient.listModels(provider)
                mutateData { data ->
                    if (data.providers.none { it.id == providerId }) {
                        return@mutateData data
                    }
                    val otherModels = data.models.filterNot { it.providerId == providerId }
                    val existing = data.models
                        .filter { it.providerId == providerId }
                        .associateBy { it.modelId }
                    val fetched = remoteModels.map { modelId ->
                        existing[modelId]?.copy(isManual = false)
                            ?: ModelInfo(modelId = modelId, providerId = providerId)
                    }
                    val manualExtras = existing.values.filter {
                        it.isManual && it.modelId !in remoteModels
                    }
                    val providerModels = (fetched + manualExtras).distinctBy { it.modelId }
                    data.copy(models = otherModels + providerModels)
                        .withValidDefaultSelection()
                }
                notify("${remoteModels.size} models found. Test and enable the ones you want.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notify(error.message ?: "Could not fetch models.")
            } finally {
                _uiState.update {
                    it.copy(fetchingProviderIds = it.fetchingProviderIds - providerId)
                }
            }
        }
    }

    fun testModel(providerId: String, modelId: String) {
        val state = _uiState.value
        val provider = state.data.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            notify("Provider not found.")
            return
        }
        val key = "$providerId::$modelId"
        if (key in state.testingModelKeys) return

        _uiState.update { it.copy(testingModelKeys = it.testingModelKeys + key) }
        viewModelScope.launch {
            try {
                val latencyMs = apiClient.testModel(provider, modelId)
                mutateData { data ->
                    if (data.providers.none { it.id == providerId }) {
                        return@mutateData data
                    }
                    val models = data.models.map { model ->
                        if (model.providerId == providerId && model.modelId == modelId) {
                            model.copy(
                                enabled = true,
                                tested = true,
                                latencyMs = latencyMs,
                                testedAt = System.currentTimeMillis()
                            )
                        } else {
                            model
                        }
                    }
                    data.copy(models = models).withValidDefaultSelection()
                }
                notify("$modelId passed the test in $latencyMs ms and was enabled.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notify(error.message ?: "The model test failed.")
            } finally {
                _uiState.update {
                    it.copy(testingModelKeys = it.testingModelKeys - key)
                }
            }
        }
    }

    fun setModelEnabled(providerId: String, modelId: String, enabled: Boolean) {
        mutateData { data ->
            if (data.models.none { it.providerId == providerId && it.modelId == modelId }) {
                return@mutateData data
            }
            val models = data.models.map { model ->
                if (model.providerId == providerId && model.modelId == modelId) {
                    model.copy(enabled = enabled)
                } else {
                    model
                }
            }
            data.copy(models = models).withValidDefaultSelection()
        }
    }

    fun addManualModel(providerId: String, rawModelId: String): Boolean {
        val modelId = rawModelId.trim()
        if (modelId.isBlank()) {
            notify("Enter a model ID.")
            return false
        }
        val state = _uiState.value
        if (state.data.providers.none { it.id == providerId }) {
            notify("Provider not found.")
            return false
        }
        if (state.data.models.any { it.providerId == providerId && it.modelId == modelId }) {
            notify("That model is already in the list.")
            return false
        }
        mutateData { data ->
            val newModel = ModelInfo(
                modelId = modelId,
                providerId = providerId,
                enabled = true,
                isManual = true
            )
            data.copy(models = data.models + newModel).withValidDefaultSelection()
        }
        notify("Model added and enabled.")
        return true
    }

    fun deleteModel(providerId: String, modelId: String) {
        mutateData { data ->
            data.copy(
                models = data.models.filterNot {
                    it.providerId == providerId && it.modelId == modelId
                }
            ).withValidDefaultSelection()
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        val state = _uiState.value
        if (state.isStreaming) {
            notify("Stop generating before switching models.")
            return
        }
        val model = state.data.models.firstOrNull {
            it.providerId == providerId && it.modelId == modelId
        }
        if (model == null) {
            notify("Model not found.")
            return
        }
        if (!model.enabled) {
            notify("Enable this model before selecting it.")
            return
        }
        val provider = state.data.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            notify("Provider not found.")
            return
        }

        val active = state.activeConversation
        val baseData = if (active == null) {
            state.data
        } else {
            state.data.copy(
                conversations = state.data.conversations.map { conversation ->
                    if (conversation.id == active.id) {
                        conversation.copy(providerId = provider.id, modelId = model.modelId)
                    } else {
                        conversation
                    }
                }
            )
        }
        val newData = baseData.copy(
            settings = baseData.settings.copy(
                lastProviderId = provider.id,
                lastModelId = model.modelId
            )
        )
        _uiState.update { it.copy(data = newData) }
        repository.save(newData)
    }

    // ---------------------------------------------------------------------
    // Conversations and generation
    // ---------------------------------------------------------------------

    fun startNewChat() {
        _uiState.update { it.copy(activeConversationId = null) }
    }

    fun openConversation(conversationId: String) {
        if (_uiState.value.data.conversations.any { it.id == conversationId }) {
            _uiState.update { it.copy(activeConversationId = conversationId) }
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        val clean = title.trim().ifBlank { "New chat" }
        mutateData { data ->
            data.copy(
                conversations = data.conversations.map {
                    if (it.id == conversationId) it.copy(title = clean) else it
                }
            )
        }
    }

    fun togglePinned(conversationId: String) {
        mutateData { data ->
            data.copy(
                conversations = data.conversations.map {
                    if (it.id == conversationId) it.copy(pinned = !it.pinned) else it
                }
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        val state = _uiState.value
        if (state.streamingConversationId == conversationId) {
            streamingJob?.cancel()
        }
        mutateData { data ->
            data.copy(conversations = data.conversations.filterNot { it.id == conversationId })
        }
        if (state.activeConversationId == conversationId) {
            _uiState.update { it.copy(activeConversationId = null) }
        }
    }

    fun clearAllConversations() {
        streamingJob?.cancel()
        mutateData { it.copy(conversations = emptyList()) }
        _uiState.update { it.copy(activeConversationId = null) }
    }

    fun sendMessage(text: String, attachments: List<Attachment>) {
        val state = _uiState.value
        if (!state.ready) return
        if (state.isStreaming) {
            notify("Wait for the current response to finish.")
            return
        }

        val cleanText = text.trim()
        val safeAttachments = attachments.take(4)
        if (cleanText.isEmpty() && safeAttachments.isEmpty()) return

        val selection = state.resolveSelection()
        if (selection == null) {
            notify("Connect a provider and enable a model in Settings first.")
            return
        }

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = cleanText,
            attachments = safeAttachments,
            createdAt = now,
            status = MessageStatus.COMPLETE
        )
        val assistantMessage = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            createdAt = now,
            status = MessageStatus.STREAMING,
            model = selection.modelId
        )
        val active = state.activeConversation
        val title = when {
            active == null -> titleFrom(cleanText, safeAttachments)
            active.messages.none { it.role == ChatRole.USER } ->
                titleFrom(cleanText, safeAttachments)

            else -> active.title
        }
        val conversation = if (active == null) {
            Conversation(
                title = title,
                providerId = selection.provider.id,
                modelId = selection.modelId,
                messages = listOf(userMessage, assistantMessage),
                createdAt = now,
                updatedAt = now
            )
        } else {
            active.copy(
                title = title,
                providerId = selection.provider.id,
                modelId = selection.modelId,
                messages = active.messages + userMessage + assistantMessage,
                updatedAt = now
            )
        }
        beginReply(conversation, selection.provider)
    }

    fun regenerateResponse(assistantMessageId: String? = null) {
        val state = _uiState.value
        if (state.isStreaming) {
            notify("Wait for the current response to finish.")
            return
        }
        val conversation = state.activeConversation ?: return
        val targetIndex = if (assistantMessageId == null) {
            conversation.messages.indexOfLast { it.role == ChatRole.ASSISTANT }
        } else {
            conversation.messages.indexOfLast {
                it.id == assistantMessageId && it.role == ChatRole.ASSISTANT
            }
        }
        if (targetIndex < 0) {
            notify("Could not find that response.")
            return
        }
        val provider = state.data.providers.firstOrNull { it.id == conversation.providerId }
        if (provider == null || conversation.modelId.isBlank()) {
            notify("This chat's model was removed. Pick a model before regenerating.")
            return
        }
        val historyBefore = conversation.messages.take(targetIndex)
        if (historyBefore.none { it.role == ChatRole.USER }) {
            notify("There is no user message to regenerate from.")
            return
        }
        val placeholder = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            createdAt = System.currentTimeMillis(),
            status = MessageStatus.STREAMING,
            model = conversation.modelId
        )
        val updated = conversation.copy(
            messages = historyBefore + placeholder,
            providerId = provider.id,
            modelId = conversation.modelId,
            updatedAt = System.currentTimeMillis()
        )
        beginReply(updated, provider)
    }

    fun editMessage(messageId: String, newText: String) {
        val state = _uiState.value
        if (state.isStreaming) {
            notify("Wait for the current response to finish.")
            return
        }
        val conversation = state.activeConversation ?: return
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val target = conversation.messages[index]
        if (target.role != ChatRole.USER) return
        val clean = newText.trim()
        if (clean.isEmpty() && target.attachments.isEmpty()) {
            notify("A message cannot be empty.")
            return
        }
        val provider = state.data.providers.firstOrNull { it.id == conversation.providerId }
        if (provider == null || conversation.modelId.isBlank()) {
            notify("This chat's model was removed. Pick a model before editing.")
            return
        }

        val edited = target.copy(
            content = clean,
            status = MessageStatus.COMPLETE,
            error = null
        )
        val firstUser = conversation.messages.firstOrNull { it.role == ChatRole.USER }
        val title = if (firstUser?.id == target.id) {
            titleFrom(clean, target.attachments)
        } else {
            conversation.title
        }
        val placeholder = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            createdAt = System.currentTimeMillis(),
            status = MessageStatus.STREAMING,
            model = conversation.modelId
        )
        val updated = conversation.copy(
            title = title,
            messages = conversation.messages.take(index) + edited + placeholder,
            updatedAt = System.currentTimeMillis()
        )
        beginReply(updated, provider)
    }

    fun stopStreaming() {
        val job = streamingJob
        if (job != null && job.isActive) {
            job.cancel()
        } else {
            streamingJob = null
            _uiState.update { it.copy(streamingConversationId = null) }
        }
    }

    private fun beginReply(conversation: Conversation, provider: Provider) {
        if (conversation.modelId.isBlank()) {
            notify("Select a model first.")
            return
        }
        val placeholder = conversation.messages.lastOrNull()
        if (placeholder?.role != ChatRole.ASSISTANT ||
            placeholder.status != MessageStatus.STREAMING
        ) {
            return
        }
        val history = messagesForApi(conversation.messages.dropLast(1))
        if (history.isEmpty() || history.last().role != "user") {
            notify("There is nothing to send.")
            return
        }

        val current = _uiState.value
        val settings = current.data.settings
        val newSettings = settings.copy(
            lastProviderId = provider.id,
            lastModelId = conversation.modelId
        )
        val conversations = if (current.data.conversations.any { it.id == conversation.id }) {
            current.data.conversations.map {
                if (it.id == conversation.id) conversation else it
            }
        } else {
            current.data.conversations + conversation
        }
        val newData = current.data.copy(
            conversations = conversations,
            settings = newSettings
        )
        _uiState.update {
            it.copy(
                data = newData,
                activeConversationId = conversation.id,
                streamingConversationId = conversation.id
            )
        }
        repository.save(newData)
        generate(
            conversationId = conversation.id,
            assistantMessageId = placeholder.id,
            provider = provider,
            modelId = conversation.modelId,
            history = history,
            settings = settings
        )
    }

    private fun generate(
        conversationId: String,
        assistantMessageId: String,
        provider: Provider,
        modelId: String,
        history: List<ApiMessage>,
        settings: AppSettings
    ) {
        val generation = ++streamToken
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var latestModel = modelId
        var usage: ApiUsage? = null

        streamingJob = viewModelScope.launch {
            try {
                apiClient.streamChat(
                    provider = provider,
                    modelId = modelId,
                    messages = history,
                    systemPrompt = settings.systemPrompt,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    stream = settings.stream
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            contentBuilder.append(event.content)
                            reasoningBuilder.append(event.reasoning)
                            event.model?.takeIf { it.isNotBlank() }?.let { latestModel = it }
                            updateAssistantMessage(
                                conversationId = conversationId,
                                messageId = assistantMessageId,
                                status = MessageStatus.STREAMING,
                                content = contentBuilder.toString(),
                                reasoning = reasoningBuilder.toString(),
                                error = null,
                                model = latestModel,
                                usage = null,
                                persist = false
                            )
                        }

                        is StreamEvent.Completed -> {
                            usage = event.usage
                        }
                    }
                }
                updateAssistantMessage(
                    conversationId = conversationId,
                    messageId = assistantMessageId,
                    status = MessageStatus.COMPLETE,
                    content = contentBuilder.toString(),
                    reasoning = reasoningBuilder.toString(),
                    error = null,
                    model = latestModel,
                    usage = usage,
                    persist = true
                )
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    updateAssistantMessage(
                        conversationId = conversationId,
                        messageId = assistantMessageId,
                        status = MessageStatus.STOPPED,
                        content = contentBuilder.toString(),
                        reasoning = reasoningBuilder.toString(),
                        error = null,
                        model = latestModel,
                        usage = null,
                        persist = true
                    )
                }
            } catch (error: Exception) {
                updateAssistantMessage(
                    conversationId = conversationId,
                    messageId = assistantMessageId,
                    status = MessageStatus.ERROR,
                    content = contentBuilder.toString(),
                    reasoning = reasoningBuilder.toString(),
                    error = error.message ?: "The request failed.",
                    model = latestModel,
                    usage = null,
                    persist = true
                )
            } finally {
                if (streamToken == generation) {
                    streamingJob = null
                    _uiState.update { it.copy(streamingConversationId = null) }
                }
            }
        }
    }

    private fun updateAssistantMessage(
        conversationId: String,
        messageId: String,
        status: MessageStatus,
        content: String,
        reasoning: String,
        error: String?,
        model: String?,
        usage: ApiUsage?,
        persist: Boolean
    ) {
        val data = _uiState.value.data
        var found = false
        val conversations = data.conversations.map { conversation ->
            if (conversation.id != conversationId) {
                conversation
            } else {
                val messages = conversation.messages.map { message ->
                    if (message.id == messageId) {
                        found = true
                        message.copy(
                            content = content,
                            reasoning = reasoning,
                            status = status,
                            error = error,
                            model = model ?: message.model,
                            promptTokens = usage?.promptTokens ?: message.promptTokens,
                            completionTokens = usage?.completionTokens ?: message.completionTokens
                        )
                    } else {
                        message
                    }
                }
                if (found) conversation.copy(messages = messages) else conversation
            }
        }
        if (!found) return

        val newData = data.copy(conversations = conversations)
        _uiState.update { it.copy(data = newData) }
        if (persist) repository.save(newData)
    }

    // ---------------------------------------------------------------------
    // Settings and export
    // ---------------------------------------------------------------------

    fun setTheme(theme: ThemeMode) {
        mutateData { it.copy(settings = it.settings.copy(theme = theme)) }
    }

    fun setSystemPrompt(prompt: String) {
        mutateData { it.copy(settings = it.settings.copy(systemPrompt = prompt.trim())) }
    }

    fun setTemperature(value: Double) {
        mutateData {
            it.copy(settings = it.settings.copy(temperature = value.coerceIn(0.0, 2.0)))
        }
    }

    fun setMaxTokens(value: Int?) {
        mutateData {
            it.copy(
                settings = it.settings.copy(
                    maxTokens = value?.takeIf { tokens -> tokens > 0 }?.coerceAtMost(200_000)
                )
            )
        }
    }

    fun setStreaming(enabled: Boolean) {
        mutateData { it.copy(settings = it.settings.copy(stream = enabled)) }
    }

    fun exportChatsJson(): String {
        val data = _uiState.value.data
        return Json {
            encodeDefaults = true
            prettyPrint = true
        }.encodeToString(
            ExportedChats.serializer(),
            ExportedChats(
                conversations = data.conversations.sortedByDescending { it.updatedAt }
            )
        )
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun mutateData(transform: (AppData) -> AppData): AppData {
        val newData = transform(_uiState.value.data)
        _uiState.update { it.copy(data = newData) }
        repository.save(newData)
        return newData
    }

    private fun normalizeLoadedData(data: AppData): AppData {
        val repaired = data.conversations.map { conversation ->
            if (conversation.messages.none { it.status == MessageStatus.STREAMING }) {
                conversation
            } else {
                conversation.copy(
                    messages = conversation.messages.map { message ->
                        if (message.status == MessageStatus.STREAMING) {
                            message.copy(status = MessageStatus.STOPPED)
                        } else {
                            message
                        }
                    }
                )
            }
        }
        return data.copy(conversations = repaired)
    }

    private fun normalizeBaseUrl(value: String): String? {
        var raw = value.trim()
        if (raw.isBlank()) return null
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "https://$raw"
        }
        raw = raw.trimEnd('/')
        return raw.takeIf {
            it.length > "https://a".length && it.toHttpUrlOrNull() != null
        }
    }

    private fun titleFrom(text: String, attachments: List<Attachment>): String {
        val compact = text.replace(whitespaceRegex, " ").trim()
        if (compact.isBlank()) {
            return if (attachments.isNotEmpty()) "Image chat" else "New chat"
        }
        val shortened = compact.take(48)
        return if (compact.length > 48) "$shortened…" else shortened
    }

    private fun messagesForApi(messages: List<ChatMessage>): List<ApiMessage> {
        return messages
            .filter {
                it.status != MessageStatus.STREAMING &&
                    it.status != MessageStatus.ERROR &&
                    (it.content.isNotBlank() || it.attachments.isNotEmpty())
            }
            .map { message ->
                ApiMessage(
                    role = if (message.role == ChatRole.USER) "user" else "assistant",
                    content = message.content,
                    attachments = message.attachments
                )
            }
    }

    private fun AppData.withValidDefaultSelection(): AppData {
        val providerIds = providers.map { it.id }.toSet()
        val currentValid = models.any {
            it.enabled &&
                it.providerId == settings.lastProviderId &&
                it.modelId == settings.lastModelId &&
                it.providerId in providerIds
        }
        if (currentValid) return this

        val first = models.firstOrNull {
            it.enabled && it.providerId in providerIds
        }
        val newSettings = if (first == null) {
            settings.copy(lastProviderId = "", lastModelId = "")
        } else {
            settings.copy(lastProviderId = first.providerId, lastModelId = first.modelId)
        }
        return copy(settings = newSettings)
    }
}
