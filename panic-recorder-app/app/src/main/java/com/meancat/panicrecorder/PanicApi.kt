package com.meancat.panicrecorder

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class StartMultipartResp(val uploadId: String, val objectKey: String)
data class PresignPartResp(val url: String, val headers: Map<String, String> = emptyMap())
data class CompletedPart(val partNumber: Int, val eTag: String)

class PanicApi(private val http: PanicHttpClient, private val jwtToken: String) {
    fun requestBuilder(path: String): Request.Builder =
        http.requestBuilder(path, jwtToken)

    fun startMultipart(objectKeyHint: String): StartMultipartResp? {
        val body =
            """{"keyHint":"$objectKeyHint"}""".toRequestBody("application/json".toMediaType())
        val req = requestBuilder("/api/upload/multipart/start")
            .post(body)
            .build()
        http.client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e("PanicApi", "startMultipart failed ${resp.code}: $txt")
                return null
            }
            Log.d("PanicApi", "startMultipart: $txt")
            val j = JSONObject(txt)
            return StartMultipartResp(j.getString("uploadId"), j.getString("objectKey"))
        }
    }

    fun presignPart(uploadId: String, partNumber: Int, objectKey: String): PresignPartResp? {
        val req =
            requestBuilder("/api/upload/multipart/presign?uploadId=$uploadId&partNumber=$partNumber&objectKey=$objectKey")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        http.client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e("PanicApi", "presignPart failed ${resp.code}: $txt")
                return null
            }
            val j = JSONObject(txt)
            val headers = mutableMapOf<String, String>()
            j.optJSONObject("headers")?.let { obj ->
                obj.keys().forEach { k -> headers[k] = obj.getString(k) }
            }
            return PresignPartResp(j.getString("url"), headers)
        }
    }
    
    fun completeMultipart(uploadId: String, parts: List<CompletedPart>) : Boolean {
        val partsJson = JSONArray().apply {
            parts.forEach {
                put(
                    JSONObject(
                        mapOf(
                            "partNumber" to it.partNumber,
                            "eTag" to it.eTag
                        )
                    )
                )
            }
        }
        val body = JSONObject(mapOf("uploadId" to uploadId, "parts" to partsJson))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = requestBuilder("/api/upload/multipart/complete")
            .post(body)
            .build()
        http.client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e("PanicApi", "completeMultipart failed ${resp.code}: $txt")
                return false
            }
            return true
        }
    }
    
    fun abortMultipart(uploadId: String) {
        val req = requestBuilder("/api/upload/multipart/abort")
            .post(JSONObject(mapOf("uploadId" to uploadId)).toString()
                .toRequestBody("application/json".toMediaType()))
            .build()
        http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("PanicApi", "abortMultipart failed ${resp.code}")
            }
        }
    }
}