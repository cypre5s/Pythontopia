package com.pythontopia.feature.hub

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object HubDestination {
    const val HUB = "hub"
}

fun NavGraphBuilder.hubGraph(navController: NavController, onLogout: () -> Unit) {
    composable(HubDestination.HUB) {
        HubScreen(onLogout = onLogout)
    }
}

/** 提供给 :app 的便捷扩展，避免上层直接引用 [NavController]。 */
@Suppress("UnusedReceiverParameter", "UnusedParameter")
fun NavGraphBuilder.hubGraphLegacy(navController: NavController) = hubGraph(navController) { /* no-op */ }
