package com.aimultiviewer.data.ai

/**
 * 온디바이스 추출적(extractive) 요약기.
 * 외부 모델/네트워크 없이 동작하며, 단어 빈도 기반으로 핵심 문장을 선택한다.
 * (고품질 추상 요약은 설정에서 클라우드 LLM을 켜면 사용)
 */
object Summarizer {

    private val stopwords = setOf(
        // 한국어
        "그리고", "그러나", "하지만", "또한", "그런데", "그래서", "이는", "있다", "없다",
        "이다", "한다", "에서", "으로", "에게", "까지", "부터", "이와", "그리하여", "또는",
        // 영어
        "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "for", "is", "are",
        "was", "were", "be", "as", "at", "by", "it", "this", "that", "with", "from"
    )

    /** @param maxSentences 추출할 핵심 문장 수 */
    fun summarize(text: String, maxSentences: Int = 5): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "요약할 텍스트가 없습니다."

        val sentences = splitSentences(clean)
        if (sentences.size <= maxSentences) return clean

        val freq = HashMap<String, Int>()
        for (s in sentences) {
            for (w in tokenize(s)) {
                if (w.length < 2 || w in stopwords) continue
                freq[w] = (freq[w] ?: 0) + 1
            }
        }
        if (freq.isEmpty()) return sentences.take(maxSentences).joinToString(" ")

        val scored = sentences.mapIndexed { index, s ->
            val tokens = tokenize(s).filter { it.length >= 2 && it !in stopwords }
            val raw = tokens.sumOf { (freq[it] ?: 0) }
            val score = if (tokens.isEmpty()) 0.0 else raw.toDouble() / tokens.size
            Triple(index, s, score)
        }

        val chosen = scored.sortedByDescending { it.third }
            .take(maxSentences)
            .sortedBy { it.first }

        return chosen.joinToString("\n") { "• " + it.second.trim() }
    }

    fun keyPoints(text: String, count: Int = 5): List<String> =
        summarize(text, count).lines().map { it.removePrefix("• ").trim() }.filter { it.isNotEmpty() }

    private fun splitSentences(text: String): List<String> {
        return text
            .replace("\r", "")
            .split(Regex("(?<=[.!?。])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length > 1 }
    }

    private fun tokenize(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
}
