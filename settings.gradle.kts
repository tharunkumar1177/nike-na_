/*
 * Framecraft Gradle settings: plugin and dependency repositories and the module list.
 */

pluginManagement {
    repositories {
        google()
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

rootProject.name = "framecraft"

include(":app")
include(":domain")
include(":data:project")
include(":engine:media3")
