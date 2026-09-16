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
        // Where the released SDK and map library live. Reading needs no credentials, so a
        // fresh clone builds with nothing installed locally — these are the same artefacts a
        // customer resolves, and there is deliberately no `mavenLocal()` here: an example
        // that only builds on the machine that published the library is not an example.
        maven("https://maven.eu.proximi.fi/releases/") {
            name = "proximiioMaven"
            content {
                includeGroup("io.proximi.sdk")
                includeGroup("io.proximi.map")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "proximiio-blueiot-minimal-android"

include(":app")
