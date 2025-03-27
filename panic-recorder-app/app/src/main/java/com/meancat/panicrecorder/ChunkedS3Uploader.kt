package com.meancat.panicrecorder

import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class ChunkedS3Uploader( 
    private val sourceFile: File,
    private val chunkDirectory: File,
    private val uploadFunction: (chunk: File ) -> Unit
) { 
    private var timer: Timer? = null
    private var lastPosition: Long = 0L
    private var chunkIndex: Int = 0
    
    fun start() {
        if (!chunkDirectory.exists()) {
            chunkDirectory.mkdirs()
        }
        // 5 second timer
        timer = timer(period = 5000L) {
            try {
                val currentSize = sourceFile.length()
                if (currentSize > lastPosition) {
                    val newChunkFile = File(chunkDirectory, getChunkName())
                    RandomAccessFile(sourceFile, "r").use { input ->
                        input.seek(lastPosition)
                        FileOutputStream(newChunkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (input.filePointer < currentSize) {
                                bytesRead = input.read(buffer)
                                if (bytesRead > 0) {
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                }
                            }
                        }
                    }
                    lastPosition = currentSize
                    uploadFunction(newChunkFile)
                    chunkIndex++
                }
            } catch (e: Exception) {
                Log.e("ChunkedS3Uploader", "Error while creating or uploading chunk", e)
            }
        }
    }
    
    fun stop() {
        timer?.cancel()
        timer = null
    }
    
    private fun getChunkName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "panic_${timestamp}_${String.format(Locale.US, "%03d", chunkIndex)}.ts"
    }
}