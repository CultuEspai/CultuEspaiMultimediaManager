package com.example.cultuespaimultimediamanager

data class MediaFile(
    val uri: String,
    val type: MediaType,
    var isSelected: Boolean = false
)