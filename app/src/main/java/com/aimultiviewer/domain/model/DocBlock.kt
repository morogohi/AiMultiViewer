package com.aimultiviewer.domain.model

/**
 * 구조화된 문서 블록. 파서가 제목/문단/표 구조를 보존해 산출하면
 * 뷰어가 포맷에 맞게(제목 강조, 표 그리드 등) 렌더링한다.
 */
sealed interface DocBlock {
    /** 제목. level 1(가장 큼)~4 */
    data class Heading(val text: String, val level: Int = 2) : DocBlock

    data class Para(val text: String, val bullet: Boolean = false) : DocBlock

    /** 표. rows[행][열] */
    data class Table(val rows: List<List<String>>) : DocBlock

    /** 부가 정보(발표자 노트, 잘림 안내 등) */
    data class Note(val text: String) : DocBlock
}

/** AI/위키 수집용 평문 변환 */
fun List<DocBlock>.toPlainText(): String = buildString {
    for (b in this@toPlainText) {
        when (b) {
            is DocBlock.Heading -> append(b.text).append('\n')
            is DocBlock.Para -> append(if (b.bullet) "• " else "").append(b.text).append('\n')
            is DocBlock.Note -> append(b.text).append('\n')
            is DocBlock.Table -> b.rows.forEach { r -> append(r.joinToString("\t")).append('\n') }
        }
    }
}.trim()
