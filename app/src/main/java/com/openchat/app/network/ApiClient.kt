package com.openchat.app.network

import com.openchat.app.data.Attachment
import com.openchat.app.data.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class ApiUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

data class ApiMessage(
    val role: String,
    val content: String,
    val attachments: List<Attachment> = emptyList()
)

sealed interface StreamEvent {
    data class Delta(
        val content: String,
        val reasoning: String,
        val model: String?
    ) : StreamEvent

    data class Completed(val usage: ApiUsage?) : StreamEvent
}

class ApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class ApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val streamingClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val shortClient = streamingClient.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun listModels(provider: Provider): List<String> = withContext(Dispatchers.IO) {
        val url = "${provider.baseUrl.trim().trimEnd('/')}/models"
        val request = authorizedRequest(url, provider)
            .get()
            .header("Accept", "application/json")
            .build()
        try {
            shortClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, raw)
                parseModelList(raw)
            }
        } catch (error: IOException) {
            throw ApiException(connectionMessage(error), cause = error)
        }
    }

    suspend fun testModel(provider: Provider, modelId: String): Long = withContext(Dispatchers.IO) {
        val url = "${provider.baseUrl.trim().trimEnd('/')}/chat/completions"
        val body = JSONObject()
            .put("model", modelId)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with the single word OK.")
                )
            )
            .put("stream", false)
        val request = authorizedRequest(url, provider)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()

        val startedAt = System.nanoTime()
        try {
            shortClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, raw)
                val root = runCatching { JSONObject(raw) }
                    .getOrElse { throw ApiException("The provider returned an unexpected response.") }
                errorFromJson(root)?.let { throw ApiException(it) }
                if (!root.has("choices")) {
                    throw ApiException("The provider did not return a chat completion.")
                }
            }
        } catch (error: IOException) {
            throw ApiException(connectionMessage(error), cause = error)
        }
        (System.nanoTime() - startedAt) / 1_000_000L
    }

    fun streamChat(
        provider: Provider,
        modelId: String,
        messages: List<ApiMessage>,
        systemPrompt: String,
        temperature: Double,
        maxTokens: Int?,
        stream: Boolean
    ): Flow<StreamEvent> = flow {
        if (messages.isEmpty()) throw ApiException("There are no messages to send.")
        val url = "${provider.baseUrl.trim().trimEnd('/')}/chat/completions"
        val body = buildChatBody(
            modelId = modelId,
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = stream
        )
        val request = authorizedRequest(url, provider)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .build()

        val call = streamingClient.newCall(request)
        // A blocked body read cannot observe coroutine cancellation, so a child
        // watcher cancels the socket as soon as this flow's job starts cancelling.
        val cancelWatcher = CoroutineScope(currentCoroutineContext()).launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    throw httpError(response.code, raw)
                }
                val responseBody = response.body
                    ?: throw ApiException("The provider returned an empty response.")
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                var hasOutput = false

                if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                    val completion = parseFullCompletion(responseBody.string())
                    if (completion.content.isNotBlank() || completion.reasoning.isNotBlank()) {
                        hasOutput = true
                        emit(
                            StreamEvent.Delta(
                                content = completion.content,
                                reasoning = completion.reasoning,
                                model = completion.model
                            )
                        )
                    }
                    if (!hasOutput) throw emptyResponseError()
                    emit(StreamEvent.Completed(completion.usage))
                } else {
                    var usage: ApiUsage? = null
                    val source = responseBody.source()
                    while (currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank() || line.startsWith(":")) continue
                        if (!line.startsWith("data:")) continue
                        val data = line.substring(5).trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") break

                        val chunk = try {
                            JSONObject(data)
                        } catch (_: JSONException) {
                            throw ApiException("The provider sent malformed streaming data.")
                        }
                        errorFromJson(chunk)?.let { throw ApiException(it) }
                        usage = parseUsage(chunk.optJSONObject("usage")) ?: usage

                        val choices = chunk.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val choice = choices.optJSONObject(0) ?: continue
                        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message")
                        val content = extractText(delta?.opt("content"))
                        val reasoning = extractReasoning(delta)
                        if (content.isNotEmpty() || reasoning.isNotEmpty()) {
                            hasOutput = true
                            emit(
                                StreamEvent.Delta(
                                    content = content,
                                    reasoning = reasoning,
                                    model = chunk.optString("model").takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                    if (!hasOutput) throw emptyResponseError()
                    emit(StreamEvent.Completed(usage))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error
        } catch (error: IOException) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Generation stopped")
            }
            throw ApiException(connectionMessage(error), cause = error)
        } catch (error: JSONException) {
            throw ApiException("The provider returned malformed JSON.")
        } finally {
            cancelWatcher.cancel()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun authorizedRequest(url: String, provider: Provider): Request.Builder {
        val parsedUrl = url.toHttpUrlOrNull()
            ?: throw ApiException("The Base URL is invalid. Include https:// and the full /v1 path.")
        val builder = Request.Builder()
            .url(parsedUrl)
            .header("User-Agent", "OpenChat/1.0 (Android)")
        if (provider.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${provider.apiKey.trim()}")
        }
        return builder
    }

    private fun buildChatBody(
        modelId: String,
        messages: List<ApiMessage>,
        systemPrompt: String,
        temperature: Double,
        maxTokens: Int?,
        stream: Boolean
    ): JSONObject {
        val apiMessages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            apiMessages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt.trim())
            )
        }
        messages.forEach { message ->
            val item = JSONObject().put("role", message.role)
            if (message.attachments.isEmpty()) {
                item.put("content", message.content)
            } else {
                val parts = JSONArray()
                if (message.content.isNotBlank()) {
                    parts.put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", message.content)
                    )
                }
                message.attachments.forEach { attachment ->
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put(
                                    "url",
                                    "data:${attachment.mimeType};base64,${attachment.base64}"
                                )
                            )
                    )
                }
                item.put("content", parts)
            }
            apiMessages.put(item)
        }

        return JSONObject()
            .put("model", modelId)
            .put("messages", apiMessages)
            .put("temperature", temperature.coerceIn(0.0, 2.0))
            .apply {
                if (maxTokens != null && maxTokens > 0) put("max_tokens", maxTokens)
            }
            .put("stream", stream)
    }

    private fun parseFullCompletion(raw: String): FullCompletion {
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            throw ApiException("The provider returned an unexpected response.")
        }
        errorFromJson(root)?.let { throw ApiException(it) }
        val choices = root.optJSONArray("choices")
            ?: throw ApiException("The provider response did not contain choices.")
        val choice = choices.optJSONObject(0)
            ?: throw ApiException("The provider response did not contain a completion.")
        val message = choice.optJSONObject("message")
        return FullCompletion(
            content = extractText(message?.opt("content")),
            reasoning = extractReasoning(message),
            model = root.optString("model").takeIf { it.isNotBlank() },
            usage = parseUsage(root.optJSONObject("usage"))
        )
    }

    private fun parseModelList(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) throw ApiException("The provider returned an empty model list.")
        val ids = linkedSetOf<String>()

        fun addModel(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val id = value.optString("id")
                        .ifBlank { value.optString("name") }
                        .ifBlank { value.optString("model") }
                    if (id.isNotBlank()) ids.add(id)
                }

                is String -> if (value.isNotBlank()) ids.add(value)
            }
        }

        try {
            if (text.startsWith("[")) {
                val array = JSONArray(text)
                for (index in 0 until array.length()) addModel(array.opt(index))
            } else {
                val root = JSONObject(text)
                errorFromJson(root)?.let { throw ApiException(it) }
                val data = root.opt("data") ?: root.opt("models") ?: root.opt("results")
                when (data) {
                    is JSONArray -> for (index in 0 until data.length()) addModel(data.opt(index))
                    is JSONObject -> {
                        data.keys().forEach { key -> addModel(key) }
                        val nested = data.opt("data") ?: data.opt("models")
                        if (nested is JSONArray) {
                            for (index in 0 until nested.length()) addModel(nested.opt(index))
                        }
                    }

                    else -> addModel(root)
                }
            }
        } catch (_: JSONException) {
            throw ApiException("The provider's model list is not valid JSON.")
        }

        if (ids.isEmpty()) {
            throw ApiException("No models were found. Add one manually if this provider supports it.")
        }
        return ids.sorted()
    }

    private fun parseUsage(usage: JSONObject?): ApiUsage? {
        if (usage == null) return null
        val prompt = usage.intOrNull("prompt_tokens")
        val completion = usage.intOrNull("completion_tokens")
        return if (prompt == null && completion == null) null else ApiUsage(prompt, completion)
    }

    private fun extractReasoning(value: JSONObject?): String {
        if (value == null) return ""
        val direct = extractText(value.opt("reasoning_content"))
        if (direct.isNotBlank()) return direct
        return extractText(value.opt("reasoning"))
    }

    private fun extractText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                append(extractText(value.opt(index)))
            }
        }

        is JSONObject -> when {
            value.has("text") -> extractText(value.opt("text"))
            value.has("content") -> extractText(value.opt("content"))
            else -> ""
        }

        else -> ""
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun errorFromJson(root: JSONObject): String? {
        val error = root.opt("error") ?: return null
        return when (error) {
            is JSONObject -> {
                error.optString("message")
                    .ifBlank { error.optString("detail") }
                    .ifBlank { error.optString("code") }
                    .ifBlank { "The provider returned an error." }
            }

            is String -> error.ifBlank { "The provider returned an error." }
            else -> "The provider returned an error."
        }
    }

    private fun httpError(statusCode: Int, raw: String): ApiException {
        val detail = readableError(raw)
        val message = if (detail != null) {
            "Provider error ($statusCode): $detail"
        } else {
            "The provider returned HTTP $statusCode."
        }
        return ApiException(message, statusCode)
    }

    private fun readableError(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        runCatching {
            val root = JSONObject(trimmed)
            errorFromJson(root)?.let { return it }
            root.optString("message").takeIf { it.isNotBlank() }?.let { return it }
            root.optString("detail").takeIf { it.isNotBlank() }?.let { return it }
        }
        return trimmed
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(600)
            .takeIf { it.isNotBlank() }
    }

    private fun emptyResponseError(): ApiException =
        ApiException("The model returned an empty response. It may not support this request.")

    private fun connectionMessage(error: IOException): String = when (error) {
        is UnknownHostException -> "Could not reach the server. Check the Base URL and your connection."
        is ConnectException -> "The server refused the connection. Check the Base URL."
        is SocketTimeoutException -> "The provider took too long to respond."
        is SSLException -> "The secure connection failed. Check the provider's certificate and Base URL."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Network request failed."
    }

    private data class FullCompletion(
        val content: String,
        val reasoning: String,
        val model: String?,
        val usage: ApiUsage?
    )
}
