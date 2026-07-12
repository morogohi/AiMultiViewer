package com.aimultiviewer.domain.model

/**
 * 모든 파서가 산출하는 공통(정규화) 표현.
 * 뷰어와 AI 기능은 원본 포맷과 무관하게 이 모델만 사용한다.
 */
data class DocumentContent(
    val plainText: String,
    val isMarkdown: Boolean = false,
    /** PDF처럼 페이지 렌더가 필요한 경우 원본 Uri를 보관 */
    val renderableUri: String? = null,
    /** 이미지 문서인 경우 원본 이미지 Uri (plainText에는 OCR 결과가 담김) */
    val renderableImageUri: String? = null,
    val pageCount: Int = 0,
    val warning: String? = null
)
