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

    // 纯黑 OLED 极暗模式 (AMOLED Pure Black)：在深色主题下关闭像素点，实现纯黑高对比度与极致省电
    private val _isAmoledModeFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getBoolean("is_amoled_mode", false)
    )
    val isAmoledModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isAmoledModeFlow

    var isAmoledMode: Boolean
        get() = _isAmoledModeFlow.value
        set(value) {
            prefs.edit().putBoolean("is_amoled_mode", value).apply()
            _isAmoledModeFlow.value = value
        }

    // 全屏歌词对齐方式：0 = 靠左对齐 (默认)，1 = 居中对齐
    private val _lyricAlignmentFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getInt("lyric_alignment", LYRIC_ALIGN_LEFT)
    )
    val lyricAlignmentFlow: kotlinx.coroutines.flow.StateFlow<Int> = _lyricAlignmentFlow

    var lyricAlignment: Int
        get() = _lyricAlignmentFlow.value
        set(value) {
            prefs.edit().putInt("lyric_alignment", value).apply()
            _lyricAlignmentFlow.value = value
        }

    companion object {
        const val LYRIC_ALIGN_LEFT = 0
        const val LYRIC_ALIGN_CENTER = 1
    }

    private fun computeDefaultTraditional(lang: String): Boolean {
        if (prefs.contains("is_traditional_chinese")) {
            return prefs.getBoolean("is_traditional_chinese", false)
        }
        if (lang == com.linernotes.app.core.i18n.AppLanguage.ZH_TW.code) return true
        if (lang == com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code) {
            val locale = java.util.Locale.getDefault()
            val country = locale.country.uppercase()
            return locale.language.lowercase() == "zh" && (country == "TW" || country == "HK" || country == "MO")
        }
        return false
    }

    private val _appLanguageFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("app_language", com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code) ?: com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code
    )
    val appLanguageFlow: kotlinx.coroutines.flow.StateFlow<String> = _appLanguageFlow

    private val _isTraditionalChineseFlow = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getBoolean("is_traditional_chinese", computeDefaultTraditional(_appLanguageFlow.value))
    )
    val isTraditionalChineseFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isTraditionalChineseFlow

    var isTraditionalChinese: Boolean
        get() = _isTraditionalChineseFlow.value
        set(value) {
            prefs.edit().putBoolean("is_traditional_chinese", value).apply()
            _isTraditionalChineseFlow.value = value
            if (value && targetLanguage == com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code) {
                targetLanguage = com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_TW.code
            } else if (!value && targetLanguage == com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_TW.code) {
                targetLanguage = com.linernotes.app.core.i18n.TranslationTargetLanguage.ZH_CN.code
            }
        }

    var appLanguage: String
        get() = _appLanguageFlow.value
        set(value) {
            prefs.edit().putString("app_language", value).apply()
            _appLanguageFlow.value = value
            when (value) {
                com.linernotes.app.core.i18n.AppLanguage.ZH_TW.code -> {
                    isTraditionalChinese = true
                }
                com.linernotes.app.core.i18n.AppLanguage.ZH_CN.code -> {
                    isTraditionalChinese = false
                }
                com.linernotes.app.core.i18n.AppLanguage.SYSTEM.code -> {
                    val locale = java.util.Locale.getDefault()
                    val country = locale.country.uppercase()
                    if (locale.language.lowercase() == "zh" && (country == "TW" || country == "HK" || country == "MO")) {
                        isTraditionalChinese = true
                    } else {
                        isTraditionalChinese = false
                    }
                }
            }
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

    data class ManualBinding(
        val source: String,
        val sourceId: String,
        val extraKey: String = ""
    )

    fun getManualLyricBinding(title: String, artist: String): ManualBinding? {
        val key = "manual_lyric_${normalizeKey(title, artist)}"
        val str = prefs.getString(key, null) ?: return null
        val parts = str.split("|")
        if (parts.size >= 2) {
            return ManualBinding(
                source = parts[0],
                sourceId = parts[1],
                extraKey = if (parts.size > 2) parts[2] else ""
            )
        }
        return null
    }

    fun saveManualLyricBinding(title: String, artist: String, source: String, sourceId: String, extraKey: String = "") {
        val key = "manual_lyric_${normalizeKey(title, artist)}"
        prefs.edit().putString(key, "$source|$sourceId|$extraKey").apply()
    }

    fun clearManualLyricBinding(title: String, artist: String) {
        val key = "manual_lyric_${normalizeKey(title, artist)}"
        prefs.edit().remove(key).apply()
    }

    fun hasManualLyricBinding(title: String, artist: String): Boolean {
        val key = "manual_lyric_${normalizeKey(title, artist)}"
        return prefs.contains(key)
    }

    private fun normalizeKey(title: String, artist: String): String {
        return "${artist.trim().lowercase()}_${title.trim().lowercase()}".replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
    }
}

