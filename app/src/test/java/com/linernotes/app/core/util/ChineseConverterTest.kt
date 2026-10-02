package com.linernotes.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseConverterTest {

    @Test
    fun testSimplifiedToTraditional() {
        val simplified = "[00:12.34]忧伤的记忆在心中蔓延，爱与希望永远长存"
        val expected = "[00:12.34]憂傷的記憶在心中蔓延，愛與希望永遠長存"
        val result = ChineseConverter.toTraditional(simplified)
        assertEquals(expected, result)
    }

    @Test
    fun testTraditionalToSimplified() {
        val traditional = "[00:12.34]憂傷的記憶在心中蔓延，愛與希望永遠長存"
        val expected = "[00:12.34]忧伤的记忆在心中蔓延，爱与希望永远长存"
        val result = ChineseConverter.toSimplified(traditional)
        assertEquals(expected, result)
    }

    @Test
    fun testCommonNuancedWords() {
        assertEquals("着", ChineseConverter.toSimplified("著"))
        assertEquals("里", ChineseConverter.toSimplified("裡"))
        assertEquals("你", ChineseConverter.toSimplified("妳"))
        assertEquals("后", ChineseConverter.toSimplified("後"))
        assertEquals("脏", ChineseConverter.toSimplified("髒"))

        assertEquals("著", ChineseConverter.toTraditional("着"))
        assertEquals("裡", ChineseConverter.toTraditional("里"))
        assertEquals("後", ChineseConverter.toTraditional("后"))
    }

    @Test
    fun testEmptyAndNull() {
        assertEquals("", ChineseConverter.toTraditional(null))
        assertEquals("", ChineseConverter.toTraditional(""))
        assertEquals("", ChineseConverter.toSimplified(null))
        assertEquals("", ChineseConverter.toSimplified(""))
    }

    @Test
    fun testIsTraditional() {
        assertTrue(ChineseConverter.isTraditional("這是一段繁體中文歌詞"))
        assertFalse(ChineseConverter.isTraditional("这是一段简体中文歌词"))
    }
}
