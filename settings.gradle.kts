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
        // Legacy data layer uses franmontiel/PersistentCookieJar, published on JitPack.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.franmontiel") }
        }
    }
}

rootProject.name = "AHUTongNEXT"

include(":app")
include(":agent-core")
include(":agent-tools")
include(":data-sdk")
include(":model-gateway")
include(":legacy-data")
