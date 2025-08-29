package com.meancat.panicrecorder

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PanicHttpClient(private val config: PanicConfig) {
    public val client: OkHttpClient by lazy { 
        OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(60))
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .build()
    } 
   
    private fun String?.orElseEmpty(): String = this ?: ""

    suspend fun fetchToken(): String? = withContext(Dispatchers.IO) {
        try {
            val json = "{}".toRequestBody("application/json".toMediaType())
            val request = requestBuilder("/api/auth/token", config.appSecret.orElseEmpty())
                .post(json)
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orElseEmpty()
                if (resp.isSuccessful) {
                    JSONObject(body).optString("token").takeIf{ it.isNotBlank()}
                } else {
                    Log.e("PanicApi", "Failed to fetch JWT: HTTP ${resp.code}, error: $body")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e("PanicApi", "Exception while fetching token", t)
            null
        } 
    }
    
    fun requestBuilder(path: String, token: String): Request.Builder = 
        Request.Builder()
            .url(config.apiUrl?.trimEnd('/') + path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
}