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

rootProject.name = "MahoodFamily"

include(":app")
include(":core")
// Feature modules. To add a new feature: create modules/<name>, include it here,
// add it to app/build.gradle.kts and register it in FamilyApplication.
include(":modules:calendar")
include(":modules:lists")
include(":modules:board")
