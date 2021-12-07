pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Su5eD Artifactory"
            url = uri("https://su5ed.jfrog.io/artifactory/maven/")
        }
        maven {
            name = "MinecraftForge"
            url = uri("https://maven.minecraftforge.net/")
        }
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "net.minecraftforge.gradle") {
                useModule("${requested.id}:ForgeGradle:${requested.version}")
            }
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
