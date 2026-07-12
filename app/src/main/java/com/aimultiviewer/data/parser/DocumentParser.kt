package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent

/**
 * 포맷별 파서 공통 인터페이스.
 * 새 포맷 지원은 이 인터페이스 구현체를 [ParserRegistry]에 추가하면 된다.
 */
interface DocumentParser {
    fun supports(format: DocFormat): Boolean
    suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent
}
