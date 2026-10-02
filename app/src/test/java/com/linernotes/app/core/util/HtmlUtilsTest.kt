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
}
