package com.pythontopia.feature.retro

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 周复盘期间多智能体生成的学习资源列表。
 *
 * 点击行为：
 *  - 调用者传入的 [onResourceClick] 优先；
 *  - 默认实现是「资源有 url 就走系统浏览器」，没 url 就回调一次让上层做 toast 提示。
 *
 * 视觉规范：左侧类型徽标用 [PytColors.bgSoft] 背景 + 类型缩写文本；
 * 主体两行文本（标题 + 创建时间）；空列表给学生明确说明。
 */
@Composable
fun ResourceLinkList(
    resources: List<ResourceDto>,
    modifier: Modifier = Modifier,
    onResourceClick: ((ResourceDto) -> Unit)? = null,
) {
    val context = LocalContext.current
    val handler: (ResourceDto) -> Unit = onResourceClick ?: { res ->
        val url = res.url
        if (!url.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    if (resources.isEmpty()) {
        Text(
            text = "本周尚未生成复盘资源（agent 还在思考，下周日 02:00 再来看看）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier.padding(vertical = PytSpace.s2),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        resources.forEach { res ->
            ResourceRow(res = res, onClick = handler)
        }
    }
}

@Composable
private fun ResourceRow(res: ResourceDto, onClick: (ResourceDto) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !res.url.isNullOrBlank()) { onClick(res) },
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(PytSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeBadge(type = res.resourceType)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = PytSpace.s3),
            ) {
                Text(
                    text = res.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildSubtitle(res)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (!res.url.isNullOrBlank()) {
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.titleMedium,
                    color = PytColors.primary,
                    modifier = Modifier.padding(start = PytSpace.s2),
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val (label, color) = badgeFor(type)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun badgeFor(type: String): Pair<String, Color> = when (type.lowercase()) {
    "mindmap", "mind_map" -> "脑图" to PytColors.info
    "slides", "slide", "ppt" -> "讲义" to PytColors.primary
    "notes", "note", "doc" -> "笔记" to PytColors.success
    "quiz", "quiz_set", "exercise" -> "题集" to PytColors.warning
    "code", "code_lab" -> "代码" to PytColors.danger
    else -> "资源" to PytColors.borderSoft
}

private fun buildSubtitle(res: ResourceDto): String {
    val parts = mutableListOf<String>()
    parts += res.resourceType
    res.createdAt?.takeIf { it.isNotBlank() }?.let(parts::add)
    if (res.url.isNullOrBlank()) parts += "资源未生成"
    return parts.joinToString(" · ")
}
