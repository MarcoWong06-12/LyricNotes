package com.linernotes.app.core.lyric

import com.linernotes.app.core.util.HtmlUtils
import java.util.regex.Pattern

/**
 * 歌词与曲目标题反审查/反和谐还原引擎 (Lyric Sanitizer / Decensoring Engine)
 *
 * 针对流媒体平台审查机制中对脏话和敏感词的掩码替换（如 B***h, *****, *************, ****ing, n****s, mother****er），
 * 电台审查净版替换（如 little sucker / mother trucker -> motherfucker, what the freak -> what the fuck），
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

    private val PROFANITY_ROOTS = listOf(
        "fuck", "shit", "bitch", "nigga", "nigg", "ass", "cunt", "dick", "cock", "pussy", "bastard", "damn"
    )

    /**
     * 常见电台审查净版词组到原版脏话的还原映射表 (Radio Clean Euphemism Reversal)
     */
    private val RADIO_CLEAN_PAIRS = listOf(
        Regex("""(?i)\blittle\s+sucker\b""") to "motherfucker",
        Regex("""(?i)\bmother\s+trucker\b""") to "motherfucker",
        Regex("""(?i)\bmother\s+sucker\b""") to "motherfucker",
        Regex("""(?i)\bmonster\s+monster\b""") to "motherfucker",
        Regex("""(?i)\bbrother\s+brother\b""") to "motherfucker",
        Regex("""(?i)\bwhat\s+the\s+freak\b""") to "what the fuck",
        Regex("""(?i)\bwho\s+the\s+freak\b""") to "who the fuck",
        Regex("""(?i)\bwhere\s+the\s+freak\b""") to "where the fuck",
        Regex("""(?i)\bhow\s+the\s+freak\b""") to "how the fuck",
        Regex("""(?i)\bwhy\s+the\s+freak\b""") to "why the fuck",
        Regex("""(?i)\bshut\s+the\s+freak\s+up\b""") to "shut the fuck up",
        Regex("""(?i)\bget\s+the\s+freak\s+out\b""") to "get the fuck out",
        Regex("""(?i)\bdon['’]?t\s+give\s+a\s+freak\b""") to "don't give a fuck",
        Regex("""(?i)\bgive\s+a\s+freak\b""") to "give a fuck",
        Regex("""(?i)\bas\s+freak\b""") to "as fuck"
    )

    private val RADIO_CLEAN_DETECTION_REGEX = Regex(
        """(?i)\b(?:little\s+sucker|mother\s+trucker|mother\s+sucker|shut\s+the\s+freak\s+up|what\s+the\s+freak|give\s+a\s+freak|don['’]?t\s+embarrass\s+me[\s,]+(?:little|little\s+sucker))\b"""
    )

    private val CENSOR_CHECK_REGEX = Regex("""[a-zA-Z]*\*+[a-zA-Z]*|\*{2,}""")
    private val WORD_TOKEN_REGEX = Regex("""[\w'’*]+""")
    private val TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?\]""")

    /**
     * 判断文本中是否包含审查掩码星号或电台审查净版短语
     */
    fun hasCensorship(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.contains('*')) return CENSOR_CHECK_REGEX.containsMatchIn(text)
        return hasRadioCleanPhrases(text)
    }

    fun hasRadioCleanPhrases(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return RADIO_CLEAN_DETECTION_REGEX.containsMatchIn(text)
    }

    private val PRIORITY_PROFANITIES = listOf(
        "fuck", "fucking", "fucked", "shit", "bitch", "nigga", "niggas", "motherfucker", "motherfuckers", "ass", "dick", "pussy", "cunt", "damn"
    )

    /**
     * 常见固定搭配与经典流行/Hip-Hop 短语正则表（实现毫秒级精准解密）
     */
    private val COMMON_PHRASE_RULES = listOf(
        // Sabrina Carpenter - Please Please Please 及流行副歌惯用语
        Regex("""(?i)\bdon['’]?t\s+embarrass\s+me[\s,]*(?:mother\*+ers?|mother\s*trucker|little\s+sucker|little|\*+)""") to "don't embarrass me, motherfucker",
        Regex("""(?i)\bembarrass\s+me[\s,]*(?:mother\*+ers?|mother\s*trucker|little\s+sucker|little|\*+)""") to "embarrass me, motherfucker",

        // Motherfucker 各类掩码与变体全覆盖
        Regex("""(?i)\bmother\s*\*+ers\b""") to "motherfuckers",
        Regex("""(?i)\bmother\s*\*+er\b""") to "motherfucker",
        Regex("""(?i)\bmother\s*\*+ing\b""") to "motherfucking",
        Regex("""(?i)\bmother\s*\*+as\b""") to "motherfuckas",
        Regex("""(?i)\bmother\s*\*+a\b""") to "motherfucka",
        Regex("""(?i)\bmother\s*\*+\b""") to "motherfucker",
        Regex("""(?i)\bmother\s*(?<!\*)\*{4,14}(?!\*)""") to "motherfucker",

        // King Kunta / Kendrick Lamar 及西海岸经典搭配
        Regex("""(?i)\bmonkey[\s-]+mouth[\s-]*(?<!\*)\*{5,15}(?!\*)""") to "monkey-mouth motherfuckers",
        Regex("""(?i)\bwhats?\s+happenin['’]?[\s,]*(?<!\*)\*{4,6}(?!\*)""") to "whats happenin' nigga",
        Regex("""(?i)\bin\s+the\s+hood[\s,]*(?<!\*)\*{4,6}(?!\*)""") to "in the hood nigga",
        Regex("""(?i)\bAye[\s,]+aye[\s,]*(?<!\*)\*{4,6}(?!\*)""") to "Aye aye nigga",
        Regex("""(?i)\bK\s*Dot\s+back\s+in\s+the\s+hood[\s,]*(?<!\*)\*{4,6}(?!\*)""") to "K Dot back in the hood nigga",
        Regex("""(?i)\bwhere\s+you\s+when\s+I\s+was\s+walkin['’]?[\s,]*(?<!\*)\*{4,6}(?!\*)""") to "where you when I was walkin' bitch",

        // 通用英语俗语与 Hip-Hop 惯用短语 - Bitch / Fuck / Shit 动宾搭配
        Regex("""(?i)\blife['’]?s\s+a\s+(?<!\*)\*{4,6}(?!\*)""") to "life's a bitch",
        Regex("""(?i)\bson\s+of\s+a\s+(?<!\*)\*{4,6}(?!\*)""") to "son of a bitch",
        Regex("""(?i)\bain['’]?t\s+that\s+a\s+(?<!\*)\*{4,6}(?!\*)""") to "ain't that a bitch",
        Regex("""(?i)\bwhat\s+the\s+(?<!\*)\*{4}(?!\*)""") to "what the fuck",
        Regex("""(?i)\bwho\s+the\s+(?<!\*)\*{4}(?!\*)""") to "who the fuck",
        Regex("""(?i)\bwhere\s+the\s+(?<!\*)\*{4}(?!\*)""") to "where the fuck",
        Regex("""(?i)\bhow\s+the\s+(?<!\*)\*{4}(?!\*)""") to "how the fuck",
        Regex("""(?i)\bwhy\s+the\s+(?<!\*)\*{4}(?!\*)""") to "why the fuck",
        Regex("""(?i)\bshut\s+the\s+(?<!\*)\*{4}\s+up\b""") to "shut the fuck up",
        Regex("""(?i)\bget\s+the\s+(?<!\*)\*{4}\s+out\b""") to "get the fuck out",
        Regex("""(?i)\bgive\s+a\s+(?<!\*)\*{4}(?!\*)""") to "give a fuck",
        Regex("""(?i)\bdon['’]?t\s+give\s+a\s+(?<!\*)\*{4}(?!\*)""") to "don't give a fuck",
        Regex("""(?i)\bgive\s+two\s+(?<!\*)\*{4,6}s?(?!\*)""") to "give two fucks",

        // "that shit" 及其衍生的各类动宾短语 (极其高频，如 Drake Family Matters: "get that shit down")
        Regex("""(?i)\bget\s+that\s+(?<!\*)\*{4}(?!\*)""") to "get that shit",
        Regex("""(?i)\bget\s+this\s+(?<!\*)\*{4}(?!\*)""") to "get this shit",
        Regex("""(?i)\btalk(?:ing)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "talk that shit",
        Regex("""(?i)\bspit(?:ting)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "spit that shit",
        Regex("""(?i)\bhear(?:d)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "heard that shit",
        Regex("""(?i)\bplay\s+that\s+(?<!\*)\*{4}(?!\*)""") to "play that shit",
        Regex("""(?i)\bstop\s+that\s+(?<!\*)\*{4}(?!\*)""") to "stop that shit",
        Regex("""(?i)\bwith\s+that\s+(?<!\*)\*{4}(?!\*)""") to "with that shit",
        Regex("""(?i)\bwith\s+this\s+(?<!\*)\*{4}(?!\*)""") to "with this shit",
        Regex("""(?i)\bon\s+that\s+(?<!\*)\*{4}(?!\*)""") to "on that shit",
        Regex("""(?i)\bon\s+this\s+(?<!\*)\*{4}(?!\*)""") to "on this shit",
        Regex("""(?i)\ball\s+that\s+(?<!\*)\*{4}(?!\*)""") to "all that shit",
        Regex("""(?i)\ball\s+this\s+(?<!\*)\*{4}(?!\*)""") to "all this shit",
        Regex("""(?i)\bthat\s+(?<!\*)\*{4}(?!\*)""") to "that shit",
        Regex("""(?i)\bthis\s+(?<!\*)\*{4}(?!\*)""") to "this shit",
        Regex("""(?i)\bsome\s+(?<!\*)\*{4}(?!\*)""") to "some shit",
        Regex("""(?i)\bon\s+some\s+(?<!\*)\*{4}(?!\*)""") to "on some shit",

        Regex("""(?i)\bholy\s+(?<!\*)\*{4}(?!\*)""") to "holy shit",
        Regex("""(?i)\boh\s+(?<!\*)\*{4}(?!\*)""") to "oh shit",
        Regex("""(?i)\bpiece\s+of\s+(?<!\*)\*{4}(?!\*)""") to "piece of shit",
        Regex("""(?i)\bfull\s+of\s+(?<!\*)\*{4}(?!\*)""") to "full of shit",
        Regex("""(?i)\bsame\s+old\s+(?<!\*)\*{4}(?!\*)""") to "same old shit",
        Regex("""(?i)\bsame\s+(?<!\*)\*{4}(?!\*)""") to "same shit",
        Regex("""(?i)\bdeep\s+(?<!\*)\*{4}(?!\*)""") to "deep shit",
        Regex("""(?i)\btake\s+a\s+(?<!\*)\*{4}(?!\*)""") to "take a shit",
        Regex("""(?i)\breal\s+(?<!\*)\*{4}(?!\*)""") to "real shit",
        Regex("""(?i)\bno\s+(?<!\*)\*{4}(?!\*)""") to "no shit",
        Regex("""(?i)\bain['’]?t\s+(?<!\*)\*{4}(?!\*)""") to "ain't shit",
        Regex("""(?i)\bain['’]?t\s+on\s+(?<!\*)\*{4}(?!\*)""") to "ain't on shit",
        Regex("""(?i)\bfeel\s+like\s+(?<!\*)\*{4}(?!\*)""") to "feel like shit",
        Regex("""(?i)\blike\s+(?<!\*)\*{4}(?!\*)""") to "like shit",
        Regex("""(?i)\beat\s+(?<!\*)\*{4}(?!\*)""") to "eat shit",
        Regex("""(?i)\bworth\s+(?<!\*)\*{4}(?!\*)""") to "worth shit",
        Regex("""(?i)\bworth\s+a\s+(?<!\*)\*{4}(?!\*)""") to "worth a shit",

        Regex("""(?i)\bkiss\s+my\s+(?<!\*)\*{3}(?!\*)""") to "kiss my ass",
        Regex("""(?i)\bbad\s+(?<!\*)\*{3}(?!\*)""") to "bad ass",
        Regex("""(?i)\bdumb\s+(?<!\*)\*{3}(?!\*)""") to "dumb ass",
        Regex("""(?i)\bbroke\s+(?<!\*)\*{3}(?!\*)""") to "broke ass",
        Regex("""(?i)\bcrazy\s+(?<!\*)\*{3}(?!\*)""") to "crazy ass",
        Regex("""(?i)\bpunk\s+(?<!\*)\*{3}(?!\*)""") to "punk ass",
        Regex("""(?i)\blame\s+(?<!\*)\*{3}(?!\*)""") to "lame ass",
        Regex("""(?i)\bbitch\s+(?<!\*)\*{3}(?!\*)""") to "bitch ass",
        Regex("""(?i)\bfat\s+(?<!\*)\*{3}(?!\*)""") to "fat ass",
        Regex("""(?i)\bmy\s+(?<!\*)\*{4,6}(?!\*)""") to "my nigga",
        Regex("""(?i)\bmy\s+(?<!\*)\*{6,7}(?!\*)""") to "my niggas",
        Regex("""(?i)\ball\s+my\s+(?<!\*)\*{6,7}(?!\*)""") to "all my niggas",
        Regex("""(?i)\breal\s+(?<!\*)\*{5,6}(?!\*)""") to "real nigga",
        Regex("""(?i)\breal\s+(?<!\*)\*{6,7}(?!\*)""") to "real niggas",
        Regex("""(?i)\bthese\s+(?<!\*)\*{6,7}(?!\*)""") to "these niggas",
        Regex("""(?i)\bthose\s+(?<!\*)\*{6,7}(?!\*)""") to "those niggas",
        Regex("""(?i)\beat\s+a\s+(?<!\*)\*{4}(?!\*)""") to "eat a dick",
        Regex("""(?i)\bstraight\s+up\s+(?<!\*)\*{7}(?!\*)""") to "straight up fucking",
        Regex("""(?i)\byou\s+(?<!\*)\*{7}(?!\*)""") to "you fucking",
        Regex("""(?i)(?<!\*)\*{4}\s+you\b""") to "fuck you",
        Regex("""(?i)(?<!\*)\*{4}\s+off\b""") to "fuck off",
        Regex("""(?i)(?<!\*)\*{4}\s+that\b""") to "fuck that",
        Regex("""(?i)(?<!\*)\*{4}\s+with\b""") to "fuck with",
        Regex("""(?i)(?<!\*)\*{4}\s+up\b""") to "fuck up",
        Regex("""(?i)(?<!\*)\*{4}\s+no\b""") to "fuck no",
        Regex("""(?i)(?<!\*)\*{4}\s+yeah\b""") to "fuck yeah",
        Regex("""(?i)\bas\s+(?<!\*)\*{4}(?!\*)""") to "as fuck"
    )

    private fun preserveCase(template: String, word: String): String {
        val letters = template.filter { it.isLetter() }
        return when {
            letters.isNotEmpty() && letters.all { it.isUpperCase() } -> word.uppercase()
            letters.isNotEmpty() && letters.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
            template.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
    }

    /**
     * 单个词的本地字典反和谐匹配 (支持 mother****er, B***h, f***ing, ****ing, s***, n**** 及纯星号长度推导)
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
                return preserveCase(token, matched)
            }
        }

        // 若为纯星号（如 *****, *************），根据字长在音乐语境中确定性推断
        val starCount = token.count { it == '*' }
        val guessed = when {
            starCount >= 13 -> "motherfuckers"
            starCount == 12 || starCount == 11 -> "motherfucker"
            starCount == 10 -> "cocksucker"
            starCount == 8 || starCount == 9 -> "bullshit"
            starCount == 7 -> "fucking"
            starCount == 6 -> "niggas"
            starCount == 5 -> "nigga"
            starCount == 4 -> "fuck"
            starCount == 3 -> "ass"
            else -> null
        }

        return guessed?.let { preserveCase(token, it) }
    }

    /**
     * 单行英文歌词或标题反和谐
     * @param censoredLine 包含掩码或审查用语的行 (如 "I beg you, don't embarrass me, mother****er, oh")
     * @param uncensoredRefLine 可选的无审查参考行 (如来自 LRCLIB 或 Genius)
     * @param translationHint 可选的该行中文翻译线索 (如 "求妳了 別讓我難堪 混蛋 噢")
     */
    fun decensorLine(
        censoredLine: String,
        uncensoredRefLine: String? = null,
        translationHint: String? = null
    ): String {
        val unescaped = HtmlUtils.unescapeHtml(censoredLine)
        if (!hasCensorship(unescaped)) return unescaped

        val tsMatch = TIMESTAMP_REGEX.find(unescaped)
        val tsPrefix = tsMatch?.value
        var stageText = if (tsPrefix != null) unescaped.replace(TIMESTAMP_REGEX, "").trim() else unescaped

        // 阶段 1：优先匹配已知高置信度经典短语规则 (如 Sabrina Carpenter 标志性副歌、monkey-mouth motherfuckers、get that shit 等)
        for ((regex, replacement) in COMMON_PHRASE_RULES) {
            stageText = stageText.replace(regex, replacement)
        }

        // 阶段 2：针对包含部分字母的掩码词 (如 mother****er, b***h, s***, ****ing, n****s)，
        // 本地词典匹配具有绝对语义权威，绝不容许被未经校验的非脏话参考词（如电台版 little sucker 中的 "little"）错误覆盖！
        if (stageText.contains('*')) {
            stageText = stageText.replace(CENSOR_CHECK_REGEX) { matchResult ->
                val tok = matchResult.value
                val letters = tok.filter { it.isLetter() }
                if (letters.isNotEmpty()) {
                    matchFromDictionary(tok) ?: tok
                } else {
                    tok
                }
            }
        }

        // 阶段 3：若仍有纯星号（如 ****, *************），且提供了参考行，执行安全锚点对齐与电台反和谐校验
        if (stageText.contains('*') && !uncensoredRefLine.isNullOrBlank()) {
            stageText = alignWithReferenceSafe(stageText, uncensoredRefLine)
        }

        // 阶段 4：若仍有星号，结合该行的中文译文线索交叉推导
        // 例如：中文行中出现 “混蛋” 且英文行长度 10-13 星号 -> 确定为 motherfucker
        if (stageText.contains('*') && !translationHint.isNullOrBlank()) {
            stageText = resolveFromTranslationHint(stageText, translationHint)
        }

        // 阶段 5：剩余未知单个星号词由词典/长度确定性推导
        if (stageText.contains('*')) {
            stageText = stageText.replace(CENSOR_CHECK_REGEX) { matchResult ->
                val tok = matchResult.value
                matchFromDictionary(tok) ?: tok
            }
        }

        // 阶段 6：纯星号长度兜底替换（杜绝任何星号遗漏）
        if (stageText.contains('*')) {
            stageText = stageText
                .replace(Regex("""\*{12,}"""), "motherfuckers")
                .replace(Regex("""\*{10,11}"""), "motherfucker")
                .replace(Regex("""\*{7,9}"""), "fucking")
                .replace(Regex("""\*{6}"""), "niggas")
                .replace(Regex("""\*{5}"""), "nigga")
                .replace(Regex("""\*{4}"""), "fuck")
                .replace(Regex("""\*{3}"""), "ass")
                .replace(Regex("""\*{1,2}"""), "")
        }

        // 阶段 7：还原残留的电台审查净版短语 (如 little sucker -> motherfucker, what the freak -> what the fuck)
        for ((regex, replacement) in RADIO_CLEAN_PAIRS) {
            stageText = stageText.replace(regex, replacement)
        }

        return if (tsPrefix != null) "$tsPrefix $stageText" else stageText
    }

    /**
     * 安全的参考行对齐机制：
     * 1. 采用前后非星号词作为锚点定位候选词切片；
     * 2. 候选词必须命中 PROFANITY_LIST 或属于电台替代短语，严禁使用如 "little", "blue" 等普通词覆盖星号；
     * 3. 杜绝因电台版词数不一致（如 mother****er 对应 little sucker 导致多出一个词）产生的下标错位。
     */
    private fun alignWithReferenceSafe(stageText: String, refLine: String): String {
        val cleanRefLine = refLine.replace(TIMESTAMP_REGEX, "").trim()
        val refWords = WORD_TOKEN_REGEX.findAll(cleanRefLine).map { it.value }.toList()
        if (refWords.isEmpty()) return stageText

        val stageMatches = WORD_TOKEN_REGEX.findAll(stageText).toList()
        val sb = StringBuilder()
        var lastEnd = 0

        for ((idx, wordMatch) in stageMatches.withIndex()) {
            sb.append(stageText.substring(lastEnd, wordMatch.range.first))
            val word = wordMatch.value

            if (word.contains('*')) {
                val prevAnchor = stageMatches.subList(0, idx).lastOrNull { !it.value.contains('*') }?.value
                val nextAnchor = stageMatches.subList(idx + 1, stageMatches.size).firstOrNull { !it.value.contains('*') }?.value

                val candidate = resolveCandidateFromRef(word, prevAnchor, nextAnchor, refWords, idx)
                if (candidate != null) {
                    sb.append(preserveCase(word, candidate))
                } else {
                    sb.append(word)
                }
            } else {
                sb.append(word)
            }
            lastEnd = wordMatch.range.last + 1
        }
        if (lastEnd < stageText.length) {
            sb.append(stageText.substring(lastEnd))
        }
        return sb.toString()
    }

    private fun resolveCandidateFromRef(
        maskedWord: String,
        prevAnchor: String?,
        nextAnchor: String?,
        refWords: List<String>,
        wordIdx: Int
    ): String? {
        // 1. 基于前后锚点搜寻候选词切片
        val prevIdx = if (prevAnchor != null) {
            refWords.indexOfFirst { it.equals(prevAnchor, ignoreCase = true) }
        } else -1
        val nextIdx = if (nextAnchor != null && prevIdx != -1) {
            refWords.subList(prevIdx + 1, refWords.size).indexOfFirst { it.equals(nextAnchor, ignoreCase = true) }.let {
                if (it != -1) prevIdx + 1 + it else -1
            }
        } else if (nextAnchor != null) {
            refWords.indexOfFirst { it.equals(nextAnchor, ignoreCase = true) }
        } else -1

        val candidateSlice = if (prevIdx != -1 && nextIdx != -1 && nextIdx > prevIdx + 1) {
            refWords.subList(prevIdx + 1, nextIdx)
        } else if (prevIdx != -1 && prevIdx + 1 < refWords.size) {
            refWords.subList(prevIdx + 1, (prevIdx + 3).coerceAtMost(refWords.size))
        } else if (nextIdx != -1 && nextIdx > 0) {
            refWords.subList((nextIdx - 2).coerceAtLeast(0), nextIdx)
        } else if (wordIdx < refWords.size) {
            listOf(refWords[wordIdx])
        } else {
            emptyList()
        }

        if (candidateSlice.isEmpty()) return null

        // 校验 A：检查候选切片是否构成电台版净词组合（如 "little sucker" -> "motherfucker"）
        val sliceCombined = candidateSlice.joinToString(" ").lowercase()
        for ((regex, replacement) in RADIO_CLEAN_PAIRS) {
            if (regex.containsMatchIn(sliceCombined)) {
                return replacement
            }
        }

        // 校验 B：候选词是否属于已知脏词
        for (cand in candidateSlice) {
            val lower = cand.lowercase()
            if (lower in PROFANITY_LIST || PROFANITY_ROOTS.any { lower.contains(it) }) {
                // 若原词带字母通配，还需验证字母吻合
                val letters = maskedWord.filter { it.isLetter() }
                if (letters.isNotEmpty()) {
                    val pattern = Pattern.compile("^" + maskedWord.lowercase().replace("*", ".") + "$")
                    if (pattern.matcher(lower).matches()) {
                        return cand
                    }
                } else {
                    return cand
                }
            }
        }

        // 严格拒绝非脏话普通单词替代星号！
        return null
    }

    /**
     * 根据中文翻译行语义线索反推英文星号词
     */
    private fun resolveFromTranslationHint(stageText: String, transHint: String): String {
        var text = stageText
        when {
            transHint.contains("混蛋们") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{6,}"""), "motherfuckers")
            }
            transHint.contains("混蛋") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{6,}"""), "motherfucker")
            }
            transHint.contains("他妈的") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{4,}"""), "fucking")
            }
            transHint.contains("狗屎") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{4,}"""), "shit")
            }
            transHint.contains("婊子") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{4,}"""), "bitch")
            }
            transHint.contains("兄弟") -> {
                text = text.replace(Regex("""(?i)\b[a-zA-Z]*\*+[a-zA-Z]*|\*{4,}"""), "nigga")
            }
        }
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
            .replace(Regex("""混\s*\*{1,4}"""), "混蛋")
            .replace(Regex("""婊\s*\*{1,4}"""), "婊子")

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
        if (censoredChineseLyrics.isNullOrBlank()) return censoredChineseLyrics
        val unescaped = HtmlUtils.unescapeHtml(censoredChineseLyrics)
        if (!unescaped.contains('*')) {
            return unescaped
        }

        val cLines = unescaped.lines()
        val eLines = uncensoredEnglishLyrics?.let { HtmlUtils.unescapeHtml(it) }?.lines() ?: emptyList()

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
        val unescaped = HtmlUtils.unescapeHtml(censoredLyrics)
        if (!hasCensorship(unescaped)) return unescaped
        if (refLines.isEmpty()) return decensorLyrics(unescaped, null as String?)

        val flattenedRef = refLines.flatMap { it.lines() }.map { HtmlUtils.unescapeHtml(it).trim() }.filter { it.isNotBlank() }
        val cLines = unescaped.lines()

        val sanitizedLines = cLines.map { cl ->
            val clClean = cl.replace(TIMESTAMP_REGEX, "").trim()
            if (!hasCensorship(clClean)) return@map cl

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
     * @param translationLyrics 可选的该歌曲中文双语翻译文本 (用于提供语义线索交叉校验)
     */
    fun decensorLyrics(
        censoredLyrics: String,
        uncensoredRefLyrics: String? = null,
        translationLyrics: String? = null
    ): String {
        val unescaped = HtmlUtils.unescapeHtml(censoredLyrics)
        if (!hasCensorship(unescaped)) return unescaped

        val cLines = unescaped.lines()

        val rLines = uncensoredRefLyrics?.let { HtmlUtils.unescapeHtml(it).lines() } ?: emptyList()
        val tLines = translationLyrics?.let { HtmlUtils.unescapeHtml(it).lines() } ?: emptyList()

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

        val transMapByTimestamp = mutableMapOf<String, String>()
        val pureTransLines = mutableListOf<String>()
        for (tl in tLines) {
            val tsMatch = TIMESTAMP_REGEX.find(tl)
            if (tsMatch != null) {
                transMapByTimestamp[tsMatch.value] = tl
            }
            val cleaned = tl.replace(TIMESTAMP_REGEX, "").trim()
            if (cleaned.isNotBlank()) {
                pureTransLines.add(cleaned)
            }
        }

        var pureRefIdx = 0
        var pureTransIdx = 0

        val sanitizedLines = cLines.map { cl ->
            val tsMatch = TIMESTAMP_REGEX.find(cl)
            val refLine = if (tsMatch != null && refMapByTimestamp.containsKey(tsMatch.value)) {
                refMapByTimestamp[tsMatch.value]
            } else if (cl.replace(TIMESTAMP_REGEX, "").trim().isNotBlank() && pureRefIdx < pureRefLines.size) {
                pureRefLines[pureRefIdx++]
            } else {
                null
            }

            val transLine = if (tsMatch != null && transMapByTimestamp.containsKey(tsMatch.value)) {
                transMapByTimestamp[tsMatch.value]
            } else if (cl.replace(TIMESTAMP_REGEX, "").trim().isNotBlank() && pureTransIdx < pureTransLines.size) {
                pureTransLines[pureTransIdx++]
            } else {
                null
            }

            decensorLine(cl, refLine, transLine)
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
