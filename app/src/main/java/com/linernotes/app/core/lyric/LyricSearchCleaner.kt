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

    private val PLACEHOLDER_ARTISTS = setOf(
        "unknown", "unknown artist", "various", "various artists",
        "群星", "合辑", "va", "ost", "soundtrack", "未知歌手", "未知艺术家"
    )

    private val MULTI_ARTIST_SPLIT_REGEX = Regex(
        """\s*[/,&×、]\s*|\s+(?:feat\.|featuring|ft\.|with|vs\.?)\s+""",
        RegexOption.IGNORE_CASE
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
}
