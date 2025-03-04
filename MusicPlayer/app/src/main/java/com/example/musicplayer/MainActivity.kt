package com.example.musicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat.END
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.MusicManager.getIsMuted
import com.example.musicplayer.MusicManager.getIsPlaying
import com.example.musicplayer.MusicManager.getIsShuffled
import com.example.musicplayer.MusicManager.getLastThumbPos
import com.example.musicplayer.MusicManager.getMusicIndexInList
import com.example.musicplayer.MusicManager.getMusicList
import com.example.musicplayer.MusicManager.getNextRepeatMode
import com.example.musicplayer.MusicManager.getRepeatMode
import com.example.musicplayer.MusicManager.setIsMuted
import com.example.musicplayer.MusicManager.setIsPhone
import com.example.musicplayer.MusicManager.setIsPlaying
import com.example.musicplayer.MusicManager.setIsShuffled
import com.example.musicplayer.MusicManager.setLastThumbPos
import com.example.musicplayer.MusicManager.setMusicIndexInList
import com.example.musicplayer.MusicManager.setMusicPath
import com.example.musicplayer.MusicManager.setRepeatMode
import com.example.musicplayer.MusicManager.shuffleMusicList
import com.example.musicplayer.MusicManager.sortMusicListByTitle
import com.example.musicplayer.adapter.MusicListAdapter
import com.example.musicplayer.adapter.OnItemClickListener
import com.example.musicplayer.databinding.ActivityMainBinding
import com.example.musicplayer.service.MediaPlayerService
import java.io.FileNotFoundException
import java.util.Locale


// 반복 모드
enum class RepeatMode {
    NONE, ALL, ONE
}

class MainActivity : AppCompatActivity(), OnItemClickListener {
    private lateinit var binding: ActivityMainBinding   // 바인딩 (kt과 xml 연결)
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaBrowser: MediaBrowserCompat

    // 볼륨 조절을 위한, audioManager
    private lateinit var audioManager: AudioManager

    // 미디어 재생 조작을 위한, mediaPlayer
    private var mediaPlayer: MediaPlayer? = null

    private var currentPlayingMusicFile: MusicFile? = null
    private lateinit var receiver: BroadcastReceiver

    // ● [액티비티 생명주기] onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("LifeCycleJ", "onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentPlayingMusicFile?.index = getMusicIndexInList()

        val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                // 2. MediaControllerCompat 설정
                val token = mediaBrowser.sessionToken
                mediaController = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                // 3. UI 버튼과 MediaController 연결
                setupMediaControls()
                mediaController.registerCallback(object : MediaControllerCompat.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                        updatePlayButton(state?.state)
                    }
                })
            }
        }

        // 1. MediaBrowserCompat 연결 (서비스와 통신)
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlayerService::class.java),
            connectionCallback,
            null
        )
        mediaBrowser.connect()
        initMainActivity()
    }

    // ● [액티비티 생명주기] 2) onStart
    override fun onStart() {
        Log.d("LifeCycleJ", "OnStart")
        super.onStart()
    }

    // ● [액티비티 생명주기] 3) onResume
    override fun onResume() {
        Log.d("LifeCycleJ", "OnResume")
        updateMusicUi(getMusicList()[getMusicIndexInList()])
        super.onResume()
    }

    // ● [액티비티 생명주기] 4) onPause
    override fun onPause() {
        Log.d("LifeCycleJ", "OnPause")
        super.onPause()
    }

    // ● [액티비티 생명주기] 5) onStop : 앱 종료될 때
    override fun onStop() {
        Log.d("LifeCycleJ", "OnStop")
        super.onStop()
    }

    // ● [액티비티 생명주기] 6) onDestroy : 액티비티 종료
    override fun onDestroy() {
        Log.d("LifeCycle", "OnDestroy")
        super.onDestroy()
        mediaPlayer?.release() // Media Player 리소스 해제
        mediaPlayer = null

        if (!getIsPlaying()) {
            val curThumbPos = mediaPlayer?.currentPosition ?: 0
            setLastThumbPos(curThumbPos)
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    private fun setupMediaControls() {
        // 4. 재생 버튼 클릭 시 MediaSession과 연동
        binding.ibPlay.setOnClickListener {
            val controller = MediaControllerCompat.getMediaController(this)
            if (controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
    }


    // ■ 메인액티비티 초기화
    private fun initMainActivity(){
        checkEveryPermissions() // 권한 허용 요청
        registerReceiver() // 브로드캐스트 리시버 등록 ( MediaPlayerService와 MainActivity )
        initPrevNextButtons() // 이전곡, 이후곡 버튼
        initPlayButtonState() // 재생 버튼
        initListButton()     // 목록 버튼
        initVolumeButtons()  // 볼륨 UP/DOWN 버튼
        initMuteButtonState()     // 뮤트 버튼 초기화 (토글)
        initShuffleButtonState()      // 셔플 버튼 초기화 (토글)
        initSeekBarConfig() // 시크바
        initReplayButtonState() // 반복재생 버튼
    }

    // ■ 외부 저장소 권한 요청 함수
    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_READ_EXTERNAL_STORAGE
        )
    }

    /***
     * 외부 저장소 접근 위한 [권한 요청] : 허용되면 mp3 파일을 시스템 저장소에서 긁어옴
     * 시스템 음량 조절을 위한 [권한 요청] : DoNotDisturb
     */
    // ■ 모든 권한 허용 확인 함수 ( 저장소 부터 확인 )
    private fun checkEveryPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED -> {
                checkDoNotDisturbPermission() // Audio Manager를 위한, 방해 금지 모드 권한 요청
                loadMusic()  // 음악 파일 로드
            }
            // 권한 요청 설명을 보여 줘야 하면,
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                showPermissionInfoForStorageDialog()
            }
            // 권한 요청 설명을 보여 줄 필요 없으면,
            else -> {
                requestStoragePermission()
            }

        }
    }

    // ■ 방해 금지 모드 권한 확인 함수
    private fun checkDoNotDisturbPermission() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionInfoForDNDDialog()
        }
    }

    // ■ Storage 허가 설정 Alert Dialog 함수 ( checkPermission에서 거절하면 한 번 더 물어봄 )
    private fun showPermissionInfoForStorageDialog() {
        AlertDialog.Builder(this).apply {
            setMessage("mp3 파일을 가져오기 위해서는, 외부 저장소 읽기 권한이 필요합니다.")
            setNegativeButton("취소", null)
            setPositiveButton("동의") { _, _ ->
                requestStoragePermission() // 권한 동의하면, 권한 요청
            }
            create()
            show()
        }
    }

    // ■ Do not Disturb 권한 Alert Dialog 함수
    private fun showPermissionInfoForDNDDialog() {
        AlertDialog.Builder(this).apply {
            setMessage("시스템 음량 조절을 하기 위해서는, 방해 금지 모드 변경 허용 권한이 필요합니다.")
            setNegativeButton("취소", null)
            setPositiveButton("동의") { _, _ ->
                requestDoNotDisturbPermission()// 권한 동의하면, 권한 요청
            }
            create()
            show()
        }
    }


    // ■ ActivityCompat.requestPermissions 를 통해 권한을 요청한 후
    // 사용자가 이 권한 요청에 응답하면 시스템에서 자동으로 호출되는, 콜백함수
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusic()  // 권한이 승인되었을 때 음악 로드
                checkDoNotDisturbPermission() // 방해 금지 권한 확인
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // 권한을 거부하고 다시 묻지 않음을 선택한 경우, 설정으로 유도
                    showGoToSettingsForStorageDialog()
                } else {
                    // 권한 거부됨
                    Toast.makeText(this, "권한 거부됨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ■ 설정으로 이동하여 직접 권한 허용하도록 하는 함수
    private fun showGoToSettingsForStorageDialog() {
        AlertDialog.Builder(this).apply {
            setMessage("이 앱은 외부 저장소 읽기 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            setNegativeButton("취소") { _, _ ->
            }
            create()
            show()
        }
    }

    companion object {
        const val REQUEST_READ_EXTERNAL_STORAGE = 100
    }


    // ■ Audio Manager를 위한, 방해금지 모드 권한 요청
    private fun requestDoNotDisturbPermission() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }


    /***
     * 목록 이미지 버튼 클릭하면, 할 일
     * 1) Drawer Layout 열고 닫기
     * 2) 버튼 터치 시, 리소스 변경 (색 변경)
     */
    // ■ 목록 이미지 버튼 클릭하면, 노래 목록 Drawer Layout 열고 닫기
    private fun initListButton() {
        binding.ibList.setOnClickListener {
            // 1) Drawer Layout 열고 닫기
            // 오른쪽에서 Drawer Layout 이 서랍이 열림 (오른쪽: END, 왼쪽: START)
            if (!binding.dlList.isOpen) {
                binding.dlList.openDrawer(END)
            } else {
                binding.dlList.closeDrawer(END)
            }

            // 2) 버튼 터치 시, 리소스 변경 (색 변경)
            setImageResourceWhenTouched(
                binding.ibList,
                R.drawable.btn_list_s,
                R.drawable.btn_list_n
            )
        }
    }

    // ■ 로컬 저장소의 음악 파일 가져오기
    private fun loadMusic() {
        val contentResolver = contentResolver

        // 1) 데이터 테이블 주소
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // 2) 가져올 데이터 칼럼 정의
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        )
        // 데이터베이스 쿼리
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // 3) 컨텐츠 리졸버에 해당 데이터 요청
        val query = contentResolver.query(
            uri,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )

        // 4) 커서로 전달받은 데이터를 꺼내서 저장
        query?.use { cursor ->
            var index: Int = 0
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA) // 로컬 PATH
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIndex)
                val artist = cursor.getString(artistIndex)
                val album = cursor.getString(albumIndex)
                val path = cursor.getString(dataIndex)
                val albumId = cursor.getLong(albumIdIndex)

                val albumUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
                )
                val albumArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val thumbnail = getContentResolver().loadThumbnail(
                            albumUri,
                            Size(300,300),
                            null
                        )
                        Bitmap.createScaledBitmap(thumbnail, 600, 600, true) // 강제 리사이징
                    } catch(e:FileNotFoundException){
                        val decodedBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_bg_album)
                        Bitmap.createScaledBitmap(decodedBitmap, 600, 600, true)
                    }
                } else {
                    getAlbumArt(path)
                }
                val duration = cursor.getLong(durationIndex) // milisec 단위임
                getMusicList().add(MusicFile(index, title, artist, album, albumArt, path, duration))
                index++
            }
        }
        Toast.makeText(applicationContext, "총 음악 개수 : ${getMusicList().size}", Toast.LENGTH_SHORT).show()
        initMainUi()
        val curPos = getMusicIndexInList()
        updateMusicUi(getMusicList()[curPos])
    }


    /***
     * 메인 UI 업데이트 할 일
     * 1) Adapter 설정 - ViewPager2, Recycler View
     * 2) 선택된 아이템 동기화
     * 3) 화면 관련 설정
     */
    // ■ 메인 UI 초기화 해주는 함수 : ViewPager2와 DrawerLayout
    @SuppressLint("SourceLockedOrientationActivity")
    private fun initMainUi() {
        // 1-1) ViewPager2 어댑터 설정
//        val viewPager2Adapter = MusicPagerAdapter(musicList)
//        viewPager2.adapter = viewPager2Adapter

        // 1-2)DrawerLayout의 RecyclerView에 어댑터 설정
        val recyclerView = binding.rvList
        recyclerView.adapter = MusicListAdapter(getMusicList(), this, applicationContext)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 3) 화면 길이 관련 설정
        // 3-1) Drawer Layout 의 가로길이는, 화면 길이의 3/5으로 (길이 동적 지정)
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        recyclerView.layoutParams.width = screenWidth * 3 / 5

        // 3-2) 화면 방향 동적 설정
        // 휴대폰이면, 세로 방향 (portrait)
        // 태블릿이면, 가로 방향 (landscape)
        setScreenOrientationByDeviceType()


        // 4) 현재 시스템 상태에 따른 mute버튼 UI 업데이트
        if(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)==0){
            binding.ibMute.setImageResource(R.drawable.icon_vol_mute)
        }else{
            binding.ibMute.setImageResource(R.drawable.icon_vol_unmute)
        }

        // 4) 마지막으로 재생한 음악으로 UI 업데이트
        updateMusicUi(getMusicList()[getMusicIndexInList()])


//        // 선택된 아이템이 ViewPager2와 동기화되도록 리스너 설정
//        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
//            override fun onPageSelected(position: Int) {
//                super.onPageSelected(position)
//                // 현재 재생 중인 음악과 목록을 동기화
//                binding.rvList
//                    .scrollToPosition(position)
//            }
//        })


    }

    /***
     * 아이템 클릭 시, 해야 할 동작
     * 1) mp3 파일 정보 저장 ( Shared Preferences )
     * 2) UI 업데이트
     * 3) Drawer Layout 닫기
     */
    // ■ 아이템 클릭 시, 동작 ★★★★★
    override fun onItemClick(musicFile: MusicFile) {
        // 1) mp3 파일 정보 저장 ( Shared Preferences )
        val listIdx = getMusicList().indexOfFirst { it.index == musicFile.index }
        setMusicIndexInList(listIdx)


        updateMusicUi(musicFile)      // 2) UI 업데이트
        binding.dlList.closeDrawer(END)     // 3) Drawer Layout 닫기

        if (getIsPlaying()) { mediaPlayerPlay(musicFile) }
        else { setIsPlaying(false) }

        // 4) Service에 음악 재생 요청 및 알림 업데이트 명령 전송
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            putExtra(getString(R.string.MUSIC_INDEX), listIdx)
        }
        startService(intent)
    }




    /***
     * 아이템 클릭시 해야 UI 업데이트
     * 1-0) 노래 번호 업데이트
     * 1-1) 노래 제목 목록에서 클릭한 노래 제목을, Main (전체) 화면에 업데이트
     * 1-2) 노래 duration TextView 업데이트 > sb 업데이트하면서 함께하도록 변경함
     * 1-3) 노래 앨범 이미지 업데이트
     * 2) 클릭한 아이템이 가장 위로 오도록 ( 노래 순서 유지하면서 )
     * 3) 클릭한 아이템 제목이 움직이고, 글자 색 오렌지색으로 변경 (RGB 228 108 10)
     */
    // ■ 아이템 클릭 시, UI 업데이트
    @SuppressLint("NotifyDataSetChanged")
    private fun updateMusicUi(musicFile: MusicFile) {
        // UI 업데이트는 항상 UI THREAD 에서...
        // 데이터 업데이트는 백그라운드 에서...
        runOnUiThread {
            // 1-0) 노래 번호 업데이트
            binding.tvSongNum.text = String.format(Locale.KOREA, "[%03d/%03d]  ", musicFile.index + 1, getMusicList().size)

            // 1-1) 노래 제목을 받아온 아이템의 노래 제목으로 업데이트
            binding.tvSongTitle.apply {
                text = musicFile.title
                isSelected = true // 마퀴 효과
            }

            // 1-3) 노래 앨범 이미지 업데이트
            binding.ivAlbum.setImageBitmap(musicFile.albumArt)

            if(getIsPlaying()) binding.ibPlay.setImageResource(R.drawable.btn_pau_n)
            else binding.ibPlay.setImageResource(R.drawable.btn_play_n)
        }
        // 3) 클릭한 아이템 제목이 움직이고, 글자 색 오렌지색으로 변경 (RGB 228 108 10)
        binding.rvList.adapter?.notifyDataSetChanged()

        // 4) 재생 상태 UI 업데이트
        updateIsPlayingUi()
    }

    /***
     * 볼륨 조절 버튼에 달아야 할 클릭 리스너
     * 1) 볼륨 다운
     * 2) 볼륨 업
     */
    // ■ 볼륨 조절 버튼에 클릭 리스너 달아주는 함수 [ AudioManager 이용 ]
    @SuppressLint("ClickableViewAccessibility")
    private fun initVolumeButtons() {
        // 1) 볼륨 다운 ( ADJUST_LOWER )
        binding.ibVolDown.setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            if(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)==0){
                binding.ibMute.setImageResource(R.drawable.icon_vol_mute)
            }
        }
        // 짧은 클릭 시, 색상 변경
        setBackGroundResourceWhenTouched(
            binding.ibVolDown,
            R.drawable.btn_bg_s,
            R.drawable.btn_bg_n
        )


        // 볼륨 업 ( ADJUST_RAISE )
        binding.ibVolUp.setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            if(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)>0){
                binding.ibMute.setImageResource(R.drawable.icon_vol_unmute)
            }
        }
        // 짧은 클릭 시, 색상 변경
        setBackGroundResourceWhenTouched(binding.ibVolUp, R.drawable.btn_bg_s, R.drawable.btn_bg_n)
    }

    // ■ 뮤트 상태 초기화 함수
    @SuppressLint("ClickableViewAccessibility")
    private fun initMuteButtonState() {
        // 복원 및 설정
        setIsMuted(false)

        // 버튼 클릭 리스너 설정 ( 뮤트 토글 )
        binding.ibMute.setOnClickListener {
            val currentlyMuted = getIsMuted()
            setMute(!currentlyMuted)
        }
        // 짧은 클릭 시, 색상 변경
        setBackGroundResourceWhenTouched(binding.ibMute, R.drawable.btn_bg_s, R.drawable.btn_bg_n)
    }

    // ■ 뮤트 버튼 상태 변경 함수
    private fun setMute(isMuted: Boolean) {
        val flag = if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, AudioManager.FLAG_SHOW_UI)

        // 아이콘 업데이트
        val iconRes = if (isMuted) R.drawable.icon_vol_mute else R.drawable.icon_vol_unmute
        binding.ibMute.setImageResource(iconRes)

        // 뮤트 상태 저장
        setIsMuted(isMuted)
    }

    // ■ 셔플 상태 초기화 함수
    private fun initShuffleButtonState() {
        // 1) 셔플 초기화 (기본은 정렬 = 셔플 false )
        setShuffle(false)

        // 2) 버튼 클릭 리스너 설정 ( 셔플 토글 )
        binding.ibShuffle.setOnClickListener {
            val currentlyShuffled = getIsShuffled()
            setIsShuffled(!currentlyShuffled)
            setShuffle(!currentlyShuffled)
        }
    }

    /***
     * 셔플 버튼 상태 변경 함수, 할 일
     * 1) 셔플 모드이면, 셔플 / 정렬 모드이면, 정렬
     * 2) 짧은 클릭 시, 리소스 변경 (아이콘 색상 변경처럼 보임)
     * 3) 모드별 아이콘 업데이트
     * 4) 셔플 상태 저장 ( Shared Preferences )
     */
    //  ■ 셔플 버튼 상태 변경 함수
    @SuppressLint("NotifyDataSetChanged")
    private fun setShuffle(isShuffled: Boolean) {
        // 1) 셔플 모드이면, 셔플 / 정렬 모드이면, 정렬
        // 대문자, 소문자 따로 정렬하길래, 소문자 기준으로 정렬했음
        if (isShuffled) { shuffleMusicList() }
        else { sortMusicListByTitle() }
        binding.rvList.adapter?.notifyDataSetChanged()
        updateMusicUi(getMusicList()[0])
        if(getIsPlaying()) mediaPlayerPlay(getMusicList()[0])
        setMusicIndexInList(getMusicList()[0].index)
        binding.rvList.adapter?.notifyDataSetChanged()

        // 2) 짧은 클릭 시, 리소스 변경 (아이콘 색상 변경처럼 보임)
        if (isShuffled) setImageResourceWhenTouched(binding.ibShuffle, R.drawable.btn_random_s, R.drawable.btn_random_n)
        else setImageResourceWhenTouched(binding.ibShuffle, R.drawable.btn_order_s, R.drawable.btn_order_n)

        // 3) 모드별 아이콘 업데이트
        val iconRes = if (isShuffled) R.drawable.btn_random_n else R.drawable.btn_order_n
        binding.ibShuffle.setImageResource(iconRes)
    }


    /************** 짧은 터치, 색깔 / 배경 리소스 처리 ************/
    // ■ 터치 시, 배경 리소스 변경해주는 함수 (볼륨/뮤트 버튼)
    private fun setBackGroundResourceWhenTouched(
        view: View,
        pressedBackground: Int,
        normalBackground: Int
    ) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                // ACTION_DOWN : 포인터가 닿음
                MotionEvent.ACTION_DOWN -> {
                    runOnUiThread { v.setBackgroundResource(pressedBackground) }
                    true
                }

                // ACTION_UP : 포인터가 떼짐, ACTION_CANCEL : 제스쳐가 취소됨
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    runOnUiThread { v.setBackgroundResource(normalBackground) }
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()  // 클릭 이벤트 발생
                    true
                }

                else -> false
            }
        }
    }

    // ■ 터치 시, 리소스 자체를 변경해주는 함수 (일시정지, 재생, 이전곡, 다음곡, 반복재생, 셔플)버튼
    @SuppressLint("ClickableViewAccessibility")
    private fun setImageResourceWhenTouched(imageView: ImageView, pressedRes: Int, normalRes: Int) {
        imageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    runOnUiThread { imageView.setImageResource(pressedRes) }
                    true  // 이벤트 처리 완료
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    runOnUiThread { imageView.setImageResource(normalRes) }
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    true  // 이벤트 처리 완료
                }

                else -> false  // 다른 이벤트는 처리하지 않음
            }
        }
    }



    // ■ 이전 곡 / 다음 곡 버튼, 클릭 리스너 붙이기
    private fun initPrevNextButtons() {
        // 이전 곡 버튼
        binding.ibPrev.setOnClickListener {
            val currentIndex = getMusicIndexInList()
            val prevIndex = if (currentIndex == 0) getMusicList().size - 1 else currentIndex - 1
            if(getIsPlaying()) mediaPlayerPrev()
            else {
                updateMusicUi(getMusicList()[prevIndex])
                setMusicIndexInList(getMusicList()[prevIndex].index)
            }
        }
        setImageResourceWhenTouched(binding.ibPrev, R.drawable.btn_prev_s, R.drawable.btn_prev_n)

        // 다음 곡 버튼
        binding.ibNext.setOnClickListener {
            val currentIndex = getMusicIndexInList()
            val nextIndex = if (currentIndex == getMusicList().size - 1) 0 else currentIndex + 1
            if(getIsPlaying()) mediaPlayerNext()
            else {
                updateMusicUi(getMusicList()[nextIndex])
                setMusicIndexInList(getMusicList()[nextIndex].index)
            }
        }
        setImageResourceWhenTouched(binding.ibNext, R.drawable.btn_next_s, R.drawable.btn_next_n)
    }

    // ■ 재생 버튼 초기화 하는 함수
    private fun initPlayButtonState() {
        // 1) 플레이상태 초기화 (앱 키자마자는 false)
        setPlay(false)


        // 2) 버튼 클릭 리스너 설정 (플레이버튼 토글)
        binding.ibPlay.setOnClickListener {
            setPlay(!getIsPlaying())
        }
    }

    /***
     * 재생 버튼 상태 변경 함수, 할 일
     * 1) 재생 모드이면, 재생 / 일시정지 모드이면, 일시정지
     * 2) 짧은 클릭 시, 리소스 변경 (아이콘 색상 변경처럼 보임)
     * 3) 모드별 아이콘 업데이트
     * 4) 재생 상태 저장 ( Shared Preferences )
     */
    // ■ 재생 버튼 상태 변경 함수
    @SuppressLint("NotifyDataSetChanged")
    private fun setPlay(isPlayed: Boolean) {
        val curIdx = getMusicIndexInList()
        val musicFile = getMusicList()[curIdx]

        // 1) 재생 모드이면, 재생 / 일시정지 모드이면, 일시정지 (마지막 thumb 위치 기억)
        if (isPlayed) {
            mediaPlayerPlay(musicFile)
        } else {
            mediaPlayerPause()
        }

        // 2) 짧은 클릭 시, 리소스 변경 (아이콘 색상 변경처럼 보임)
        if (isPlayed) setImageResourceWhenTouched(binding.ibPlay, R.drawable.btn_pau_s, R.drawable.btn_pau_n)
        else setImageResourceWhenTouched(binding.ibPlay, R.drawable.btn_play_s, R.drawable.btn_play_n)

        // 3) 모드별 아이콘 업데이트
        val iconRes = if (isPlayed) R.drawable.btn_pau_n else R.drawable.btn_play_n
        binding.ibPlay.setImageResource(iconRes)

        // [어댑터]에게 모든 뷰 홀더를 바인딩하라고 알려주게 됨
        // onBindViewHolder() 가 실행되면서
        // 현재 재생되고 있는, 첫 번째 아이템만 UI 최신상태로 갱신
        binding.rvList.adapter?.notifyDataSetChanged()

        // Service에게 Play 상태 알려주기
        // 4) 브로드캐스트
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            action = if (!getIsPlaying()) MediaPlayerService.ACTION_PLAY else MediaPlayerService.ACTION_PAUSE
            putExtra(getString(R.string.IS_PLAYED), !getIsPlaying())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun mediaPlayerPlay(musicFile: MusicFile?) {
        val playIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = MediaPlayerService.ACTION_PLAY
            setMusicPath(musicFile!!.path)
        }
        startService(playIntent)
    }

    private fun mediaPlayerPause() {
        val pauseIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = MediaPlayerService.ACTION_PAUSE
        }
        startService(pauseIntent)
    }

    private fun mediaPlayerNext() {
        val nextIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = MediaPlayerService.ACTION_NEXT
        }
        startService(nextIntent)
    }

    private fun mediaPlayerPrev() {
        val prevIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = MediaPlayerService.ACTION_PREV
        }
        startService(prevIntent)
    }

    // ■ [시크바] 클릭 리스너 설정 함수
    private fun initSeekBarConfig() {
        binding.sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            // 최초로 탭하였을 시작 당시 발생하는 함수
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 시크바 길이 고정돼있으므로, progress는 scaling 되어야 함
                val curIdx = getMusicIndexInList()
                val totalTime = getMusicList()[curIdx].duration / 1000
                seekBar?.max = totalTime.toInt()
                val currentTime = (progress.toDouble() / seekBar!!.max * totalTime).toInt()
                updateSeekbarTime(currentTime)
            }

            // 드래그 하는 중에 발생하는 함수
            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            // 드래그를 멈추면 발생하는 함수 : 사용자가 SeekBar에서 손을 떼면,
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val seekPosition = it.progress * 1000  // 밀리초 단위로 위치 계산
                    val intent = Intent(this@MainActivity, MediaPlayerService::class.java).apply {
                        action = MediaPlayerService.ACTION_SEEK_TO
                        putExtra(getString(R.string.LAST_THUMB_LOC), seekPosition)
                        setLastThumbPos(seekPosition)
                    }
                    startService(intent)
                }
            }
        })
        // thumb 터치 가능 영역 늘리기


        // [ 할 일 ] 시크바 터치하면, 세로 폭 넓어지도록
    }

    // ■ [시크바] thumb 위치에 따른, 현재 play 시간 업데이트 함수
    private fun updateSeekbarTime(progress: Int) {
        val hours = progress / 3600         // 총 초에서 시간 계산
        val minutes = (progress % 3600) / 60 // 남은 초에서 분 계산
        val seconds = progress % 60          // 남은 초 계산

        val curTimeTV = binding.tvCurTime
        curTimeTV.text = String.format(Locale.KOREA, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    // ■ [시크바] 이전곡, 다음곡 버튼 클릭 시, 시크바 0으로 초기화하고
    // 노래 처음부터 PLAY
    private fun resetSeekBarUi() {
        val curIdx = getMusicIndexInList()

        runOnUiThread {
            // SeekBar 진행 상태를 0으로 초기화
            binding.sb.progress = 0

            // 현재 재생 시간을 0으로 설정
            binding.tvCurTime.text = "00:00:00"

            // 로드된 새 음악에 따라 SeekBar의 최대값 설정 (새 음악의 길이에 맞게)
            val newMusicDuration = (getMusicList()[curIdx].duration / 1000).toInt()  // 밀리초를 초로 변환
            binding.sb.max = newMusicDuration

            // 마지막 thumb 위치 업데이트 : 0으로
            setLastThumbPos(0)
        }

    }



    // ■ 기기 종류에 따라서, Orientation 방향 결정해주기 (Landscape-가로모드 또는 Portrait-세로모)
    @SuppressLint("SourceLockedOrientationActivity")
    private fun setScreenOrientationByDeviceType() {
        val screenSize =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val paramForReplayIB =
            binding.guideLineForReplayIB.layoutParams as ConstraintLayout.LayoutParams
        val paramForShuffleIB =
            binding.guideLineForShuffleIB.layoutParams as ConstraintLayout.LayoutParams
        when (screenSize) {
            Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.SCREENLAYOUT_SIZE_XLARGE -> {
                // 태블릿으로 간주 (대형, 초대형)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                paramForReplayIB.guidePercent = 0.1f   // 좌
                paramForShuffleIB.guidePercent = 0.9f  // 우
                setIsPhone(false)
            }

            else -> {
                // 휴대폰으로 간주 (소형, 정상)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                paramForReplayIB.guidePercent = 0.0f    // 좌
                paramForShuffleIB.guidePercent = 1.0f   // 우
                setIsPhone(true)
            }
        }
    }

    // ■ 반복 재생 버튼 초기화 하는 함수
    private fun initReplayButtonState() {
        // 1) 초기화
        updateRepeatModeUi(getRepeatMode())

        // 2) 클릭 리스너 부착
        binding.ibReplay.setOnClickListener {
            val nextMode = getNextRepeatMode()
            setRepeatMode(nextMode)
            updateRepeatModeUi(nextMode)
        }
    }

    // ■ 반복 재생 버튼 상태 변경 함수
    private fun updateRepeatModeUi(repeatMode: RepeatMode) {
        val iconRes = when (repeatMode) {
            RepeatMode.NONE -> R.drawable.btn_repeat_x_n
            RepeatMode.ALL -> R.drawable.btn_all_n
            RepeatMode.ONE -> R.drawable.btn_repeat_n
        }
        binding.ibReplay.setImageResource(iconRes)
        when (repeatMode) {
            RepeatMode.NONE -> setImageResourceWhenTouched(binding.ibReplay, R.drawable.btn_repeat_x_s, R.drawable.btn_repeat_x_n)
            RepeatMode.ALL -> setImageResourceWhenTouched(binding.ibReplay, R.drawable.btn_all_s, R.drawable.btn_all_n)
            RepeatMode.ONE -> setImageResourceWhenTouched(binding.ibReplay, R.drawable.btn_repeat_s, R.drawable.btn_repeat_n)
        }
    }

    // ■ 앨범 이미지 추출 (파일 경로로 이미지 추출, ffmpeg - 다익스트라)
    // BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_bg_album)
    private fun getAlbumArt(filePath: String): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val decodedBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                return Bitmap.createScaledBitmap(decodedBitmap, 600, 600, true)
            }
            // 앨범 이미지가 없으면, 기본 앨범 이미지 넣어주기
            else {
                val decodedBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_bg_album)
                return Bitmap.createScaledBitmap(decodedBitmap, 600, 600, true)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            val decodedBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_bg_album)
            return Bitmap.createScaledBitmap(decodedBitmap, 600, 600, true)
        } finally {
            retriever.release()
        }
    }



    // ■ 1초마다 시크바 Thumbs 이동시키는 함수 (브로드캐스트 리시버)
    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    MediaPlayerService.ACTION_MEDIA_UPDATE -> {
                        val duration = intent.getIntExtra(getString(R.string.MUSIC_DURATION), 0)
                        val spPosition = getLastThumbPos()  // 마지막 thumb 위치 불러오기 (Shared Preferences에서?)
                        val currentPosition = intent.getIntExtra(getString(R.string.LAST_THUMB_LOC), spPosition)
                        updateSeekBar(duration, currentPosition)
                    }
                    MediaPlayerService.ACTION_PLAYBACK_COMPLETE -> {
                        updateUIForPlaybackCompletion()
                    }
                    MediaPlayerService.ACTION_INDEX_UPDATE -> {
                        updateMusicUi(getMusicList()[getMusicIndexInList()])
                    }
                    MediaPlayerService.ACTION_IS_PLAYING_UPDATE -> {
                        updateIsPlayingUi()
                    }

                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(MediaPlayerService.ACTION_MEDIA_UPDATE)
            addAction(MediaPlayerService.ACTION_PLAYBACK_COMPLETE)
            addAction(MediaPlayerService.ACTION_INDEX_UPDATE)
            addAction(MediaPlayerService.ACTION_PLAY)
            addAction(MediaPlayerService.ACTION_PAUSE)
            addAction(MediaPlayerService.ACTION_IS_PLAYING_UPDATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    // ■ 시크바 업데이트 함수
    private fun updateSeekBar(duration: Int, currentPosition: Int) {
        runOnUiThread {
            binding.sb.max = duration / 1000 // 전체 길이 설정
            binding.sb.progress = currentPosition / 1000 // 현재 위치 설정

            // 현재 시간과 전체 시간 표시 업데이트
            binding.tvCurTime.text = formatTime(currentPosition)
            binding.tvTotalTime.text = formatTime(duration)
        }
    }

    // ■ 재생 완료 시, 반복모드에 따른 처리 ( UI 업데이트 )
    private fun updateUIForPlaybackCompletion() {
        val repeatMode = RepeatMode.entries[getRepeatMode().ordinal]
        when (repeatMode) {
            RepeatMode.NONE -> {
                updateMusicUi(getMusicList()[getMusicIndexInList()])
                mediaPlayer?.seekTo(0)
            }
            RepeatMode.ONE -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
            RepeatMode.ALL -> {
                updateMusicUi(getMusicList()[getMusicIndexInList()])
            }
        }
    }

    // ■ 재생 상태 UI에 업데이트
    private fun updateIsPlayingUi() {
        runOnUiThread {
            if(getIsPlaying()) binding.ibPlay.setImageResource(R.drawable.btn_pau_n)
            else binding.ibPlay.setImageResource(R.drawable.btn_play_n)
        }
    }

    private fun updatePlayButton(state: Int?) {
        when (state) {
            PlaybackStateCompat.STATE_PLAYING -> binding.ibPlay.setImageResource(R.drawable.btn_pau_n)
            PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> binding.ibPlay.setImageResource(R.drawable.btn_play_n)
        }
    }

}

// ■ 시간 포메팅 함수 (00:00:00)
private fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60)) % 24
    return String.format(Locale.KOREA, "%02d:%02d:%02d", hours, minutes, seconds)
}

