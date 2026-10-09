package com.linernotes.app.core.preference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FloatingLyricsPreferencesEntryPoint {
    fun floatingLyricsPreferences(): FloatingLyricsPreferences
}

/**
 * 桌面悬浮歌词全局配置与持久化偏好
 */
@Singleton
class FloatingLyricsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("floating_lyrics_prefs", Context.MODE_PRIVATE)

    // 1. 悬浮窗是否启用
    private val _isEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    var isEnabled: Boolean
        get() = _isEnabledFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            _isEnabledFlow.value = value
        }

    // 2. 是否穿透锁定（触摸不拦截）
    private val _isLockedFlow = MutableStateFlow(prefs.getBoolean(KEY_LOCKED, false))
    val isLockedFlow: StateFlow<Boolean> = _isLockedFlow.asStateFlow()

    var isLocked: Boolean
        get() = _isLockedFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_LOCKED, value).apply()
            _isLockedFlow.value = value
        }

    // 3. 是否开启双语对照
    private val _isBilingualFlow = MutableStateFlow(prefs.getBoolean(KEY_BILINGUAL, true))
    val isBilingualFlow: StateFlow<Boolean> = _isBilingualFlow.asStateFlow()

    var isBilingual: Boolean
        get() = _isBilingualFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_BILINGUAL, value).apply()
            _isBilingualFlow.value = value
        }

    // 4. 背景不透明度 (0.0f ~ 1.0f，纯净桌面歌词模式默认 0.0f 纯透明)
    private val initialBgAlpha: Float = if (!prefs.contains(KEY_BG_ALPHA_V3)) {
        // 升级至纯净无边框桌面歌词：默认设为 0.0f 纯透明全透
        prefs.edit().putFloat(KEY_BG_ALPHA_V3, 0.0f).putFloat(KEY_BG_ALPHA, 0.0f).apply()
        0.0f
    } else {
        prefs.getFloat(KEY_BG_ALPHA, 0.0f)
    }

    private val _backgroundAlphaFlow = MutableStateFlow(initialBgAlpha)
    val backgroundAlphaFlow: StateFlow<Float> = _backgroundAlphaFlow.asStateFlow()

    var backgroundAlpha: Float
        get() = _backgroundAlphaFlow.value
        set(value) {
            prefs.edit().putFloat(KEY_BG_ALPHA, value).putFloat(KEY_BG_ALPHA_V3, value).apply()
            _backgroundAlphaFlow.value = value
        }

    // 5. 字体缩放比例 (0.85f ~ 1.35f)
    private val _fontScaleFlow = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, 1.0f))
    val fontScaleFlow: StateFlow<Float> = _fontScaleFlow.asStateFlow()

    var fontScale: Float
        get() = _fontScaleFlow.value
        set(value) {
            prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()
            _fontScaleFlow.value = value
        }

    // 6. 歌词文字自定义颜色 (默认纯白 0xFFFFFFFF)
    private val _textColorFlow = MutableStateFlow(prefs.getInt(KEY_TEXT_COLOR, -1)) // -1 表示 0xFFFFFFFF 纯白
    val textColorFlow: StateFlow<Int> = _textColorFlow.asStateFlow()

    var textColor: Int
        get() = _textColorFlow.value
        set(value) {
            prefs.edit().putInt(KEY_TEXT_COLOR, value).apply()
            _textColorFlow.value = value
        }

    // 7. 时间轴延迟/提前补偿 (默认 -200ms 抵消系统与蓝牙音频缓冲延迟，消除提前跳行感)
    private val _lyricOffsetMsFlow = MutableStateFlow(prefs.getLong(KEY_OFFSET_MS, -200L))
    val lyricOffsetMsFlow: StateFlow<Long> = _lyricOffsetMsFlow.asStateFlow()

    var lyricOffsetMs: Long
        get() = _lyricOffsetMsFlow.value
        set(value) {
            prefs.edit().putLong(KEY_OFFSET_MS, value).apply()
            _lyricOffsetMsFlow.value = value
        }

    // 8. 记忆屏幕坐标 X / Y（区分横竖屏独立记忆，-1 表示初次由系统计算安全居中与下移避让挖孔）
    var lastPositionX: Int
        get() = prefs.getInt(KEY_POS_PORTRAIT_X, prefs.getInt(KEY_POS_X, -1))
        set(value) = setLastPosition(value, lastPositionY, isLandscape = false)

    var lastPositionY: Int
        get() = prefs.getInt(KEY_POS_PORTRAIT_Y, prefs.getInt(KEY_POS_Y, -1))
        set(value) = setLastPosition(lastPositionX, value, isLandscape = false)

    fun getLastPositionX(isLandscape: Boolean): Int {
        return if (isLandscape) {
            prefs.getInt(KEY_POS_LANDSCAPE_X, -1)
        } else {
            prefs.getInt(KEY_POS_PORTRAIT_X, prefs.getInt(KEY_POS_X, -1))
        }
    }

    fun getLastPositionY(isLandscape: Boolean): Int {
        return if (isLandscape) {
            prefs.getInt(KEY_POS_LANDSCAPE_Y, -1)
        } else {
            prefs.getInt(KEY_POS_PORTRAIT_Y, prefs.getInt(KEY_POS_Y, -1))
        }
    }

    fun setLastPosition(x: Int, y: Int, isLandscape: Boolean = false) {
        val editor = prefs.edit()
        if (isLandscape) {
            editor.putInt(KEY_POS_LANDSCAPE_X, x)
                .putInt(KEY_POS_LANDSCAPE_Y, y)
        } else {
            editor.putInt(KEY_POS_PORTRAIT_X, x)
                .putInt(KEY_POS_PORTRAIT_Y, y)
                .putInt(KEY_POS_X, x)
                .putInt(KEY_POS_Y, y)
        }
        editor.apply()
    }

    // 9. 歌词展示模式：0 = 居中自动折行（网易云经典全显），1 = 单行跑马灯平滑滚动
    private val _displayModeFlow = MutableStateFlow(prefs.getInt(KEY_DISPLAY_MODE, DISPLAY_MODE_WRAP))
    val displayModeFlow: StateFlow<Int> = _displayModeFlow.asStateFlow()

    var displayMode: Int
        get() = _displayModeFlow.value
        set(value) {
            prefs.edit().putInt(KEY_DISPLAY_MODE, value).apply()
            _displayModeFlow.value = value
        }

    // 10. 紧凑悬浮窗宽度 (默认 356dp，充分利用屏幕宽度展示完整歌词)
    private val _capsuleWidthDpFlow = MutableStateFlow(prefs.getInt(KEY_CAPSULE_WIDTH, 356))
    val capsuleWidthDpFlow: StateFlow<Int> = _capsuleWidthDpFlow.asStateFlow()

    var capsuleWidthDp: Int
        get() = _capsuleWidthDpFlow.value
        set(value) {
            prefs.edit().putInt(KEY_CAPSULE_WIDTH, value).apply()
            _capsuleWidthDpFlow.value = value
        }

    // 11. 桌面歌词形态：0 = 纯净桌面歌词 (网易云经典风格，无底色边框与封面，默认)，1 = 卡片胶囊模式 (带封面与气泡底板)
    private val _floatingStyleFlow = MutableStateFlow(prefs.getInt(KEY_FLOATING_STYLE, STYLE_PURE_LYRICS))
    val floatingStyleFlow: StateFlow<Int> = _floatingStyleFlow.asStateFlow()

    var floatingStyle: Int
        get() = _floatingStyleFlow.value
        set(value) {
            prefs.edit().putInt(KEY_FLOATING_STYLE, value).apply()
            _floatingStyleFlow.value = value
        }

    // 12. 应用在前台时自动隐藏悬浮窗 (防止与主界面歌词重叠遮挡，退至后台/桌面自动重新显示，默认开启)
    private val _hideWhenAppInForegroundFlow = MutableStateFlow(prefs.getBoolean(KEY_HIDE_IN_FOREGROUND, true))
    val hideWhenAppInForegroundFlow: StateFlow<Boolean> = _hideWhenAppInForegroundFlow.asStateFlow()

    var hideWhenAppInForeground: Boolean
        get() = _hideWhenAppInForegroundFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_IN_FOREGROUND, value).apply()
            _hideWhenAppInForegroundFlow.value = value
        }

    // 13. 桌面悬浮歌词对齐方式：0 = 靠左对齐，1 = 居中对齐 (默认居中)
    private val _textAlignmentFlow = MutableStateFlow(prefs.getInt(KEY_TEXT_ALIGNMENT, ALIGNMENT_CENTER))
    val textAlignmentFlow: StateFlow<Int> = _textAlignmentFlow.asStateFlow()

    var textAlignment: Int
        get() = _textAlignmentFlow.value
        set(value) {
            prefs.edit().putInt(KEY_TEXT_ALIGNMENT, value).apply()
            _textAlignmentFlow.value = value
        }

    companion object {
        const val STYLE_PURE_LYRICS = 0
        const val STYLE_CAPSULE_CARD = 1

        const val DISPLAY_MODE_WRAP = 0
        const val DISPLAY_MODE_MARQUEE = 1

        const val ALIGNMENT_LEFT = 0
        const val ALIGNMENT_CENTER = 1

        private const val KEY_ENABLED = "floating_enabled"
        private const val KEY_LOCKED = "floating_locked"
        private const val KEY_BILINGUAL = "floating_bilingual"
        private const val KEY_BG_ALPHA = "floating_bg_alpha"
        private const val KEY_BG_ALPHA_V3 = "floating_bg_alpha_v3"
        private const val KEY_FONT_SCALE = "floating_font_scale"
        private const val KEY_TEXT_COLOR = "floating_text_color"
        private const val KEY_OFFSET_MS = "floating_offset_ms"
        private const val KEY_POS_X = "floating_pos_x"
        private const val KEY_POS_Y = "floating_pos_y"
        private const val KEY_POS_PORTRAIT_X = "floating_pos_portrait_x"
        private const val KEY_POS_PORTRAIT_Y = "floating_pos_portrait_y"
        private const val KEY_POS_LANDSCAPE_X = "floating_pos_landscape_x"
        private const val KEY_POS_LANDSCAPE_Y = "floating_pos_landscape_y"
        private const val KEY_DISPLAY_MODE = "floating_display_mode"
        private const val KEY_CAPSULE_WIDTH = "floating_capsule_width"
        private const val KEY_FLOATING_STYLE = "floating_style"
        private const val KEY_HIDE_IN_FOREGROUND = "floating_hide_in_foreground"
        private const val KEY_TEXT_ALIGNMENT = "floating_text_alignment"

        fun get(context: Context): FloatingLyricsPreferences {
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                FloatingLyricsPreferencesEntryPoint::class.java
            ).floatingLyricsPreferences()
        }
    }
}
