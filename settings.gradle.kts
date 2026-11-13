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

rootProject.name = "EV Navigator Iran"

include(":app")
include(":core")
include(":core:energy")
include(":core:roadprofile")
include(":core:routing")
include(":core:tripplanner")
include(":core:weather")
include(":data")
include(":domain")
include(":feature")
include(":design-system")
include(":navigation")
