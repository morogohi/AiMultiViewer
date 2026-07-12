package com.aimultiviewer.data.parser

/**
 * OOXML(DOCX) / OWPML(HWPX) 처럼 XML 본문에서 사람이 읽을 텍스트만 뽑아내는 경량 추출기.
 * 정식 스키마 파싱 대신 단락 구분 태그를 줄바꿈으로 바꾸고 나머지 태그를 제거한다.
 */
internal object XmlTextExtractor {

    /** 단락/줄바꿈으로 간주할 닫는/단독 태그(접두사 무시). */
    private val paragraphBreaks = listOf(
        "</w:p>", "<w:br/>", "<w:br />",       // DOCX
        "</hp:p>", "</p>", "<hp:lineBreak/>"   // HWPX
    )
    private val tabTags = listOf("<w:tab/>", "<w:tab />")

    fun extract(xml: String): String {
        var s = xml
        for (b in paragraphBreaks) s = s.replace(b, "\n", ignoreCase = true)
        for (t in tabTags) s = s.replace(t, "\t", ignoreCase = true)
        s = TAG_REGEX.replace(s, "")
        s = unescape(s)
        return s.lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private val TAG_REGEX = Regex("<[^>]*>")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
