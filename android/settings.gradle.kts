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
        // 中国大陆/WSL2 网络下 Maven Central 经 Cloudflare 偶尔 403；
        // 国内 maven 镜像作为同步副本优先尝试，本机已缓存的 artifact 仍走本地 cache。
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
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
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
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
include(":feature:hub")
include(":feature:dialog")
include(":feature:profile")
include(":feature:fields")
include(":feature:workshop")
include(":feature:arena")
include(":feature:mine")
include(":feature:retro")
include(":feature:path")
include(":feature:mainline")
include(":feature:classroom")
include(":feature:tutor")
include(":feature:library")
include(":feature:boss")
include(":feature:endless")
include(":feature:lecture")
