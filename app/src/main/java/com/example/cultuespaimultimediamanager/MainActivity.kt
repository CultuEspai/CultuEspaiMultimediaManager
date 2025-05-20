package com.example.cultuespaimultimediamanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.Button
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
    private val fullMediaList = mutableListOf<MediaFile>()
    private val mediaList = mutableListOf<MediaFile>()
    private lateinit var adapter: MediaAdapter

    private var permissionCallback: (() -> Unit)? = null

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            val clipData = it.data!!.clipData
            val singleUri = it.data!!.data

            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    addUriToMediaList(uri)
                }
            } else if (singleUri != null) {
                addUriToMediaList(singleUri)
            }

            adapter.notifyDataSetChanged()
        }
    }


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

    private fun filterMedia(type: MediaType?) {
        mediaList.clear()
        if (type == null) {
            mediaList.addAll(fullMediaList)
        } else {
            mediaList.addAll(fullMediaList.filter { it.type == type })
        }
        adapter.notifyDataSetChanged()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val media = MediaFile(photoUri.toString(), MediaType.PHOTO)
            fullMediaList.add(media)
            mediaList.add(media)
            adapter.notifyItemInserted(mediaList.size - 1)

            saveMediaListToStorage()
        }
    }

    private val recordVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val media = MediaFile(videoUri.toString(), MediaType.VIDEO)
            fullMediaList.add(media)
            mediaList.add(media)
            adapter.notifyItemInserted(mediaList.size - 1)

            saveMediaListToStorage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadMediaListFromStorage()

        val photoButton = findViewById<ImageView>(R.id.navPhotoIcon)
        val videoButton = findViewById<ImageView>(R.id.navVideoIcon)
        val audioButton = findViewById<ImageView>(R.id.navAudioIcon)
        val filesButton = findViewById<ImageView>(R.id.navFilesIcon)
        val deleteButton = findViewById<ImageView>(R.id.navDeleteIcon)

        val allButton = findViewById<Button>(R.id.allFilterButton)
        val photoFilterButton = findViewById<Button>(R.id.photoFilterButton)
        val videoFilterButton = findViewById<Button>(R.id.videoFilterButton)
        val audioFilterButton = findViewById<Button>(R.id.audioFilterButton)

        photoButton.setOnClickListener { capturePhoto() }
        videoButton.setOnClickListener { captureVideo() }
        audioButton.setOnClickListener { recordAudio() }
        filesButton.setOnClickListener { openDeviceGallery() }

        allButton.setOnClickListener { filterMedia(null) }
        photoFilterButton.setOnClickListener { filterMedia(MediaType.PHOTO) }
        videoFilterButton.setOnClickListener { filterMedia(MediaType.VIDEO) }
        audioFilterButton.setOnClickListener { filterMedia(MediaType.AUDIO) }
        deleteButton.setOnClickListener {
            if (adapter.selectionMode) {
                deleteSelectedMedia()
            } else {
                toggleSelectionMode()
            }
        }


        adapter = MediaAdapter(this, mediaList)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFiles)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

    }

    private fun openDeviceGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickMediaLauncher.launch(Intent.createChooser(intent, "Selecciona una imatge o vídeo"))
    }

    private fun addUriToMediaList(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
        val mediaType = when {
            mimeType?.startsWith("image") == true -> MediaType.PHOTO
            mimeType?.startsWith("video") == true -> MediaType.VIDEO
            else -> null
        }

        mediaType?.let {
            val media = MediaFile(uri.toString(), it)
            fullMediaList.add(media)
            mediaList.add(media)
        }
    }
    private fun loadMediaListFromStorage() {
        val sharedPrefs = getSharedPreferences("media_storage", MODE_PRIVATE)
        val jsonList = sharedPrefs.getString("media_list", "") ?: ""

        if (jsonList.isNotEmpty()) {
            val items = jsonList.split("||")
            for (item in items) {
                val parts = item.split("##")
                if (parts.size == 2) {
                    val uri = parts[0]
                    val type = when (parts[1]) {
                        "PHOTO" -> MediaType.PHOTO
                        "VIDEO" -> MediaType.VIDEO
                        "AUDIO" -> MediaType.AUDIO
                        else -> null
                    }
                    type?.let {
                        val media = MediaFile(uri, it)
                        fullMediaList.add(media)
                        mediaList.add(media)
                    }
                }
            }
        }
    }

    private fun saveMediaListToStorage() {
        val sharedPrefs = getSharedPreferences("media_storage", MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        val jsonList = fullMediaList.joinToString("||") { "${it.uri}##${it.type}" }
        editor.putString("media_list", jsonList)
        editor.apply()
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
                val media = MediaFile(audioUri.toString(), MediaType.AUDIO)
                fullMediaList.add(media)
                mediaList.add(media)
                adapter.notifyItemInserted(mediaList.size - 1)

                saveMediaListToStorage()

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

    private fun toggleSelectionMode() {
        adapter.selectionMode = !adapter.selectionMode
        if (!adapter.selectionMode) {
            fullMediaList.forEach { it.isSelected = false }
            mediaList.forEach { it.isSelected = false }
        }
        adapter.notifyDataSetChanged()
    }

    private fun deleteSelectedMedia() {
        val toRemove = mediaList.filter { it.isSelected }
        for (item in toRemove) {
            val file = File(Uri.parse(item.uri).path ?: "")
            if (file.exists()) file.delete()
            fullMediaList.remove(item)
        }
        mediaList.removeAll(toRemove)
        saveMediaListToStorage()
        toggleSelectionMode()
        adapter.notifyDataSetChanged()
    }


    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}