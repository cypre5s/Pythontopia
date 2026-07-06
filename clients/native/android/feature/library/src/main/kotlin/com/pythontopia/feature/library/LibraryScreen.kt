package com.pythontopia.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(horizontal = PytSpace.s3),
    ) {
        CrumbBar(
            crumb = state.crumb,
            canBack = state.level != LibraryLevel.CHAPTERS,
            onBack = viewModel::back,
        )

        QaPanel(state, viewModel)

        when {
            state.loading -> PytLoading()
            else -> when (state.level) {
                LibraryLevel.CHAPTERS -> ChapterList(state, viewModel)
                LibraryLevel.KCS -> KcList(state, viewModel)
                LibraryLevel.PAGES -> PageList(state, viewModel)
                LibraryLevel.READER -> PageReader(state)
            }
        }
    }
}

@Composable
private fun CrumbBar(crumb: String, canBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = PytSpace.s2),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        if (canBack) {
            Surface(
                color = PytColors.bgSoft,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.clickable(onClick = onBack),
            ) {
                Text(
                    "← 返回",
                    color = PytColors.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(crumb, color = PytColors.textPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun ErrorOrList(error: String?, content: @Composable () -> Unit) {
    if (error != null) {
        Text(error, color = PytColors.textHint, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s3))
    } else {
        content()
    }
}

@Composable
private fun QaPanel(state: LibraryUiState, vm: LibraryViewModel) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth().padding(bottom = PytSpace.s2),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3)) {
            Text(
                "课件问答 · RAG" + (state.selectedKc?.let { " · ${it.kcName}" } ?: " · 全课程"),
                color = PytColors.textMuted,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1),
                horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Surface(
                    color = PytColors.bgSoft,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(2.dp, PytColors.borderStrong),
                    modifier = Modifier.weight(1f),
                ) {
                    BasicTextField(
                        value = state.askQuery,
                        onValueChange = vm::onAskChange,
                        singleLine = true,
                        textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(PytColors.warning),
                        decorationBox = { inner ->
                            if (state.askQuery.isEmpty()) {
                                Text("就课件内容提问，如：for 循环怎么用", color = PytColors.textHint, fontSize = 13.sp)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth().padding(PytSpace.s2),
                    )
                }
                Button(
                    onClick = vm::ask,
                    enabled = !state.asking && state.askQuery.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(if (state.asking) "检索中…" else "问")
                }
            }
            state.askError?.let {
                Text(it, color = PytColors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s1))
            }
            if (state.answerMd.isNotBlank()) {
                Surface(
                    color = PytColors.bgSunken,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s2),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(PytSpace.s2),
                    ) {
                        Text(state.answerMd, color = PytColors.textPrimary, fontSize = 13.sp)
                        if (state.citations.isNotEmpty()) {
                            Text(
                                "引用课件页 " + state.citations.joinToString(" · ") { "P${it.pageNo}" },
                                color = PytColors.success,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = PytSpace.s1),
                            )
                        }
                    }
                }
            } else if (state.noEvidence) {
                Text("未命中课件证据，换个问法或先选定知识点。", color = PytColors.textHint, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s1))
            }
        }
    }
}

@Composable
private fun ChapterList(state: LibraryUiState, vm: LibraryViewModel) {
    ErrorOrList(state.error) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            items(state.chapters) { ch ->
                RowCard(
                    title = "第 ${ch.orderIndex} 章 · ${ch.title}",
                    subtitle = ch.summaryMarkdown,
                    onClick = { vm.openChapter(ch) },
                )
            }
        }
    }
}

@Composable
private fun KcList(state: LibraryUiState, vm: LibraryViewModel) {
    ErrorOrList(state.error) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            items(state.kcs) { kc ->
                RowCard(
                    title = kc.kcName,
                    subtitle = kc.summaryMarkdown,
                    onClick = { vm.openKc(kc) },
                )
            }
        }
    }
}

@Composable
private fun PageList(state: LibraryUiState, vm: LibraryViewModel) {
    ErrorOrList(state.error) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            items(state.pages) { p ->
                RowCard(
                    title = "P${p.pageNo} ${p.title.orEmpty()}".trim(),
                    subtitle = p.excerpt ?: p.contentMarkdown?.take(60),
                    onClick = { vm.openPage(p) },
                )
            }
        }
    }
}

@Composable
private fun PageReader(state: LibraryUiState) {
    val page = state.selectedPage ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = PytSpace.s5),
    ) {
        Text(
            text = page.title ?: "第 ${page.pageNo} 页",
            color = PytColors.textPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = PytSpace.s2),
        )
        // PDF 渲染页图（后端 PNG）：优先展示原版课件页；加载中或失败降级到 markdown 文本。
        when {
            state.pageImage != null -> Surface(
                color = PytColors.bgElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap = state.pageImage,
                    contentDescription = page.title ?: "课件第 ${page.pageNo} 页",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(PytSpace.s1),
                )
            }
            state.pageImageLoading -> Text(
                "正在加载课件页图…",
                color = PytColors.textHint,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = PytSpace.s2),
            )
        }
        if (!page.contentMarkdown.isNullOrBlank()) {
            Surface(
                color = PytColors.bgElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s2),
            ) {
                Text(
                    text = page.contentMarkdown,
                    color = PytColors.textPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(PytSpace.s3),
                )
            }
        } else if (state.pageImage == null && !state.pageImageLoading) {
            Text("（本页无文本内容）", color = PytColors.textHint, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s2))
        }
    }
}

@Composable
private fun RowCard(title: String, subtitle: String?, onClick: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3)) {
            Text(title, color = PytColors.textPrimary, fontSize = 14.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle.trim(),
                    color = PytColors.textHint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
