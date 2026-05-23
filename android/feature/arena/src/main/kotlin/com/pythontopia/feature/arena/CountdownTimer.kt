package com.pythontopia.feature.arena

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 赛事倒计时 Composable。
 *
 * 三种显示状态由 [classifyPhase] 计算：
 *  - [TimerPhase.UPCOMING]  : 还没开始（now < start），显示 "未开始"
 *  - [TimerPhase.RUNNING]   : 进行中（start ≤ now < end），显示 mm:ss 剩余时间
 *  - [TimerPhase.ENDED]     : 已结束（now ≥ end），显示 "已结束"
 *
 * **为什么不用 [System.currentTimeMillis] 直接驱动 UI？**
 *  - 直接读 wallclock 会被 Compose 当成"静态值"，不会自动重组
 *  - 用 `MutableLongState` + `LaunchedEffect { delay(1_000) }` 形成 1 Hz 重组节奏，
 *    刚好满足 mm:ss 显示精度，又不会浪费帧率
 *  - 当组件离开屏幕时 LaunchedEffect 自动取消，无后台泄漏
 *
 * **可测性**：核心格式逻辑下沉到三个纯函数 [formatRemaining] / [classifyPhase]
 * / [phaseLabel]，便于 [CountdownTimerTest] 用 JVM 单测覆盖，不依赖 Compose Runtime。
 */
@Composable
fun CountdownTimer(
    startEpochMs: Long,
    endEpochMs: Long,
    modifier: Modifier = Modifier,
    nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    var now by remember { mutableLongStateOf(nowProvider()) }
    LaunchedEffect(startEpochMs, endEpochMs) {
        while (true) {
            now = nowProvider()
            if (now >= endEpochMs) break
            delay(TICK_INTERVAL_MS)
        }
    }

    val phase = classifyPhase(now = now, startEpochMs = startEpochMs, endEpochMs = endEpochMs)
    val label = phaseLabel(phase = phase, now = now, startEpochMs = startEpochMs, endEpochMs = endEpochMs)
    val container = when (phase) {
        TimerPhase.UPCOMING -> MaterialTheme.colorScheme.tertiaryContainer
        TimerPhase.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        TimerPhase.ENDED -> MaterialTheme.colorScheme.surfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier.padding(horizontal = 4.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = container),
    )
}

/** 倒计时三阶段。 */
enum class TimerPhase { UPCOMING, RUNNING, ENDED }

/**
 * 把当前时间与赛事起止时间分类。
 *
 * 边界口径：`now == end` 视为已结束（含右端点收 0）；`now == start` 视为进行中。
 */
internal fun classifyPhase(now: Long, startEpochMs: Long, endEpochMs: Long): TimerPhase = when {
    now < startEpochMs -> TimerPhase.UPCOMING
    now >= endEpochMs -> TimerPhase.ENDED
    else -> TimerPhase.RUNNING
}

/**
 * 把毫秒级剩余时间格式化成 `mm:ss` 字符串。
 *
 * - 输入负数或 0 → "00:00"
 * - 99 分 59 秒以上 → "99:59+"（避免演示初期题目时长超过两小时时显示成 120:00 不直观）
 *
 * 故意不依赖 [java.text.SimpleDateFormat] / [java.time.Duration.toString]：纯字符串拼接
 * 更便于在 Roborazzi/jvm-only 测试中跑。
 */
internal fun formatRemaining(remainingMs: Long): String {
    if (remainingMs <= 0L) return "00:00"
    val totalSeconds = remainingMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > MAX_DISPLAY_MINUTES) {
        "${MAX_DISPLAY_MINUTES}:59+"
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * 根据阶段拼出 chip 上的最终文案。
 *
 * 提取成纯函数，[CountdownTimerTest] 可以直接传 (now, start, end) 三元组断言显示文案，
 * 不需要 Compose UI Test。
 */
internal fun phaseLabel(
    phase: TimerPhase,
    now: Long,
    startEpochMs: Long,
    endEpochMs: Long,
): String = when (phase) {
    TimerPhase.UPCOMING -> "未开始 (${formatRemaining(startEpochMs - now)})"
    TimerPhase.RUNNING -> "剩余 ${formatRemaining(endEpochMs - now)}"
    TimerPhase.ENDED -> "已结束"
}

private const val TICK_INTERVAL_MS = 1_000L
private const val MAX_DISPLAY_MINUTES = 99L
