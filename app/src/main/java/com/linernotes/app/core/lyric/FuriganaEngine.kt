package com.linernotes.app.core.lyric

object FuriganaEngine {
    data class FuriganaSegment(
        val text: String,
        val reading: String? = null,
        val isKanji: Boolean = false
    )

    private val commonKanjiReadings = mapOf(
        "愛" to "あい",
        "夢" to "ゆめ",
        "心" to "こころ",
        "世界" to "せかい",
        "君" to "きみ",
        "僕" to "ぼく",
        "私" to "わたし",
        "恋" to "こい",
        "空" to "そら",
        "涙" to "なみだ",
        "星" to "ほし",
        "夜" to "よる",
        "明日" to "あした",
        "時間" to "じかん",
        "言葉" to "ことば",
        "光" to "ひかり",
        "風" to "かぜ",
        "花" to "はな",
        "空" to "そら",
        "道" to "みち",
        "手" to "て",
        "目" to "め",
        "声" to "こえ",
        "歌" to "うた",
        "雨" to "あめ",
        "海" to "うみ",
        "雪" to "ゆき",
        "月" to "つき",
        "太陽" to "たいよう",
        "笑顔" to "えがお",
        "記憶" to "きおく",
        "約束" to "やくそく",
        "秘密" to "ひみつ",
        "永遠" to "えいえん",
        "運命" to "うんめい",
        "希望" to "きぼう",
        "未来" to "みらい",
        "過去" to "かこ",
        "今" to "いま",
        "奇跡" to "きせき",
        "嘘" to "うそ",
        "本当" to "ほんとう",
        "優" to "やさ",
        "誰" to "だれ",
        "何" to "なに",
        "色" to "いろ",
        "音" to "おと",
        "影" to "かげ",
        "形" to "かたち",
        "翼" to "つばさ",
        "空" to "そら",
        "中" to "なか",
        "人" to "ひと",
        "生" to "せい",
        "日" to "ひ"
    )

    private val romajiMap = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo"
    )

    fun isJapanese(text: String): Boolean {
        // 真正的日文歌词必含平假名、片假名，或含有行内注音标记如 夢(ゆめ)
        // 纯 CJK 汉字（如中文歌词）绝不可被误判为日文
        return text.any {
            it in '\u3040'..'\u309F' || // Hiragana
            it in '\u30A0'..'\u30FF'    // Katakana
        } || (text.contains("(") && parseInlineReadings(text).any { it.reading != null })
    }

    fun katakanaToHiragana(text: String): String {
        val builder = StringBuilder()
        for (char in text) {
            if (char in '\u30A1'..'\u30F6') {
                builder.append((char.code - 0x60).toChar())
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    fun hiraganaToRomaji(text: String): String {
        var result = ""
        var i = 0
        var gemination = false
        
        while (i < text.length) {
            val char = text[i].toString()
            val nextChar = if (i + 1 < text.length) text[i + 1].toString() else ""
            
            if (char == "っ") {
                gemination = true
                i++
                continue
            }
            
            val combo = char + nextChar
            if (romajiMap.containsKey(combo)) {
                val romaji = romajiMap[combo]!!
                if (gemination) {
                    result += romaji.first() + romaji
                    gemination = false
                } else {
                    result += romaji
                }
                i += 2
            } else if (romajiMap.containsKey(char)) {
                val romaji = romajiMap[char]!!
                if (gemination) {
                    result += romaji.first() + romaji
                    gemination = false
                } else {
                    result += romaji
                }
                i++
            } else {
                result += char
                gemination = false
                i++
            }
        }
        return result
    }

    fun segmentText(text: String): List<FuriganaSegment> {
        val inlineReadings = parseInlineReadings(text)
        if (inlineReadings.isNotEmpty() && inlineReadings.size > 1) { // If it actually split something
            return inlineReadings
        }

        val segments = mutableListOf<FuriganaSegment>()
        var currentKanjiRun = StringBuilder()
        var currentNonKanjiRun = StringBuilder()
        
        fun commitRuns() {
            if (currentKanjiRun.isNotEmpty()) {
                val kanjiText = currentKanjiRun.toString()
                var start = 0
                while (start < kanjiText.length) {
                    var matched = false
                    for (len in minOf(4, kanjiText.length - start) downTo 1) {
                        val sub = kanjiText.substring(start, start + len)
                        val reading = commonKanjiReadings[sub]
                        if (reading != null) {
                            segments.add(FuriganaSegment(sub, reading, true))
                            start += len
                            matched = true
                            break
                        }
                    }
                    if (!matched) {
                        val singleChar = kanjiText[start].toString()
                        segments.add(FuriganaSegment(singleChar, null, true))
                        start += 1
                    }
                }
                currentKanjiRun.clear()
            }
            if (currentNonKanjiRun.isNotEmpty()) {
                segments.add(FuriganaSegment(currentNonKanjiRun.toString(), null, false))
                currentNonKanjiRun.clear()
            }
        }

        for (char in text) {
            if (char in '\u4E00'..'\u9FFF') {
                if (currentNonKanjiRun.isNotEmpty()) commitRuns()
                currentKanjiRun.append(char)
            } else {
                if (currentKanjiRun.isNotEmpty()) commitRuns()
                currentNonKanjiRun.append(char)
            }
        }
        commitRuns()
        
        return segments
    }

    fun parseInlineReadings(text: String): List<FuriganaSegment> {
        val regex = Regex("([\\u4E00-\\u9FFF]+)\\(([\\u3040-\\u309F]+)\\)")
        val segments = mutableListOf<FuriganaSegment>()
        
        var lastIndex = 0
        val matches = regex.findAll(text)
        
        for (match in matches) {
            if (match.range.first > lastIndex) {
                segments.add(FuriganaSegment(text.substring(lastIndex, match.range.first), null, false))
            }
            segments.add(FuriganaSegment(match.groupValues[1], match.groupValues[2], true))
            lastIndex = match.range.last + 1
        }
        
        if (lastIndex < text.length) {
            segments.add(FuriganaSegment(text.substring(lastIndex), null, false))
        }
        
        if (segments.isEmpty() && text.isNotEmpty()) {
            segments.add(FuriganaSegment(text, null, false))
        }
        
        return segments
    }
}
