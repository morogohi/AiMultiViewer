package com.aimultiviewer.data.parser

/**
 * OOXML(DOCX/PPTX) / OWPML(HWPX) / ODF 처럼 XML 본문에서 사람이 읽을 텍스트만 뽑아내는 경량 추출기.
 * 정식 스키마 파싱 대신 단락 구분 태그를 줄바꿈으로 바꾸고 나머지 태그를 제거한다.
 */
internal object XmlTextExtractor {

    /** 단락/줄바꿈으로 간주할 닫는/단독 태그(접두사 무시). */
    private val paragraphBreaks = listOf(
        "</w:p>", "<w:br/>", "<w:br />",        // DOCX
        "</a:p>", "<a:br/>", "<a:br />",        // PPTX (DrawingML)
        "</hp:p>", "</p>", "<hp:lineBreak/>",   // HWPX
        "</text:p>", "</text:h>", "<text:line-break/>"  // ODF
    )
    private val tabTags = listOf("<w:tab/>", "<w:tab />", "<text:tab/>")

    /** 화면에 보이지 않는 내용(필드 명령, 삭제된 텍스트, 호환용 대체 트리)은 통째로 제거. */
    private val invisibleBlocks = listOf(
        Regex("""<w:instrText[^>]*>.*?</w:instrText>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<w:delText[^>]*>.*?</w:delText>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<mc:Fallback>.*?</mc:Fallback>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<w:fldData[^>]*>.*?</w:fldData>""", RegexOption.DOT_MATCHES_ALL),
        // 그림/도형 위치 메타데이터(wp:posOffset 등) — 숫자 잡음의 원인
        Regex("""<((?:wp|wp14|a|pic|v|o|w10|w14|w15):(?:posOffset|pctPosHOffset|pctPosVOffset|pctWidth|pctHeight))[^>]*>[^<]*</\1>""")
    )

    fun extract(xml: String): String {
        var s = xml
        for (r in invisibleBlocks) s = r.replace(s, "")
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
