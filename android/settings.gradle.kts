@file:Suppress("UnstableApiUsage")

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pythontopia-android"

include(":app")
include(":core:common")
include(":core:data")
include(":core:design")
include(":core:network")
include(":game")
include(":feature:auth")
include(":feature:profile")
include(":feature:fields")
include(":feature:workshop")
include(":feature:arena")
include(":feature:mine")
include(":feature:retro")
include(":feature:path")
include(":feature:classroom")
