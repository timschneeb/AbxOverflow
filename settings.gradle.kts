pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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

// Include reflection explorer using alias, so its hidden-api submodule is accessible to it
include(":library")
include(":library:hidden-api")

project(":library").projectDir = file("reflection-explorer/library")
project(":library:hidden-api").projectDir = file("reflection-explorer/library/hidden-api")
include(":droppedapk:hidden-api")
