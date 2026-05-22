package com.pythontopia.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pythontopia.feature.auth.AuthDestination
import com.pythontopia.feature.auth.authGraph
import com.pythontopia.feature.hub.HubScreen
import com.pythontopia.feature.hub.hubGraph

/**
 * 顶层导航：Login → Hub。
 *
 * `:feature:auth` 把成功登录后跳转的目的地命名为 [AuthDestination.HOME] = "home"，
 * 这里直接为同名路由注册 Hub 的渲染入口，避免修改 :feature:auth；
 * 同时单独保留 [com.pythontopia.feature.hub.HubDestination.HUB] = "hub" 路由
 * 以便其它入口（如 deep link）直接跳到 Hub。
 *
 * Android-R13+ 计划：补 profile / fields / workshop 等 feature 路由 + Scaffold + 底部 tab。
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val onLogout: () -> Unit = {
        navController.navigate(AuthDestination.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }
    NavHost(navController = navController, startDestination = AuthDestination.LOGIN) {
        authGraph(navController)
        homeRedirect(onLogout)
        hubGraph(navController = navController, onLogout = onLogout)
    }
}

private fun NavGraphBuilder.homeRedirect(onLogout: () -> Unit) {
    composable(AuthDestination.HOME) {
        HubScreen(onLogout = onLogout)
    }
}
