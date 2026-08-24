plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

kotlin {
    // AGP 는 Android 모듈의 자바 툴체인을 자체 기본값(JDK 21)으로 고정한다.
    // 타깃을 그보다 높이려면 여기서 툴체인을 명시해야 javac 이 따라온다.
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.javaToolchain.get())
    }
}

android {
    namespace = "io.rami.screenrecorder.service"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // Robolectric 이 R.drawable / R.string 을 읽으려면 리소스를 단위 테스트에 포함해야 한다.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
