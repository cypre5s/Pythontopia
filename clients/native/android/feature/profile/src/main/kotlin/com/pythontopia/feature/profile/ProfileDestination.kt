package com.pythontopia.feature.profile

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object ProfileDestination {
    const val PROFILE = "profile"
}

@Suppress("UnusedParameter")
fun NavGraphBuilder.profileGraph(navController: NavController) {
    composable(ProfileDestination.PROFILE) {
        ProfileScreen()
    }
}
