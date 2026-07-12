package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 이미지(JPG/PNG/WEBP 등) 판독.
 * 화면에는 원본 이미지를 표시하고, ML Kit 온디바이스 OCR(한국어+라틴)로
 * 텍스트를 추출해 AI 분석에 사용한다. 네트워크 불필요.
 */
class ImageParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.IMAGE

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            var text = ""
            var warning: String? = null
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(
                    KoreanTextRecognizerOptions.Builder().build()
                )
                val result = recognizer.process(image).await()
                text = result.textBlocks.joinToString("\n\n") { block ->
                    block.lines.joinToString("\n") { it.text }
                }
                if (text.isBlank()) {
                    warning = "이미지에서 텍스트를 찾지 못했습니다. (사진·그림만 있는 이미지일 수 있음)"
                }
            } catch (t: Throwable) {
                warning = "이미지 OCR 실패: ${t.message}"
            }
            DocumentContent(
                plainText = text,
                renderableImageUri = uri.toString(),
                warning = warning
            )
        }
}
