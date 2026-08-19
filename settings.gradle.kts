pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScreenRecorder"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":domain")
include(":data")
include(":presentation")
include(":service")
