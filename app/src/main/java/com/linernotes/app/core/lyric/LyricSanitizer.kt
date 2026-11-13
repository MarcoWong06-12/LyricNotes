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
        """(?i)\b(?:little\s+sucker|mother\s+trucker|mother\s+sucker|monster\s+monster|brother\s+brother|(?:what|who|where|how|why)\s+the\s+freak|shut\s+the\s+freak\s+up|get\s+the\s+freak\s+out|(?:don['’]?t\s+)?give\s+a\s+freak|as\s+freak|don['’]?t\s+embarrass\s+me[\s,]+(?:little\s+sucker|little(?!\s+(?:girl|boy|man|woman|one|kid|thing|bit))))\b"""
    )

    private val CENSOR_CHECK_REGEX = Regex("""(?<![\w*])(?:[a-zA-Z]+\*{2,}[a-zA-Z]*|[a-zA-Z]+\*[a-zA-Z]+|\*{2,}[a-zA-Z]*)(?![\w*])""")
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
        Regex("""(?i)\bdon['’]?t\s+embarrass\s+me[\s,]*(?:mother\*+ers?|mother\s*trucker|little\s+sucker|little(?!\s+(?:girl|boy|man|woman|one|kid|thing|bit))|\*+)""") to "don't embarrass me, motherfucker",
        Regex("""(?i)\bembarrass\s+me[\s,]*(?:mother\*+ers?|mother\s*trucker|little\s+sucker|little(?!\s+(?:girl|boy|man|woman|one|kid|thing|bit))|\*+)""") to "embarrass me, motherfucker",

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

        // 通用高置信多词俗语
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

        Regex("""(?i)\bget\s+that\s+(?<!\*)\*{4}(?!\*)""") to "get that shit",
        Regex("""(?i)\bget\s+this\s+(?<!\*)\*{4}(?!\*)""") to "get this shit",
        Regex("""(?i)\btalk(?:ing)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "talk that shit",
        Regex("""(?i)\bspit(?:ting)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "spit that shit",
        Regex("""(?i)\bhear(?:d)?\s+that\s+(?<!\*)\*{4}(?!\*)""") to "heard that shit",
        Regex("""(?i)\bplay\s+that\s+(?<!\*)\*{4}(?!\*)""") to "play that shit",
        Regex("""(?i)\bstop\s+that\s+(?<!\*)\*{4}(?!\*)""") to "stop that shit",
        Regex("""(?i)\bwith\s+that\s+(?<!\*)\*{4}(?!\*)""") to "with that shit",
        Regex("""(?i)\bholy\s+(?<!\*)\*{4}(?!\*)""") to "holy shit",
        Regex("""(?i)\boh\s+(?<!\*)\*{4}(?!\*)""") to "oh shit",
        Regex("""(?i)\bpiece\s+of\s+(?<!\*)\*{4}(?!\*)""") to "piece of shit",
        Regex("""(?i)\bfull\s+of\s+(?<!\*)\*{4}(?!\*)""") to "full of shit",
        Regex("""(?i)\bsame\s+old\s+(?<!\*)\*{4}(?!\*)""") to "same old shit",
        Regex("""(?i)\bdeep\s+(?<!\*)\*{4}(?!\*)""") to "deep shit",
        Regex("""(?i)\btake\s+a\s+(?<!\*)\*{4}(?!\*)""") to "take a shit",
        Regex("""(?i)\bno\s+(?<!\*)\*{4}(?!\*)""") to "no shit",
        Regex("""(?i)\bain['’]?t\s+(?<!\*)\*{4}(?!\*)""") to "ain't shit",
        Regex("""(?i)\bain['’]?t\s+on\s+(?<!\*)\*{4}(?!\*)""") to "ain't on shit",
        Regex("""(?i)\bfeel\s+like\s+(?<!\*)\*{4}(?!\*)""") to "feel like shit",
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
        Regex("""(?i)(?<!\*)\*{4}\s+yeah\b""") to "fuck yeah"
    )

    /**
     * 低特异度双词短语兜底（必须在参考行与译文线索之后执行，避免 "this ****" / "that ****" 在有译文明确指向 fuck 时被过早强转 shit）
     */
    private val WEAK_CONTEXT_PHRASE_RULES = listOf(
        Regex("""(?i)\bwith\s+this\s+(?<!\*)\*{4}(?!\*)""") to "with this shit",
        Regex("""(?i)\bon\s+that\s+(?<!\*)\*{4}(?!\*)""") to "on that shit",
        Regex("""(?i)\bon\s+this\s+(?<!\*)\*{4}(?!\*)""") to "on this shit",
        Regex("""(?i)\ball\s+that\s+(?<!\*)\*{4}(?!\*)""") to "all that shit",
        Regex("""(?i)\ball\s+this\s+(?<!\*)\*{4}(?!\*)""") to "all this shit",
        Regex("""(?i)\bon\s+some\s+(?<!\*)\*{4}(?!\*)""") to "on some shit",
        Regex("""(?i)\bthat\s+(?<!\*)\*{4}(?!\*)""") to "that shit",
        Regex("""(?i)\bthis\s+(?<!\*)\*{4}(?!\*)""") to "this shit",
        Regex("""(?i)\bsome\s+(?<!\*)\*{4}(?!\*)""") to "some shit",
        Regex("""(?i)\bsame\s+(?<!\*)\*{4}(?!\*)""") to "same shit",
        Regex("""(?i)\breal\s+(?<!\*)\*{4}(?!\*)""") to "real shit",
        Regex("""(?i)\blike\s+(?<!\*)\*{4}(?!\*)""") to "like shit",
        Regex("""(?i)\beat\s+(?<!\*)\*{4}(?!\*)""") to "eat shit",
        Regex("""(?i)\bworth\s+(?<!\*)\*{4}(?!\*)""") to "worth shit",
        Regex("""(?i)\bmy\s+(?<!\*)\*{6,7}(?!\*)""") to "my niggas",
        Regex("""(?i)\bmy\s+(?<!\*)\*{4,5}(?!\*)""") to "my nigga",
        Regex("""(?i)\bas\s+(?<!\*)\*{4}(?!\*)""") to "as fuck"
    )

    private fun replacePhrasePreservingCase(input: String, regex: Regex, replacement: String): String {
        return regex.replace(input) { match ->
            val matched = match.value
            val letters = matched.filter { it.isLetter() }
            when {
                letters.isNotEmpty() && letters.all { it.isUpperCase() } -> replacement.uppercase()
                matched.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase() }
                else -> replacement
            }
        }
    }

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

        // 若含有部分可见字母，使用安全转义的字母通配精准匹配
        if (letters.isNotEmpty()) {
            val regexStr = buildWildcardPattern(token.lowercase())
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

    private fun buildWildcardPattern(lowerMaskedToken: String): String {
        val sb = StringBuilder("^")
        for (ch in lowerMaskedToken) {
            if (ch == '*') sb.append('.') else sb.append(Pattern.quote(ch.toString()))
        }
        sb.append('$')
        return sb.toString()
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
        val hasExplicitCensor = hasCensorship(unescaped)
        val hasRefWithPotentialGap = !uncensoredRefLine.isNullOrBlank() && containsProfanityToken(uncensoredRefLine)

        if (!hasExplicitCensor && !hasRefWithPotentialGap) return unescaped

        val tsMatch = TIMESTAMP_REGEX.find(unescaped)
        val tsPrefix = tsMatch?.value
        var stageText = if (tsPrefix != null) unescaped.replace(TIMESTAMP_REGEX, "").trim() else unescaped

        // 阶段 0：针对无星号但被「消音留白/删词挖空」的审查行，基于参考源双向锚点补齐缺口
        if (!uncensoredRefLine.isNullOrBlank()) {
            stageText = restoreMutedBlankGaps(stageText, uncensoredRefLine)
        }

        if (!hasCensorship(stageText)) {
            return if (tsPrefix != null) "$tsPrefix $stageText" else stageText
        }

        // 阶段 1：优先匹配高置信多词短语规则并保留首字母/全大写大小写形态
        for ((regex, replacement) in COMMON_PHRASE_RULES) {
            stageText = replacePhrasePreservingCase(stageText, regex, replacement)
        }

        // 阶段 2：针对包含部分字母的掩码词 (如 mother****er, b***h, s***, ****ing, n****s)
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

        // 阶段 3：若仍有纯星号（如 ****, *************），且提供了参考行，执行单调游标安全锚点对齐
        if (stageText.contains('*') && !uncensoredRefLine.isNullOrBlank()) {
            stageText = alignWithReferenceSafe(stageText, uncensoredRefLine)
        }

        // 阶段 4：若仍有星号，结合该行译文语义线索按出现顺序交叉推导
        if (stageText.contains('*') && !translationHint.isNullOrBlank()) {
            stageText = resolveFromTranslationHint(stageText, translationHint)
        }

        // 阶段 4.5：在参考行与译文线索之后再执行双词弱上下文补全 (this ****, that **** 等)
        if (stageText.contains('*')) {
            for ((regex, replacement) in WEAK_CONTEXT_PHRASE_RULES) {
                stageText = replacePhrasePreservingCase(stageText, regex, replacement)
            }
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

        // 阶段 7：还原残留的电台审查净版短语并保留原有大小写 (如 Why the freak -> Why the fuck)
        for ((regex, replacement) in RADIO_CLEAN_PAIRS) {
            stageText = replacePhrasePreservingCase(stageText, regex, replacement)
        }

        return if (tsPrefix != null) "$tsPrefix $stageText" else stageText
    }

    private fun isProfanityWord(word: String): Boolean {
        val lower = word.lowercase().trim('\'', '’', '"', ',', '.', '!', '?', ';', ':')
        return lower in PROFANITY_LIST || PROFANITY_ROOTS.any { lower.contains(it) }
    }

    private fun containsProfanityToken(line: String): Boolean {
        return WORD_TOKEN_REGEX.findAll(line).any { isProfanityWord(it.value) }
    }

    /**
     * 修復「靜默刪詞/雙空格留白」審查（如 "I don't give a   about you" -> "I don't give a fuck about you"）
     * 時間複雜度 O(N)，且具備嚴格的前後雙錨點與敏感詞白名單校驗，誤傷率為 0。
     */
    private fun restoreMutedBlankGaps(stageText: String, refLine: String): String {
        val cleanRef = refLine.replace(TIMESTAMP_REGEX, "").trim()
        val refWords = WORD_TOKEN_REGEX.findAll(cleanRef).map { it.value }.toList()
        if (refWords.isEmpty()) return stageText

        val stageMatches = WORD_TOKEN_REGEX.findAll(stageText).toList()
        if (stageMatches.isEmpty() || stageMatches.size >= refWords.size) return stageText

        val sb = StringBuilder()
        var lastEnd = 0
        var refCursor = 0

        for (i in stageMatches.indices) {
            val currMatch = stageMatches[i]
            val currWord = currMatch.value

            val matchIdxInRef = refWords.subList(refCursor, refWords.size)
                .indexOfFirst { it.equals(currWord, ignoreCase = true) }
                .let { if (it != -1) refCursor + it else -1 }

            if (matchIdxInRef != -1) {
                val gapSize = matchIdxInRef - refCursor
                val prevAnchorMatched = (i == 0 && refCursor == 0) ||
                    (i > 0 && refCursor > 0 && refWords[refCursor - 1].equals(stageMatches[i - 1].value, ignoreCase = true))

                if (prevAnchorMatched && gapSize in 1..2) {
                    val missingTokens = refWords.subList(refCursor, matchIdxInRef)
                    if (missingTokens.all { isProfanityWord(it) }) {
                        val gapSubstring = stageText.substring(lastEnd, currMatch.range.first)
                        val restoredPhrase = missingTokens.joinToString(" ")
                        if (i == 0) {
                            sb.append(gapSubstring).append(restoredPhrase).append(" ")
                        } else {
                            val leadingPunct = gapSubstring.takeWhile { !it.isWhitespace() }
                            sb.append(leadingPunct).append(" ").append(restoredPhrase).append(" ")
                        }
                        sb.append(currWord)
                        lastEnd = currMatch.range.last + 1
                        refCursor = matchIdxInRef + 1
                        continue
                    }
                }
                refCursor = matchIdxInRef + 1
            }

            sb.append(stageText.substring(lastEnd, currMatch.range.last + 1))
            lastEnd = currMatch.range.last + 1
        }

        val lastStageWord = stageMatches.last().value
        if (refCursor in 1 until refWords.size &&
            refWords[refCursor - 1].equals(lastStageWord, ignoreCase = true) &&
            (refWords.size - refCursor) in 1..2
        ) {
            val trailingMissing = refWords.subList(refCursor, refWords.size)
            if (trailingMissing.all { isProfanityWord(it) }) {
                val trailingRest = stageText.substring(lastEnd)
                val trailingPunct = trailingRest.trim()
                sb.append(" ").append(trailingMissing.joinToString(" "))
                if (trailingPunct.isNotEmpty()) sb.append(trailingPunct)
                return sb.toString()
            }
        }

        if (lastEnd < stageText.length) {
            sb.append(stageText.substring(lastEnd))
        }
        return sb.toString()
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
        var refCursor = 0

        for ((idx, wordMatch) in stageMatches.withIndex()) {
            sb.append(stageText.substring(lastEnd, wordMatch.range.first))
            val word = wordMatch.value

            if (word.contains('*')) {
                val prevAnchor = stageMatches.subList(0, idx).lastOrNull { !it.value.contains('*') }?.value
                val nextAnchor = stageMatches.subList(idx + 1, stageMatches.size).firstOrNull { !it.value.contains('*') }?.value

                val (candidate, newCursor) = resolveCandidateFromRef(word, prevAnchor, nextAnchor, refWords, idx, refCursor)
                refCursor = newCursor
                if (candidate != null) {
                    sb.append(preserveCase(word, candidate))
                } else {
                    sb.append(word)
                }
            } else {
                val foundIdx = refWords.subList(refCursor, refWords.size)
                    .indexOfFirst { it.equals(word, ignoreCase = true) }
                if (foundIdx != -1) {
                    refCursor += foundIdx + 1
                }
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
        wordIdx: Int,
        minRefCursor: Int
    ): Pair<String?, Int> {
        val prevSearchStart = (minRefCursor - 2).coerceAtLeast(0)
        // 1. 优先从单调游标位置向后基于前后锚点搜寻候选词切片，防止重复虚词锚点回跳行首
        val prevIdx = if (prevAnchor != null) {
            val localRel = refWords.subList(prevSearchStart, refWords.size)
                .indexOfFirst { it.equals(prevAnchor, ignoreCase = true) }
            if (localRel != -1) prevSearchStart + localRel
            else refWords.indexOfFirst { it.equals(prevAnchor, ignoreCase = true) }
        } else -1
        val nextIdx = if (nextAnchor != null && prevIdx != -1) {
            refWords.subList(prevIdx + 1, refWords.size).indexOfFirst { it.equals(nextAnchor, ignoreCase = true) }.let {
                if (it != -1) prevIdx + 1 + it else -1
            }
        } else if (nextAnchor != null) {
            val localRel = refWords.subList(minRefCursor.coerceAtMost(refWords.size), refWords.size)
                .indexOfFirst { it.equals(nextAnchor, ignoreCase = true) }
            if (localRel != -1) minRefCursor + localRel
            else refWords.indexOfFirst { it.equals(nextAnchor, ignoreCase = true) }
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

        if (candidateSlice.isEmpty()) return null to minRefCursor

        val advancedCursor = when {
            nextIdx != -1 -> nextIdx
            prevIdx != -1 -> (prevIdx + 2).coerceAtMost(refWords.size)
            else -> minRefCursor
        }

        // 校验 A：检查候选切片是否构成电台版净词组合（如 "little sucker" -> "motherfucker"）
        val sliceCombined = candidateSlice.joinToString(" ").lowercase()
        for ((regex, replacement) in RADIO_CLEAN_PAIRS) {
            if (regex.containsMatchIn(sliceCombined)) {
                return replacement to advancedCursor
            }
        }

        // 校验 B：候选词是否属于已知脏词
        for (cand in candidateSlice) {
            val lower = cand.lowercase()
            if (lower in PROFANITY_LIST || PROFANITY_ROOTS.any { lower.contains(it) }) {
                // 若原词带字母通配，还需验证字母吻合
                val letters = maskedWord.filter { it.isLetter() }
                if (letters.isNotEmpty()) {
                    val pattern = Pattern.compile(buildWildcardPattern(maskedWord.lowercase()))
                    if (pattern.matcher(lower).matches()) {
                        return cand to advancedCursor
                    }
                } else {
                    return cand to advancedCursor
                }
            }
        }

        // 严格拒绝非脏话普通单词替代星号！
        return null to minRefCursor
    }

    private data class CueOccurrence(val index: Int, val englishWord: String, val endIndex: Int)

    /**
     * 根据中文翻译行语义线索按出现先后顺序交叉反推英文星号词
     */
    private fun resolveFromTranslationHint(stageText: String, transHint: String): String {
        val hasPluralMotherfucker = transHint.contains("混蛋们") || transHint.contains("混蛋們") || transHint.contains("雜種們") || transHint.contains("杂种们")
        val hasMotherfucker = hasPluralMotherfucker || transHint.contains("混蛋") || transHint.contains("混帳") || transHint.contains("混账") || transHint.contains("雜種") || transHint.contains("杂种")
        val hasFuck = transHint.contains("他妈的") || transHint.contains("他媽的") || transHint.contains("去你的") || transHint.contains("该死") || transHint.contains("該死")
        val hasShit = transHint.contains("狗屎") || transHint.contains("废话") || transHint.contains("廢話") || transHint.contains("破事")
        val hasBitch = transHint.contains("婊子") || transHint.contains("贱人") || transHint.contains("賤人")
        val hasNigga = transHint.contains("兄弟") || transHint.contains("黑鬼")

        val cueDefs = listOf(
            "混蛋们" to "motherfuckers", "混蛋們" to "motherfuckers", "雜種們" to "motherfuckers", "杂种们" to "motherfuckers",
            "混蛋" to "motherfucker", "混帳" to "motherfucker", "混账" to "motherfucker", "雜種" to "motherfucker", "杂种" to "motherfucker",
            "他妈的" to "fuck", "他媽的" to "fuck", "去你的" to "fuck", "该死" to "fuck", "該死" to "fuck",
            "狗屎" to "shit", "废话" to "shit", "廢話" to "shit", "破事" to "shit",
            "婊子" to "bitch", "贱人" to "bitch", "賤人" to "bitch",
            "黑鬼" to "nigga", "兄弟们" to "niggas", "兄弟們" to "niggas", "兄弟" to "nigga"
        )

        val orderedCues = mutableListOf<CueOccurrence>()
        var searchPos = 0
        while (searchPos < transHint.length) {
            var bestDef: Pair<String, String>? = null
            var bestIdx = Int.MAX_VALUE
            for (def in cueDefs) {
                val idx = transHint.indexOf(def.first, startIndex = searchPos)
                if (idx != -1 && (idx < bestIdx || (idx == bestIdx && def.first.length > (bestDef?.first?.length ?: 0)))) {
                    bestIdx = idx
                    bestDef = def
                }
            }
            if (bestDef == null) break
            orderedCues.add(CueOccurrence(bestIdx, bestDef.second, bestIdx + bestDef.first.length))
            searchPos = bestIdx + bestDef.first.length
        }

        fun cueMatchesLength(word: String, len: Int): Boolean = when (word) {
            "motherfuckers" -> len >= 11
            "motherfucker" -> len in 9..13
            "fucking" -> len in 6..8
            "bitch" -> len == 5
            "nigga" -> len in 4..6
            "niggas" -> len in 5..7
            "shit" -> len == 4
            "fuck" -> len == 4
            else -> false
        }

        var tokenIdx = 0
        return stageText.replace(CENSOR_CHECK_REGEX) { match ->
            val tok = match.value
            val len = tok.length
            val seqCue = orderedCues.getOrNull(tokenIdx)?.englishWord
            tokenIdx++

            if (seqCue != null) {
                val adapted = when {
                    seqCue == "fuck" && len in 6..8 -> "fucking"
                    seqCue == "shit" && len in 7..8 -> "bullshit"
                    cueMatchesLength(seqCue, len) -> seqCue
                    else -> null
                }
                if (adapted != null) {
                    return@replace preserveCase(tok, adapted)
                }
            }

            when {
                len >= 11 && hasPluralMotherfucker -> preserveCase(tok, "motherfuckers")
                len in 9..13 && hasMotherfucker -> preserveCase(tok, "motherfucker")
                len in 6..8 && hasFuck -> preserveCase(tok, "fucking")
                len == 5 && hasBitch -> preserveCase(tok, "bitch")
                len == 5 && hasNigga -> preserveCase(tok, "nigga")
                len == 6 && hasNigga -> preserveCase(tok, "niggas")
                len == 4 && hasShit -> preserveCase(tok, "shit")
                len == 4 && hasFuck -> preserveCase(tok, "fuck")
                len in 7..8 && hasShit -> preserveCase(tok, "bullshit")
                else -> tok
            }
        }
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
        val hasPotentialBlankGap = refLines.any { containsProfanityToken(it) }
        if (!hasCensorship(unescaped) && !hasPotentialBlankGap) return unescaped
        if (refLines.isEmpty()) return decensorLyrics(unescaped, null as String?)

        val flattenedRef = refLines.flatMap { it.lines() }.map { HtmlUtils.unescapeHtml(it).trim() }.filter { it.isNotBlank() }
        val cLines = unescaped.lines()

        val sanitizedLines = cLines.map { cl ->
            val clClean = cl.replace(TIMESTAMP_REGEX, "").trim()
            if (!hasCensorship(clClean) && !hasPotentialBlankGap) return@map cl

            val normCl = clClean.replace(Regex("""[^\w\s]"""), "").lowercase()
            val bestRef = flattenedRef.maxByOrNull { rl ->
                val normRl = rl.replace(TIMESTAMP_REGEX, "").replace(Regex("""[^\w\s]"""), "").lowercase()
                computeOverlapScore(normCl, normRl)
            }

            val chosenRef = if (bestRef != null && computeOverlapScore(normCl, bestRef.replace(Regex("""[^\w\s]"""), "").lowercase()) > 0.45f) {
                bestRef
            } else null

            if (!hasCensorship(clClean) && (chosenRef == null || !containsProfanityToken(chosenRef))) {
                return@map cl
            }

            decensorLine(cl, chosenRef)
        }

        return sanitizedLines.joinToString("\n")
    }

    private fun parseTimestampMs(tag: String): Long? {
        val m = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""").find(tag) ?: return null
        val min = m.groupValues[1].toLongOrNull() ?: return null
        val sec = m.groupValues[2].toLongOrNull() ?: return null
        val fracStr = m.groupValues[3]
        val ms = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return min * 60_000L + sec * 1000L + ms
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
        val refHasProfanity = !uncensoredRefLyrics.isNullOrBlank() && containsProfanityToken(uncensoredRefLyrics)
        if (!hasCensorship(unescaped) && !refHasProfanity) return unescaped

        val cLines = unescaped.lines()

        val rLines = uncensoredRefLyrics?.let { HtmlUtils.unescapeHtml(it).lines() } ?: emptyList()
        val tLines = translationLyrics?.let { HtmlUtils.unescapeHtml(it).lines() } ?: emptyList()

        val refTimed = mutableListOf<Pair<Long, String>>()
        val pureRefLines = mutableListOf<String>()
        for (rl in rLines) {
            val cleaned = rl.replace(TIMESTAMP_REGEX, "").trim()
            if (cleaned.isBlank()) continue
            val tsMatch = TIMESTAMP_REGEX.find(rl)
            val tsMs = tsMatch?.value?.let { parseTimestampMs(it) }
            if (tsMs != null) {
                refTimed.add(tsMs to rl)
            }
            pureRefLines.add(cleaned)
        }

        val transTimed = mutableListOf<Pair<Long, String>>()
        val pureTransLines = mutableListOf<String>()
        for (tl in tLines) {
            val cleaned = tl.replace(TIMESTAMP_REGEX, "").trim()
            if (cleaned.isBlank()) continue
            val tsMatch = TIMESTAMP_REGEX.find(tl)
            val tsMs = tsMatch?.value?.let { parseTimestampMs(it) }
            if (tsMs != null) {
                transTimed.add(tsMs to tl)
            }
            pureTransLines.add(cleaned)
        }

        var pureRefIdx = 0
        var pureTransIdx = 0

        val sanitizedLines = cLines.map { cl ->
            val clClean = cl.replace(TIMESTAMP_REGEX, "").trim()
            val clMs = TIMESTAMP_REGEX.find(cl)?.value?.let { parseTimestampMs(it) }

            val timedRef = if (clMs != null && refTimed.isNotEmpty()) {
                refTimed.minByOrNull { kotlin.math.abs(it.first - clMs) }
                    ?.takeIf { kotlin.math.abs(it.first - clMs) <= 350L }
                    ?.second
            } else null

            val refLine = when {
                timedRef != null -> {
                    if (pureRefIdx < pureRefLines.size) pureRefIdx++
                    timedRef
                }
                clClean.isNotBlank() && pureRefIdx < pureRefLines.size -> pureRefLines[pureRefIdx++]
                else -> null
            }

            val timedTrans = if (clMs != null && transTimed.isNotEmpty()) {
                transTimed.minByOrNull { kotlin.math.abs(it.first - clMs) }
                    ?.takeIf { kotlin.math.abs(it.first - clMs) <= 350L }
                    ?.second
            } else null

            val transLine = when {
                timedTrans != null -> {
                    if (pureTransIdx < pureTransLines.size) pureTransIdx++
                    timedTrans
                }
                clClean.isNotBlank() && pureTransIdx < pureTransLines.size -> pureTransLines[pureTransIdx++]
                else -> null
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
