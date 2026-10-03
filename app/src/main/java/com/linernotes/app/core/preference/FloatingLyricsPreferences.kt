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

    // 4. 背景不透明度 (0.20f ~ 1.0f，默认 0.55f 清透毛玻璃质感)
    private val _backgroundAlphaFlow = MutableStateFlow(prefs.getFloat(KEY_BG_ALPHA, 0.55f))
    val backgroundAlphaFlow: StateFlow<Float> = _backgroundAlphaFlow.asStateFlow()

    var backgroundAlpha: Float
        get() = _backgroundAlphaFlow.value
        set(value) {
            prefs.edit().putFloat(KEY_BG_ALPHA, value).apply()
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

    // 8. 记忆屏幕坐标 X / Y
    var lastPositionX: Int
        get() = prefs.getInt(KEY_POS_X, -1)
        set(value) = prefs.edit().putInt(KEY_POS_X, value).apply()

    var lastPositionY: Int
        get() = prefs.getInt(KEY_POS_Y, 220)
        set(value) = prefs.edit().putInt(KEY_POS_Y, value).apply()

    companion object {
        private const val KEY_ENABLED = "floating_enabled"
        private const val KEY_LOCKED = "floating_locked"
        private const val KEY_BILINGUAL = "floating_bilingual"
        private const val KEY_BG_ALPHA = "floating_bg_alpha"
        private const val KEY_FONT_SCALE = "floating_font_scale"
        private const val KEY_TEXT_COLOR = "floating_text_color"
        private const val KEY_OFFSET_MS = "floating_offset_ms"
        private const val KEY_POS_X = "floating_pos_x"
        private const val KEY_POS_Y = "floating_pos_y"

        fun get(context: Context): FloatingLyricsPreferences {
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                FloatingLyricsPreferencesEntryPoint::class.java
            ).floatingLyricsPreferences()
        }
    }
}
