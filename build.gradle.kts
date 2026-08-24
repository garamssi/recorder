plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

val javaToolchainVersion = libs.versions.javaToolchain.get()
val javaTargetVersion = libs.versions.javaTarget.get()

// detekt 내장 컴파일러가 받는 jvmTarget 상한. 프로젝트 타깃이 이보다 높아도
// 정적 분석 결과에는 영향이 없으므로 여기서만 낮춰 맞춘다.
val detektMaxJvmTarget = 22
val detektJvmTarget = minOf(javaTargetVersion.toInt(), detektMaxJvmTarget).toString()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    // detekt 는 jvmTarget 을 자바 툴체인에서 가져온다. 툴체인(javaToolchain)은 detekt
    // 내장 컴파일러가 받는 상한보다 높으므로, 분석 대상 바이트코드 수준으로 고정한다.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = detektJvmTarget
    }

    // Hilt 플러그인이 만드는 hiltJavaCompile* 태스크는 모듈의 kotlin{} 툴체인을
    // 따라오지 않고 AGP 기본 툴체인(JDK 21)을 쓴다. 자바 컴파일 태스크 전체를
    // 한 툴체인으로 묶어 타깃과 컴파일러 버전이 어긋나지 않게 한다.
    pluginManager.withPlugin("java-base") {
        val toolchains = extensions.getByType<JavaToolchainService>()
        val compiler =
            toolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
            }
        tasks.withType<JavaCompile>().configureEach {
            javaCompiler.set(compiler)
        }
    }
}
