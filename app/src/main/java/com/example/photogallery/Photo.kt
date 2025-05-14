package com.example.photogallery

import android.net.Uri

data class Photo(
    val uri: Uri,
    val displayName: String,
    val size: Long
)