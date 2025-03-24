package com.meancat.panicrecorder

import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object StreamingUploader {
    @Volatile
    private var keepRunning = true
    
    fun startStreaming(file: File, serverUrl: String) {
        Thread {
            try {
                val inputStream = RandomAccessFile(file, "r")
                var lastReadPosition = 0L
                while (keepRunning) {
                    val fileLength = file.length()
                    if (fileLength > lastReadPosition) {
                        val buffer = ByteArray((fileLength - lastReadPosition).toInt())
                        inputStream.seek(lastReadPosition)
                        inputStream.readFully(buffer)
                        lastReadPosition = fileLength

                        sendChunkToServer(buffer, serverUrl)
                    }
                    Thread.sleep(1000)
                }
                inputStream.close()
            } catch (e: Exception) {
                Log.e("StreamingUploader", "Error streaming", e)
            }
        }.start()
    }
    
    fun stopStreaming() {
        keepRunning = false    
    }
    
    private fun sendChunkToServer(data: ByteArray, serverUrl: String) {
        try {
            val connection = URL(serverUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            val out = BufferedOutputStream(connection.outputStream)
            out.write(data)
            out.flush()
            out.close()

            connection.inputStream.close()
            Log.d("StreamingUploader", "Chunk sent ${data.size} bytes")
        } catch (e: Exception) {
            Log.e("StreamingUploader", "Chunk of size ${data.size} failed", e)
        }
    }
}
