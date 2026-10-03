package com.linernotes.app.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
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
    val isAnnotationTranslating: Boolean = false,
    val isSongStoryTranslating: Boolean = false,
    val furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
    val isTraditionalChinese: Boolean = false,
    val isDeCensorEnabled: Boolean = true,
    val showPlaybackControls: Boolean = true,
    val lyricOffsetMs: Long = -200L,
    val isQueueSheetOpen: Boolean = false,
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
                _uiState.update { current ->
                    val updatedSelected = if (current.selectedAnnotation != null) {
                        data.annotatedLines.values.find {
                            (it.id > 0L && it.id == current.selectedAnnotation.id) ||
                            (it.lyricFragment == current.selectedAnnotation.lyricFragment && it.explanationText == current.selectedAnnotation.explanationText)
                        } ?: current.selectedAnnotation
                    } else null

                    current.copy(
                        nowPlayingData = data,
                        selectedAnnotation = updatedSelected
                    )
                }
            }
        }

        viewModelScope.launch {
            nowPlayingRepository.lyricOffsetMsFlow.collect { offset ->
                _uiState.update { it.copy(lyricOffsetMs = offset) }
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

    fun toggleShuffle() {
        nowPlayingRepository.toggleShuffle()
    }

    fun cycleRepeatMode() {
        nowPlayingRepository.cycleRepeatMode()
    }

    fun openQueueSheet() {
        _uiState.update { it.copy(isQueueSheetOpen = true) }
    }

    fun closeQueueSheet() {
        _uiState.update { it.copy(isQueueSheetOpen = false) }
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

    fun setLyricOffset(offsetMs: Long) {
        _uiState.update { it.copy(lyricOffsetMs = offsetMs) }
        nowPlayingRepository.setLyricOffset(offsetMs)
    }

    fun resetLyricOffset() {
        _uiState.update { it.copy(lyricOffsetMs = -200L) }
        nowPlayingRepository.setLyricOffset(-200L)
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

    private fun isTranslationNeeded(original: String, translation: String?): Boolean {
        if (original.isBlank() || AiAnnotationCurator.isAlreadyChinese(original)) return false
        if (translation.isNullOrBlank()) return true
        val cleanOrig = original.trim()
        val cleanTrans = translation.trim()
        if (cleanOrig.equals(cleanTrans, ignoreCase = true)) return true
        if (!Regex("""[\u4e00-\u9fa5]""").containsMatchIn(cleanTrans)) return true
        val origParas = cleanOrig.split(Regex("""(?:\r?\n\s*){2,}""")).filter { it.isNotBlank() }
        val transParas = cleanTrans.split(Regex("""(?:\r?\n\s*){2,}""")).filter { it.isNotBlank() }
        if (origParas.size > 1 && transParas.size < origParas.size) return true
        return false
    }

    fun openGeniusAnnotation(annotation: LyricAnnotationEntity) {
        val needsTrans = isTranslationNeeded(annotation.explanationText, annotation.explanationTranslation)

        _uiState.update {
            it.copy(
                selectedAnnotation = annotation,
                isGeniusSheetOpen = true,
                isAnnotationTranslating = needsTrans
            )
        }

        if (needsTrans) {
            viewModelScope.launch {
                try {
                    val updated = nowPlayingRepository.translateSingleAnnotation(annotation)
                    _uiState.update { cur ->
                        if (cur.selectedAnnotation?.lyricFragment == annotation.lyricFragment) {
                            cur.copy(
                                selectedAnnotation = updated,
                                isAnnotationTranslating = false
                            )
                        } else {
                            cur.copy(isAnnotationTranslating = false)
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isAnnotationTranslating = false) }
                }
            }
        }
    }

    fun retryAnnotationTranslation() {
        val current = _uiState.value.selectedAnnotation ?: return
        _uiState.update { it.copy(isAnnotationTranslating = true) }
        viewModelScope.launch {
            try {
                val updated = nowPlayingRepository.translateSingleAnnotation(current)
                _uiState.update { cur ->
                    if (cur.selectedAnnotation?.lyricFragment == current.lyricFragment) {
                        cur.copy(
                            selectedAnnotation = updated,
                            isAnnotationTranslating = false
                        )
                    } else {
                        cur.copy(isAnnotationTranslating = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnnotationTranslating = false) }
            }
        }
    }

    fun closeGeniusSheet() {
        _uiState.update {
            it.copy(
                selectedAnnotation = null,
                isGeniusSheetOpen = false,
                isAnnotationTranslating = false
            )
        }
    }

    fun openSongStory() {
        val story = _uiState.value.nowPlayingData.songStory
        val needsTrans = story != null && isTranslationNeeded(story.descriptionPlain, story.descriptionTranslation)

        _uiState.update {
            it.copy(
                isSongStorySheetOpen = true,
                isSongStoryTranslating = needsTrans
            )
        }

        if (needsTrans && story != null) {
            viewModelScope.launch {
                try {
                    nowPlayingRepository.translateCurrentStory(story)
                    _uiState.update { it.copy(isSongStoryTranslating = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSongStoryTranslating = false) }
                }
            }
        }
    }

    fun retrySongStoryTranslation() {
        val story = _uiState.value.nowPlayingData.songStory ?: return
        _uiState.update { it.copy(isSongStoryTranslating = true) }
        viewModelScope.launch {
            try {
                nowPlayingRepository.translateCurrentStory(story)
                _uiState.update { it.copy(isSongStoryTranslating = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSongStoryTranslating = false) }
            }
        }
    }

    fun closeSongStory() {
        _uiState.update {
            it.copy(
                isSongStorySheetOpen = false,
                isSongStoryTranslating = false
            )
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun dismissGeniusNotice() {
        nowPlayingRepository.dismissGeniusNotice()
    }

    fun retryGenius() {
        nowPlayingRepository.reloadGeniusOnly()
    }
}
