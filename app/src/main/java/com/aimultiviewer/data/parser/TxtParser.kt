package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TxtParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.TXT

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val text = IoUtils.decodeText(IoUtils.readBytes(context, uri))
            DocumentContent(plainText = text, isMarkdown = false)
        }
}
