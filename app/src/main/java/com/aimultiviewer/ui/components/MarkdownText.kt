package com.aimultiviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 의존성 없는 경량 Markdown 렌더러.
 * 지원: #~###### 제목, - / * / 1. 목록, **굵게**, *기울임*, `인라인 코드`.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        markdown.replace("\r", "").split("\n").forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.height(8.dp))
                line.startsWith("### ") -> Text(
                    text = inline(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleMedium
                )
                line.startsWith("## ") -> Text(
                    text = inline(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleLarge
                )
                line.startsWith("# ") -> Text(
                    text = inline(line.removePrefix("# ")),
                    style = MaterialTheme.typography.headlineSmall
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = inline("• " + line.drop(2)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Regex("^\\d+\\.\\s").containsMatchIn(line) -> Text(
                    text = inline(line),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                else -> Text(
                    text = inline(line),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
