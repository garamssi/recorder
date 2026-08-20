package io.rami.screenrecorder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rami.screenrecorder.presentation.home.HomeActions
import io.rami.screenrecorder.presentation.home.HomeScreen
import io.rami.screenrecorder.presentation.library.LibraryScreen
import io.rami.screenrecorder.presentation.player.PlayerScreen
import io.rami.screenrecorder.presentation.player.PlayerViewModel
import io.rami.screenrecorder.presentation.settings.SettingsScreen
import io.rami.screenrecorder.presentation.trash.TrashScreen

internal const val ROUTE_HOME = "home"
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_LIBRARY = "library"
internal const val ROUTE_TRASH = "trash"
internal const val ROUTE_PLAYER = "player"

/**
 * 앱 네비게이션 호스트 (기능명세서 1절 화면 구성).
 *
 * 홈/목록/설정/휴지통은 [AppShell]의 레일·하단 바 안에서 그리고,
 * 전체 화면을 쓰는 플레이어에서는 셸 크롬을 감춘다.
 */
@Composable
fun AppNavHost(control: RecordingControlActions) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    AppShell(
        showChrome = currentRoute?.startsWith(ROUTE_PLAYER) != true,
        selected = shellDestinationFor(currentRoute),
        settingsSelected = currentRoute == ROUTE_SETTINGS,
        onNavigate = { destination -> navController.switchTo(destination.route) },
        onOpenSettings = { navController.switchTo(ROUTE_SETTINGS) },
    ) {
        NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    actions =
                        HomeActions(
                            control = control,
                            onOpenLibrary = { navController.switchTo(ROUTE_LIBRARY) },
                        ),
                    onPlay = { recording -> navController.navigate("$ROUTE_PLAYER/${recording.id.value}") },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    onOpenTrash = { navController.navigate(ROUTE_TRASH) },
                    onPlay = { recording -> navController.navigate("$ROUTE_PLAYER/${recording.id.value}") },
                )
            }
            composable(ROUTE_TRASH) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$ROUTE_PLAYER/{${PlayerViewModel.ARG_RECORDING_ID}}",
                arguments =
                    listOf(
                        navArgument(PlayerViewModel.ARG_RECORDING_ID) { type = NavType.LongType },
                    ),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 레일/하단 바에서 강조할 목적지. 대응하는 탭이 없으면 null. */
private fun shellDestinationFor(route: String?): ShellDestination? =
    when (route) {
        ROUTE_HOME -> ShellDestination.RECORD
        // 휴지통은 목록의 하위 화면이므로 목록 탭 강조를 유지한다.
        ROUTE_LIBRARY, ROUTE_TRASH -> ShellDestination.LIBRARY
        else -> null
    }

/**
 * 최상위 목적지로 전환한다.
 *
 * 탭을 오갈 때 백스택이 쌓이지 않도록 시작 목적지까지 popUpTo하고 화면 상태는 보존한다.
 */
private fun NavHostController.switchTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(ROUTE_HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
