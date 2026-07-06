package com.pythontopia.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 统一错误态：展示 [message] 与 [onRetry] 重试按钮，供各屏失败反馈复用。 */
@Composable
fun PytErrorRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "重试",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PytSpace.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Text(
            text = message,
            color = PytColors.danger,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onRetry,
            shape = PytShape.md,
            colors = ButtonDefaults.buttonColors(
                containerColor = PytColors.primary,
                contentColor = PytColors.onPrimary,
            ),
        ) {
            Text(retryLabel)
        }
    }
}

/** 统一加载占位（进度圈 + 可选 [label]）。 */
@Composable
fun PytLoading(
    modifier: Modifier = Modifier,
    label: String = "加载中…",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PytSpace.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        CircularProgressIndicator(color = PytColors.primary)
        Text(
            text = label,
            color = PytColors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
