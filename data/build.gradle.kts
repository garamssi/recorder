plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.rami.screenrecorder.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // android.util.Log 등 프레임워크 스텁이 예외 대신 기본값을 반환하게 한다 (JVM 단위 테스트).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.media3.muxer)
    implementation(libs.media3.common)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
