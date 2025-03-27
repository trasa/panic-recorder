package com.meancat.panicrecorder

import android.util.Log
import io.minio.MinioClient
import io.minio.PutObjectArgs
import java.io.File
import java.io.FileInputStream

// TODO: rewrite using presigned url (and need a presigned url service too
val minioClient: MinioClient = MinioClient.builder()
    .endpoint("https://sfo2.digitaloceanspaces.com")
    .credentials("ACCESS_KEY", "SECRET")
    .build()

val s3uploadFunction: (File) -> Unit = { chunk ->
    try {
        val inputStream = FileInputStream(chunk)
        val objectName = "panic_chunks/${chunk.name}"
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket("your-bucket-name")
                .`object`(objectName)
                .stream(inputStream, chunk.length(), -1)
                .contentType("video/MP2T")
                .build()
        )
        inputStream.close()
        Log.d("S3Uploader", "Uploaded $objectName")
    } catch (e: Exception) {
        Log.e("S3Uploader", "Upload to s3 failed: ${chunk.name}", e)
    }
}