package com.pythontopia.feature.hub

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object HubDestination {
    const val HUB = "hub"
}

fun NavGraphBuilder.hubGraph(onLogout: () -> Unit) {
    composable(HubDestination.HUB) {
        HubScreen(onLogout = onLogout)
    }
}

/** 旧 NavGraph 兼容入口，logout 为空操作。 */
fun NavGraphBuilder.hubGraphLegacy() = hubGraph { /* no-op */ }
