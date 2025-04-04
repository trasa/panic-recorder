package com.meancat.panicrecorder

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URI


data class PresignResult(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

class PanicApi(private val config: PanicConfig) {
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(60))
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .build()
    }
    
    public fun fetchToken(): String? {
        if (!config.enableUpload) {
            throw IllegalStateException("can't get url if enableUpload is false")
        }
        return try {
            val url = URI(config.apiUrl).resolve("/api/auth/token").toString()
            val json = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(json)
                .addHeader("Authorization", "Bearer ${config.appSecret}")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orElseEmpty()
                if (resp.isSuccessful) {
                    val token = JSONObject(body).optString("token")
                    if (token.isNullOrBlank()) {
                        Log.e("PanicApi", "No token in response body")
                        null
                    } else {
                        token
                    }
                } else {
                    Log.e("PanicApi", "Failed to fetch JWT: HTTP ${resp.code}, error: $body")
                    null
                } 
            }
        } catch (e: Exception) {
            Log.e("PanicApi", "Exception while fetching token", e)
            null
        }
    }
    
    private fun String?.orElseEmpty(): String = this ?: ""

    fun getPresignedUrl(filename: String, jwtToken: String): PresignResult? {
        if (!config.enableUpload) {
            throw IllegalStateException("can't get presigned url if enableUpload is false")
        }
        return try {
            val url =
                URI(config.apiUrl).resolve("/api/upload/presigned?filename=$filename").toString()
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orElseEmpty()
                if (resp.isSuccessful) {
                    PresignResult(body, resp.headers.toMap())
                } else {
                    Log.e(
                        "PanicApi",
                        "failed to fetch presigned url for $filename: HTTP ${resp.code}, error: $body"
                    )
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("PanicApi", "Exception while fetching presigned url", e)
            null
        }
    }
}