package com.example.photogallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.ContentUris
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.ContentValues
import android.os.Environment
import java.io.OutputStream
import android.database.Cursor

class PhotoGalleryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val photos = mutableListOf<Photo>()
    private lateinit var adapter: PhotoAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadPhotos()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)
        recyclerView = view.findViewById(R.id.photoRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        adapter = PhotoAdapter(photos)
        recyclerView.adapter = adapter

        // Download button setup
        val downloadButton: Button = view.findViewById(R.id.downloadButton)
        downloadButton.setOnClickListener {
            downloadPlaceholderImage()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermissionAndLoadPhotos()
    }

    override fun onResume() {
        super.onResume()
        // Refresh photos when returning to this fragment
        if (hasPermission()) {
            loadPhotos()
        }
    }

    private fun hasPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionAndLoadPhotos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                loadPhotos()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // Optionally show rationale
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadPhotos() {
        if (!isAdded) return // Prevent crash if fragment is not attached
        photos.clear()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val query = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                photos.add(Photo(contentUri, name, size))
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun downloadPlaceholderImage() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageUrl = "https://placehold.co/600x400"
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to download image", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val inputStream = connection.inputStream
                val fileName = "placeholder_${System.currentTimeMillis()}.jpg"

                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                    inputStream.copyTo(outputStream!!)
                    outputStream.close()
                    inputStream.close()
                    connection.disconnect()

                    // Update size in MediaStore after writing
                    val size = getImageSizeFromUri(imageUri)
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Images.Media.SIZE, size)
                    }
                    resolver.update(imageUri, updateValues, null, null)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Downloaded $fileName", Toast.LENGTH_SHORT).show()
                        loadPhotos()
                    }
                } else {
                    inputStream.close()
                    connection.disconnect()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Helper to get the file size from the Uri
    private fun getImageSizeFromUri(uri: android.net.Uri): Long {
        val projection = arrayOf(MediaStore.Images.Media.SIZE)
        requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                return cursor.getLong(sizeIndex)
            }
        }
        return 0L
    }
}
