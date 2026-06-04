package com.pythontopia.feature.classroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.common.learner.StudentRole
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * `:feature:classroom` 主屏：学生 + 教师视角统一入口。
 *
 * 双视角共用顶部 [TabRow]，标签按 [role] 动态裁剪：
 *  - 学生：`我的班级` + `公告板`
 *  - 教师：`我的班级` + `教师看板`
 *
 * 学生与教师的"我的班级" tab UI 一致（列出班级 + 选中态），所以 [ClassroomListPanel]
 * 同时服务两边；公告板 / 看板各自独立 panel。
 *
 * Role 判定：当前未持有 SessionRepository，role 由外层导航在 NavGraph 里通过参数注入，
 * 调用方写 `ClassroomScreen(role = currentRole)`。默认 [StudentRole.STUDENT] 让
 * R-12 演示能直接 `ClassroomScreen()` 跑起来。
 *
 * @param role 当前用户角色；驱动 ViewModel `bind()` 选择哪条分支。
 * @param viewModel Hilt 注入的 [ClassroomViewModel]，测试时可手动传 mock。
 */
@Composable
fun ClassroomScreen(
    role: StudentRole = StudentRole.STUDENT,
    viewModel: ClassroomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var joinDialogVisible by remember { mutableStateOf(false) }
    var evidenceQuest by remember { mutableStateOf<ClassroomQuest?>(null) }

    LaunchedEffect(role) { viewModel.bind(role) }

    val tabs = remember(role) {
        when (role) {
            StudentRole.STUDENT -> listOf("我的班级", "公告板")
            StudentRole.TEACHER, StudentRole.ADMIN -> listOf("我的班级", "教师看板")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s4),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Text(
            text = "班级",
            style = MaterialTheme.typography.headlineMedium,
            color = PytColors.textPrimary,
        )

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(label) },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (val current = state) {
                is ClassroomUiState.Loading -> LoadingPlaceholder(current.message)
                is ClassroomUiState.Failure -> FailurePlaceholder(
                    code = current.code,
                    message = current.message,
                    onDismiss = viewModel::dismissFailure,
                )
                is ClassroomUiState.StudentState -> StudentTabContent(
                    tabIndex = selectedTabIndex,
                    state = current,
                    onSelectClassroom = viewModel::selectClassroom,
                    onShowJoinDialog = { joinDialogVisible = true },
                    onSubmitEvidence = { quest -> evidenceQuest = quest },
                )
                is ClassroomUiState.TeacherState -> TeacherTabContent(
                    tabIndex = selectedTabIndex,
                    state = current,
                    onSelectClassroom = viewModel::selectClassroom,
                )
            }
        }
    }

    JoinClassroomDialog(
        visible = joinDialogVisible && state is ClassroomUiState.StudentState,
        pending = (state as? ClassroomUiState.StudentState)?.pendingJoin == true,
        onDismiss = { joinDialogVisible = false },
        onConfirm = { code ->
            viewModel.join(code)
            joinDialogVisible = false
        },
    )

    QuestEvidenceDialog(
        quest = evidenceQuest,
        onDismiss = { evidenceQuest = null },
        onConfirm = { quest, evidence ->
            evidenceQuest = null
            viewModel.submitEvidence(quest, evidence)
        },
    )

    val messageState = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        val msg = when (val s = state) {
            is ClassroomUiState.StudentState -> s.message
            is ClassroomUiState.TeacherState -> s.message
            else -> null
        }
        if (msg != null) {
            messageState.value = msg
            viewModel.consumeMessage()
        }
    }
    val toastMessage = messageState.value
    if (toastMessage != null) {
        AlertDialog(
            onDismissRequest = { messageState.value = null },
            title = { Text("提示") },
            text = { Text(toastMessage) },
            confirmButton = {
                TextButton(onClick = { messageState.value = null }) { Text("好的") }
            },
        )
    }
}

@Composable
private fun StudentTabContent(
    tabIndex: Int,
    state: ClassroomUiState.StudentState,
    onSelectClassroom: (Long) -> Unit,
    onShowJoinDialog: () -> Unit,
    onSubmitEvidence: (ClassroomQuest) -> Unit,
) {
    when (tabIndex) {
        0 -> ClassroomListPanel(
            classrooms = state.classrooms,
            selectedId = state.selectedClassroomId,
            onSelect = onSelectClassroom,
            footer = {
                Button(onClick = onShowJoinDialog, enabled = !state.pendingJoin) {
                    Text(if (state.pendingJoin) "加入中…" else "用邀请码加入新班级")
                }
            },
        )
        1 -> StudentBoardPanel(
            quests = state.quests,
            submittingQuestId = state.submittingQuestId,
            onSubmit = onSubmitEvidence,
        )
    }
}

@Composable
private fun TeacherTabContent(
    tabIndex: Int,
    state: ClassroomUiState.TeacherState,
    onSelectClassroom: (Long) -> Unit,
) {
    when (tabIndex) {
        0 -> ClassroomListPanel(
            classrooms = state.classrooms,
            selectedId = state.selectedClassroomId,
            onSelect = onSelectClassroom,
            footer = null,
        )
        1 -> TeacherDashboardPanel(
            quests = state.quests,
            heatmap = state.heatmap,
            errorHotspots = state.errorHotspots,
        )
    }
}

@Composable
private fun QuestEvidenceDialog(
    quest: ClassroomQuest?,
    onDismiss: () -> Unit,
    onConfirm: (ClassroomQuest, QuestEvidence) -> Unit,
) {
    if (quest == null) return
    var evidenceValue by remember(quest.id) { mutableStateOf("") }
    var note by remember(quest.id) { mutableStateOf("") }
    val evidenceType = quest.requiredEvidenceType
    val needsStructuredEvidence = evidenceType != null
    val valueValid = evidenceValueValid(evidenceType, evidenceValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交任务证据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PytColors.textPrimary,
                )
                if (needsStructuredEvidence) {
                    OutlinedTextField(
                        value = evidenceValue,
                        onValueChange = { evidenceValue = it.trim() },
                        label = { Text(evidenceInputLabel(evidenceType)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (evidenceType == "kc_mastery") KeyboardType.Decimal else KeyboardType.Number,
                        ),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("补充说明（可选）") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(quest, evidenceFromInput(evidenceType, evidenceValue, note)) },
                enabled = valueValid,
            ) { Text("提交") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ClassroomListPanel(
    classrooms: List<Classroom>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    footer: (@Composable () -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        if (classrooms.isEmpty()) {
            Text(
                text = "暂无班级",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textHint,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
            ) {
                items(classrooms, key = { it.classroomId }) { classroom ->
                    ClassroomRow(
                        classroom = classroom,
                        selected = classroom.classroomId == selectedId,
                        onSelect = { onSelect(classroom.classroomId) },
                    )
                }
            }
        }
        if (footer != null) footer()
    }
}

@Composable
private fun ClassroomRow(
    classroom: Classroom,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val bg = if (selected) PytColors.bgElevated else PytColors.bgSoft
    val border = if (selected) PytColors.borderStrong else PytColors.borderSoft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(PytSpace.s3),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = classroom.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = PytColors.textPrimary,
                )
                AssistChip(
                    onClick = onSelect,
                    label = { Text(if (selected) "已选" else "选中") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = border,
                        labelColor = PytColors.onPrimary,
                    ),
                )
            }
            Text(
                text = "邀请码 ${classroom.joinCode} · 状态 ${classroom.status}",
                style = MaterialTheme.typography.labelSmall,
                color = PytColors.textMuted,
            )
        }
    }
}

@Composable
private fun StudentBoardPanel(
    quests: List<ClassroomQuest>,
    submittingQuestId: Long?,
    onSubmit: (ClassroomQuest) -> Unit,
) {
    if (quests.isEmpty()) {
        Text(
            text = "公告板暂无任务",
            style = MaterialTheme.typography.bodyMedium,
            color = PytColors.textHint,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s1),
    ) {
        items(quests, key = { it.id }) { quest ->
            ClassroomQuestCard(
                quest = quest,
                showSubmitButton = true,
                isSubmitting = submittingQuestId != null && submittingQuestId == quest.studentQuestId,
                onSubmit = { onSubmit(quest) },
            )
        }
    }
}

@Composable
private fun TeacherDashboardPanel(
    quests: List<ClassroomQuest>,
    heatmap: List<KcHeatmapEntry>,
    errorHotspots: List<ErrorHotspot>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        item {
            Text(
                text = "KC 热力图",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
            KcHeatmapView(entries = heatmap)
        }
        if (errorHotspots.isNotEmpty()) {
            item {
                Text(
                    text = "高频误区 Top ${errorHotspots.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PytColors.textPrimary,
                )
            }
            items(errorHotspots, key = { it.kcCode }) { hotspot ->
                Text(
                    text = "· ${hotspot.kcName}：失败 ${hotspot.failedSubmissionCount} 次，影响 ${hotspot.affectedStudentCount} 人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PytColors.warning,
                )
            }
        }
        item {
            Text(
                text = "已发布任务（${quests.size}）",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
        }
        items(quests, key = { it.id }) { quest ->
            ClassroomQuestCard(
                quest = quest,
                showSubmitButton = false,
                isSubmitting = false,
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder(message: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            CircularProgressIndicator(color = PytColors.primary)
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PytColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun FailurePlaceholder(code: String, message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Text(
                text = "出错了：$code",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.danger,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            Button(onClick = onDismiss) { Text("重试") }
        }
    }
}



private fun evidenceValueValid(type: String?, raw: String): Boolean = when (type) {
    null -> true
    "submission", "resource", "page_read" -> raw.toLongOrNull() != null
    "kc_mastery" -> raw.toDoubleOrNull()?.let { it in 0.0..1.0 } == true
    else -> raw.isNotBlank()
}

private fun evidenceFromInput(type: String?, raw: String, note: String): QuestEvidence {
    val cleanNote = note.trim().ifBlank { null }
    return when (type) {
        "submission" -> QuestEvidence(note = cleanNote, submissionId = raw.toLongOrNull())
        "resource" -> QuestEvidence(note = cleanNote, resourceId = raw.toLongOrNull())
        "page_read" -> QuestEvidence(note = cleanNote, pageReadId = raw.toLongOrNull())
        "kc_mastery" -> QuestEvidence(note = cleanNote, kcMasteryDelta = raw.toDoubleOrNull())
        else -> QuestEvidence(note = cleanNote)
    }
}

private fun evidenceInputLabel(type: String?): String = when (type) {
    "submission" -> "练习提交 ID"
    "resource" -> "资源记录 ID"
    "page_read" -> "课件阅读记录 ID"
    "kc_mastery" -> "KC 掌握度提升（0.0 - 1.0）"
    else -> "证据值"
}
