package com.meancat.panicrecorder

import android.content.Context


data class PanicConfig(
    val apiUrl: String?, 
    val appSecret: String?,
    val enableUpload: Boolean,
    val s3PathPrefix: String?) {
    
    companion object {
        fun load(context: Context): PanicConfig {
            val prefs = context.getSharedPreferences("panic_config", Context.MODE_PRIVATE)
            val enableUpload = prefs.getBoolean("enable_upload", false)
            val rawApiUrl = prefs.getString("api_url", null)
            val apiUrl = rawApiUrl?.trim()?.removeSuffix("/")
            val appSecret = prefs.getString("app_secret", null)
            val rawPathPrefix = prefs.getString("s3_path_prefix", "streams")
            val s3PathPrefix = rawPathPrefix?.trim()?.removePrefix("/")?.removeSuffix("/")
            return PanicConfig(apiUrl, appSecret, enableUpload, s3PathPrefix)
        }
    }
} 