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
    ":core:inventory",
    ":core:store-api",
    ":core:index",
    ":core:matching",
    ":core:handoff",
    ":core:data",
    ":core:ui",
    ":feature:catalog",
    ":feature:favourites",
    ":feature:inventory",
    ":feature:matching",
    ":feature:companion",
    ":feature:settings",
    ":feature:stores",
)
