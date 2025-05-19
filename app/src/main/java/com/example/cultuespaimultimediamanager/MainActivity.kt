package com.example.cultuespaimultimediamanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var photoUri: Uri
    private lateinit var videoUri: Uri
    private var audioFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private val mediaList = mutableListOf<MediaFile>()
    private lateinit var adapter: MediaAdapter

    private var permissionCallback: (() -> Unit)? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            permissionCallback?.invoke()
        } else {
            Toast.makeText(this, "Permisos requeridos no concedidos", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            mediaList.add(MediaFile(photoUri.toString(), MediaType.PHOTO))
            adapter.notifyItemInserted(mediaList.size - 1)
        }
    }

    private val recordVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            mediaList.add(MediaFile(videoUri.toString(), MediaType.VIDEO))
            adapter.notifyItemInserted(mediaList.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val photoButton = findViewById<ImageView>(R.id.navPhotoIcon)
        val videoButton = findViewById<ImageView>(R.id.navVideoIcon)
        val audioButton = findViewById<ImageView>(R.id.navAudioIcon)

        photoButton.setOnClickListener { capturePhoto() }
        videoButton.setOnClickListener { captureVideo() }
        audioButton.setOnClickListener { recordAudio() }

        adapter = MediaAdapter(this, mediaList)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFiles)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun capturePhoto() {
        checkAndRequestPermissions {
            val photoFile = createMediaFile("IMG_", ".jpg")
            photoUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            }
            takePhotoLauncher.launch(intent)
        }
    }

    private fun captureVideo() {
        checkAndRequestPermissions {
            val videoFile = createMediaFile("VID_", ".mp4")
            videoUri = FileProvider.getUriForFile(this, "$packageName.provider", videoFile)
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            }
            recordVideoLauncher.launch(intent)
        }
    }

    private fun recordAudio() {
        checkAndRequestPermissions {
            audioFile = createMediaFile("AUDIO_", ".3gp")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            Toast.makeText(this, "Grabando audio por 5 segundos...", Toast.LENGTH_SHORT).show()

            Handler(mainLooper).postDelayed({
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                Toast.makeText(this, "Audio grabado", Toast.LENGTH_SHORT).show()

                val audioUri = Uri.fromFile(audioFile)
                mediaList.add(MediaFile(audioUri.toString(), MediaType.AUDIO))
                adapter.notifyItemInserted(mediaList.size - 1)
            }, 5000)
        }
    }

    private fun createMediaFile(prefix: String, extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(prefix + timeStamp, extension, storageDir)
    }

    private fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.CAMERA)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isEmpty()) {
            onGranted()
        } else {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
            permissionCallback = onGranted
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}