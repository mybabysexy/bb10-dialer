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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The BB10 UI kit: ./gradlew :bb10-kotlin-ui-kit:publishToMavenLocal in D:\Personal\bb10-kotlin
        mavenLocal {
            content { includeGroup("duc1607") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "BB10 Dialer"
include(":app")
 