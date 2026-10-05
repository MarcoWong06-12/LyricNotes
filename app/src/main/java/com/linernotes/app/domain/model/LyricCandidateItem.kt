package com.linernotes.app.domain.model

enum class LyricSource(val displayName: String) {
    NETEASE("网易云音乐"),
    QQ_MUSIC("QQ 音乐")
}

data class LyricCandidateItem(
    val source: LyricSource,
    val sourceId: String,          // NetEase: songId, QQ: songmid
    val extraKey: String = "",     // QQ: albummid
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val hasTranslation: Boolean = false
)
