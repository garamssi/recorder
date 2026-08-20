package io.rami.screenrecorder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rami.screenrecorder.presentation.home.HomeActions
import io.rami.screenrecorder.presentation.home.HomeScreen
import io.rami.screenrecorder.presentation.library.LibraryScreen
import io.rami.screenrecorder.presentation.player.PlayerScreen
import io.rami.screenrecorder.presentation.player.PlayerViewModel
import io.rami.screenrecorder.presentation.settings.SettingsScreen
import io.rami.screenrecorder.presentation.trash.TrashScreen

/** 앱 네비게이션 호스트 (기능명세서 1절 화면 구성). */
@Composable
fun AppNavHost(control: RecordingControlActions) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                actions =
                    HomeActions(
                        control = control,
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onOpenTrash = { navController.navigate(ROUTE_TRASH) },
                        onOpenLibrary = { navController.navigate(ROUTE_LIBRARY) },
                    ),
                onPlay = { recording -> navController.navigate("$ROUTE_PLAYER/${recording.id.value}") },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
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

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_PLAYER = "player"
