package com.meancat.panicrecorder

import android.util.Log
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

class MultipartStreamer(
    private val api: PanicApi,
    private val http: PanicHttpClient,
    private val file: File,
    private val objectKeyHint: String,
    private val partSizeBytes: Int = 5*1024*1024 // 5 MB
) {
   @Volatile private var running = true 
    
    data class State (
        val uploadId: String,
        val objectKey: String,
        var nextPartNum: Int = 1,
        val completed: MutableList<CompletedPart> = mutableListOf()
    )
    
    fun stop() {
        running = false
    }
    
    fun runBlocking(): Boolean {
        val start = api.startMultipart(objectKeyHint) ?: return false
        Log.d("MultipartStreamer", "startMultipart uploadId ${start.uploadId} objectKey ${start.objectKey}")
        val state = State(uploadId = start.uploadId, objectKey = start.objectKey)
        val raf = RandomAccessFile(file, "r")
        var readPos = 0L
        val buffer = ByteArrayOutputStream(partSizeBytes + 1024)
        try {
            while (running || readPos < file.length()) {
                // read newly written bytes
                val available = (file.length() - readPos).coerceAtMost(1024L * 256L) // 256kb step
                if (available > 0) {
                    val tmp = ByteArray(available.toInt())
                    raf.seek(readPos)
                    val n = raf.read(tmp)
                    if (n > 0) {
                        buffer.write(tmp, 0, n)
                        readPos += n
                    }
                } else {
                    // nothing new yet, so wait
                    Thread.sleep(30)
                }
                // if buffer is big enough, upload a part
                if (buffer.size() >= partSizeBytes) {
                    val ok = uploadPart(state, buffer.toByteArray())
                    buffer.reset()
                    if (!ok) {
                        throw RuntimeException("part ${state.nextPartNum - 1} upload failed")
                    }
                }
            }
            // last part
            if (buffer.size() > 0) {
                if (!uploadPart(state, buffer.toByteArray())) {
                    throw RuntimeException("final part upload failed")
                }
            }
            return api.completeMultipart(state.uploadId, state.objectKey, state.completed)
        } catch(t: Throwable) {
            Log.e("MultipartStreamer", "streaming error", t)
            // best effort abort
            runCatching { api.abortMultipart(state.uploadId) }
            return false
        } finally {
            raf.close()
        }
    }
    
    private fun uploadPart(state: State, bytes: ByteArray): Boolean {
        val partNum = state.nextPartNum
        val presign = api.presignPart(state.uploadId,  partNum, state.objectKey) ?: return false
        
        val reqBody = bytes.toRequestBody(null, 0, bytes.size) // do not set content type
        val rb = Request.Builder()
            .url(presign.url)
            .put(reqBody)
        presign.headers.forEach { (k,v) -> rb.addHeader(k, v) }
        
        http.client.newCall(rb.build()).execute().use { resp ->
            val bodyTxt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e("MultipartStreamer", "part $partNum failed ${resp.code}: $bodyTxt")
                return false
            } else {
                Log.d(
                    "MultipartStreamer",
                    "part $partNum containing ${bytes.size} bytes uploaded to ${presign.url}"
                )
            }
            val eTag = resp.header("ETag") ?: return false
            state.completed += CompletedPart(partNum, eTag)
            state.nextPartNum++
            return true
        }
    }
}