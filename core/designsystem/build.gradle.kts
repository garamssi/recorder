plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // AGP 는 Android 모듈의 자바 툴체인을 자체 기본값(JDK 21)으로 고정한다.
    // 타깃을 그보다 높이려면 여기서 툴체인을 명시해야 javac 이 따라온다.
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.javaToolchain.get())
    }
}

android {
    namespace = "io.rami.screenrecorder.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
}
