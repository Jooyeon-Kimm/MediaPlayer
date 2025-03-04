package com.example.musicplayer

import java.util.Locale

object MusicManager {
    private var MUSIC_LIST: MutableList<MusicFile> = mutableListOf()
    private var IS_PLAYING : Boolean = false
    private var LAST_THUMB_LOC : Int = 0
    private var MUSIC_INDEX_IN_LIST : Int = 0
    private var MUSIC_PATH : String = ""
    private var IS_MUTED : Boolean = false
    private var IS_SHUFFLED : Boolean = false
    private var IS_PHONE : Boolean = true
    private var REPEAT_MODE : RepeatMode = RepeatMode.NONE

    fun getMusicList(): MutableList<MusicFile> { return MUSIC_LIST}
    fun setMusicList(newList: MutableList<MusicFile>) { MUSIC_LIST = newList }

    fun shuffleMusicList() { MUSIC_LIST.shuffle() }
    fun sortMusicListByTitle() { MUSIC_LIST.sortBy { it.title.lowercase(Locale.getDefault()) } }
    fun sortMusicListByArtist() { MUSIC_LIST.sortBy { it.artist.lowercase()}}
    fun sortMusicListByAlbum() { MUSIC_LIST.sortBy { it.album }}

    fun setIsPlaying(isPlayed : Boolean){  IS_PLAYING = isPlayed }
    fun getIsPlaying() : Boolean { return IS_PLAYING }

    fun setLastThumbPos(thumbLoc : Int) { LAST_THUMB_LOC = thumbLoc }
    fun getLastThumbPos() : Int {return LAST_THUMB_LOC }

    // 음악 인덱스로, 음악파일의 LIST에서의 INDEX SET
    fun setMusicIndexInList(musicIdx: Int) {
        MUSIC_INDEX_IN_LIST = MUSIC_LIST.indexOfFirst { it.index == musicIdx }
    }
    fun getMusicIndexInList() : Int { return MUSIC_INDEX_IN_LIST }

    fun setIsMuted(isMuted : Boolean){ IS_MUTED = isMuted }
    fun getIsMuted(): Boolean { return IS_MUTED }

    fun setIsShuffled(isShuffled : Boolean) { IS_SHUFFLED = isShuffled }
    fun getIsShuffled(): Boolean { return IS_SHUFFLED }

    fun setIsPhone(isPhone : Boolean) { IS_PHONE = isPhone}
    fun getIsPhone() : Boolean { return IS_PHONE }


    fun isFirstMusic() : Boolean { return getMusicIndexInList() == 0 }
    fun isLastMusic() : Boolean { return getMusicList().size -1 == getMusicIndexInList() }

    fun setMusicPath(path:String) { MUSIC_PATH = path}
    fun getMusicPath() : String { return MUSIC_PATH}

    fun setRepeatMode(mode:RepeatMode) { REPEAT_MODE = mode}
    fun getRepeatMode() : RepeatMode { return REPEAT_MODE }
    fun getNextRepeatMode(): RepeatMode {
        return when (REPEAT_MODE) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }
}
