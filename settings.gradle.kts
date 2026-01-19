pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("reflection-explorer/gradle/libs.versions.toml"))
        }
    }

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AbxOverflow"
include(":app")
include(":droppedapk")
include(":reflection-explorer:library")

// Map the included project paths to the actual directories inside the `reflection-explorer` submodule
project(":reflection-explorer:library").projectDir = file("reflection-explorer/library")
