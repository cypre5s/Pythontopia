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

/** 邀请码加入班级对话框；本地校验 `^[A-Z0-9]{6}$`，[pending] 期间禁用提交。 */
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
