package com.meancat.panicrecorder

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var recordButton: Button
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mediaRecorder: MediaRecorder
    private var previewSize: Size = Size(1280, 768)
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var isRecording = false
    private lateinit var videoFile: File
    private var mediaStreamer: MultipartStreamer? = null
    private var streamerThread: Thread? = null
    private var previewSurface: Surface? = null
    private var recorderSurface: Surface? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            openCamera()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        recordButton = findViewById(R.id.record_button)

        if (!hasPermissions()) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            textureView.surfaceTextureListener = textureListener
        }

        recordButton.setOnClickListener {
            val config = PanicConfig.load(this)
            if (isRecording) stopRecording() else startRecording(config)
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun findCameraId(manager: CameraManager) : String {
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return ""
    }
    
    private fun getOptimalPreviewSize(characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(1920, 1080) // fallback

        val recorderSizes = map.getOutputSizes(MediaRecorder::class.java)
        val textureSizes = map.getOutputSizes(SurfaceTexture::class.java)

        // Find a size that works for both preview and recording
        val commonSizes = recorderSizes.filter { recorderSize ->
            textureSizes.any { textureSize ->
                textureSize.width == recorderSize.width && textureSize.height == recorderSize.height
            }
        }
        // Prefer 1080p, fall back to largest available
        return commonSizes.find { it.width == 1920 && it.height == 1080 }
            ?: commonSizes.maxByOrNull { it.width * it.height }
            ?: Size(1920, 1080) 
    }
    
    private fun logSupportedSizes(characteristics: CameraCharacteristics) {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map != null) {
            Log.d(TAG, "Supported MediaRecorder sizes:")
            map.getOutputSizes(MediaRecorder::class.java).forEach { size ->
                Log.d(TAG, "  ${size.width}x${size.height}")
            }
            Log.d(TAG, "Supported SurfaceTexture sizes:")
            map.getOutputSizes(SurfaceTexture::class.java).forEach { size ->
                Log.d(TAG, "  ${size.width}x${size.height}")
            }
        }
    } 
    private fun isConfigurationSupported(characteristics: CameraCharacteristics): Boolean {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return false
        
        val recorderSizes = map.getOutputSizes(MediaRecorder::class.java)
        val textureSizes = map.getOutputSizes(SurfaceTexture::class.java)

        // Check if our chosen size is supported by both
        return recorderSizes.contains(previewSize) && textureSizes.contains(previewSize)
    }
   
    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Camera permission not granted")
                return
            }
            val cameraId = findCameraId(manager)
            if (cameraId == "") {
                Log.e(TAG, "No cameraId found")
                return
            } else {
                Log.d(TAG, "camera is $cameraId")
            }
            val characteristics = manager.getCameraCharacteristics(cameraId)
            logSupportedSizes(characteristics)
            previewSize = getOptimalPreviewSize(characteristics)
            Log.d(TAG, "Using preview size ${previewSize.width}x${previewSize.height}")
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "CameraDevice.StateCallback error: $error")
                    camera.close()
                    if (::mediaRecorder.isInitialized) {
                        mediaRecorder.reset()
                        mediaRecorder.release()
                    }
                }
            }, null)
            Log.d(TAG, "camera opened")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error opening camera", e)
        }
    }

    private fun startRecording(config: PanicConfig) {
        Log.d(TAG, "start recording")
        try {
            setupMediaRecorder()
            if (config.enableUpload) {
                startUploading(config)
            }
            // no sleeping
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val surfaceTexture = textureView.surfaceTexture!!
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface = Surface(surfaceTexture)
            recorderSurface = mediaRecorder.surface

            val captureRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface!!)
                    addTarget(recorderSurface!!)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, 
                listOf(
                    OutputConfiguration(previewSurface!!),
                    OutputConfiguration(recorderSurface!!)
                ),
                ContextCompat.getMainExecutor(this),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "session configured")
                        cameraCaptureSession = session
                       
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        mediaRecorder.start()
                        isRecording = true
                        invalidateOptionsMenu()
                        recordButton.text = getString(R.string.stop_recording)
                        Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "session configuration failed!")
                        isRecording = false
                        stopUploading()
                        Toast.makeText(
                            this@MainActivity,
                            "Camera config failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            cameraDevice.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    private fun startUploading(config: PanicConfig) {
        val client = PanicHttpClient(config)
        lifecycleScope.launch {
            val token = client.fetchToken()
            if (token == null) {
                Toast.makeText(this@MainActivity, "Auth Failed", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val api = PanicApi(client, token)
            val s3PathPrefix = config.s3PathPrefix ?: "streams"
            mediaStreamer = MultipartStreamer(
                api,
                client,
                videoFile,
                "$s3PathPrefix/${videoFile.nameWithoutExtension}.ts"
            )
            streamerThread = thread(start = true, name = "MultipartStreamer") {
                val ok = mediaStreamer?.runBlocking() ?: false
                Log.d(TAG, "Streaming completed: $ok")
            }
        }
    }

    private fun supportsUnprocessedAudio(): Boolean {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val v = am.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return v?.equals("true", ignoreCase = true) == true
    }
    
    private fun setupMediaRecorder() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        videoFile = File(getExternalFilesDir(null), "panic_$timestamp.ts")

        val audioSource = if (supportsUnprocessedAudio())
            MediaRecorder.AudioSource.UNPROCESSED
        else
            MediaRecorder.AudioSource.CAMCORDER
        
        try {
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(audioSource)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
                setOutputFile(videoFile.absolutePath)
                Log.d(TAG, "output is ${videoFile.absolutePath}")
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                setAudioSamplingRate(48_000) // 48 kHz
                setAudioEncodingBitRate(192_000) // 192 kbps
                try { setAudioChannels(2) } catch(_: Exception) { setAudioChannels(1) } // stereo if supported
                
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(30)
                setVideoSize(previewSize.width, previewSize.height)
                prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder.prepare() failed", e)
            throw e
        } 
    }

    private fun stopRecording() {
        try {
            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.abortCaptures()
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            // finish recording
            mediaRecorder.reset()
            mediaRecorder.release()
            isRecording = false
            invalidateOptionsMenu()
            recordButton.text = getString(R.string.start_recording)
            previewSurface?.release()
            recorderSurface?.release()
            
            // stop uploading to finish reading the file
            stopUploading()
            val uri = copyToMovies()
            if (uri != null) {
                videoFile.delete()
                logMediaInfo(uri)
            }
            Toast.makeText(this, "Recording saved to Movies: ${videoFile.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "failed to stop recording", e)
        } finally {
            // back to sleep
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    private fun stopUploading() {
            mediaStreamer?.stop()
            streamerThread?.join()
    }
    
    private fun copyToMovies(): Uri? {
        val resolver = contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val values = ContentValues().apply { 
            put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp2t")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/PanicRecorder"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            videoFile.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
    
    private fun logMediaInfo(uri: Uri) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name =
                    c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val rel =
                    c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                Log.i(TAG, "Created media file $rel$name ($size bytes)")
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu) : Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // cant change settings if we're recording
        menu?.findItem(R.id.action_settings)?.isVisible = !isRecording
        return super.onPrepareOptionsMenu(menu)
    }

    companion object {
        private const val TAG = "PanicRecorder"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}