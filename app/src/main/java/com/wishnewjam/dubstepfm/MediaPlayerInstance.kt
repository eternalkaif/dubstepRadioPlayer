package com.wishnewjam.dubstepfm

import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import androidx.core.net.toUri
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import dagger.Module

@Module
class MediaPlayerInstance(val context: Context) : Player.EventListener {
    companion object {
        private const val USER_AGENT: String = "dubstep.fm"
        private const val WAKE_LOCK = "mp_wakelock"
    }

    var status: Int = UIStates.STATUS_UNDEFINED
    var serviceCallback: CallbackInterface? = null

    private var currentUrl: CurrentUrl = CurrentUrl(context)

    private var audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var wifiLock: WifiManager.WifiLock?
    private var mediaPlayer: SimpleExoPlayer?

    init {

        val trackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(DefaultBandwidthMeter()))

        mediaPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
        mediaPlayer?.addListener(this)

        wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, WAKE_LOCK)

    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        Tools.logDebug { "exoPlayer: onRepeatModeChanged: $repeatMode" }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
        Tools.logDebug { "exoPlayer: onPlaybackParametersChanged: $playbackParameters" }
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
        Tools.logDebug { "exoPlayer: onTracksChanged: trackGroups = $trackGroups, trackSelections = $trackSelections" }

    }

    override fun onPlayerError(error: ExoPlaybackException?) {
        Tools.logDebug { "exoPlayer: onPlayerError: error = $error" }
        status = UIStates.STATUS_ERROR
        serviceCallback?.onError("Playback error: $error")

    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> notifyStatusChanged(UIStates.STATUS_LOADING)
            Player.STATE_READY     -> if (playWhenReady) notifyStatusChanged(UIStates.STATUS_PLAY)
            Player.STATE_ENDED     -> notifyStatusChanged(UIStates.STATUS_STOP)
        }
        Tools.logDebug { "exoPlayer: onPlayerStateChanged: playWhenReady = $playWhenReady, playbackState = $playbackState " }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        Tools.logDebug { "exoPlayer: onLoadingChanged: isLoading = $isLoading" }
    }

    override fun onPositionDiscontinuity(reason: Int) {
        Tools.logDebug { "exoPlayer: onPositionDiscontinuity" }
    }

    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
        Tools.logDebug { "exoPlayer: onTimelineChanged: timeline = $timeline, manifest = $manifest" }
    }

    override fun onSeekProcessed() {
        Tools.logDebug { "exoPlayer: onSeekProcessed" }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        Tools.logDebug { "exoPlayer: shuffleModeEnabled" }
    }

    fun callPlay() {
        Tools.logDebug { "Call play, status: $status" }
        when (status) {

            UIStates.STATUS_PLAY    -> {
                // do nothing
            }

            UIStates.STATUS_LOADING -> {
                // do nothing
            }

            else                    -> {
                play()
            }
        }
    }

    fun callStop() {
        when (status) {
            UIStates.STATUS_PLAY -> {
                mediaPlayer?.stop()
                notifyStatusChanged(UIStates.STATUS_STOP)
                audioManager?.abandonAudioFocus(afChangeListener)
            }
        }
//        releaseWakeLock()
    }

    fun changeUrl(newUrl: String) {
        if (currentUrl.currentUrl != newUrl) {
            currentUrl.updateUrl(newUrl)
            play()
        }
    }

    private val afChangeListener: AudioManager.OnAudioFocusChangeListener
        get() = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (mediaPlayer?.playbackState == Player.STATE_READY) {
                    mediaPlayer?.stop()
                    notifyStatusChanged(UIStates.STATUS_STOP)
                }
                AudioManager.AUDIOFOCUS_GAIN                                         -> if (status == UIStates.STATUS_WAITING) {
                    callPlay()
                }
            }
        }

    private fun notifyStatusChanged(status: Int) {
        this.status = status
        serviceCallback?.onChangeStatus(status)
    }

    private fun play() {
        val source = currentUrl.currentUrl.toUri()
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, USER_AGENT))
        val mediaSourceFactory = ExtractorMediaSource.Factory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(source)
        mediaPlayer?.playWhenReady = true
        mediaPlayer?.prepare(mediaSource)

        notifyStatusChanged(UIStates.STATUS_LOADING)

        audioManager?.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    interface CallbackInterface {
        fun onChangeStatus(status: Int)
        fun onError(error: String)
    }
}