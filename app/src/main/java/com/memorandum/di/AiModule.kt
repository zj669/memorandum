package com.memorandum.di

import android.content.Context
import com.memorandum.data.local.room.entity.LlmConfigEntity
import com.memorandum.data.remote.llm.ImageProcessor
import com.memorandum.data.remote.llm.LlmClient
import com.memorandum.data.remote.llm.OpenAiCompatibleClient
import com.memorandum.data.remote.mcp.HttpMcpClient
import com.memorandum.data.remote.mcp.McpClient
import com.memorandum.data.repository.ConfigRepository
import com.memorandum.util.CryptoHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideLlmClient(
        configRepository: ConfigRepository,
        okHttpClient: OkHttpClient,
        cryptoHelper: CryptoHelper,
        json: Json,
    ): LlmClient {
        // Provide a lazy-loading client that fetches config on first use
        return LazyLlmClient(configRepository, okHttpClient, cryptoHelper, json)
    }

    @Provides
    @Singleton
    fun provideImageProcessor(
        @ApplicationContext context: Context,
    ): ImageProcessor = ImageProcessor(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {

    @Binds
    @Singleton
    abstract fun bindMcpClient(impl: HttpMcpClient): McpClient
}

/**
 * A wrapper that lazily resolves the LLM config from the database.
 * This avoids blocking at DI time while still providing a single LlmClient instance.
 */
private class LazyLlmClient(
    private val configRepository: ConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val cryptoHelper: CryptoHelper,
    private val json: Json,
) : LlmClient {

    @Volatile
    private var delegate: OpenAiCompatibleClient? = null
    @Volatile
    private var lastConfigId: String? = null

    override suspend fun chat(request: com.memorandum.data.remote.llm.LlmRequest): Result<com.memorandum.data.remote.llm.LlmResponse> {
        val client = getOrCreateClient()
            ?: return Result.failure(IllegalStateException("No LLM configuration found. Please configure an AI provider in Settings."))
        return client.chat(request)
    }

    override suspend fun testConnection(): Result<Boolean> {
        val client = getOrCreateClient()
            ?: return Result.failure(IllegalStateException("No LLM configuration found."))
        return client.testConnection()
    }

    private suspend fun getOrCreateClient(): OpenAiCompatibleClient? {
        val config = configRepository.getDefaultLlm().getOrNull() ?: return null
        // Recreate if config changed
        if (delegate == null || lastConfigId != config.id) {
            delegate = OpenAiCompatibleClient(config, okHttpClient, cryptoHelper, json)
            lastConfigId = config.id
        }
        return delegate
    }
}
