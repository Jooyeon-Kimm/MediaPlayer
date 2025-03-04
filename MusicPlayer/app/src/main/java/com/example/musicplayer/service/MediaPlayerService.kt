package com.example.musicplayer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.musicplayer.MainActivity
import com.example.musicplayer.MusicManager
import com.example.musicplayer.MusicManager.getIsPlaying
import com.example.musicplayer.MusicManager.getMusicIndexInList
import com.example.musicplayer.MusicManager.getMusicList
import com.example.musicplayer.MusicManager.getMusicPath
import com.example.musicplayer.MusicManager.getRepeatMode
import com.example.musicplayer.MusicManager.setIsPlaying
import com.example.musicplayer.MusicManager.setMusicIndexInList
import com.example.musicplayer.MusicManager.setMusicPath
import com.example.musicplayer.R
import com.example.musicplayer.RepeatMode
import java.io.IOException


class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var currentMusicFilePath: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (getIsPlaying()) {
                    val duration = it.duration
                    val currentPosition = it.currentPosition
                    broadcastSbThumbUpdate(duration, currentPosition)
                }

                handler.postDelayed(this, 1000) // 1초마다 반복
            }
        }
    }

    // MediaSession : 잠금화면과 알림에서 음악 재생 제어
    private lateinit var mediaSession: MediaSessionCompat


    companion object {
        const val ACTION_PLAY = "com.example.musicplayer.action.PLAY"
        const val ACTION_PAUSE = "com.example.musicplayer.action.PAUSE"
        const val ACTION_STOP = "com.example.musicplayer.action.STOP"
        const val ACTION_PREV = "com.example.musicplayer.action.ACTION_PREV"
        const val ACTION_NEXT = "com.example.musicplayer.action.ACTION_NEXT"
        const val ACTION_SEEK_TO = "com.example.musicplayer.action.SEEK_TO"
        const val ACTION_MEDIA_UPDATE = "com.example.musicplayer.action.ACTION_MEDIA_UPDATE"
        const val ACTION_IS_PLAYING_UPDATE = "com.example.musicplayer.action.ACTION_IS_PLAYING_UPDATE"
        const val ACTION_INDEX_UPDATE = "com.example.musicplayer.action.ACTION_INDEX_UPDATE"
        const val ACTION_PLAYBACK_COMPLETE = "com.example.musicplayer.action.PLAYBACK_COMPLETE"
    }

    // ■ [Service 생명주기] 1) OnCreate
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initMediaSession()
        createChannel()
        startNotification()
        updateNotification()

        val notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val index = intent.getIntExtra(getString(R.string.MUSIC_INDEX), -1)
                if (intent.action == Companion.ACTION_IS_PLAYING_UPDATE) {
                    val isPlayed = intent.getBooleanExtra(getString(R.string.IS_PLAYED), false)
                    setIsPlaying(isPlayed)
                    updateNotification()
                }
            }
        }
        val filter = IntentFilter("com.example.musicplayer.UPDATE_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

    }

    // ■ [Service 생명주기] 2) onStartCommand
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playMusic(intent)
            ACTION_PAUSE -> pauseMusic()
            ACTION_STOP -> stopMusic()
            ACTION_NEXT -> nextMusic()
            ACTION_PREV -> prevMusic()
            ACTION_SEEK_TO -> intent.getIntExtra(getString(R.string.LAST_THUMB_LOC), 0).also { seekToPosition(it) }
        }
        return START_NOT_STICKY
    }

    // ■ [Service 생명주기] 2) onStartCommand
    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacks(updateRunnable) // 서비스 종료 시 업데이트 중단
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    // ■ 미디어 세션 초기화
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MediaPlayerService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            isActive = true
        }
    }

    // ■ 음악 재생 ( Play )
    private fun playMusic(intent: Intent) {
        val musicFilePath = getMusicPath()

        // 같은 음악에서 일시정지 > 재생 이면, thumb 위치에서 PLAY
        if (musicFilePath == currentMusicFilePath) {
            mediaPlayer?.apply {
                seekTo(currentPosition)
                if (requestAudioFocus()) {
                    // 오디오 포커스가 승인되면 음악 재생
                    start()
                }
            }
        }
        // 다른 노래 들어오면, 리셋하고 새로운 노래 PLAY
        else {
            mediaPlayer?.reset()
            currentMusicFilePath = musicFilePath

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(musicFilePath)
                    prepare()
                    seekTo(currentPosition)
                    if (requestAudioFocus()) {
                        // 오디오 포커스가 승인되면 음악 재생
                        start()
                    }
                    broadcastSbThumbUpdate(duration, currentPosition)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

//            // set
            setMusicIndexInList(getMusicList().find { it.path == currentMusicFilePath }!!.index)
        }

        // 핸들러
        setIsPlaying(true)
        broadcastIsPlayedUpdate(true)
        handler.post(updateRunnable)
        updateNotification()

        // 모두 재생되면...
        mediaPlayer!!.setOnCompletionListener {
            handlePlaybackCompletion()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_PLAYBACK_COMPLETE))
        }
    }

    // ■ 노래 재생 완료 시, 반복모드에 따라, 동작 처리하는 함수
    private fun handlePlaybackCompletion() {
        val repeatMode = RepeatMode.entries[getRepeatMode().ordinal]
        when (repeatMode) {
            // 반복하지 않으면,
            RepeatMode.NONE -> {
                if (getMusicIndexInList() == getMusicList().size - 1) {
                    mediaPlayer?.seekTo(0)
                    setIsPlaying(false)
                    broadcastIsPlayedUpdate(false)
                    pauseMusic()
                } else {
                    nextMusic()
                }
            }
            // 한 곡 반복이면,
            RepeatMode.ONE -> {
                mediaPlayer?.seekTo(0) // thumb 위치를 0밀리세컨즈로 변경 후
                mediaPlayer?.start() // 다시 시작
            }

            // 노래 목록 전체 반복이면,
            RepeatMode.ALL -> {
                    nextMusic() //  곡 재생
                }
        }
    }

    // ■ 시크바 thumb 위치를 실시간으로 공유 (브로드캐스트)
    private fun broadcastSbThumbUpdate(duration: Int, currentPosition: Int) {
        Intent(ACTION_MEDIA_UPDATE).also { intent ->
            intent.putExtra(getString(R.string.MUSIC_DURATION), duration)
            intent.putExtra(getString(R.string.LAST_THUMB_LOC), currentPosition)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    // ■ 재생상태를 실시간으로 공유 (브로드캐스트)
    private fun broadcastIsPlayedUpdate(isPlaying: Boolean) {
        val intent = Intent(ACTION_IS_PLAYING_UPDATE)
        intent.putExtra(getString(R.string.IS_PLAYED), isPlaying)
        setIsPlaying(isPlaying)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // ■ 음악 INDEX 를 실시간 공유 (브로드캐스트)
    private fun broadCastMusicIndexUpdate(musicIndex: Int) {
        val intent = Intent(ACTION_INDEX_UPDATE)
        intent.putExtra(getString(R.string.MUSIC_INDEX), musicIndex)
        setMusicIndexInList(musicIndex)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // ■ 미디어 플레이어 일시정지
    private fun pauseMusic() {
        mediaPlayer?.pause()
        setIsPlaying(false)
        broadcastIsPlayedUpdate(false)
        mediaPlayer?.apply {
            seekTo(currentPosition)
            broadcastSbThumbUpdate(duration, currentPosition)
        }

        updateNotification()
        broadcastIsPlayedUpdate(false)
    }

    // ■ 미디어 플레이어 정지
    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        setIsPlaying(false)
        broadcastIsPlayedUpdate(false)
    }

    // ■ 다음 곡 재생
    private fun nextMusic() {
        val currentIndex = getMusicIndexInList()
        val nextIndex = if (currentIndex == getMusicList().size - 1) 0 else currentIndex + 1

        setMusicIndexInList(getMusicList()[nextIndex].index)
        setMusicPath(getMusicList()[nextIndex].path)
        broadCastMusicIndexUpdate(nextIndex)

        if (getIsPlaying()) {
            val playIntent = Intent().apply {
                action = ACTION_PLAY
            }
            playMusic(playIntent)
        }
        updateNotification()
    }

    // ■ 이전 곡 재생
    private fun prevMusic() {
        val currentIndex = getMusicIndexInList()
        val prevIndex = if (currentIndex == 0) getMusicList().size - 1 else currentIndex - 1

        setMusicIndexInList(getMusicList()[prevIndex].index)
        setMusicPath(getMusicList()[prevIndex].path)
        broadCastMusicIndexUpdate(prevIndex)

        if (getIsPlaying()) {
            val playIntent = Intent().apply {
                action = ACTION_PLAY
            }
            playMusic(playIntent)
        }
        updateNotification()
    }



    // ■ PendingIntent 가져오는 함수
    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    // ■ 알림채널 생성
    private fun createChannel() {
        val channelName = "Media Playback"
        val channelDescription = "Media playback controls"
        val channelImportance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(getString(R.string.CHANNEL_ID), channelName, channelImportance)
        channel.description = channelDescription
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        // 채널을 시스템에 등록
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startNotification() {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_MUTABLE)

        // 처음 재생하면 PAUSE 상태
        val actionIcon: Int = R.drawable.baseline_pause_24
        val actionIntent: PendingIntent =
            PendingIntent.getService(this, 0, Intent(this, MediaPlayerService::class.java).apply {
                action = ACTION_PAUSE
            }, PendingIntent.FLAG_MUTABLE)

        val notification = NotificationCompat.Builder(this, getString(R.string.CHANNEL_ID))
            .setContentTitle(getMusicList()[getMusicIndexInList()].title)
            .setContentText(getMusicList()[getMusicIndexInList()].artist)
            .setSmallIcon(R.drawable.music_icon)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.baseline_skip_previous_24, "Prev", getPendingIntent(ACTION_PREV))
            .addAction(actionIcon, if (getIsPlaying()) "Pause" else "Play", actionIntent)
            .addAction(R.drawable.baseline_skip_next_24, "Next", getPendingIntent(ACTION_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
        startForeground(1, notification)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE)

        val playPauseIcon: Int
        val playPauseAction: String
        val playPauseTitle: String

        if (mediaPlayer == null) {
            setMusicIndexInList(getMusicList()[0].index)
            broadCastMusicIndexUpdate(getMusicList()[0].index)
            setMusicPath(getMusicList()[0].path)
            playPauseIcon = R.drawable.baseline_play_arrow_24
            playPauseAction = ACTION_PLAY
            playPauseTitle = "Play"
        } else if (getIsPlaying()) {
            playPauseIcon = R.drawable.baseline_pause_24
            playPauseAction = ACTION_PAUSE
            playPauseTitle = "Pause"
        } else {
            playPauseIcon = R.drawable.baseline_play_arrow_24
            playPauseAction = ACTION_PLAY
            playPauseTitle = "Play"
        }

        val playPauseIntent = PendingIntent.getService(this, 0, Intent(this, MediaPlayerService::class.java).apply {
            action = playPauseAction
        }, PendingIntent.FLAG_MUTABLE)
        val prevIntent = PendingIntent.getService(this, 0, Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_PREV
        }, PendingIntent.FLAG_MUTABLE)
        val nextIntent = PendingIntent.getService(this, 0, Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_NEXT
        }, PendingIntent.FLAG_MUTABLE)

        val notification = NotificationCompat.Builder(this, getString(R.string.CHANNEL_ID))
            .setContentTitle(getMusicList()[getMusicIndexInList()].title)
            .setContentText(getMusicList()[getMusicIndexInList()].artist)
            .setSmallIcon(R.drawable.music_icon)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.baseline_skip_previous_24, "Prev", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.baseline_skip_next_24, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()

        notificationManager.notify(1, notification) // 이전에 startForeground에 사용했던 같은 ID를 사용하여 알림을 업데이트
    }


    private fun seekToPosition(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            mediaPlayer?.start()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
        }

        override fun onPause() {
            super.onPause()
            mediaPlayer?.pause()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            nextMusic()
            updateNotification()
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            updateNotification()
        }
    }
    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build() // .build()를 호출하여 PlaybackStateCompat 객체를 생성

        mediaSession.setPlaybackState(playbackState) // 생성된 PlaybackStateCompat 객체를 사용
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 오디오 포커스를 장기간 잃었을 때 (다른 앱이 오디오를 재생)
                pauseMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 오디오 포커스를 일시적으로 잃었을 때 (예: 전화가 오는 경우)
                pauseMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 오디오 포커스를 일시적으로 잃었지만, 소리를 줄일 수 있는 경우 (덕)
                // 볼륨을 줄이거나, 필요에 따라 일시정지
                lowerVolume()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 오디오 포커스를 다시 얻었을 때
                resumeMusic()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocus(focusChangeListener)
    }

    private fun resumeMusic() {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
        }
    }

    private fun lowerVolume() {
        mediaPlayer?.setVolume(0.1f, 0.1f)
    }

}
