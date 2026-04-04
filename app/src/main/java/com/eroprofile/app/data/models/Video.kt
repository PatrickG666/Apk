package com.eroprofile.app.data.models

data class Video(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val duration: String = "",
    val views: String = "",
    val rating: String = "",
    val author: String = "",
    val isHd: Boolean = false
)

data class Category(
    val name: String,
    val url: String,
    val thumbnailUrl: String = "",
    val count: String = ""
)
