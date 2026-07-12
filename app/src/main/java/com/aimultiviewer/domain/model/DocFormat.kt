package com.aimultiviewer.domain.model

enum class DocFormat(val label: String, val extensions: List<String>) {
    HWP("HWP", listOf("hwp")),
    HWPX("HWPX", listOf("hwpx")),
    DOC("DOC", listOf("doc")),
    DOCX("DOCX", listOf("docx")),
    PDF("PDF", listOf("pdf")),
    TXT("TXT", listOf("txt", "log", "csv")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromName(name: String): DocFormat {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: UNKNOWN
        }
    }
}
