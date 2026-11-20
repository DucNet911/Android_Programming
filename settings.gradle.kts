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

rootProject.name = "ArcadeTetris"
include(":app", ":game_core")

file("games").listFiles()
    ?.filter { it.isDirectory }
    ?.sortedBy { it.name }
    ?.forEach { directory ->
        if (directory.resolve("build.gradle").exists() || directory.resolve("build.gradle.kts").exists()) {
            require(directory.name.matches(Regex("game_[a-z0-9_]+")) && directory.name != "game_core") {
                "Game module must be named games/game_<name>; found ${directory.name}"
            }
            include(":${directory.name}")
            project(":${directory.name}").projectDir = directory
        }
    }

