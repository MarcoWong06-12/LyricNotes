package com.linernotes.app.core.preference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用全局配置偏好（界面多语言、目标翻译语种、在线歌词源）
 */
@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeModeFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("theme_mode", ThemeMode.SYSTEM.code) ?: ThemeMode.SYSTEM.code
    )
    val themeModeFlow: kotlinx.coroutines.flow.StateFlow<String> = _themeModeFlow

    var themeMode: String
        get() = _themeModeFlow.value
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            _themeModeFlow.value = value
        }

    private val _appLanguageFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("app_language", com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code) ?: com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code
    )
    val appLanguageFlow: kotlinx.coroutines.flow.StateFlow<String> = _appLanguageFlow

    var appLanguage: String
        get() = _appLanguageFlow.value
        set(value) {
            prefs.edit().putString("app_language", value).apply()
            _appLanguageFlow.value = value
        }

    var targetLanguage: String
        get() = prefs.getString("target_language", com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code) ?: com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code
        set(value) = prefs.edit().putString("target_language", value).apply()

    var musixmatchToken: String
        get() = prefs.getString("musixmatch_token", "") ?: ""
        set(value) = prefs.edit().putString("musixmatch_token", value.trim()).apply()

    var discogsToken: String
        get() = prefs.getString("discogs_token", "") ?: ""
        set(value) = prefs.edit().putString("discogs_token", value.trim()).apply()

    var geniusToken: String
        get() = prefs.getString("genius_token", "") ?: ""
        set(value) = prefs.edit().putString("genius_token", value.trim()).apply()

    var lyricsSource: String
        get() = prefs.getString("lyrics_source", LyricsSourcePreference.AUTO_FIRST.code) ?: LyricsSourcePreference.AUTO_FIRST.code
        set(value) = prefs.edit().putString("lyrics_source", value).apply()

    enum class LyricsSourcePreference(val code: String, val displayNameZh: String, val displayNameEn: String) {
        AUTO_FIRST("auto_first", "智能多源聚合 (推荐：网易云+QQ+酷狗+Musixmatch+LRCLIB)", "Smart Multi-Source (NetEase + QQ + Kugou + Musixmatch + LRCLIB)"),
        NETEASE_ONLY("netease_only", "网易云音乐 (Netease Cloud Music)", "Netease Cloud Music"),
        QQ_ONLY("qq_only", "QQ 音乐 (QQ Music)", "QQ Music"),
        KUGOU_ONLY("kugou_only", "酷狗音乐 (Kugou Music)", "Kugou Music"),
        MUSIXMATCH_ONLY("musixmatch_only", "Musixmatch (国际曲库与逐行翻译)", "Musixmatch"),
        LRCLIB_ONLY("lrclib_only", "LRCLIB (全球开源歌词库)", "LRCLIB");

        companion object {
            fun fromCode(code: String): LyricsSourcePreference =
                entries.find { it.code.equals(code, ignoreCase = true) } ?: AUTO_FIRST
        }
    }

    enum class ThemeMode(val code: String) {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark");

        companion object {
            fun fromCode(code: String): ThemeMode =
                entries.find { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}
