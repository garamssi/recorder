import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

val javaToolchainVersion = libs.versions.javaToolchain.get()
val javaTargetVersion = libs.versions.javaTarget.get()

kotlin {
    // 컴파일에는 최신 JDK 를 쓰고, 산출 바이트코드만 낮춘다.
    // Android 모듈이 이 산출물을 덱싱하므로 D8 이 받는 범위를 넘지 않아야 한다.
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
    }
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaTargetVersion)
    }
}

// Kotlin 은 compileJava 와 compileKotlin 의 타깃이 다르면 빌드를 거부한다.
// 툴체인을 그대로 두면 compileJava 가 툴체인 버전을 쓰므로 여기서 함께 낮춘다.
java {
    sourceCompatibility = JavaVersion.toVersion(javaTargetVersion)
    targetCompatibility = JavaVersion.toVersion(javaTargetVersion)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
