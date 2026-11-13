package com.linernotes.app.presentation.nowplaying

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.NowPlayingMessageKeys
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.playback.PlaybackClockState
import com.linernotes.app.core.playback.QueueTrackItem
import com.linernotes.app.core.playback.TrackMetadataState
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.repository.LyricsContentState
import com.linernotes.app.data.repository.NowPlayingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FuriganaDisplayMode {
    OFF,
    HIRAGANA,
    ROMAJI
}

@Immutable
data class NowPlayingUiState(
    val trackMetadata: TrackMetadataState = TrackMetadataState(),
    val isPlaying: Boolean = false,
    val lyricsContent: LyricsContentState = LyricsContentState(),
    val selectedAnnotation: LyricAnnotationEntity? = null,
    val isGeniusSheetOpen: Boolean = false,
    val isSongStorySheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isAnnotationTranslating: Boolean = false,
    val isSongStoryTranslating: Boolean = false,
    val furiganaMode: FuriganaDisplayMode = FuriganaDisplayMode.OFF,
    val isTraditionalChinese: Boolean = false,
    val themeMode: String = AiPreferences.ThemeMode.SYSTEM.code,
    val appLanguage: String = AppLanguage.SYSTEM.code,
    val targetLanguage: String = TranslationTargetLanguage.ZH_CN.code,
    val lyricsSource: String = AiPreferences.LyricsSourcePreference.AUTO_FIRST.code,
    val geniusToken: String = "",
    val isAmoledMode: Boolean = false,
    val lyricAlignment: Int = AiPreferences.LYRIC_ALIGN_LEFT,
    val lyricTextSize: Int = AiPreferences.LYRIC_TEXT_SIZE_STANDARD,
    val isDynamicAuroraEnabled: Boolean = true,
    val isWordByWordEnabled: Boolean = true,
    val isDeCensorEnabled: Boolean = true,
    val showPlaybackControls: Boolean = true,
    val songOffsetMs: Long = 0L,
    val globalOffsetMs: Long = 0L,
    val lyricOffsetMs: Long = 0L,
    val isQueueSheetOpen: Boolean = false,
    val sleepTimerMinutesLeft: Int = 0,
    val isManualSearchSheetOpen: Boolean = false,
    val manualSearchQuery: String = "",
    val manualCandidates: List<com.linernotes.app.domain.model.LyricCandidateItem> = emptyList(),
    val isSearchingCandidates: Boolean = false,
    val isApplyingCandidate: Boolean = false,
    val hasManualBinding: Boolean = false,
    val candidateSearchError: String? = null,
    val userMessage: String? = null
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val nowPlayingRepository: NowPlayingRepository,
    private val aiPreferences: AiPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NowPlayingUiState(
            isTraditionalChinese = aiPreferences.isTraditionalChinese,
            themeMode = aiPreferences.themeMode,
            appLanguage = aiPreferences.appLanguage,
            targetLanguage = aiPreferences.targetLanguage,
            lyricsSource = aiPreferences.lyricsSource,
            geniusToken = aiPreferences.geniusToken,
            isAmoledMode = aiPreferences.isAmoledMode,
            lyricAlignment = aiPreferences.lyricAlignment,
            lyricTextSize = aiPreferences.lyricTextSize,
            isDynamicAuroraEnabled = aiPreferences.isDynamicAuroraEnabled,
            isWordByWordEnabled = aiPreferences.isWordByWordEnabled,
            isDeCensorEnabled = aiPreferences.isDeCensorEnabled
        )
    )
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    val playbackClockFlow: StateFlow<PlaybackClockState> = nowPlayingRepository.playbackClockFlow
    val currentLineIndexFlow: StateFlow<Int> = nowPlayingRepository.currentLineIndexFlow
    val notificationAccessGrantedFlow: StateFlow<Boolean> = nowPlayingRepository.notificationAccessGrantedFlow

    private var annotationTransJob: Job? = null
    private var songStoryTransJob: Job? = null

    init {
        viewModelScope.launch {
            nowPlayingRepository.trackMetadataFlow.collect { metadata ->
                _uiState.update { it.copy(trackMetadata = metadata) }
            }
        }

        viewModelScope.launch {
            nowPlayingRepository.playbackClockFlow
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { playing ->
                    _uiState.update { it.copy(isPlaying = playing) }
                }
        }

        viewModelScope.launch {
            nowPlayingRepository.lyricsContentFlow.collect { content ->
                val wasAnnotationTranslating = _uiState.value.isAnnotationTranslating
                val wasStoryTranslating = _uiState.value.isSongStoryTranslating
                val nextState = _uiState.updateAndGet { current ->
                    val updatedSelected = if (current.selectedAnnotation != null) {
                        content.annotatedLines.values.find {
                            (it.id > 0L && it.id == current.selectedAnnotation.id) ||
                            (it.lyricFragment == current.selectedAnnotation.lyricFragment && it.explanationText == current.selectedAnnotation.explanationText)
                        } ?: current.selectedAnnotation
                    } else null

                    val annotationDone = updatedSelected != null && !isAnnotationTranslationPending(updatedSelected)
                    val storyDone = content.songStory != null &&
                        !isTranslationNeeded(content.songStory.descriptionPlain, content.songStory.descriptionTranslation)

                    current.copy(
                        lyricsContent = content,
                        selectedAnnotation = updatedSelected,
                        isAnnotationTranslating = if (current.isAnnotationTranslating && annotationDone) false else current.isAnnotationTranslating,
                        isSongStoryTranslating = if (current.isSongStoryTranslating && storyDone) false else current.isSongStoryTranslating
                    )
                }
                if (wasAnnotationTranslating && !nextState.isAnnotationTranslating) {
                    annotationTransJob?.cancel()
                }
                if (wasStoryTranslating && !nextState.isSongStoryTranslating) {
                    songStoryTransJob?.cancel()
                }
            }
        }

        viewModelScope.launch {
            nowPlayingRepository.lyricOffsetMsFlow.collect { offset ->
                _uiState.update { it.copy(lyricOffsetMs = offset) }
            }
        }

        viewModelScope.launch {
            nowPlayingRepository.songOffsetMsFlow.collect { offset ->
                _uiState.update { it.copy(songOffsetMs = offset) }
            }
        }

        viewModelScope.launch {
            nowPlayingRepository.globalOffsetMsFlow.collect { offset ->
                _uiState.update { it.copy(globalOffsetMs = offset) }
            }
        }

        viewModelScope.launch {
            aiPreferences.isTraditionalChineseFlow.collect { isTrad ->
                _uiState.update { it.copy(isTraditionalChinese = isTrad) }
            }
        }

        viewModelScope.launch {
            aiPreferences.themeModeFlow.collect { theme ->
                _uiState.update { it.copy(themeMode = theme) }
            }
        }

        viewModelScope.launch {
            aiPreferences.appLanguageFlow.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }

        viewModelScope.launch {
            aiPreferences.targetLanguageFlow.collect { targetLang ->
                _uiState.update { it.copy(targetLanguage = targetLang) }
            }
        }

        viewModelScope.launch {
            aiPreferences.lyricsSourceFlow.collect { source ->
                _uiState.update { it.copy(lyricsSource = source) }
            }
        }

        viewModelScope.launch {
            aiPreferences.geniusTokenFlow.collect { token ->
                _uiState.update { it.copy(geniusToken = token) }
            }
        }

        viewModelScope.launch {
            aiPreferences.isAmoledModeFlow.collect { isAmoled ->
                _uiState.update { it.copy(isAmoledMode = isAmoled) }
            }
        }

        viewModelScope.launch {
            aiPreferences.lyricAlignmentFlow.collect { align ->
                _uiState.update { it.copy(lyricAlignment = align) }
            }
        }

        viewModelScope.launch {
            aiPreferences.lyricTextSizeFlow.collect { size ->
                _uiState.update { it.copy(lyricTextSize = size) }
            }
        }

        viewModelScope.launch {
            aiPreferences.isDynamicAuroraEnabledFlow.collect { aurora ->
                _uiState.update { it.copy(isDynamicAuroraEnabled = aurora) }
            }
        }

        viewModelScope.launch {
            aiPreferences.isWordByWordEnabledFlow.collect { wbw ->
                _uiState.update { it.copy(isWordByWordEnabled = wbw) }
            }
        }

        viewModelScope.launch {
            aiPreferences.isDeCensorEnabledFlow.collect { decensor ->
                _uiState.update { it.copy(isDeCensorEnabled = decensor) }
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

    fun adjustSongOffset(deltaMs: Long) {
        nowPlayingRepository.adjustSongOffset(deltaMs)
    }

    fun resetSongOffset() {
        nowPlayingRepository.resetSongOffset()
    }

    fun adjustGlobalOffset(deltaMs: Long) {
        nowPlayingRepository.adjustGlobalOffset(deltaMs)
    }

    fun resetGlobalOffset() {
        nowPlayingRepository.resetGlobalOffset()
    }

    fun adjustLyricOffset(deltaMs: Long) {
        adjustSongOffset(deltaMs)
    }

    fun setLyricOffset(offsetMs: Long) {
        nowPlayingRepository.setLyricOffset(offsetMs)
    }

    fun resetLyricOffset() {
        resetSongOffset()
    }

    fun togglePlaybackControls() {
        _uiState.update { it.copy(showPlaybackControls = !it.showPlaybackControls) }
    }

    fun toggleTraditionalChinese() {
        aiPreferences.isTraditionalChinese = !aiPreferences.isTraditionalChinese
    }

    fun setThemeMode(themeCode: String) {
        aiPreferences.themeMode = themeCode
    }

    fun setAmoledMode(enabled: Boolean) {
        aiPreferences.isAmoledMode = enabled
    }

    fun setLyricAlignment(alignment: Int) {
        aiPreferences.lyricAlignment = alignment
    }

    fun setLyricTextSize(size: Int) {
        aiPreferences.lyricTextSize = size
    }

    fun setDynamicAuroraEnabled(enabled: Boolean) {
        aiPreferences.isDynamicAuroraEnabled = enabled
    }

    fun setWordByWordEnabled(enabled: Boolean) {
        aiPreferences.isWordByWordEnabled = enabled
    }

    fun setAppLanguage(langCode: String) {
        aiPreferences.appLanguage = langCode
    }

    fun setTargetLanguage(langCode: String) {
        aiPreferences.targetLanguage = langCode
    }

    fun setLyricsSource(sourceCode: String) {
        aiPreferences.lyricsSource = sourceCode
    }

    fun setGeniusToken(token: String) {
        aiPreferences.geniusToken = token.trim()
    }

    fun toggleDeCensor() {
        val newVal = !aiPreferences.isDeCensorEnabled
        aiPreferences.isDeCensorEnabled = newVal
        nowPlayingRepository.reloadCurrentSong()
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
        val state = _uiState.value.trackMetadata
        if (state.hasValidTrack) {
            val songKey = "${state.artist.trim().lowercase()} - ${state.title.trim().lowercase()}"
            nowPlayingRepository.forceReloadLyrics(state.title, state.artist, songKey, state.trackEpoch)
        }
    }

    private fun matchesExpectedTargetScript(text: String, targetIso: String): Boolean {
        return when {
            targetIso.startsWith("zh", ignoreCase = true) -> CJK_REGEX.containsMatchIn(text)
            targetIso.equals("ja", ignoreCase = true) -> JA_SCRIPT_REGEX.containsMatchIn(text)
            targetIso.equals("ko", ignoreCase = true) -> KO_SCRIPT_REGEX.containsMatchIn(text)
            else -> text.any { it.isLetter() }
        }
    }

    private fun isTranslationNeeded(original: String, translation: String?): Boolean {
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isChineseTarget = targetIso.startsWith("zh", ignoreCase = true)
        if (original.isBlank() || (isChineseTarget && AiAnnotationCurator.isAlreadyChinese(original))) return false
        if (translation.isNullOrBlank()) return true
        val cleanOrig = original.trim()
        val cleanTrans = translation.trim()
        if (cleanOrig.equals(cleanTrans, ignoreCase = true)) return true
        if (!matchesExpectedTargetScript(cleanTrans, targetIso)) return true
        val origParas = cleanOrig.split(PARA_SPLIT_REGEX).filter { it.isNotBlank() }
        val transParas = cleanTrans.split(PARA_SPLIT_REGEX).filter { it.isNotBlank() }
        if (origParas.size > 1 && transParas.size < origParas.size) return true

        // 检查末尾段落是否疑似被截断吞句 (例如原文长段但译文畸短如“这条线”、“但这并不是全部”)
        val lastOrig = origParas.lastOrNull() ?: ""
        val lastTrans = transParas.lastOrNull() ?: ""
        if (isChineseTarget) {
            if (lastOrig.length >= 35 && lastTrans.length < 12) return true
            if (lastOrig.length >= 70 && lastTrans.length < 22) return true
            if (lastOrig.length >= 120 && lastTrans.length < (lastOrig.length * 0.16).toInt()) return true
        }

        return false
    }

    private fun isAnnotationTranslationPending(annotation: LyricAnnotationEntity): Boolean {
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val isChineseTarget = targetIso.startsWith("zh", ignoreCase = true)
        val needsExplanationTrans = isTranslationNeeded(annotation.explanationText, annotation.explanationTranslation)
        val needsLyricTrans = annotation.lyricTranslation.isNullOrBlank() &&
            !(isChineseTarget && AiAnnotationCurator.isAlreadyChinese(annotation.lyricFragment))
        return needsExplanationTrans || needsLyricTrans
    }

    fun openGeniusAnnotation(annotation: LyricAnnotationEntity) {
        annotationTransJob?.cancel()
        val needsTrans = isAnnotationTranslationPending(annotation)

        _uiState.update {
            it.copy(
                selectedAnnotation = annotation,
                isGeniusSheetOpen = true,
                isAnnotationTranslating = needsTrans
            )
        }

        if (needsTrans) {
            annotationTransJob = viewModelScope.launch {
                try {
                    val updated = nowPlayingRepository.translateSingleAnnotation(annotation)
                    _uiState.update { cur ->
                        if (cur.selectedAnnotation?.lyricFragment == annotation.lyricFragment &&
                            cur.selectedAnnotation.explanationText == annotation.explanationText
                        ) {
                            cur.copy(
                                selectedAnnotation = updated,
                                isAnnotationTranslating = false
                            )
                        } else {
                            cur
                        }
                    }
                } catch (_: Exception) {
                    _uiState.update { cur ->
                        if (cur.selectedAnnotation?.lyricFragment == annotation.lyricFragment &&
                            cur.selectedAnnotation.explanationText == annotation.explanationText
                        ) {
                            cur.copy(isAnnotationTranslating = false)
                        } else {
                            cur
                        }
                    }
                }
            }
        }
    }

    fun retryAnnotationTranslation() {
        val current = _uiState.value.selectedAnnotation ?: return
        annotationTransJob?.cancel()
        _uiState.update { it.copy(isAnnotationTranslating = true) }
        annotationTransJob = viewModelScope.launch {
            try {
                val updated = nowPlayingRepository.translateSingleAnnotation(current)
                _uiState.update { cur ->
                    if (cur.selectedAnnotation?.lyricFragment == current.lyricFragment &&
                        cur.selectedAnnotation.explanationText == current.explanationText
                    ) {
                        cur.copy(
                            selectedAnnotation = updated,
                            isAnnotationTranslating = false
                        )
                    } else {
                        cur
                    }
                }
            } catch (_: Exception) {
                _uiState.update { cur ->
                    if (cur.selectedAnnotation?.lyricFragment == current.lyricFragment &&
                        cur.selectedAnnotation.explanationText == current.explanationText
                    ) {
                        cur.copy(isAnnotationTranslating = false)
                    } else {
                        cur
                    }
                }
            }
        }
    }

    fun closeGeniusSheet() {
        annotationTransJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAnnotation = null,
                isGeniusSheetOpen = false,
                isAnnotationTranslating = false
            )
        }
    }

    fun openSongStory() {
        songStoryTransJob?.cancel()
        val content = _uiState.value.lyricsContent
        val story = content.songStory
        val targetEpoch = content.trackEpoch
        val needsTrans = story != null && isTranslationNeeded(story.descriptionPlain, story.descriptionTranslation)

        _uiState.update {
            it.copy(
                isSongStorySheetOpen = true,
                isSongStoryTranslating = needsTrans
            )
        }

        if (needsTrans && story != null) {
            songStoryTransJob = viewModelScope.launch {
                try {
                    nowPlayingRepository.translateCurrentStory(story)
                } finally {
                    _uiState.update { cur ->
                        if (cur.lyricsContent.trackEpoch == targetEpoch) {
                            cur.copy(isSongStoryTranslating = false)
                        } else {
                            cur
                        }
                    }
                }
            }
        }
    }

    fun retrySongStoryTranslation() {
        songStoryTransJob?.cancel()
        val content = _uiState.value.lyricsContent
        val story = content.songStory ?: return
        val targetEpoch = content.trackEpoch
        _uiState.update { it.copy(isSongStoryTranslating = true) }
        songStoryTransJob = viewModelScope.launch {
            try {
                nowPlayingRepository.translateCurrentStory(story)
            } finally {
                _uiState.update { cur ->
                    if (cur.lyricsContent.trackEpoch == targetEpoch) {
                        cur.copy(isSongStoryTranslating = false)
                    } else {
                        cur
                    }
                }
            }
        }
    }

    fun closeSongStory() {
        songStoryTransJob?.cancel()
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

    fun skipToQueueItem(item: QueueTrackItem) {
        nowPlayingRepository.skipToQueueItem(item)
    }

    private var candidateSearchJob: kotlinx.coroutines.Job? = null

    fun openManualSearchSheet() {
        val curTrack = _uiState.value.trackMetadata
        val defaultQuery = "${curTrack.title} ${curTrack.artist}".trim()
        val hasBinding = nowPlayingRepository.hasManualBinding(curTrack.title, curTrack.artist)
        _uiState.update {
            it.copy(
                isManualSearchSheetOpen = true,
                isSettingsSheetOpen = false,
                manualSearchQuery = defaultQuery,
                manualCandidates = emptyList(),
                isSearchingCandidates = false,
                candidateSearchError = null,
                hasManualBinding = hasBinding
            )
        }
        if (defaultQuery.isNotBlank()) {
            searchCandidates(defaultQuery)
        }
    }

    fun closeManualSearchSheet() {
        candidateSearchJob?.cancel()
        _uiState.update { it.copy(isManualSearchSheetOpen = false) }
    }

    fun searchCandidates(query: String) {
        candidateSearchJob?.cancel()
        _uiState.update {
            it.copy(
                manualSearchQuery = query,
                isSearchingCandidates = true,
                candidateSearchError = null
            )
        }
        candidateSearchJob = viewModelScope.launch {
            try {
                val results = nowPlayingRepository.searchLyricCandidates(query)
                _uiState.update {
                    it.copy(
                        manualCandidates = results,
                        isSearchingCandidates = false,
                        candidateSearchError = if (results.isEmpty()) NowPlayingMessageKeys.CANDIDATE_NOT_FOUND else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchingCandidates = false,
                        candidateSearchError = NowPlayingMessageKeys.CANDIDATE_SEARCH_FAILED
                    )
                }
            }
        }
    }

    fun selectCandidate(candidate: com.linernotes.app.domain.model.LyricCandidateItem) {
        if (_uiState.value.isApplyingCandidate) return
        val curTrack = _uiState.value.trackMetadata
        _uiState.update { it.copy(isApplyingCandidate = true) }
        viewModelScope.launch {
            val success = nowPlayingRepository.applyManualCandidate(
                candidate = candidate,
                trackTitle = curTrack.title,
                artistName = curTrack.artist
            )
            _uiState.update {
                it.copy(
                    isApplyingCandidate = false,
                    isManualSearchSheetOpen = false,
                    hasManualBinding = success || it.hasManualBinding,
                    userMessage = if (success) NowPlayingMessageKeys.MSG_CANDIDATE_APPLIED else NowPlayingMessageKeys.MSG_CANDIDATE_FAILED
                )
            }
        }
    }

    fun resetToAutoMatch() {
        val curTrack = _uiState.value.trackMetadata
        viewModelScope.launch {
            nowPlayingRepository.resetManualLyricBinding(curTrack.title, curTrack.artist)
            _uiState.update {
                it.copy(
                    isManualSearchSheetOpen = false,
                    hasManualBinding = false,
                    userMessage = NowPlayingMessageKeys.MSG_AUTO_MATCH_RESTORED
                )
            }
        }
    }

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMinutesLeft = 0) }
            return
        }
        _uiState.update { it.copy(sleepTimerMinutesLeft = minutes) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                kotlinx.coroutines.delay(60_000L)
                remaining--
                _uiState.update { it.copy(sleepTimerMinutesLeft = remaining) }
            }
            if (nowPlayingRepository.playbackClockFlow.value.isPlaying) {
                nowPlayingRepository.pause()
            }
            _uiState.update { it.copy(sleepTimerMinutesLeft = 0) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerMinutesLeft = 0) }
    }

    companion object {
        private val CJK_REGEX = Regex("""[\u4e00-\u9fff]""")
        private val JA_SCRIPT_REGEX = Regex("""[\u3040-\u30ff\u4e00-\u9fff]""")
        private val KO_SCRIPT_REGEX = Regex("""[\uac00-\ud7af]""")
        private val PARA_SPLIT_REGEX = Regex("""(?:\r?\n\s*){2,}""")
    }
}

