package com.pythontopia.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Android-R4.4 注册页：3 字段 + 提交按钮 + 错误提示。
 *
 * 与登录页保持同一种视觉骨架（24dp 内边距 + 16dp 间距 + Material3 OutlinedTextField），
 * 让用户在两个页面切换时不会感到结构跳跃。
 *
 * 成功后回调 [onRegisterSuccess] 时携带新建账号的用户名，调用方可在 Login 页 prefill。
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: (prefilledUsername: String?) -> Unit,
    onBackToLogin: () -> Unit = {},
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("注册 Pythontopia", style = MaterialTheme.typography.headlineMedium)
        Text(
            "用户名 ≥ 3 字符；密码 ≥ 8 位且需含字母与数字。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("用户名") },
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text("昵称（可选）") },
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::submit,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isLoading) "提交中..." else "提交")
        }
        TextButton(
            onClick = onBackToLogin,
            enabled = !state.isLoading,
        ) {
            Text("返回登录")
        }
        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
    LaunchedEffect(state.success) {
        if (state.success) {
            onRegisterSuccess(state.prefilledUsername)
        }
    }
}
