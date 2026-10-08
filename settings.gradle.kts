pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "GbaLite"
include(":app", ":core-api", ":core-mgba", ":emulator-session", ":renderer", ":audio", ":input", ":feature-player")
include(":storage", ":data")
