package com.pythontopia.feature.workshop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 极简代码编辑器：等宽字体 + 行号 + 共享纵向 [ScrollState]；不引入 IDE 级依赖。
 * 行号列宽随行数在 3–5 位间伸缩，避免超长文件挤压代码区。
 */
@Composable
fun CodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val lineCount by remember(code) {
        derivedStateOf {
            if (code.isEmpty()) 1 else code.count { it == '\n' } + 1
        }
    }
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val lineNumberWidth = remember(lineCount) {
        val digits = lineCount.toString().length.coerceAtLeast(3).coerceAtMost(5)
        (digits * MONO_CHAR_DP + LINE_NUMBER_PADDING_DP * 2).dp
    }
    val codeContentWidth = remember(code) {
        val longestLine = code.lineSequence().maxOfOrNull { it.length } ?: 0
        val widthDp = ((longestLine + CODE_TRAILING_CHARS) * MONO_CHAR_DP)
            .coerceAtLeast(CODE_MIN_WIDTH_DP)
            .coerceAtMost(CODE_MAX_WIDTH_DP)
        widthDp.dp
    }

    LaunchedEffect(code.length) {
        if (scrollState.value > scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val baseStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = CODE_FONT_SIZE.sp,
        color = PytColors.textPrimary,
        lineHeight = CODE_LINE_HEIGHT.sp,
    )

    Row(
        modifier = modifier
            .background(PytColors.bgSunken, RoundedCornerShape(PytSpace.s2))
            .border(1.dp, PytColors.borderSoft, RoundedCornerShape(PytSpace.s2)),
    ) {
        LineNumberColumn(
            lineCount = lineCount,
            style = baseStyle.copy(color = PytColors.textHint),
            scrollState = scrollState,
            modifier = Modifier
                .width(lineNumberWidth)
                .fillMaxHeight()
                .background(PytColors.bgSoft),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(horizontal = PytSpace.s2, vertical = PytSpace.s2),
        ) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.width(codeContentWidth),
                enabled = enabled,
                textStyle = baseStyle,
                cursorBrush = SolidColor(PytColors.primary),
                decorationBox = { inner ->
                    if (code.isEmpty()) {
                        Text(
                            text = CODE_PLACEHOLDER,
                            style = baseStyle.copy(color = PytColors.textHint),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun LineNumberColumn(
    lineCount: Int,
    style: TextStyle,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(scrollState, enabled = false)
            .padding(PaddingValues(vertical = PytSpace.s2)),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Top,
    ) {
        for (line in 1..lineCount) {
            Text(
                text = line.toString(),
                style = style,
                modifier = Modifier.padding(horizontal = LINE_NUMBER_PADDING_DP.dp),
            )
        }
    }
}

private const val MONO_CHAR_DP: Int = 9
private const val LINE_NUMBER_PADDING_DP: Int = 6
private const val CODE_FONT_SIZE: Int = 13
private const val CODE_LINE_HEIGHT: Int = 20
private const val CODE_TRAILING_CHARS: Int = 8
private const val CODE_MIN_WIDTH_DP: Int = 720
private const val CODE_MAX_WIDTH_DP: Int = 1800
private const val CODE_PLACEHOLDER: String = "# 在这里编写你的解答"

/** UI 测试可引用的占位文案与字号常量。 */
object CodeEditorDefaults {
    const val PLACEHOLDER: String = CODE_PLACEHOLDER
    const val FONT_SIZE_SP: Int = CODE_FONT_SIZE
}
