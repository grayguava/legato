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
        // NewPipeExtractor (YouTube stream resolution) is published via JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Legato"
include(":legato:app")
include(":legato:ui")
include(":legato:uicore")
include(":legato:player")
include(":legato:base")
include(":legato:settings")
include(":legato:source")
include(":legato:lyrics")
include(":legato:library")
include(":legato:audio")
