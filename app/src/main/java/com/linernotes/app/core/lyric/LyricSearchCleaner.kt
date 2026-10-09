package com.linernotes.app.core.lyric

object LyricSearchCleaner {

    private val TRACK_NUMBER_PREFIX_REGEX = Regex(
        """^\s*(?:track\s*\d{1,3}[\.\-_\s]*|\d{1,3}[\.\-_\s]+|[a-zA-Z]\d{1,2}[\.\-_\s]+)""",
        RegexOption.IGNORE_CASE
    )

    private val AUDIO_EXTENSION_REGEX = Regex(
        """\.(?:mp3|flac|wav|m4a|aac|ogg|alac|dsd|ape|wma)$""",
        RegexOption.IGNORE_CASE
    )

    private val PARENTHETICAL_NOISE_REGEX = Regex(
        """\s*[\(\[]\s*(?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|mv|audio|live(?:\s+.*?)?|instrumental|off\s*vocal|karaoke|tv\s*size|(?:anime|movie|film|album|single|radio|original|acoustic)?\s*ver(?:\.|sion)?|remastered(?:\s*\d{4})?|\d{4}\s*remaster(?:ed)?|deluxe\s*edition|bonus\s*track|feat\..*?|featuring.*?)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private val HYPHEN_NOISE_REGEX = Regex(
        """\s*-\s*(?:remastered.*|\d{4}\s*remaster.*|live.*|single.*|ep.*|instrumental.*|feat\..*|off\s*vocal.*)""",
        RegexOption.IGNORE_CASE
    )

    private val STANDALONE_FEAT_REGEX = Regex(
        """\s+(?:feat\.?|featuring|ft\.?)\s+.*$""",
        RegexOption.IGNORE_CASE
    )

    private val PLACEHOLDER_ARTISTS = setOf(
        "unknown", "unknown artist", "various", "various artists",
        "群星", "合辑", "va", "ost", "soundtrack", "未知歌手", "未知艺术家"
    )

    private val MULTI_ARTIST_SPLIT_REGEX = Regex(
        """\s*[/,&×、]\s*|\s+(?:feat\.|featuring|ft\.|with|vs\.?)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val TIMESTAMP_PARSER_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")

    private val TITLE_STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "of", "with",
        "feat", "ft", "version", "remix", "mix", "edit", "explicit", "clean", "audio", "video"
    )

    private val META_LINE_TAGS = setOf(
        "作词", "作曲", "编曲", "制作", "ti:", "ar:", "al:", "by:", "offset:",
        "lyricist", "composer", "producer", "written by"
    )

    /**
     * 清理曲目标题中的音轨编号、音频扩展名以及多余的视频/混音/伴奏标签
     */
    fun cleanTrackTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""

        var title = rawTitle.trim()

        // 1. 去除音频扩展名
        title = title.replace(AUDIO_EXTENSION_REGEX, "").trim()

        // 2. 去除音轨序号前缀 (如 "01. ", "Track 02 - ", "A1. ")
        title = title.replace(TRACK_NUMBER_PREFIX_REGEX, "").trim()

        // 3. 统一全角括号为半角括号
        title = title
            .replace('（', '(').replace('）', ')')
            .replace('【', '[').replace('】', ']')
            .replace('［', '[').replace('］', ']')

        // 4. 去除多余的标签后缀与混音信息
        title = title.replace(PARENTHETICAL_NOISE_REGEX, "").trim()
        title = title.replace(HYPHEN_NOISE_REGEX, "").trim()
        title = title.replace(STANDALONE_FEAT_REGEX, "").trim()

        return if (title.isNotBlank()) title else rawTitle.trim()
    }

    /**
     * 清理歌手名称，过滤占位符（如 Unknown Artist、Various Artists）
     */
    fun cleanArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return ""
        val trimmed = rawArtist.trim()
        if (PLACEHOLDER_ARTISTS.contains(trimmed.lowercase())) {
            return ""
        }
        return trimmed
    }

    /**
     * 提取主歌手（遇到合作歌手 A / B 或 A feat. B 时提取第一歌手）
     */
    fun extractPrimaryArtist(cleanedArtist: String): String {
        if (cleanedArtist.isBlank()) return ""
        val parts = cleanedArtist.split(MULTI_ARTIST_SPLIT_REGEX)
        return parts.firstOrNull()?.trim() ?: cleanedArtist
    }

    /**
     * 生成 3 级阶梯式搜索关键词队列：
     * 1. 纯净曲名 + 主歌手 (最高精确度)
     * 2. 纯净曲名 + 完整歌手 (多歌手或特异歌手名)
     * 3. 纯净曲名 (跨语言/影视原声/歌手标签不一致时的可靠保底)
     */
    fun buildSearchQueries(rawTitle: String, rawArtist: String): List<String> {
        val cleanTitle = cleanTrackTitle(rawTitle)
        val cleanArt = cleanArtist(rawArtist)
        val primaryArt = extractPrimaryArtist(cleanArt)

        val queries = mutableListOf<String>()

        if (cleanTitle.isNotBlank()) {
            if (primaryArt.isNotBlank()) {
                queries.add("$cleanTitle $primaryArt")
            }
            if (cleanArt.isNotBlank() && cleanArt != primaryArt) {
                queries.add("$cleanTitle $cleanArt")
            }
            // 纯净歌名单独搜索作为兜底
            queries.add(cleanTitle)
        } else {
            if (rawTitle.isNotBlank()) {
                queries.add(rawTitle.trim())
            }
        }

        return queries.distinct()
    }

    /**
     * 严格比对候选曲目与目标曲目的置信度评分 (Anti-Mismatch Engine)
     * 严防同歌手异曲、同名异曲、加长版混音以及无关热门曲目误匹配
     */
    fun scoreCandidateMatch(
        candidateTitle: String,
        candidateArtist: String,
        targetTitle: String,
        targetArtist: String,
        candidateDurationMs: Long = 0L,
        targetDurationMs: Long = 0L
    ): Int {
        val normCandTitle = normalizeForMatching(candidateTitle)
        val normTargetTitle = normalizeForMatching(targetTitle)
        val normCandArtist = normalizeForMatching(candidateArtist)
        val normTargetArtist = normalizeForMatching(targetArtist)

        if (normCandTitle.isBlank() || normTargetTitle.isBlank()) return -100

        var score = 0

        // 1. 标题匹配度核心判定
        when {
            normCandTitle == normTargetTitle -> score += 120
            normCandTitle.startsWith(normTargetTitle) || normTargetTitle.startsWith(normCandTitle) -> score += 90
            normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle) -> score += 75
            else -> {
                // 东亚文字（中文/日文通常不以空格分隔单词）：直接检查连续字符包含度
                val candCJK = normCandTitle.replace(" ", "")
                val targetCJK = normTargetTitle.replace(" ", "")
                if (candCJK.isNotEmpty() && targetCJK.isNotEmpty() && (candCJK.contains(targetCJK) || targetCJK.contains(candCJK))) {
                    score += 70
                } else {
                    val candWords = normCandTitle.split(" ").filter { it.length >= 2 }.toSet()
                    val targetWords = normTargetTitle.split(" ").filter { it.length >= 2 }.toSet()
                    val overlap = candWords.intersect(targetWords).size
                    if (overlap > 0 && targetWords.isNotEmpty()) {
                        val ratio = overlap.toFloat() / targetWords.size
                        if (ratio >= 0.5f) {
                            score += (60 * ratio).toInt()
                        } else {
                            return -200 // 标题有效单词重合度过低，拒绝
                        }
                    } else {
                        return -500 // 标题零单词重合，绝对是错误歌曲，直接排除！
                    }
                }
            }
        }

        // 2. 歌手匹配度判定 (针对多语言别名如 Sheena Ringo vs 椎名林檎，温和容错)
        if (normTargetArtist.isNotBlank() && normCandArtist.isNotBlank()) {
            when {
                normCandArtist == normTargetArtist -> score += 70
                normCandArtist.contains(normTargetArtist) || normTargetArtist.contains(normCandArtist) -> score += 55
                else -> {
                    val candArtWords = normCandArtist.split(" ").filter { it.length >= 2 }.toSet()
                    val targetArtWords = normTargetArtist.split(" ").filter { it.length >= 2 }.toSet()
                    if (candArtWords.intersect(targetArtWords).isNotEmpty()) {
                        score += 40
                    } else {
                        // 跨语言艺人名差异（如 Spotify 传递罗马音 Sheena Ringo，国内平台为汉字/假名 椎名林檎）：
                        // 若标题高度吻合且物理时长极度接近（误差 <= 6s），仅轻微扣分（-10），坚决不误杀正解
                        val titleStrongMatched = (normCandTitle == normTargetTitle || normCandTitle.startsWith(normTargetTitle) || normTargetTitle.startsWith(normCandTitle))
                        val durationMatched = (targetDurationMs > 0L && candidateDurationMs > 0L && Math.abs(targetDurationMs - candidateDurationMs) <= 6000L)
                        if (titleStrongMatched && durationMatched) {
                            score -= 10
                        } else {
                            score -= 90 // 歌手完全不匹配
                        }
                    }
                }
            }
        }

        // 3. 歌曲物理时长偏差判定 (若双方皆提供时长)
        if (targetDurationMs > 0L && candidateDurationMs > 0L) {
            val diffSec = Math.abs(targetDurationMs - candidateDurationMs) / 1000L
            when {
                diffSec <= 3L -> score += 40
                diffSec <= 8L -> score += 20
                diffSec > 25L -> score -= 200 // 时长偏差大于 25 秒，通常为不同版本或串烧，予以重罚
            }
        }

        // 4. 特殊版本干扰项过滤
        val isTargetRemix = targetTitle.contains("remix", ignoreCase = true)
        val isCandRemix = candidateTitle.contains("remix", ignoreCase = true)
        if (!isTargetRemix && isCandRemix) score -= 40

        val isTargetLive = targetTitle.contains("live", ignoreCase = true)
        val isCandLive = candidateTitle.contains("live", ignoreCase = true)
        if (!isTargetLive && isCandLive) score -= 40

        return score
    }

    fun normalizeForMatching(text: String): String {
        // 先统一将繁体转为简体，打通繁简通用匹配 (如 "幸福論" 与 "幸福论")
        val simplified = com.linernotes.app.core.util.ChineseConverter.toSimplified(text)
        var s = simplified.lowercase()
        s = s.replace(Regex("""[\(\[\{（【［].*?[\)\]\}）】］]"""), " ")
        // 允许拉丁字符、数字、中文字符(\u4e00-\u9fa5)、日文平假名(\u3040-\u309f)与片假名(\u30a0-\u30ff)
        s = s.replace(Regex("""[^a-z0-9\u4e00-\u9fa5\u3040-\u309f\u30a0-\u30ff\s]"""), " ")
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * 提取曲目标题中的实质性鉴别关键词 (排除冠词与虚词)
     */
    fun extractSignificantTitleKeywords(title: String): List<String> {
        val norm = normalizeForMatching(title)
        val spaceWords = norm.split(" ").filter { it.length >= 2 && !TITLE_STOP_WORDS.contains(it) }
        if (spaceWords.isNotEmpty()) {
            return spaceWords.distinct()
        }
        // 如果是没有空格分词的东亚字符（如 "幸福论"），按整体提取
        val compact = norm.replace(" ", "")
        return if (compact.length >= 2) listOf(compact) else emptyList()
    }

    /**
     * 计算歌词文本中包含标题关键词的比例 (0.0 ~ 1.0)
     */
    fun calculateTitleKeywordRelevance(lyric: String, keywords: List<String>): Float {
        if (keywords.isEmpty() || lyric.isBlank()) return 0.5f
        val lyricNorm = normalizeForMatching(lyric)
        var hits = 0
        for (kw in keywords) {
            val kwNorm = normalizeForMatching(kw)
            if (kwNorm.isNotEmpty() && lyricNorm.contains(kwNorm)) {
                hits++
            }
        }
        return hits.toFloat() / keywords.size
    }

    /**
     * 从 LRC 歌词中提取末句时间戳（毫秒）用于推算歌曲时长
     */
    fun extractLastTimestampMs(lyric: String): Long {
        if (lyric.isBlank()) return 0L
        val matches = TIMESTAMP_PARSER_REGEX.findAll(lyric).toList()
        if (matches.isEmpty()) return 0L
        val lastMatch = matches.last()
        val minutes = lastMatch.groupValues[1].toLongOrNull() ?: 0L
        val seconds = lastMatch.groupValues[2].toLongOrNull() ?: 0L
        val msStr = lastMatch.groupValues.getOrNull(3).orEmpty()
        val ms = msStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (minutes * 60L + seconds) * 1000L + ms
    }

    /**
     * 提取歌词纯正演唱正文指纹 (用于跨数据源一致性验证)
     */
    fun extractLyricSungFingerprint(lyric: String): List<String> {
        if (lyric.isBlank()) return emptyList()
        val results = mutableListOf<String>()
        val lines = lyric.lines()
        for (line in lines) {
            val textOnly = line.replace(TIMESTAMP_PARSER_REGEX, "").trim()
            if (textOnly.length < 3) continue
            val lower = textOnly.lowercase()
            if (META_LINE_TAGS.any { lower.contains(it) }) continue
            val simplified = com.linernotes.app.core.util.ChineseConverter.toSimplified(lower)
            val cleanNorm = simplified.replace(Regex("""[^a-z0-9\u4e00-\u9fa5\u3040-\u309f\u30a0-\u30ff]"""), "")
            if (cleanNorm.length >= 3) {
                results.add(cleanNorm)
                if (results.size >= 5) break
            }
        }
        return results
    }
}
