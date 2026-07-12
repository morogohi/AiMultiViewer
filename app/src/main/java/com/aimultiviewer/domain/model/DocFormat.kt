package com.aimultiviewer.domain.model

enum class DocFormat(val label: String, val extensions: List<String>) {
    HWP("HWP", listOf("hwp")),
    HWPX("HWPX", listOf("hwpx")),
    DOC("DOC", listOf("doc")),
    DOCX("DOCX", listOf("docx")),
    PPTX("PPTX", listOf("pptx")),
    XLSX("XLSX", listOf("xlsx")),
    ODF("OpenDocument", listOf("odt", "ods", "odp")),
    PDF("PDF", listOf("pdf")),
    TXT("TXT", listOf("txt", "log", "csv")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
    IMAGE("이미지", listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")),
    /** Google Docs/Sheets/Slides — Drive의 가상 파일. PDF로 내보내 판독한다. */
    GOOGLE("Google 문서", emptyList()),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromName(name: String): DocFormat {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: UNKNOWN
        }

        /** 확장자로 판별 실패 시 MIME 타입으로 판별 (Drive의 Google 문서, 확장자 없는 이미지 등). */
        fun fromMime(mime: String?): DocFormat = when {
            mime == null -> UNKNOWN
            mime.startsWith("application/vnd.google-apps.") -> GOOGLE
            mime.startsWith("image/") -> IMAGE
            mime == "application/pdf" -> PDF
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
            mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX
            mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX
            mime == "application/vnd.oasis.opendocument.text" -> ODF
            mime == "application/vnd.oasis.opendocument.spreadsheet" -> ODF
            mime == "application/vnd.oasis.opendocument.presentation" -> ODF
            mime.startsWith("text/") -> TXT
            else -> UNKNOWN
        }
    }
}
