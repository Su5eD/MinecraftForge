pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Su5eD Maven"
            url = uri("https://maven.su5ed.dev/releases/")
        }
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
    }
}

rootProject.name = "MinecraftForge"

include(":mcp")
include(":clean")
include(":forge")

project(":mcp").projectDir = file("projects/mcp")
project(":clean").projectDir = file("projects/clean")
project(":forge").projectDir = file("projects/forge")
