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

rootProject.name = "Cheghadr"

include(":app")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:notifications")
include(":core:widgets")

include(":feature:home")
include(":feature:markets")
include(":feature:watchlist")
include(":feature:assetdetails")
include(":feature:converter")
include(":feature:alerts")
include(":feature:portfolio")
include(":feature:settings")
