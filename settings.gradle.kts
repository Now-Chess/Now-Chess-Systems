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
    "modules:json",
    "modules:io",
    "modules:rule",
    "modules:security",
    "modules:bot-platform",
    "modules:official-bots",
    "modules:account",
    "modules:ws",
    "modules:store",
    "modules:coordinator",
)