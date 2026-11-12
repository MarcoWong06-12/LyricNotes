package com.linernotes.app.core.preference

import android.content.Context
import com.linernotes.app.core.i18n.AppLanguage
import com.linernotes.app.core.i18n.TranslationTargetLanguage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AiPreferencesEntryPoint {
    fun aiPreferences(): AiPreferences
}

/**
 * 应用全局配置偏好（界面多语言、目标翻译语种、在线歌词源与视觉排版偏好）
 */
@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 异步预热磁盘 XML 缓存，避免后续 UI 线程读取阻塞
        preloadScope.launch {
            runCatching { prefs.all }
        }
    }

    private val _themeModeFlow = MutableStateFlow(
        prefs.getString("theme_mode", ThemeMode.SYSTEM.code) ?: ThemeMode.SYSTEM.code
    )
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    var themeMode: String
        get() = _themeModeFlow.value
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            _themeModeFlow.value = value
        }

    // 纯黑 OLED 极暗模式 (AMOLED Pure Black)
    private val _isAmoledModeFlow = MutableStateFlow(
        prefs.getBoolean("is_amoled_mode", false)
    )
    val isAmoledModeFlow: StateFlow<Boolean> = _isAmoledModeFlow.asStateFlow()

    var isAmoledMode: Boolean
        get() = _isAmoledModeFlow.value
        set(value) {
            prefs.edit().putBoolean("is_amoled_mode", value).apply()
            _isAmoledModeFlow.value = value
        }

    // 全屏歌词对齐方式：0 = 靠左对齐 (默认)，1 = 居中对齐
    private val _lyricAlignmentFlow = MutableStateFlow(
        prefs.getInt("lyric_alignment", LYRIC_ALIGN_LEFT)
    )
    val lyricAlignmentFlow: StateFlow<Int> = _lyricAlignmentFlow.asStateFlow()

    var lyricAlignment: Int
        get() = _lyricAlignmentFlow.value
        set(value) {
            prefs.edit().putInt("lyric_alignment", value).apply()
            _lyricAlignmentFlow.value = value
        }

    // 全屏歌词字体字号大小：0 = 紧凑 (26sp), 1 = 标准 (34sp, 默认), 2 = 大号 (38sp), 3 = 特大 (44sp)
    private val _lyricTextSizeFlow = MutableStateFlow(
        prefs.getInt("lyric_text_size", LYRIC_TEXT_SIZE_STANDARD)
    )
    val lyricTextSizeFlow: StateFlow<Int> = _lyricTextSizeFlow.asStateFlow()

    var lyricTextSize: Int
        get() = _lyricTextSizeFlow.value
        set(value) {
            prefs.edit().putInt("lyric_text_size", value).apply()
            _lyricTextSizeFlow.value = value
        }

    // 动态流体极光背景
    private val _isDynamicAuroraEnabledFlow = MutableStateFlow(
        prefs.getBoolean("is_dynamic_aurora_enabled", true)
    )
    val isDynamicAuroraEnabledFlow: StateFlow<Boolean> = _isDynamicAuroraEnabledFlow.asStateFlow()

    var isDynamicAuroraEnabled: Boolean
        get() = _isDynamicAuroraEnabledFlow.value
        set(value) {
            prefs.edit().putBoolean("is_dynamic_aurora_enabled", value).apply()
            _isDynamicAuroraEnabledFlow.value = value
        }

    // 逐字流光歌词动效
    private val _isWordByWordEnabledFlow = MutableStateFlow(
        prefs.getBoolean("is_word_by_word_enabled", true)
    )
    val isWordByWordEnabledFlow: StateFlow<Boolean> = _isWordByWordEnabledFlow.asStateFlow()

    var isWordByWordEnabled: Boolean
        get() = _isWordByWordEnabledFlow.value
        set(value) {
            prefs.edit().putBoolean("is_word_by_word_enabled", value).apply()
            _isWordByWordEnabledFlow.value = value
        }

    // 歌词脏字反审查/反和谐
    private val _isDeCensorEnabledFlow = MutableStateFlow(
        prefs.getBoolean("is_decensor_enabled", true)
    )
    val isDeCensorEnabledFlow: StateFlow<Boolean> = _isDeCensorEnabledFlow.asStateFlow()

    var isDeCensorEnabled: Boolean
        get() = _isDeCensorEnabledFlow.value
        set(value) {
            prefs.edit().putBoolean("is_decensor_enabled", value).apply()
            _isDeCensorEnabledFlow.value = value
        }

    companion object {
        const val LYRIC_ALIGN_LEFT = 0
        const val LYRIC_ALIGN_CENTER = 1
        const val LYRIC_TEXT_SIZE_COMPACT = 0
        const val LYRIC_TEXT_SIZE_STANDARD = 1
        const val LYRIC_TEXT_SIZE_LARGE = 2
        const val LYRIC_TEXT_SIZE_EXTRA_LARGE = 3

        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_IS_TRADITIONAL_CHINESE = "is_traditional_chinese"
        private const val KEY_TARGET_LANGUAGE = "target_language"

        private val NORMALIZE_KEY_REGEX = Regex("[^a-zA-Z0-9\u4e00-\u9fa5]")
    }

    private fun isTraditionalSystemLocale(): Boolean {
        val locale = java.util.Locale.getDefault()
        val country = locale.country.uppercase()
        return locale.language.lowercase() == "zh" && (country == "TW" || country == "HK" || country == "MO")
    }

    private fun computeDefaultTraditional(lang: String): Boolean {
        if (lang == AppLanguage.ZH_TW.code) return true
        if (lang == AppLanguage.SYSTEM.code) return isTraditionalSystemLocale()
        return false
    }

    private fun computeDefaultTargetLanguage(): String =
        if (isTraditionalSystemLocale()) TranslationTargetLanguage.ZH_TW.code else TranslationTargetLanguage.ZH_CN.code

    private val _appLanguageFlow = MutableStateFlow(
        prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code
    )
    val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    // 仅在尚未显式保存过繁简偏好时按当前语言/系统区域推算默认值；之后各选项完全解耦互不覆盖
    private val _isTraditionalChineseFlow = MutableStateFlow(
        if (prefs.contains(KEY_IS_TRADITIONAL_CHINESE)) {
            prefs.getBoolean(KEY_IS_TRADITIONAL_CHINESE, false)
        } else {
            computeDefaultTraditional(_appLanguageFlow.value)
        }
    )
    val isTraditionalChineseFlow: StateFlow<Boolean> = _isTraditionalChineseFlow.asStateFlow()

    var isTraditionalChinese: Boolean
        get() = _isTraditionalChineseFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_IS_TRADITIONAL_CHINESE, value).apply()
            _isTraditionalChineseFlow.value = value
        }

    var appLanguage: String
        get() = _appLanguageFlow.value
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()
            _appLanguageFlow.value = value
        }

    private val _targetLanguageFlow = MutableStateFlow(
        if (prefs.contains(KEY_TARGET_LANGUAGE)) {
            prefs.getString(KEY_TARGET_LANGUAGE, TranslationTargetLanguage.ZH_CN.code)
                ?: TranslationTargetLanguage.ZH_CN.code
        } else {
            computeDefaultTargetLanguage()
        }
    )
    val targetLanguageFlow: StateFlow<String> = _targetLanguageFlow.asStateFlow()

    var targetLanguage: String
        get() = _targetLanguageFlow.value
        set(value) {
            prefs.edit().putString(KEY_TARGET_LANGUAGE, value).apply()
            _targetLanguageFlow.value = value
        }

    private val _geniusTokenFlow = MutableStateFlow(
        prefs.getString("genius_token", "") ?: ""
    )
    val geniusTokenFlow: StateFlow<String> = _geniusTokenFlow.asStateFlow()

    var geniusToken: String
        get() = _geniusTokenFlow.value
        set(value) {
            val trimmed = value.trim()
            prefs.edit().putString("genius_token", trimmed).apply()
            _geniusTokenFlow.value = trimmed
        }

    private val _lyricsSourceFlow = MutableStateFlow(
        prefs.getString("lyrics_source", LyricsSourcePreference.AUTO_FIRST.code)
            ?: LyricsSourcePreference.AUTO_FIRST.code
    )
    val lyricsSourceFlow: StateFlow<String> = _lyricsSourceFlow.asStateFlow()

    var lyricsSource: String
        get() = _lyricsSourceFlow.value
        set(value) {
            prefs.edit().putString("lyrics_source", value).apply()
            _lyricsSourceFlow.value = value
        }

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
        return "${artist.trim().lowercase()}_${title.trim().lowercase()}".replace(NORMALIZE_KEY_REGEX, "")
    }
}

