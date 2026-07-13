package com.aimultiviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val initial = remember { viewModel.snapshot() }
    var cloudEnabled by remember { mutableStateOf(initial.cloudEnabled) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var wikiExport by remember { mutableStateOf(initial.wikiAutoExport) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("클라우드 AI", style = MaterialTheme.typography.titleMedium)
            Text(
                "끄면 요약은 온디바이스로 동작합니다. 켜면 질의응답·비교·회의록·기술분석 등 고품질 기능이 활성화됩니다. " +
                    "OpenAI 호환 API를 지원합니다.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("클라우드 AI 사용")
                Switch(checked = cloudEnabled, onCheckedChange = { cloudEnabled = it; saved = false })
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("모델명 (예: gpt-4o-mini)") },
                singleLine = true
            )

            Text("llm-wiki 자동 수집", style = MaterialTheme.typography.titleMedium)
            Text(
                "열람한 문서를 Obsidian 호환 마크다운(요약+전문)으로 변환해 " +
                    "기기 Documents/llm-wiki/ 폴더에 자동 축적합니다. " +
                    "PC의 llm-wiki 볼트로는 동기화 스크립트로 수집합니다.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("열람 문서 자동 수집")
                Switch(checked = wikiExport, onCheckedChange = { wikiExport = it; saved = false })
            }

            Button(
                onClick = {
                    viewModel.save(SettingsSnapshot(cloudEnabled, baseUrl, apiKey, model, wikiExport))
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saved) "저장됨 ✓" else "저장") }

            Text(
                "지원 문서: HWP · HWPX · DOCX · PPTX · XLSX · PDF · ODF · 이미지(OCR) · Google 문서 · TXT · Markdown",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
