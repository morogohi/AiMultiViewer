package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent

/**
 * 구 바이너리 포맷(HWP, DOC) 임시 처리.
 * - HWP(한컴 OLE 바이너리), DOC(MS OLE 바이너리)는 별도 네이티브 파서가 필요(로드맵 M2).
 * - 현재는 안내 메시지를 반환해 앱이 깨지지 않도록 한다.
 */
class UnsupportedBinaryParser : DocumentParser {
    override fun supports(format: DocFormat) =
        format == DocFormat.HWP || format == DocFormat.DOC

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent {
        val hint = when (format) {
            DocFormat.HWP -> "HWP(구 바이너리) 파싱은 다음 업데이트에서 지원됩니다. 한/글에서 HWPX로 저장 후 열어주세요."
            DocFormat.DOC -> "DOC(구 바이너리) 파싱은 다음 업데이트에서 지원됩니다. Word에서 DOCX로 저장 후 열어주세요."
            else -> "지원하지 않는 형식입니다."
        }
        return DocumentContent(plainText = "", warning = hint)
    }
}
