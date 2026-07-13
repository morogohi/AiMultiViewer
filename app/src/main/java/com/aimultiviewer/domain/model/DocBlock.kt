package com.aimultiviewer.domain.model

/**
 * 구조화된 문서 블록. 파서가 제목/문단/표 구조를 보존해 산출하면
 * 뷰어가 포맷에 맞게(제목 강조, 표 그리드 등) 렌더링한다.
 */
sealed interface DocBlock {
    /** 제목. level 1(가장 큼)~4 */
    data class Heading(val text: String, val level: Int = 2) : DocBlock

    data class Para(val text: String, val bullet: Boolean = false) : DocBlock

    /**
     * 서식(글자 크기/굵기/기울임/색)과 정렬을 보존한 문단.
     * align: 0=양쪽, 1=왼쪽, 2=오른쪽, 3=가운데
     */
    data class RichPara(val runs: List<RichRun>, val align: Int = 1) : DocBlock {
        val text: String get() = runs.joinToString("") { it.text }
    }

    /** 표. rows[행][열] */
    data class Table(val rows: List<List<String>>) : DocBlock

    /** 본문 삽입 이미지 (캐시로 추출된 파일 경로) */
    data class Image(val path: String) : DocBlock

    /** 부가 정보(발표자 노트, 잘림 안내 등) */
    data class Note(val text: String) : DocBlock
}

/** RichPara 내 서식 런 */
data class RichRun(
    val text: String,
    /** 글꼴 크기(pt). null이면 기본 */
    val sizePt: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** 0xRRGGBB. null이면 기본 색 */
    val colorRgb: Int? = null
)

/** AI/위키 수집용 평문 변환 */
fun List<DocBlock>.toPlainText(): String = buildString {
    for (b in this@toPlainText) {
        when (b) {
            is DocBlock.Heading -> append(b.text).append('\n')
            is DocBlock.Para -> append(if (b.bullet) "• " else "").append(b.text).append('\n')
            is DocBlock.RichPara -> append(b.text).append('\n')
            is DocBlock.Note -> append(b.text).append('\n')
            is DocBlock.Table -> b.rows.forEach { r -> append(r.joinToString("\t")).append('\n') }
            is DocBlock.Image -> {}
        }
    }
}.trim()
