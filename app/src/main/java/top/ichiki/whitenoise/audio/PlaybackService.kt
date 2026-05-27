package top.ichiki.whitenoise.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import top.ichiki.whitenoise.MainActivity
import top.ichiki.whitenoise.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject
    lateinit var soundEngine: SoundEngine

    private val binder = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        const val CHANNEL_ID = "whitenoise_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                soundEngine.resumeAll()
                updatePlaybackState(true)
            }
            ACTION_PAUSE -> {
                soundEngine.pauseAll()
                updatePlaybackState(false)
            }
            ACTION_STOP -> {
                soundEngine.stopAll()
                mediaSession.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val isPlaying = intent?.action != ACTION_PAUSE
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying))
        return START_STICKY
    }

    /** Called by ViewModel to sync notification state */
    fun updateNotification(isPlaying: Boolean) {
        updatePlaybackState(isPlaying)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "WhiteNoiseSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    soundEngine.resumeAll()
                    updatePlaybackState(true)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(true))
                }

                override fun onPause() {
                    soundEngine.pauseAll()
                    updatePlaybackState(false)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(false))
                }

                override fun onStop() {
                    soundEngine.stopAll()
                    isActive = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "白噪音")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "正在播放")
                    .build()
            )
            isActive = true
        }
        updatePlaybackState(true)
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "白噪音播放", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "白噪音后台播放控制"
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause toggle action
        val toggleAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this, 1,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "暂停", pauseIntent)
        } else {
            val playIntent = PendingIntent.getService(
                this, 1,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_play, "播放", playIntent)
        }

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("白噪音")
            .setContentText(if (isPlaying) "正在播放..." else "已暂停")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
    }
}
