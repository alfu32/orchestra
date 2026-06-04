pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "orchestra"

include(
    "core",
    "storage-json",
    "completion-core",
    "compiler-api",
    "compilers-impl",
    "assets",
    "app-desktop",
)
