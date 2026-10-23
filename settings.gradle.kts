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
    }
}

rootProject.name = "FossScout"
include(
    ":app",
    ":core:model",
    ":core:feature",
    ":core:store-api",
    ":core:data",
    ":core:ui",
    ":feature:catalog",
    ":feature:favourites",
    ":feature:settings",
    ":feature:stores",
)
