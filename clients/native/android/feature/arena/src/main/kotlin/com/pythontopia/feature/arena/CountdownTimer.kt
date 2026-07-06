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
 * 赛事倒计时；用 `MutableLongState` + 1 Hz 轮询驱动重组（wallclock 不会自动触发重组）。
 * 格式逻辑下沉到 [formatRemaining] / [classifyPhase] / [phaseLabel] 便于 JVM 单测。
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

enum class TimerPhase { UPCOMING, RUNNING, ENDED }

/** `now == end` 视为已结束；`now == start` 视为进行中。 */
internal fun classifyPhase(now: Long, startEpochMs: Long, endEpochMs: Long): TimerPhase = when {
    now < startEpochMs -> TimerPhase.UPCOMING
    now >= endEpochMs -> TimerPhase.ENDED
    else -> TimerPhase.RUNNING
}

/** 负数或 0 → `00:00`；超过 99 分 → `99:59+`。 */
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
