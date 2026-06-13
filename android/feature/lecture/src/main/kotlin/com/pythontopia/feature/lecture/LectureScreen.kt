package com.pythontopia.feature.lecture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

@Composable
fun LectureScreen(viewModel: LectureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(horizontal = PytSpace.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = PytSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            if (state.level == LectureLevel.READER) {
                Surface(
                    color = PytColors.bgSoft,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(2.dp, PytColors.borderStrong),
                    modifier = Modifier.clickable(onClick = viewModel::back),
                ) {
                    Text(
                        "← 讲义列表",
                        color = PytColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                if (state.level == LectureLevel.READER) state.selected?.title.orEmpty() else "讲义阅读",
                color = PytColors.textPrimary,
                fontSize = 16.sp,
            )
        }

        when {
            state.loading -> PytLoading()
            state.level == LectureLevel.READER -> Reader(state)
            else -> ListView(state, viewModel)
        }
    }
}

@Composable
private fun ListView(state: LectureUiState, vm: LectureViewModel) {
    if (state.error != null) {
        Text(state.error, color = PytColors.textHint, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s3))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        items(state.items) { item ->
            Surface(
                color = PytColors.bgElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.fillMaxWidth().clickable { vm.openResource(item.id) },
            ) {
                Column(modifier = Modifier.padding(PytSpace.s3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title.ifBlank { "讲义 #${item.id}" },
                            color = PytColors.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        GroundingBadge(item.groundingStatus)
                    }
                    if (item.summary.isNotBlank()) {
                        Text(item.summary, color = PytColors.textHint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (item.kcCodes.isNotEmpty()) {
                        Text(
                            item.kcCodes.joinToString(" · "),
                            color = PytColors.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Reader(state: LectureUiState) {
    val resource = state.selected ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = PytSpace.s5),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2), verticalAlignment = Alignment.CenterVertically) {
            GroundingBadge(resource.groundingStatus)
            if (resource.evidencePageIds.isNotEmpty()) {
                Text(
                    "引用课件页 " + resource.evidencePageIds.joinToString(", ") { "P$it" },
                    color = PytColors.textHint,
                    fontSize = 11.sp,
                )
            }
        }
        if (resource.summary.isNotBlank()) {
            Text(resource.summary, color = PytColors.textMuted, fontSize = 13.sp)
        }

        val handout = state.handout
        if (handout == null) {
            Surface(
                color = PytColors.bgElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "本讲义暂无结构化正文（可能为其它资源类型）。",
                    color = PytColors.textHint,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(PytSpace.s3),
                )
            }
        } else {
            handout.sections.forEachIndexed { index, section ->
                SectionCard(index + 1, section)
            }
        }
    }
}

@Composable
private fun SectionCard(order: Int, section: HandoutSection) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Text("$order. ${section.heading}", color = PytColors.textPrimary, fontSize = 16.sp)
            section.paragraphs.forEach { para ->
                Text(para, color = PytColors.textPrimary, fontSize = 14.sp)
            }
            section.codeSnippets.forEach { snippet ->
                Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        snippet,
                        color = PytColors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(PytSpace.s2),
                    )
                }
            }
            if (section.pageRefs.isNotEmpty()) {
                Text(
                    "课件依据 " + section.pageRefs.joinToString(", ") { "P$it" },
                    color = PytColors.success,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GroundingBadge(status: String?) {
    val label = groundingLabel(status)
    if (label.isBlank()) return
    val color = when (status) {
        "PASSED" -> PytColors.success
        "NO_EVIDENCE" -> PytColors.info
        else -> PytColors.warning
    }
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
        Text(label, color = color, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
