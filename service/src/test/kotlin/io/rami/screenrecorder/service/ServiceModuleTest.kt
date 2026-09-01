package io.rami.screenrecorder.service

import io.rami.screenrecorder.service.di.ServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.inject.Singleton

/**
 * 저장 완료 배너의 스코프 (기능명세서 6.1절 [결정]).
 *
 * 배너를 띄운 녹화 서비스는 발행 직후 접히고 다음 세션은 새 인스턴스에서 시작된다. 배너가
 * 서비스마다 새로 만들어지면 다음 세션의 `dismiss()` 가 창을 붙인 적 없는 객체를 향해,
 * 지난 배너가 다음 녹화의 첫 프레임에 찍힌다.
 *
 * 이 성질은 조립에만 있어서 다른 어떤 테스트도 보지 못한다 — `@Singleton` 을 지워도 나머지는
 * 전부 초록이다. 그래서 애너테이션 자체를 고정한다.
 */
class ServiceModuleTest {
    @Test
    fun `완료 배너는 프로세스에 하나만 둔다`() {
        val provider =
            ServiceModule::class.java.methods.single { it.name == "provideSaveCompleteBanner" }

        assertNotNull(
            "provideSaveCompleteBanner 에 @Singleton 이 없다 — 서비스마다 배너가 새로 만들어진다",
            provider.getAnnotation(Singleton::class.java),
        )
    }
}
