package com.pythontopia.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

object TutorDestination {
    const val ROUTE = "tutor"
}

@Composable
fun TutorScreen(viewModel: TutorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(horizontal = PytSpace.s3),
    ) {
        Text(
            text = "学习总管 · AI 答疑",
            color = PytColors.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = PytSpace.s3, bottom = PytSpace.s1),
        )
        Text(
            text = "围绕 Python 课件提问，回答会标注课件佐证或通识来源",
            color = PytColors.textHint,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = PytSpace.s2),
        )

        if (state.messages.isEmpty()) {
            EmptyHint(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
            ) {
                items(state.messages) { msg -> MessageBubble(msg) }
            }
        }

        state.error?.let { err ->
            Text(
                text = "⚠ $err",
                color = PytColors.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = PytSpace.s1),
            )
        }

        InputBar(
            value = state.input,
            sending = state.sending,
            canSend = state.canSend,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::ask,
        )
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "向学习总管提问，例如：\nfor 循环和 while 循环的区别？",
            color = PytColors.textHint,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun MessageBubble(msg: TutorMessage) {
    val isUser = msg.role == TutorMessage.Role.USER
    val bubbleColor = if (isUser) PytColors.primary else PytColors.bgElevated
    val textColor = if (isUser) PytColors.onPrimary else PytColors.textPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, PytColors.borderStrong),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(PytSpace.s2)) {
                Text(
                    text = msg.text.ifBlank { if (msg.streaming) "…" else "" },
                    color = textColor,
                    fontSize = 14.sp,
                )
                if (!isUser && msg.streaming) {
                    CircularProgressIndicator(
                        color = PytColors.textHint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(top = PytSpace.s1).size(14.dp),
                    )
                }
                if (!isUser && !msg.streaming) {
                    GroundingFooter(msg.groundingStatus, msg.evidencePageIds)
                }
            }
        }
    }
}

@Composable
private fun GroundingFooter(groundingStatus: String?, evidencePageIds: List<Int>) {
    val label = groundingLabel(groundingStatus)
    if (label.isEmpty() && evidencePageIds.isEmpty()) return
    val accent = when (groundingStatus?.uppercase()) {
        "PASSED" -> PytColors.success
        "NO_EVIDENCE" -> PytColors.warning
        else -> PytColors.textHint
    }
    Row(
        modifier = Modifier.padding(top = PytSpace.s1),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) {
            Surface(
                color = accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, accent),
            ) {
                Text(
                    text = label,
                    color = accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (evidencePageIds.isNotEmpty()) {
            Text(
                text = "课件页 ${evidencePageIds.joinToString("、")}",
                color = PytColors.textHint,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    sending: Boolean,
    canSend: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("问点什么…", color = PytColors.textHint) },
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PytColors.bgElevated,
                unfocusedContainerColor = PytColors.bgElevated,
                focusedTextColor = PytColors.textPrimary,
                unfocusedTextColor = PytColors.textPrimary,
                focusedIndicatorColor = PytColors.borderStrong,
                unfocusedIndicatorColor = PytColors.borderSoft,
                cursorColor = PytColors.primary,
            ),
        )
        Button(
            onClick = onSend,
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = PytColors.primary,
                contentColor = PytColors.onPrimary,
                disabledContainerColor = PytColors.bgSoft,
                disabledContentColor = PytColors.textHint,
            ),
        ) {
            Text(if (sending) "…" else "发送")
        }
    }
}
