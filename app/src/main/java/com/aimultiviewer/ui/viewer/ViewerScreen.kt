package com.aimultiviewer.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimultiviewer.BuildConfig
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.ui.components.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    documentId: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(documentId) { viewModel.load(documentId) }
    LaunchedEffect(state.editMessage) {
        state.editMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearEditMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI 멀티뷰어 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            (if (editing) "편집: " else "") + (state.document?.name ?: "문서"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // 일반 텍스트(TXT/MD)만 편집 가능 — 그 외 포맷은 뷰어 전용
                    if (viewModel.isEditable && tab == 0 && !state.loading) {
                        if (editing) {
                            IconButton(onClick = { editing = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "편집 취소")
                            }
                            IconButton(onClick = {
                                viewModel.saveText(editText)
                                editing = false
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "저장")
                            }
                        } else {
                            IconButton(onClick = {
                                editText = state.content?.plainText.orEmpty()
                                editing = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "편집")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!editing) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("보기") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("AI") })
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    editing -> OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions.Default
                    )
                    tab == 0 -> DocumentContentView(state)
                    else -> AiPanel(
                        state = state,
                        otherDocuments = viewModel.otherDocuments,
                        onSummarize = viewModel::summarize,
                        onAsk = viewModel::ask,
                        onMinutes = viewModel::minutes,
                        onTechDoc = viewModel::analyzeTechDoc,
                        onCompare = viewModel::compareWith
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentContentView(state: ViewerUiState) {
    val content = state.content
    if (content == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("내용이 없습니다.") }
        return
    }

    Column(Modifier.fillMaxSize()) {
        content.warning?.let { w ->
            Text(
                text = "⚠ $w",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }
        when {
            content.renderableImageUri != null ->
                ImageViewer(
                    uriString = content.renderableImageUri,
                    ocrText = content.plainText,
                    modifier = Modifier.fillMaxSize()
                )

            content.renderableUri != null -> ZoomableCanvas {
                PdfViewer(uriString = content.renderableUri, modifier = Modifier.fillMaxSize())
            }

            content.slideDeck != null -> ZoomableCanvas {
                SlideDeckViewer(deck = content.slideDeck, modifier = Modifier.fillMaxSize())
            }

            content.isMarkdown -> ZoomableDoc {
                MarkdownText(
                    markdown = content.plainText,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                )
            }

            content.blocks != null -> ZoomableDoc {
                StructuredDocView(
                    blocks = content.blocks,
                    modifier = Modifier.fillMaxSize(),
                    pageStyle = state.document?.format in
                        setOf(DocFormat.HWP, DocFormat.HWPX, DocFormat.DOCX)
                )
            }

            else -> ZoomableDoc {
                Text(
                    text = content.plainText.ifBlank { "표시할 텍스트가 없습니다." },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                )
            }
        }
    }
}
