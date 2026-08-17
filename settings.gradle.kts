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

rootProject.name = "GameDeck"

// Gradle module count is kept deliberately small (PROJECT_STRUCTURE.md §24).
//
// :core is a plain Kotlin/JVM module with no Android dependency on its classpath. That is what
// enforces the rule in PROJECT_STRUCTURE.md §21 — core cannot depend on Compose, Android UI, or
// Shizuku, because none of them can resolve there. The boundary is checked by the compiler rather
// than by review.
//
// Further modules (feature/, platform/, data/) are added when the corresponding implementation
// begins, per PROJECT_STRUCTURE.md §26.
include(":app")
include(":core")
