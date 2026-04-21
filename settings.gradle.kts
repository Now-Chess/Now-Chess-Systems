rootProject.name = "NowChessSystems"

pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}

include(
    "modules:core",
    "modules:api",
    "modules:io",
    "modules:rule",
    "modules:bot",
)