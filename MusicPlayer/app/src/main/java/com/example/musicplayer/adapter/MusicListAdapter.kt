package com.example.musicplayer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.MusicFile
import com.example.musicplayer.MusicManager.getMusicIndexInList
import com.example.musicplayer.MusicManager.getMusicList
import com.example.musicplayer.R
import kotlin.text.*


// 노래 리스트 받아서
// Recycler View 에 노래 제목만 표시하기
class MusicListAdapter(private val musicList: List<MusicFile>, private val listener: OnItemClickListener, private val context : Context) : RecyclerView.Adapter<MusicListAdapter.MusicViewHolder>() {

    // 뷰홀더 ( 1. 변경할 뷰 가져오기  2. 이벤트 리스너 (예: 클릭 리스너) 를 bind 함수에 구현 )
    class MusicViewHolder(itemView: View, context:Context) : RecyclerView.ViewHolder(itemView) {
        private val itemTitle: TextView = itemView.findViewById(R.id.tv_item_title)
        private lateinit var sharedPreferences: SharedPreferences // 음악 재생 상태 저장

        @SuppressLint("DefaultLocale")
        fun bind(music: MusicFile, clickListener: OnItemClickListener) {
            val formattedIndex = String.format("%03d - ", music.index + 1)

            // 노래 제목 목록 : 몇 번째 곡인지 표시하기
            itemTitle.text = buildString {
                append(formattedIndex) // 몇 번째 곡인지
                append(music.title)     // 곡 제목
            }
            // 목록 아이템 : 클릭 리스너 달기
            itemView.setOnClickListener {
                clickListener.onItemClick(music)
            }
            // 목록 아이템 : 터치 리스너 달기
            setupTouchListener(itemView, itemTitle, R.color.pressedText, R.color.semi_transparent_black, R.color.white)
        }

        // ■ 현재 재생 중인 음악 아이템 업데이트
        fun updateCurrentMusicItemUi(context: Context) {
            sharedPreferences = context.getSharedPreferences(context.getString(R.string.SHARED_PREFERENCE_SPACE), Context.MODE_PRIVATE)
            val isPlayed = sharedPreferences.getBoolean(context.getString(R.string.IS_PLAYED), false)

            // 1) 주황색 글씨 ( RGB 228 108 10 )
            itemTitle.setTextColor(ContextCompat.getColor(context, R.color.pressedText))

            // 2-1) 재생 중이면, 애니메이션 효과 (마퀴)
            // 마퀴 효과는 텍스트 길이 > 뷰 길이 일때만 적용됨
            if(isPlayed) {
                itemTitle.apply {
                    ellipsize = TextUtils.TruncateAt.MARQUEE // 마퀴
                    marqueeRepeatLimit = -1 // 무한 반복
                    isSingleLine = true
                    canScrollHorizontally(1)
                    isSelected = true // 마퀴 활성화하기 위해 필요
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }
        }

        // ■ 현재 선택된 곡이 아니면, 주황색 글씨 해제, 마퀴 해제
        fun updatePastMusicItemUi(context: Context) {
            // 1) 흰색 글씨로 되돌림
            itemTitle.setTextColor(ContextCompat.getColor(context, R.color.white))

            // 2) 마퀴 애니메이션 효과 해제
            // 2-2) 일시정지이면, 마퀴 비활성화
            itemTitle.apply {
                ellipsize = TextUtils.TruncateAt.END
                marqueeRepeatLimit = 0
                isSingleLine = false
                isFocusable = false
                isFocusableInTouchMode = false
                isSelected = false
                canScrollHorizontally(0)
            }

        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.music_item_list, parent, false)
        return MusicViewHolder(view, context)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]
        holder.bind(music, listener)

        // 현재 재생 중인 음악의 리스트 인덱스 가져오기
        val curIdx = getMusicIndexInList()
        // 현재 위치와 저장된 인덱스 비교
        val isCurr = (getMusicList()[curIdx].index == music.index)

        if (isCurr) {
            holder.updateCurrentMusicItemUi(context) // 현재 음악이면 UI 업데이트
        } else {
            holder.updatePastMusicItemUi(context) // 현재 음악이 아니면 다른 UI 업데이트
        }
    }

    override fun getItemCount(): Int = musicList.size


}
fun setupTouchListener(view: View, itemTitle: TextView, pressedColorRes: Int, normalColorRes: Int, textColorRes: Int) {
    view.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                itemTitle.setBackgroundColor(ContextCompat.getColor(itemTitle.context, pressedColorRes))
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                itemTitle.setBackgroundColor(ContextCompat.getColor(itemTitle.context, normalColorRes))
                itemTitle.setTextColor(ContextCompat.getColor(itemTitle.context, textColorRes))
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                true
            }
            else -> false
        }
    }
}

// ■ 아이템 클릭 리스너 인터페이스
interface OnItemClickListener {
    fun onItemClick(musicFile: MusicFile)
}
// onClick 함수는, 외부에서 오버라이드 해줘야하는 함수이다.