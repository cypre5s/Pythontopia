package com.pythontopia.feature.retro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/** 单周复盘报告卡片；无状态展示组件，资源点击由 [onResourceClick] 透传。 */
@Composable
fun WeeklyReportCard(
    report: RetroReport,
    modifier: Modifier = Modifier,
    onResourceClick: ((ResourceDto) -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = PytColors.border,
                shape = RoundedCornerShape(12.dp),
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PytColors.bgElevated),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            CardHeader(report = report)
            MetricsRow(
                attempts = report.attempts,
                bugMineCount = report.bugMineCount,
                kcCount = report.kcGains.size,
            )
            SectionTitle(text = "知识掌握度 · Top 10")
            KcGainBarChart(gains = report.kcGains)
            SectionTitle(text = "复盘资源")
            ResourceLinkList(
                resources = report.generatedResources,
                onResourceClick = onResourceClick,
            )
        }
    }
}

@Composable
private fun CardHeader(report: RetroReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${report.weekIndex} 周复盘",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${report.periodStart} — ${report.periodEnd}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        ReadBadge(isRead = report.isRead)
    }
}

@Composable
private fun ReadBadge(isRead: Boolean) {
    val (label, color) = if (isRead) {
        "已读" to PytColors.borderSoft
    } else {
        "未读" to PytColors.warning
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = PytSpace.s2, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricsRow(attempts: Int, bugMineCount: Int, kcCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        MetricChip(modifier = Modifier.weight(1f), label = "提交", value = attempts, accent = PytColors.primary)
        MetricChip(modifier = Modifier.weight(1f), label = "矿洞", value = bugMineCount, accent = PytColors.danger)
        MetricChip(modifier = Modifier.weight(1f), label = "KC", value = kcCount, accent = PytColors.success)
    }
}

@Composable
private fun MetricChip(
    modifier: Modifier,
    label: String,
    value: Int,
    accent: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PytColors.bgSoft)
            .border(width = 1.dp, color = accent.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
            .padding(PytSpace.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = PytColors.textMuted,
        fontWeight = FontWeight.SemiBold,
    )
}
