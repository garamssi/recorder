plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

// CLAUDE.md 5절: domain 커버리지 90% 이상을 빌드 게이트로 강제한다.
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
