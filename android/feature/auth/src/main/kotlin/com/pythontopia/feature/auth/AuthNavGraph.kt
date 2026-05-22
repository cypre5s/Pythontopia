package com.pythontopia.feature.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object AuthDestination {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    composable(AuthDestination.LOGIN) {
        LoginScreen(
            onLoginSuccess = {
                navController.navigate(AuthDestination.HOME) {
                    popUpTo(AuthDestination.LOGIN) { inclusive = true }
                }
            },
        )
    }
    composable(AuthDestination.REGISTER) {
        RegisterScreen(onRegisterSuccess = { navController.popBackStack() })
    }
}
