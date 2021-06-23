package com.wishnewjam.dubstepfm.ui.home

import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wishnewjam.dubstepfm.R
import com.wishnewjam.dubstepfm.data.RadioStream
import com.wishnewjam.dubstepfm.ui.state.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class HomeViewModelPreview : HomeViewModel {
    override val initialPlayButtonState: Int = R.drawable.ic_play
    override val nowPlaying: LiveData<String?> = MutableLiveData("Now playing something")
    override val allStreams: Array<RadioStream> = arrayOf(RadioStream.RadioStream24(),
        RadioStream.RadioStream64(),
        RadioStream.RadioStream128(),
        RadioStream.RadioStream256())

    override fun toggleButton() {

    }

    override fun playbackStateChanged(state: PlaybackStateCompat?) {
    }

    override val playbackState: LiveData<PlaybackState> = MutableLiveData(PlaybackState.Stop)
    override val statusIcon: LiveData<Int?> = MutableLiveData(R.drawable.ic_play)
    override val playButtonRes: LiveData<Int> = MutableLiveData(R.drawable.ic_play)
    override val currentRadioStream: Flow<RadioStream> = flow { RadioStream.default }
    override val statusText: LiveData<String?> = MutableLiveData("Status")
}