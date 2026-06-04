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

/** Legacy no-op logout entry point kept for old app graph callers. */
fun NavGraphBuilder.hubGraphLegacy() = hubGraph { /* no-op */ }
