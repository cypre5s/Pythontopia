package com.pythontopia.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pythontopia.core.design.PytColors
import com.pythontopia.feature.auth.AuthDestination
import com.pythontopia.feature.auth.authGraph
import com.pythontopia.feature.arena.ArenaScreen
import com.pythontopia.feature.arena.ChapterTowerScreen
import com.pythontopia.feature.classroom.ClassroomScreen
import com.pythontopia.feature.fields.FieldsScreen
import com.pythontopia.feature.boss.BossScreen
import com.pythontopia.feature.endless.EndlessScreen
import com.pythontopia.feature.hub.HubScreen
import com.pythontopia.feature.lecture.LectureScreen
import com.pythontopia.feature.library.LibraryScreen
import com.pythontopia.feature.mine.MineScreen
import com.pythontopia.feature.path.PathScreen
import com.pythontopia.feature.profile.ProfileScreen
import com.pythontopia.feature.retro.RetroScreen
import com.pythontopia.feature.tutor.TutorScreen
import com.pythontopia.feature.workshop.WorkshopScreen

/**
 * 顶层导航：Login → [AppShell] 功能工作台；
 * [AuthDestination.HOME] 内 NavHost 懒加载各 feature，减轻冷启动压力。
 */
@Composable
fun MainScreen(entryViewModel: AppEntryViewModel = hiltViewModel()) {
    val start by entryViewModel.startDestination.collectAsState()
    val resolved = start
    if (resolved == null) {
        SplashLoading()
        return
    }
    val navController = rememberNavController()
    val onLogout: () -> Unit = {
        entryViewModel.logout()
        navController.navigate(AuthDestination.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }
    NavHost(navController = navController, startDestination = resolved) {
        authGraph(navController)
        homeRedirect(onLogout)
    }
}

@Composable
private fun SplashLoading() {
    Box(modifier = Modifier.fillMaxSize().background(PytColors.bgPrimary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PYTHONTOPIA",
                color = PytColors.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = PytColors.primary)
        }
    }
}

private fun NavGraphBuilder.homeRedirect(onLogout: () -> Unit) {
    composable(AuthDestination.HOME) {
        AppShell(onLogout = onLogout)
    }
}

private data class AppDestination(
    val route: String,
    val label: String,
    val accent: androidx.compose.ui.graphics.Color,
)

private val APP_DESTINATIONS = listOf(
    AppDestination("hub", "小镇", PytColors.success),
    AppDestination("tutor", "答疑", PytColors.info),
    AppDestination("profile", "画像", PytColors.info),
    AppDestination("fields", "知识田", PytColors.success),
    AppDestination("workshop", "工坊", PytColors.warning),
    AppDestination("library", "图书馆", PytColors.info),
    AppDestination("arena", "训练场", PytColors.danger),
    AppDestination("boss", "BOSS战", PytColors.danger),
    AppDestination("endless", "无尽练习", PytColors.warning),
    AppDestination("lecture", "讲义", PytColors.success),
    AppDestination("tower", "闯关塔", PytColors.warning),
    AppDestination("mine", "矿洞", PytColors.textMuted),
    AppDestination("retro", "复盘", PytColors.info),
    AppDestination("path", "路径", PytColors.success),
    AppDestination("classroom", "班级", PytColors.danger),
)

@Composable
private fun AppShell(onLogout: () -> Unit) {
    val featureNav = rememberNavController()
    Scaffold(
        topBar = {
            FeatureDock(
                navController = featureNav,
                onLogout = onLogout,
            )
        },
    ) { padding ->
        NavHost(
            navController = featureNav,
            startDestination = "hub",
            modifier = Modifier.padding(padding),
        ) {
            composable("hub") { HubScreen(onLogout = onLogout) }
            composable("tutor") { TutorScreen() }
            composable("profile") { ProfileScreen() }
            composable("fields") { FieldsScreen() }
            composable("workshop") { WorkshopScreen() }
            composable("library") { LibraryScreen() }
            composable("arena") { ArenaScreen() }
            composable("boss") { BossScreen() }
            composable("endless") { EndlessScreen() }
            composable("lecture") { LectureScreen() }
            composable("tower") { ChapterTowerScreen() }
            composable("mine") { MineScreen() }
            composable("retro") { RetroScreen() }
            composable("path") { PathScreen() }
            composable("classroom") { ClassroomScreen() }
        }
    }
}

@Composable
private fun FeatureDock(
    navController: NavHostController,
    onLogout: () -> Unit,
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: "hub"
    Surface(color = PytColors.bgElevated) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PytColors.primary),
                )
                Text(
                    text = "  PYTHONTOPIA",
                    color = PytColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                LogoutPill(onLogout)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                APP_DESTINATIONS.forEach { destination ->
                    PixelTab(
                        label = destination.label,
                        accent = destination.accent,
                        selected = destination.route == currentRoute,
                        onClick = {
                            if (destination.route != currentRoute) {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo("hub") { saveState = true }
                                }
                            }
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(PytColors.borderStrong),
            )
        }
    }
}

@Composable
private fun PixelTab(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PytColors.primary else PytColors.bgSoft
    val border = if (selected) PytColors.borderStrong else PytColors.borderSoft
    val textColor = if (selected) PytColors.onPrimary else PytColors.textMuted
    Surface(
        color = bg,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(2.dp, border),
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) PytColors.onPrimary.copy(alpha = 0.7f) else accent),
            )
            Text(
                text = label,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun LogoutPill(onLogout: () -> Unit) {
    Surface(
        color = PytColors.bgSunken,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(2.dp, PytColors.danger.copy(alpha = 0.6f)),
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onLogout),
    ) {
        Text(
            text = "退出",
            color = PytColors.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
