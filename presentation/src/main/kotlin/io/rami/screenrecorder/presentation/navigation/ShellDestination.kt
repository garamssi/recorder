package io.rami.screenrecorder.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import io.rami.screenrecorder.presentation.R

/** 셸 내비게이션의 최상위 목적지 (DESIGN_GUIDE.md 4절). */
enum class ShellDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    RECORD(ROUTE_HOME, Icons.Default.Videocam, R.string.nav_record),
    LIBRARY(ROUTE_LIBRARY, Icons.AutoMirrored.Filled.List, R.string.nav_library),
}
