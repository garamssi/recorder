package io.rami.screenrecorder.foreground

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.rami.screenrecorder.service.AppForegroundState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시작된 액티비티 수로 전면 여부를 센다 ([AppForegroundState] 구현).
 *
 * 콜백은 모두 메인 스레드에서 오므로 카운터에 동기화가 필요 없다.
 */
@Singleton
class ForegroundActivityTracker
    @Inject
    constructor() :
    AppForegroundState,
        Application.ActivityLifecycleCallbacks {
        private var startedCount = 0
        private val foreground = MutableStateFlow(false)

        override val isForeground: StateFlow<Boolean> = foreground

        override fun onActivityStarted(activity: Activity) {
            if (activity is TransparentTrampoline) return
            startedCount++
            foreground.value = true
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity is TransparentTrampoline) return
            startedCount--
            foreground.value = startedCount > 0
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

/**
 * 사용자가 보던 앱을 그대로 비추는 투명 액티비티.
 *
 * 전면 판정에서 뺀다 — 이것을 앱 화면으로 세면 버블로 녹화를 시작할 때마다 버블이 깜빡인다.
 */
interface TransparentTrampoline
