package com.aimultiviewer.domain.model

/**
 * PPTX 슬라이드의 시각적 레이아웃 모델.
 * 좌표/크기는 슬라이드 전체 대비 비율(0..1)로 정규화한다.
 */
data class SlideDeck(
    /** 슬라이드 원본 크기 (EMU, 914400/inch) */
    val widthEmu: Long,
    val heightEmu: Long,
    val slides: List<Slide>
) {
    val aspectRatio: Float get() = if (heightEmu > 0) widthEmu.toFloat() / heightEmu else 16f / 9f
    /** 슬라이드 폭 (pt) — 글꼴 크기 스케일 계산용 */
    val widthPt: Float get() = widthEmu / 12700f
}

data class Slide(
    val number: Int,
    val elements: List<SlideElement>,
    val note: String? = null
)

sealed interface SlideElement {
    val x: Float
    val y: Float
    val w: Float
    val h: Float

    data class TextBox(
        override val x: Float, override val y: Float,
        override val w: Float, override val h: Float,
        val paragraphs: List<SlidePara>
    ) : SlideElement

    data class Picture(
        override val x: Float, override val y: Float,
        override val w: Float, override val h: Float,
        /** 캐시에 추출된 이미지 파일 경로 */
        val path: String
    ) : SlideElement

    data class TableBox(
        override val x: Float, override val y: Float,
        override val w: Float, override val h: Float,
        val rows: List<List<String>>
    ) : SlideElement
}

data class SlidePara(
    val runs: List<SlideRun>,
    val bullet: Boolean = false,
    val level: Int = 0,
    /** "l" | "ctr" | "r" | null */
    val align: String? = null
) {
    val text: String get() = runs.joinToString("") { it.text }
}

data class SlideRun(
    val text: String,
    /** 글꼴 크기 (pt). null이면 기본값 사용 */
    val sizePt: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** 0xRRGGBB, null이면 기본 색 */
    val colorRgb: Int? = null
)
