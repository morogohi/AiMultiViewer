package com.aimultiviewer.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimultiviewer.domain.model.Document
import com.aimultiviewer.ui.components.MarkdownText

@Composable
fun AiPanel(
    state: ViewerUiState,
    otherDocuments: List<Document>,
    onSummarize: () -> Unit,
    onAsk: (String) -> Unit,
    onMinutes: () -> Unit,
    onTechDoc: () -> Unit,
    onCompare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var question by remember { mutableStateOf("") }
    var compareMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("AI 기능", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = false, onClick = onSummarize, label = { Text("요약") })
            FilterChip(selected = false, onClick = onMinutes, label = { Text("회의록") })
            FilterChip(selected = false, onClick = onTechDoc, label = { Text("기술분석") })
            Box {
                FilterChip(
                    selected = false,
                    onClick = { compareMenu = true },
                    label = { Text("비교") }
                )
                DropdownMenu(expanded = compareMenu, onDismissRequest = { compareMenu = false }) {
                    if (otherDocuments.isEmpty()) {
                        DropdownMenuItem(text = { Text("비교할 다른 문서 없음") }, onClick = { compareMenu = false })
                    } else {
                        otherDocuments.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d.name) },
                                onClick = { compareMenu = false; onCompare(d.id) }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("문서에 질문하기 (RAG/Q&A)") },
            trailingIcon = {
                IconButton(
                    onClick = { if (question.isNotBlank()) onAsk(question) },
                    enabled = question.isNotBlank()
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "질문") }
            }
        )

        if (state.aiLoading) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("AI 처리 중…")
            }
        }

        if (state.aiOutput.isNotBlank()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (state.aiTitle.isNotBlank()) {
                        Text(state.aiTitle, style = MaterialTheme.typography.titleSmall)
                    }
                    MarkdownText(markdown = state.aiOutput, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
