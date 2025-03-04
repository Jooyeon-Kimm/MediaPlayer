package com.example.musicplayer

import android.graphics.Bitmap


data class MusicFile(
    var index : Int,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArt : Bitmap?,
    val path: String,
    val duration: Long, // mili-sec 단위
)


val albumArtMap = mutableMapOf<Int, Bitmap?>()
