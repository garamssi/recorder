package io.rami.screenrecorder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt DI 그래프의 루트가 되는 Application. */
@HiltAndroidApp
class ScreenRecorderApplication : Application()
