package com.linernotes.app.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.repository.NowPlayingData
import com.linernotes.app.data.repository.NowPlayingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FuriganaDisplayMode {
    OFF,
    HIRAGANA,
    ROMAJI
}

data class NowPlayingUiState(
    val nowPlayingData: NowPlayingData = NowPlayingData(),
    val selectedAnnotation: LyricAnnotationEntity? = null,
    val isGeniusSheetOpen: Boolean = false,
    val isSongStorySheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
    val isTraditionalChinese: Boolean = false,
    val isDeCensorEnabled: Boolean = true,
    val showPlaybackControls: Boolean = false,
    val lyricOffsetMs: Long = 0L,
    val userMessage: String? = null
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val nowPlayingRepository: NowPlayingRepository,
    private val aiPreferences: AiPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            nowPlayingRepository.nowPlayingData.collect { data ->
                _uiState.update { it.copy(nowPlayingData = data) }
            }
        }
    }

    fun togglePlayPause() {
        nowPlayingRepository.togglePlayPause()
    }

    fun skipToNext() {
        nowPlayingRepository.skipToNext()
    }

    fun skipToPrevious() {
        nowPlayingRepository.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        nowPlayingRepository.seekTo(positionMs)
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsSheetOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsSheetOpen = false) }
    }

    fun adjustLyricOffset(deltaMs: Long) {
        val newOffset = _uiState.value.lyricOffsetMs + deltaMs
        _uiState.update { it.copy(lyricOffsetMs = newOffset) }
        nowPlayingRepository.setLyricOffset(newOffset)
    }

    fun resetLyricOffset() {
        _uiState.update { it.copy(lyricOffsetMs = 0L) }
        nowPlayingRepository.setLyricOffset(0L)
    }

    fun togglePlaybackControls() {
        _uiState.update { it.copy(showPlaybackControls = !it.showPlaybackControls) }
    }

    fun toggleTraditionalChinese() {
        _uiState.update { it.copy(isTraditionalChinese = !it.isTraditionalChinese) }
    }

    fun toggleDeCensor() {
        _uiState.update { it.copy(isDeCensorEnabled = !it.isDeCensorEnabled) }
    }

    fun setFuriganaMode(mode: FuriganaDisplayMode) {
        _uiState.update { it.copy(furiganaMode = mode) }
    }

    fun cycleFuriganaMode() {
        val next = when (_uiState.value.furiganaMode) {
            FuriganaDisplayMode.OFF -> FuriganaDisplayMode.HIRAGANA
            FuriganaDisplayMode.HIRAGANA -> FuriganaDisplayMode.ROMAJI
            FuriganaDisplayMode.ROMAJI -> FuriganaDisplayMode.OFF
        }
        _uiState.update { it.copy(furiganaMode = next) }
    }

    fun reloadLyrics() {
        val state = _uiState.value.nowPlayingData.playbackState
        if (state.hasValidTrack) {
            val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
            nowPlayingRepository.forceReloadLyrics(state.title, state.artist, songKey)
        }
    }

    fun openGeniusAnnotation(annotation: LyricAnnotationEntity) {
        _uiState.update {
            it.copy(
                selectedAnnotation = annotation,
                isGeniusSheetOpen = true
            )
        }
    }

    fun closeGeniusSheet() {
        _uiState.update {
            it.copy(
                selectedAnnotation = null,
                isGeniusSheetOpen = false
            )
        }
    }

    fun openSongStory() {
        _uiState.update { it.copy(isSongStorySheetOpen = true) }
    }

    fun closeSongStory() {
        _uiState.update { it.copy(isSongStorySheetOpen = false) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
