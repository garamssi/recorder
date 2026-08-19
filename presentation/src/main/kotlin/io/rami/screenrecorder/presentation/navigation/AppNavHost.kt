package io.rami.screenrecorder.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.HomeActions
import io.rami.screenrecorder.presentation.home.HomeScreen
import io.rami.screenrecorder.presentation.settings.SettingsScreen

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
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_LIBRARY) {
            // Stage 7에서 녹화 목록 화면으로 대체된다.
            PlaceholderScreen(stringResource(R.string.home_recent_title))
        }
        composable(ROUTE_TRASH) {
            // Stage 7에서 휴지통 화면으로 대체된다.
            PlaceholderScreen(stringResource(R.string.home_open_trash))
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Text(
        text = title,
        modifier =
            Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
    )
}

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_TRASH = "trash"
