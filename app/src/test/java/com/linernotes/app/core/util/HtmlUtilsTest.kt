package com.linernotes.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlUtilsTest {

    @Test
    fun testNamedEntities() {
        assertEquals("I'm so sorry", HtmlUtils.unescapeHtml("I&#39;m so sorry"))
        assertEquals("you're", HtmlUtils.unescapeHtml("you&rsquo;re"))
        assertEquals("\"Hello\"", HtmlUtils.unescapeHtml("&quot;Hello&quot;"))
        assertEquals("Rock & Roll", HtmlUtils.unescapeHtml("Rock &amp; Roll"))
        assertEquals("1 < 2 & 3 > 2", HtmlUtils.unescapeHtml("1 &lt; 2 &amp; 3 &gt; 2"))
        assertEquals("hello world", HtmlUtils.unescapeHtml("hello&nbsp;world"))
        assertEquals("em—dash and en–dash", HtmlUtils.unescapeHtml("em&mdash;dash and en&ndash;dash"))
    }

    @Test
    fun testHexAndNumericEntities() {
        assertEquals("It's alive", HtmlUtils.unescapeHtml("It&#x27;s alive"))
        assertEquals("A", HtmlUtils.unescapeHtml("&#65;"))
    }

    @Test
    fun testBlankOrNormal() {
        assertEquals("", HtmlUtils.unescapeHtml(""))
        assertEquals("Pure text", HtmlUtils.unescapeHtml("Pure text"))
    }

    @Test
    fun testGeniusTagsAndEntityCleaning() {
        val input = "Here is an artist <a href=\"https://genius.com/artist\">Kendrick</a> andr<e:1> with &amp;#039;King Kunta&amp;#039;"
        val cleaned = HtmlUtils.cleanPlainText(input)
        assertEquals("Here is an artist Kendrick with 'King Kunta'", cleaned)
    }

    @Test
    fun testTranslationOutputCleaning() {
        val rawTranslation = "这首歌由制作人录制 andr<e:1>，并且在&quot;康普顿&quot;完成<p>第二段</p>"
        val cleaned = HtmlUtils.cleanTranslationOutput(rawTranslation)
        assertEquals("这首歌由制作人录制，并且在\"康普顿\"完成\n\n第二段", cleaned)
    }
}

