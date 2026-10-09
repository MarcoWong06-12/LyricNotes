package com.linernotes.app.core.lyric

import java.util.regex.Pattern

/**
 * 歌词与曲目标题反审查/反和谐还原引擎 (Lyric Sanitizer / Decensoring Engine)
 *
 * 针对流媒体平台审查机制中对脏话和敏感词的掩码替换（如 B***h, *****, *************, ****ing, n****s），
 * 以及中文译文中遗留的星号乱码（如 猴嘴*************、发生什么事*****），
 * 在完全保留高品质双语翻译与时间戳的同时，毫秒级还原原版英文词汇与自然中文译文。
 */
object LyricSanitizer {

    private val PROFANITY_LIST = listOf(
        "fuck", "fucking", "fucked", "fucker", "fuckers", "fucks",
        "motherfucker", "motherfuckers", "motherfucking", "motherfucka", "motherfuckas",
        "bitch", "bitches", "bitching", "bitchy",
        "shit", "shits", "shitty", "bullshit", "horseshit", "dipshit",
        "nigga", "niggas", "niggaz", "nigger", "niggers",
        "ass", "asses", "asshole", "assholes", "badass", "jackass", "dumbass",
        "dick", "dicks", "dickhead",
        "cock", "cocks", "cocksucker", "cocksuckers",
        "pussy", "pussies",
        "cunt", "cunts",
        "bastard", "bastards",
        "slut", "sluts", "whore", "whores",
        "damn", "damned", "goddamn"
    )

    private val CENSOR_CHECK_REGEX = Regex("""[a-zA-Z]*\*+[a-zA-Z]*|\*{2,}""")
    private val WORD_TOKEN_REGEX = Regex("""[\w'’*]+""")
    private val TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")

    /**
     * 判断文本中是否包含审查掩码星号
     */
    fun hasCensorship(text: String?): Boolean {
        if (text.isNullOrBlank() || !text.contains('*')) return false
        return CENSOR_CHECK_REGEX.containsMatchIn(text)
    }

    private val PRIORITY_PROFANITIES = listOf(
        "fuck", "fucking", "fucked", "shit", "bitch", "nigga", "niggas", "motherfucker", "motherfuckers", "ass", "dick", "pussy", "cunt", "damn"
    )

    /**
     * 常见固定搭配与 Hip-Hop 经典短语正则表（实现无参考歌词时毫秒级精准解密）
     */
    private val COMMON_PHRASE_RULES = listOf(
        // King Kunta / Kendrick Lamar 及西海岸经典搭配
        Regex("""(?i)\bmonkey[\s-]+mouth[\s-]*\*{5,15}""") to "monkey-mouth motherfuckers",
        Regex("""(?i)\bwhats?\s+happenin['’]?[\s,]*\*{4,6}""") to "whats happenin' nigga",
        Regex("""(?i)\bin\s+the\s+hood[\s,]*\*{4,6}""") to "in the hood nigga",
        Regex("""(?i)\bAye[\s,]+aye[\s,]*\*{4,6}""") to "Aye aye nigga",
        Regex("""(?i)\bK\s*Dot\s+back\s+in\s+the\s+hood[\s,]*\*{4,6}""") to "K Dot back in the hood nigga",
        Regex("""(?i)\bwhere\s+you\s+when\s+I\s+was\s+walkin['’]?[\s,]*\*{4,6}""") to "where you when I was walkin' bitch",

        // 通用英语俗语与 Hip-Hop 惯用短语
        Regex("""(?i)\blife['’]?s\s+a\s+\*{4,6}""") to "life's a bitch",
        Regex("""(?i)\bson\s+of\s+a\s+\*{4,6}""") to "son of a bitch",
        Regex("""(?i)\bain['’]?t\s+that\s+a\s+\*{4,6}""") to "ain't that a bitch",
        Regex("""(?i)\bwhat\s+the\s+\*{4}""") to "what the fuck",
        Regex("""(?i)\bwho\s+the\s+\*{4}""") to "who the fuck",
        Regex("""(?i)\bwhere\s+the\s+\*{4}""") to "where the fuck",
        Regex("""(?i)\bhow\s+the\s+\*{4}""") to "how the fuck",
        Regex("""(?i)\bwhy\s+the\s+\*{4}""") to "why the fuck",
        Regex("""(?i)\bshut\s+the\s+\*{4}\s+up\b""") to "shut the fuck up",
        Regex("""(?i)\bget\s+the\s+\*{4}\s+out\b""") to "get the fuck out",
        Regex("""(?i)\bgive\s+a\s+\*{4}\b""") to "give a fuck",
        Regex("""(?i)\bdon['’]?t\s+give\s+a\s+\*{4}\b""") to "don't give a fuck",
        Regex("""(?i)\bgive\s+two\s+\*{4,6}s?\b""") to "give two fucks",
        Regex("""(?i)\bholy\s+\*{4}\b""") to "holy shit",
        Regex("""(?i)\boh\s+\*{4}\b""") to "oh shit",
        Regex("""(?i)\bpiece\s+of\s+\*{4}\b""") to "piece of shit",
        Regex("""(?i)\bfull\s+of\s+\*{4}\b""") to "full of shit",
        Regex("""(?i)\btalk\s+that\s+\*{4}\b""") to "talk that shit",
        Regex("""(?i)\bspit\s+that\s+\*{4}\b""") to "spit that shit",
        Regex("""(?i)\bsame\s+old\s+\*{4}\b""") to "same old shit",
        Regex("""(?i)\bdeep\s+\*{4}\b""") to "deep shit",
        Regex("""(?i)\btake\s+a\s+\*{4}\b""") to "take a shit",
        Regex("""(?i)\bkiss\s+my\s+\*{3}\b""") to "kiss my ass",
        Regex("""(?i)\bbad\s+\*{3}\b""") to "bad ass",
        Regex("""(?i)\bdumb\s+\*{3}\b""") to "dumb ass",
        Regex("""(?i)\bbroke\s+\*{3}\b""") to "broke ass",
        Regex("""(?i)\bcrazy\s+\*{3}\b""") to "crazy ass",
        Regex("""(?i)\bpunk\s+\*{3}\b""") to "punk ass",
        Regex("""(?i)\blame\s+\*{3}\b""") to "lame ass",
        Regex("""(?i)\bbitch\s+\*{3}\b""") to "bitch ass",
        Regex("""(?i)\bfat\s+\*{3}\b""") to "fat ass",
        Regex("""(?i)\bmy\s+\*{4,6}\b""") to "my nigga",
        Regex("""(?i)\bmy\s+\*{6,7}\b""") to "my niggas",
        Regex("""(?i)\ball\s+my\s+\*{6,7}\b""") to "all my niggas",
        Regex("""(?i)\breal\s+\*{4,6}\b""") to "real nigga",
        Regex("""(?i)\breal\s+\*{6,7}\b""") to "real niggas",
        Regex("""(?i)\bthese\s+\*{6,7}\b""") to "these niggas",
        Regex("""(?i)\bthose\s+\*{6,7}\b""") to "those niggas",
        Regex("""(?i)\beat\s+a\s+\*{4}\b""") to "eat a dick",
        Regex("""(?i)\bstraight\s+up\s+\*{7}\b""") to "straight up fucking",
        Regex("""(?i)\byou\s+\*{7}\b""") to "you fucking",
        Regex("""(?i)\b\*{4}\s+you\b""") to "fuck you",
        Regex("""(?i)\b\*{4}\s+off\b""") to "fuck off",
        Regex("""(?i)\b\*{4}\s+that\b""") to "fuck that",
        Regex("""(?i)\b\*{4}\s+with\b""") to "fuck with",
        Regex("""(?i)\b\*{4}\s+up\b""") to "fuck up",
        Regex("""(?i)\b\*{4}\s+no\b""") to "fuck no",
        Regex("""(?i)\b\*{4}\s+yeah\b""") to "fuck yeah",
        Regex("""(?i)\bmother\s*\*{6,8}\b""") to "motherfucker",
        Regex("""(?i)\bmother\s*\*{7,9}s\b""") to "motherfuckers"
    )

    /**
     * 单个词的本地字典反和谐匹配 (支持 B***h, f***ing, ****ing, s***, n**** 及纯星号长度推导)
     */
    fun matchFromDictionary(token: String): String? {
        if (!token.contains('*')) return null
        val letters = token.filter { it.isLetter() }

        // 若含有部分可见字母，使用字母通配精准匹配
        if (letters.isNotEmpty()) {
            val clean = token.lowercase()
            val regexStr = "^" + clean.replace("*", ".") + "$"
            val pattern = Pattern.compile(regexStr)

            val candidates = PROFANITY_LIST.filter { pattern.matcher(it).matches() }
            val matched = if (candidates.size == 1) {
                candidates.first()
            } else if (candidates.size > 1) {
                candidates.firstOrNull { it in PRIORITY_PROFANITIES } ?: candidates.first()
            } else {
                null
            }

            if (matched != null) {
                return when {
                    letters.all { it.isUpperCase() } -> matched.uppercase()
                    letters.first().isUpperCase() -> matched.replaceFirstChar { it.uppercase() }
                    else -> matched
                }
            }
        }

        // 若为纯星号（如 *****, *************），根据字长在音乐语境中确定性推断
        val starCount = token.count { it == '*' }
        val guessed = when {
            starCount >= 13 -> "motherfuckers"
            starCount == 12 -> "motherfucker"
            starCount == 11 -> "motherfucker"
            starCount == 10 -> "cocksucker"
            starCount == 8 || starCount == 9 -> "bullshit"
            starCount == 7 -> "fucking"
            starCount == 6 -> "niggas"
            starCount == 5 -> "nigga"
            starCount == 4 -> "fuck"
            starCount == 3 -> "ass"
            else -> null
        }

        return guessed
    }

    /**
     * 单行英文歌词或标题反和谐
     * @param censoredLine 包含掩码的行 (如 "Life's a B***h" 或 "monkey mouth ************* sittin'")
     * @param uncensoredRefLine 可选的无审查参考行 (如来自 Genius 或 LRCLIB)
     */
    fun decensorLine(censoredLine: String, uncensoredRefLine: String? = null): String {
        if (!hasCensorship(censoredLine)) return censoredLine

        var text = censoredLine

        // 阶段 1：若提供了无审查参考行，优先执行词级严格对齐替换（准确率最高）
        if (!uncensoredRefLine.isNullOrBlank()) {
            val cleanRefLine = uncensoredRefLine.replace(TIMESTAMP_REGEX, "").trim()
            val refWords = WORD_TOKEN_REGEX.findAll(cleanRefLine).map { it.value }.toList()
            if (refWords.isNotEmpty()) {
                val tsMatch = TIMESTAMP_REGEX.find(text)
                val tsPrefix = tsMatch?.value
                val stageText = if (tsPrefix != null) text.replace(TIMESTAMP_REGEX, "").trim() else text

                val sb = StringBuilder()
                var lastEnd = 0
                for ((wordIdx, wordMatch) in WORD_TOKEN_REGEX.findAll(stageText).withIndex()) {
                    sb.append(stageText.substring(lastEnd, wordMatch.range.first))
                    val word = wordMatch.value
                    if (word.contains('*')) {
                        var rep = if (wordIdx < refWords.size) {
                            refWords[wordIdx]
                        } else {
                            refWords.firstOrNull { Math.abs(it.length - word.length) <= 1 } ?: word
                        }
                        val letters = word.filter { it.isLetter() }
                        if (letters.isNotEmpty()) {
                            if (letters.all { it.isUpperCase() }) {
                                rep = rep.uppercase()
                            } else if (letters.first().isUpperCase()) {
                                rep = rep.replaceFirstChar { it.uppercase() }
                            }
                        }
                        sb.append(rep)
                    } else {
                        sb.append(word)
                    }
                    lastEnd = wordMatch.range.last + 1
                }
                if (lastEnd < stageText.length) {
                    sb.append(stageText.substring(lastEnd))
                }
                text = if (tsPrefix != null) "$tsPrefix ${sb}" else sb.toString()
            }
        }

        if (!text.contains('*')) return text

        // 阶段 2：应用固定搭配与短语规则（如 "monkey mouth *************" -> "monkey-mouth motherfuckers"）
        for ((regex, replacement) in COMMON_PHRASE_RULES) {
            text = text.replace(regex, replacement)
        }

        if (!text.contains('*')) return text

        // 阶段 3：使用本地词典及确定性长度推导单个星号词
        text = text.replace(CENSOR_CHECK_REGEX) { matchResult ->
            val tok = matchResult.value
            matchFromDictionary(tok) ?: tok
        }

        if (!text.contains('*')) return text

        // 阶段 4：纯星号长度兜底替换（杜绝任何星号遗漏）
        text = text
            .replace(Regex("""\*{12,}"""), "motherfuckers")
            .replace(Regex("""\*{10,11}"""), "motherfucker")
            .replace(Regex("""\*{7,9}"""), "fucking")
            .replace(Regex("""\*{6}"""), "niggas")
            .replace(Regex("""\*{5}"""), "nigga")
            .replace(Regex("""\*{4}"""), "fuck")
            .replace(Regex("""\*{3}"""), "ass")
            .replace(Regex("""\*{1,2}"""), "")

        return text
    }

    /**
     * 单行中文译文反和谐
     * 解决国内平台翻译中充斥的如 “猴嘴*************”、“是啊是啊*****” 等星号遗留
     */
    fun decensorChineseLine(chineseLine: String, uncensoredEnglishLine: String? = null): String {
        var result = chineseLine

        // 1. 中文常见特定搭配与俚语还原（如 猴嘴混蛋们、回到街头兄弟、闭嘴等）
        result = result
            .replace(Regex("""猴嘴\s*\*{3,}"""), "猴嘴混蛋们")
            .replace(Regex("""是啊\s*是啊\s*\*{3,}"""), "是啊是啊兄弟")
            .replace(Regex("""發生了什麼事\s*\*{3,}"""), "發生了什麼事兄弟")
            .replace(Regex("""发生了什么事\s*\*{3,}"""), "发生了什么事兄弟")
            .replace(Regex("""回到引擎[蓋盖]\s*(\*{3,}|兄弟)?"""), "回到街头兄弟")
            .replace(Regex("""他\s*\*{2,4}\s*的"""), "他妈的")
            .replace(Regex("""真\s*\*{2,4}\s*的"""), "真他妈的")
            .replace(Regex("""去\s*\*{2,4}\s*的"""), "去他妈的")
            .replace(Regex("""狗\s*\*{2,4}"""), "狗屎")
            .replace(Regex("""闭\s*\*{2,4}\s*嘴"""), "闭嘴")

        if (!result.contains('*')) return result

        // 2. 若有对应的无审查英文行，精准按英文词汇语义替换中文星号
        if (!uncensoredEnglishLine.isNullOrBlank()) {
            val engLower = uncensoredEnglishLine.lowercase()
            val replacement = when {
                engLower.contains("motherfuckers") -> "混蛋们"
                engLower.contains("motherfucker") || engLower.contains("motherfucking") -> "混蛋"
                engLower.contains("niggas") || engLower.contains("niggers") -> "兄弟们"
                engLower.contains("nigga") || engLower.contains("nigger") -> "兄弟"
                engLower.contains("bitches") -> "婊子们"
                engLower.contains("bitch") -> "婊子"
                engLower.contains("fucking") || engLower.contains("fuck") -> "他妈的"
                engLower.contains("shit") || engLower.contains("bullshit") -> "狗屎"
                engLower.contains("asshole") || engLower.contains("ass") -> "混蛋"
                engLower.contains("dick") || engLower.contains("cock") -> "混球"
                engLower.contains("pussy") -> "怂包"
                engLower.contains("damn") -> "该死"
                else -> null
            }
            if (replacement != null) {
                result = result.replace(Regex("""\*{2,}"""), replacement)
            }
        }

        if (!result.contains('*')) return result

        // 3. 中文语境常见屏蔽词兜底替换
        result = result
            .replace(Regex("""\*{10,}"""), "混蛋们")
            .replace(Regex("""\*{5,9}"""), "兄弟")
            .replace(Regex("""\*{2,4}"""), "他妈的")
            .replace(Regex("""\*+"""), "")

        return result
    }

    /**
     * 整首中文歌词反和谐
     */
    fun decensorChineseLyrics(censoredChineseLyrics: String?, uncensoredEnglishLyrics: String? = null): String? {
        if (censoredChineseLyrics.isNullOrBlank() || !censoredChineseLyrics.contains('*')) {
            return censoredChineseLyrics
        }

        val cLines = censoredChineseLyrics.lines()
        val eLines = uncensoredEnglishLyrics?.lines() ?: emptyList()

        return cLines.mapIndexed { idx, cLine ->
            val eLine = eLines.getOrNull(idx)
            decensorChineseLine(cLine, eLine)
        }.joinToString("\n")
    }

    /**
     * 歌曲标题反和谐 (如 "Life's a B***h" -> "Life's a Bitch")
     */
    fun decensorTitle(title: String, refTitle: String? = null): String {
        return decensorLine(title, refTitle)
    }

    /**
     * 基于参考行列表（如来自 Genius 注释引文列表）反和谐整首歌曲歌词
     */
    fun decensorLyrics(censoredLyrics: String, refLines: List<String>): String {
        if (!hasCensorship(censoredLyrics)) return censoredLyrics
        if (refLines.isEmpty()) return decensorLyrics(censoredLyrics, null as String?)

        val flattenedRef = refLines.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotBlank() }
        val cLines = censoredLyrics.lines()

        val sanitizedLines = cLines.map { cl ->
            val clClean = cl.replace(TIMESTAMP_REGEX, "").trim()
            if (!hasCensorship(clClean)) return@map cl

            // 在参考列表中搜寻最相似行
            val normCl = clClean.replace(Regex("""[^\w\s]"""), "").lowercase()
            val bestRef = flattenedRef.maxByOrNull { rl ->
                val normRl = rl.replace(TIMESTAMP_REGEX, "").replace(Regex("""[^\w\s]"""), "").lowercase()
                computeOverlapScore(normCl, normRl)
            }

            val chosenRef = if (bestRef != null && computeOverlapScore(normCl, bestRef.replace(Regex("""[^\w\s]"""), "").lowercase()) > 0.45f) {
                bestRef
            } else null

            decensorLine(cl, chosenRef)
        }

        return sanitizedLines.joinToString("\n")
    }

    /**
     * 整首歌曲歌词反和谐
     * @param censoredLyrics 包含审查掩码的歌词文本 (支持 LRC 时间轴格式)
     * @param uncensoredRefLyrics 可选的无审查全球参考歌词 (如 LRCLIB 返回的英文纯净歌词)
     */
    fun decensorLyrics(censoredLyrics: String, uncensoredRefLyrics: String? = null): String {
        if (!hasCensorship(censoredLyrics)) return censoredLyrics

        val cLines = censoredLyrics.lines()
        if (uncensoredRefLyrics.isNullOrBlank()) {
            return cLines.joinToString("\n") { decensorLine(it, null) }
        }

        val rLines = uncensoredRefLyrics.lines()

        // 构建参考行提取器（按时间戳优先匹配，否则按顺序匹配）
        val refMapByTimestamp = mutableMapOf<String, String>()
        val pureRefLines = mutableListOf<String>()

        for (rl in rLines) {
            val tsMatch = TIMESTAMP_REGEX.find(rl)
            if (tsMatch != null) {
                refMapByTimestamp[tsMatch.value] = rl
            }
            val cleaned = rl.replace(TIMESTAMP_REGEX, "").trim()
            if (cleaned.isNotBlank()) {
                pureRefLines.add(cleaned)
            }
        }

        var pureRefIdx = 0
        val sanitizedLines = cLines.map { cl ->
            val tsMatch = TIMESTAMP_REGEX.find(cl)
            val refLine = if (tsMatch != null && refMapByTimestamp.containsKey(tsMatch.value)) {
                refMapByTimestamp[tsMatch.value]
            } else if (cl.replace(TIMESTAMP_REGEX, "").trim().isNotBlank() && pureRefIdx < pureRefLines.size) {
                pureRefLines[pureRefIdx++]
            } else {
                null
            }

            decensorLine(cl, refLine)
        }

        return sanitizedLines.joinToString("\n")
    }

    private fun computeOverlapScore(s1: String, s2: String): Float {
        val w1 = s1.split(Regex("""\s+""")).filter { it.length > 2 }.toSet()
        val w2 = s2.split(Regex("""\s+""")).filter { it.length > 2 }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return 0f
        val intersection = w1.intersect(w2).size
        return (2f * intersection) / (w1.size + w2.size)
    }
}
