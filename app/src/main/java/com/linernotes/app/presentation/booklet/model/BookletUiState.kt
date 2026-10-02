package com.linernotes.app.presentation.booklet.model

import androidx.compose.ui.graphics.Color
import com.linernotes.app.data.local.relation.AlbumWithTracks
import com.linernotes.app.domain.model.BilingualLyricLine
import com.linernotes.app.domain.model.LyricDisplayMode
import com.linernotes.app.presentation.booklet.components.FuriganaMode

enum class AnnotationLoadState {
    IDLE,
    LOADING,
    LOADED,
    EMPTY,
    FAILED
}

data class BookletUiState(
    val isLoading: Boolean = true,
    val albumWithTracks: AlbumWithTracks? = null,
    val currentTrackIndex: Int = 0,
    val displayMode: LyricDisplayMode = LyricDisplayMode.BILINGUAL,
    val alignedLyrics: List<BilingualLyricLine> = emptyList(),
    val ambientCoverColor: Color = Color(0xFF1E1E24),
    val isEditingSheetOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isTranslating: Boolean = false,
    val isTranslateMenuOpen: Boolean = false,
    val userMessage: String? = null,
    val isCompanionPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val activeLineIndex: Int = -1,
    val trackDurationMs: Long = 0L,
    val showCalibrationBar: Boolean = false,
    // 日语假名注音模式
    val furiganaMode: FuriganaMode = FuriganaMode.OFF,
    // 歌词时间轴微调偏移 (正数=歌词提前，负数=歌词延后)
    val lyricOffsetMs: Long = 0L,
    // CD 光盘展示区域是否展开
    val isDiscViewExpanded: Boolean = false,
    // 实体 CD 蓝牙同步状态
    val cdConnectionState: com.linernotes.app.core.bluetooth.CdConnectionState = com.linernotes.app.core.bluetooth.CdConnectionState.DISCONNECTED,
    val cdDeviceName: String? = null,
    val cdTotalTracks: Int = 0,
    val cdCurrentTrackNumber: Int = 1,
    val isCdSheetOpen: Boolean = false,
    val isCdTracklistOpen: Boolean = false,
    val isCdMatchAlbumOpen: Boolean = false,
    val isBookletSheetOpen: Boolean = false,
    // Discogs 实体 CD 版本库状态
    val isDiscogsPickerOpen: Boolean = false,
    val isDiscogsLoading: Boolean = false,
    val discogsResults: List<com.linernotes.app.data.remote.DiscogsReleaseSummary> = emptyList(),
    val selectedDiscogsDetail: com.linernotes.app.data.remote.DiscogsReleaseDetail? = null,
    val isDiscogsDetailLoading: Boolean = false,
    // Genius 歌词典故与背景故事
    val lineAnnotations: Map<Int, com.linernotes.app.data.local.entity.LyricAnnotationEntity> = emptyMap(),
    val songStory: com.linernotes.app.data.local.entity.SongStoryEntity? = null,
    val selectedAnnotation: com.linernotes.app.data.local.entity.LyricAnnotationEntity? = null,
    val isAnnotationSheetOpen: Boolean = false,
    val isSongStoryExpanded: Boolean = false,
    val isLoadingAnnotations: Boolean = false,
    val isTranslatingAnnotation: Boolean = false,
    val isTranslatingSongStory: Boolean = false,
    val isTraditionalMode: Boolean = false,
    // 行内手风琴典故展开行索引与沉浸式阅读模式
    val expandedAnnotationLineIndex: Int? = null,
    val isImmersiveMode: Boolean = false,
    val annotationLoadState: AnnotationLoadState = AnnotationLoadState.IDLE,
    val isAlbumPrewarming: Boolean = false,
    val prewarmProgress: Pair<Int, Int>? = null
)
