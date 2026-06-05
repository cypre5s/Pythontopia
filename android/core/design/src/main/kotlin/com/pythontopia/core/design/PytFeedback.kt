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

/**
 * 复用的「错误 + 重试」反馈块。
 *
 * 目标：消灭各 feature 里 `runCatching{}.getOrNull()` 静默吞错导致的白屏——失败时给出
 * 明确文案 + 重试按钮。任何屏的错误态都应改用本组件，统一交互与视觉（星露谷棕金）。
 */
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

/** 复用的加载占位块。 */
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
