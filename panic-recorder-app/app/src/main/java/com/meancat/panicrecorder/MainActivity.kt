package com.meancat.panicrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var recordButton: Button
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mediaRecorder: MediaRecorder
    private var previewSize: Size = Size(1280, 768)
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var isRecording = false
    private lateinit var videoFile: File
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
            // size is now hard-coded, because this kept causing the hardware to crash
            
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "CameraDevice.StateCallback error: ${error}")
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
                Thread {
                    val api = PanicApi(config)
                    val token = api.fetchToken()
                    if (token != null) {
                        val presignedUrl = PanicApi(config).getPresignedUrl(videoFile.name, token)
                        if (presignedUrl != null) {
                            Log.d(TAG, "Presigned URL obtained: $presignedUrl")
                            // TODO uploader was here
                        } else {
                            Log.e(TAG, "Failed to get presigned url - upload disabled")
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch JWT from PanicAPI (is your secret correct?) - upload disabled")
                    }
                }.start()
            }
            
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
        }
    }

    private fun setupMediaRecorder() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        videoFile = File(getExternalFilesDir(null), "panic_$timestamp.ts")

        try {
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
                setOutputFile(videoFile.absolutePath)
                Log.d(TAG, "output is ${videoFile.absolutePath}")
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
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
            //uploader?.stop()
            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.abortCaptures()
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            mediaRecorder.reset()
            mediaRecorder.release()
            isRecording = false
            invalidateOptionsMenu()
            recordButton.text = getString(R.string.start_recording)
            previewSurface?.release()
            recorderSurface?.release()
            Toast.makeText(this, "Recording saved: ${videoFile.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "failed to stop recording", e)
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