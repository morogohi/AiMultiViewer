package com.aimultiviewer.domain.model

data class Document(
    val id: String,
    val uri: String,
    val name: String,
    val format: DocFormat,
    val importedAt: Long
)
