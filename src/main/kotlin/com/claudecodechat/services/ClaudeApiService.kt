package com.claudecodechat.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class ClaudeApiService {
    private val logger = Logger.getInstance(ClaudeApiService::class.java)
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    companion object {
        fun getInstance(): ClaudeApiService = service()
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
    
    suspend fun sendMessage(
        message: String,
        apiKey: String,
        model: String = "claude-3-sonnet-20240229",
        maxTokens: Int = 4096
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JsonObject().apply {
                addProperty("model", model)
                addProperty("max_tokens", maxTokens)
                add("messages", gson.toJsonTree(listOf(
                    mapOf(
                        "role" to "user",
                        "content" to message
                    )
                )))
            }
            
            val request = Request.Builder()
                .url(ANTHROPIC_API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext Result.failure(
                    IOException("Empty response body")
                )
                
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val content = jsonResponse.getAsJsonArray("content")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString
                    ?: return@withContext Result.failure(
                        IOException("Invalid response format")
                    )
                
                Result.success(content)
            } else {
                Result.failure(
                    IOException("API request failed: ${response.code} ${response.message}")
                )
            }
        } catch (e: Exception) {
            logger.error("Error sending message to Claude API", e)
            Result.failure(e)
        }
    }
    
    fun validateApiKey(apiKey: String): Boolean {
        return apiKey.isNotBlank() && apiKey.startsWith("sk-ant-")
    }
}