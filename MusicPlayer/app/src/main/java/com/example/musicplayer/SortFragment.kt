package com.example.musicplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.DialogFragment
import com.example.musicplayer.databinding.ActivityMainBinding

class SortFragment : DialogFragment() {

    lateinit var binding : ActivityMainBinding


    @SuppressLint("NotifyDataSetChanged")
    private fun sortByTitle() {
        MusicManager.sortMusicListByTitle()
        binding.rvList.adapter?.notifyDataSetChanged()

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortByArtist() {
        MusicManager.sortMusicListByArtist()
        binding.rvList.adapter?.notifyDataSetChanged()

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sortByAlbum() {
        MusicManager.sortMusicListByAlbum()
        binding.rvList.adapter?.notifyDataSetChanged()

    }
}
