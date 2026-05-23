package com.pythontopia.feature.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.pythontopia.core.design.PytSpace

/**
 * 学生用"邀请码加入班级"对话框。
 *
 * UX 设计：
 *  - 单输入框 + 6 位预校验：本地拦掉明显非法输入，减轻后端无效请求；
 *  - 输入框强制大写（[KeyboardCapitalization.Characters]）与后端 `^[A-Z0-9]{6}$` 对齐；
 *  - "加入"按钮在 [pending] 期间禁用并改文案为"加入中…"，避免双击；
 *  - "取消"始终可点击，即使在 pending 也允许学生放弃（实际网络请求继续，但 UI 关闭）。
 *
 * 不在此组件内做角色判定（仅学生可见入口由调用方 [ClassroomScreen] 控制），保持本组件
 * 纯展示，便于 Roborazzi 截图测试。
 *
 * @param visible 是否显示；false 时直接返回不渲染，让外层不必 if 包裹。
 * @param pending 网络请求飞行中标志；来自 [ClassroomUiState.StudentState.pendingJoin]。
 * @param onDismiss 用户点击外区域或"取消"时触发；调用方负责把 visible 置 false。
 * @param onConfirm 用户输入合法 6 位码并点击"加入"时触发；参数是大写化后的邀请码。
 */
@Composable
fun JoinClassroomDialog(
    visible: Boolean,
    pending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (joinCode: String) -> Unit,
) {
    if (!visible) return

    var input by remember { mutableStateOf("") }
    val normalized = input.uppercase().take(ClassroomViewModel.JOIN_CODE_LENGTH)
    val isValid = normalized.length == ClassroomViewModel.JOIN_CODE_LENGTH &&
        normalized.all { it.isLetterOrDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入班级") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
                Text(
                    "请输入教师告诉你的 6 位邀请码（大小写不区分，数字 + 大写字母）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = normalized,
                    onValueChange = { input = it },
                    singleLine = true,
                    enabled = !pending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PytSpace.s2),
                    label = { Text("邀请码") },
                    supportingText = {
                        if (normalized.isNotEmpty() && !isValid) {
                            Text("邀请码必须是 6 位数字或大写字母")
                        }
                    },
                    isError = normalized.isNotEmpty() && !isValid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalized) },
                enabled = isValid && !pending,
            ) {
                Text(if (pending) "加入中…" else "加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
