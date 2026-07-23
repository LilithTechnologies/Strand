pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases/") {
            name = "NeoForged"
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "KikuGie Releases"
        }
        maven("https://maven.kikugie.dev/snapshots") {
            name = "KikuGie Snapshots"
        }
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "2.4.10"
        kotlin("plugin.serialization") version "2.4.10"
        id("net.fabricmc.fabric-loom") version "1.17.16"
        id("net.neoforged.moddev") version "2.0.142"
        id("dev.yumi.gradle.licenser") version "4.0.+"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) =
            loaders.forEach { version("$version-$it", version).buildscript = "build.$it.gradle.kts" }

        match("26.2", "fabric", "neoforge")

        vcsVersion = "26.2-fabric"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://maven.cloverclient.com/releases")
        maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
        mavenCentral()
    }
}

rootProject.name = "strand"

include(":backend")
include(":common")
