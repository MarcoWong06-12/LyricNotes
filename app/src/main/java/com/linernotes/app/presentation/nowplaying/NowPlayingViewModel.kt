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
    val furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
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

    fun cycleFuriganaMode() {
        val next = when (_uiState.value.furiganaMode) {
            FuriganaDisplayMode.OFF -> FuriganaDisplayMode.HIRAGANA
            FuriganaDisplayMode.HIRAGANA -> FuriganaDisplayMode.ROMAJI
            FuriganaDisplayMode.ROMAJI -> FuriganaDisplayMode.OFF
        }
        _uiState.update { it.copy(furiganaMode = next) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
