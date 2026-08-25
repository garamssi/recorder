plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
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
    namespace = "io.rami.screenrecorder.presentation"
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

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // android.util.Log 등 프레임워크 스텁이 예외 대신 기본값을 반환하게 한다 (JVM 단위 테스트).
        unitTests.isReturnDefaultValues = true
        // Compose UI 테스트가 Robolectric 으로 문자열·테마 리소스를 읽으려면 필요하다.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI 테스트는 JUnit4 러너를 요구하므로 vintage 엔진으로 JUnit5 실행기 위에 얹는다.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
