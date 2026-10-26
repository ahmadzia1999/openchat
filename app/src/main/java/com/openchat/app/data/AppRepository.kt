package com.openchat.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class LoadResult(
    val data: AppData,
    val error: String? = null
)

class AppRepository(context: Context) {
    companion object {
        private const val TAG = "AppRepository"
        private const val PREFS = "openchat_local_store"
        private const val KEY_STATE = "encrypted_state_v1"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = CryptoManager()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveChannel = Channel<AppData>(Channel.CONFLATED)

    init {
        saveScope.launch {
            for (state in saveChannel) {
                runCatching { persist(state) }
                    .onFailure { Log.e(TAG, "Could not save local state", it) }
            }
        }
    }

    suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        val encrypted = prefs.getString(KEY_STATE, null)
            ?: return@withContext LoadResult(AppData())
        runCatching {
            val decoded = crypto.decrypt(encrypted)
            json.decodeFromString(AppData.serializer(), decoded)
        }.fold(
            onSuccess = { LoadResult(it) },
            onFailure = {
                Log.e(TAG, "Could not load local state", it)
                LoadResult(
                    data = AppData(),
                    error = "Local chats could not be decrypted on this device."
                )
            }
        )
    }

    fun save(state: AppData) {
        saveChannel.trySend(state)
    }

    private fun persist(state: AppData) {
        val encoded = json.encodeToString(AppData.serializer(), state)
        val encrypted = crypto.encrypt(encoded)
        prefs.edit().putString(KEY_STATE, encrypted).apply()
    }
}
