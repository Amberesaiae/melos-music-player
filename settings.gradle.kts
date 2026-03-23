pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "melos-music-player"

// Application
include(":app")

// Core modules
include(":core:player")
include(":core:database")
include(":core:network")
include(":core:model")
include(":core:ui")

// Feature modules
include(":feature:library")
include(":feature:now-playing")
include(":feature:playlists")
include(":feature:search")
include(":feature:server")
include(":feature:settings")

// Platform modules
include(":platform:android-auto")
include(":platform:notifications")
