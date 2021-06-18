package com.wishnewjam.dubstepfm.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.media.session.MediaButtonReceiver
import com.wishnewjam.dubstepfm.MainActivity
import com.wishnewjam.dubstepfm.legacy.Tools
import com.wishnewjam.dubstepfm.notification.LogoProvider
import com.wishnewjam.dubstepfm.notification.NotificationBuilder
import com.wishnewjam.dubstepfm.ui.state.PlayerState

class MediaCore(
    private val notificationBuilder: NotificationBuilder,
    private val logoProvider: LogoProvider,
    private val startForeground: (Notification) -> Unit,
    private val stopForeground: (Notification?) -> Unit
) {

    var token: MediaSessionCompat.Token? = null
        private set

    private var mediaSession: MediaSessionCompat? = null
    private var mediaPlayerInstance: MediaPlayerInstance? = null

    fun init(context: Context) {
        mediaSession = MediaSessionCompat(
            context,
            "PlayerService"
        )
        mediaSession?.run {
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            setPlaybackState(stateBuilder.build())

            val notificationFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            val mediaPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                mediaButtonIntent,
                notificationFlags
            )
            setMediaButtonReceiver(mediaPendingIntent)
            setCallback(MediaSessionCallback())
            setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    notificationFlags
                )
            )
            isActive = true
            token = sessionToken
        }

        val mediaPlayer =
            MediaPlayerInstance(
                context = context,
                { state -> onPlayerStateChanged(state) },
                { track -> onTrackNameChanged(track) })
        mediaPlayerInstance = mediaPlayer
    }

    private fun callSessionError(error: String) {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_ERROR,
                    PLAYBACK_POSITION_UNKNOWN,
                    0.0f
                )
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setErrorMessage(
                    PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                    error
                )
                .build()
        )
    }

    private fun callSessionPlay() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    PLAYBACK_POSITION_UNKNOWN,
                    0.0f
                )
                .setActions(PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )
        val notification =
            notificationBuilder.buildNotification(
                mediaSession,
                NotificationBuilder.NotificationStatus.Play
            )
        startForeground(notification)
    }

    private fun callSessionLoading() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_BUFFERING,
                    PLAYBACK_POSITION_UNKNOWN,
                    0.0f
                )
                .setActions(PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )
    }

    private fun callSessionPause() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    PLAYBACK_POSITION_UNKNOWN,
                    0.0f
                )
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .build()
        )
    }

    private fun callSessionStop() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    PLAYBACK_POSITION_UNKNOWN,
                    0.0f
                )
                .build()
        )
    }

    private fun dispatchPlay() {
        val notification =
            notificationBuilder.buildNotification(
                mediaSession,
                NotificationBuilder.NotificationStatus.Loading
            )
        startForeground(notification)
        mediaPlayerInstance?.callPlay()
        startForeground(notification)
    }

    private fun dispatchStop() {
        mediaPlayerInstance?.callStop()
        stopForeground(null)
    }

    private fun dispatchPause() {
        mediaPlayerInstance?.callStop()
        val notification =
            notificationBuilder.buildNotification(
                mediaSession,
                NotificationBuilder.NotificationStatus.Pause
            )
        stopForeground(notification)
    }

    fun destroy() {
        try {
            mediaPlayerInstance?.destroy()
            mediaSession?.release()
        } catch (e: Exception) {
            Tools.logDebug { "Exception onDestroy: ${e.message}" }
        }
        mediaPlayerInstance = null
        mediaSession = null
    }

    fun handleIntent(intent: Intent) {
        MediaButtonReceiver.handleIntent(
            mediaSession,
            intent
        )
    }

    private fun onMediaPlayerError(error: String) {
        callSessionError(error)
        val notification =
            notificationBuilder.buildNotification(
                mediaSession,
                NotificationBuilder.NotificationStatus.Error
            )
        stopForeground(notification)
    }

    private fun onPlayerStateChanged(status: PlayerState) {
        Tools.logDebug { "mediaPlayerCallback: onPlayerStateChanged, status = $status" }
        when (status) {
            is PlayerState.Play -> {
                callSessionPlay()
            }
            is PlayerState.Buffering -> {
                callSessionLoading()
            }
            is PlayerState.Error -> {
                onMediaPlayerError(status.errorText)
            }
            else -> {
            }
        }
    }

    private fun onTrackNameChanged(track: String) {
        val builder = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                ""
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                track
            )
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                10000
            )
        logoProvider.updateMetadataBuilderWithLogo(builder)
        mediaSession?.setMetadata(builder.build())
        notificationBuilder.updateMetaData(mediaSession)
    }

    private fun startForeground(notification: Notification?) {
        startForeground.invoke(notification ?: return)
    }

    private fun stopForeground(notification: Notification?) {
        stopForeground.invoke(notification)
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            Tools.logDebug { "mediaSessionCallback: onMediaButtonEvent $mediaButtonEvent extras ${mediaButtonEvent?.extras}" }
            val intentAction = mediaButtonEvent?.action
            if (Intent.ACTION_MEDIA_BUTTON != intentAction) {
                return false
            }
            val event: KeyEvent? = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            return handleMediaButtonIntent(event)
        }

        override fun onCommand(
            command: String?,
            extras: Bundle?,
            cb: ResultReceiver?
        ) {
            Tools.logDebug { "mediaSessionCallback: onCommand $command" }
            super.onCommand(command, extras, cb)
        }


        override fun onPlay() {
            super.onPlay()
            Tools.logDebug { "mediaSessionCallback: onPlay" }
            if (mediaPlayerInstance?.isPlaying() == true) {
                callSessionPlay()
            } else {
                callSessionLoading()
                dispatchPlay()
            }
        }

        override fun onPause() {
            super.onPause()
            Tools.logDebug { "mediaSessionCallback: onPause" }
            dispatchPause()

        }

        override fun onStop() {
            super.onStop()
            Tools.logDebug { "mediaSessionCallback: onStop" }
            dispatchStop()
        }
    }


    private fun handleMediaButtonIntent(event: KeyEvent?): Boolean {
        if (event != null) {
            val action = event.action
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    callSessionPause()
                    dispatchPause()
                } else if (event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
                    callSessionStop()
                    dispatchStop()
                } else if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    callSessionLoading()
                    dispatchPlay()
                } else if (event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    if (mediaSession?.controller?.playbackState?.playbackState == STATE_PLAYING) {
                        callSessionStop()
                        dispatchStop()
                    } else {
                        callSessionLoading()
                        dispatchPlay()
                    }
                }
                return true
            }
        }
        return false
    }
}