buildscript {
    repositories {
        mavenCentral()
    }
    
    dependencies {
        classpath("org.ow2.asm:asm:7.1")
        classpath("org.ow2.asm:asm-tree:7.1")
        classpath("org.eclipse.jgit:org.eclipse.jgit:5.10.+")
        classpath("com.github.jponge:lzma-java:1.3")
        
        classpath(kotlin("gradle-plugin", version = "1.6.0"))
        classpath(kotlin("serialization", version = "1.6.0"))
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.0")
    }
    
    extra.apply {
        set("JAR_SIGNER", null)
        if (project.hasProperty("keystore")) {
            set("JAR_SIGNER", mapOf(
                "storepass" to project.properties["keystoreStorePass"],
                "keypass" to project.properties["keystoreKeyPass"],
                "keystore" to project.properties["keystore"]
            ))
        }
    }
}

plugins {
    id("net.minecraftforge.gradle-legacy") version "5.1.+" apply false
    id("org.cadixdev.licenser") version "0.6.1" apply false
    id("de.undercouch.download") version "3.3.0" apply false
    id("com.github.ben-manes.versions") version "0.22.0" apply false
    eclipse
}

val minecraftVersion: String by extra("1.4.7")
val mappingsChannel: String by extra("stable")
val mcpVersion: String by extra("7.26")
val mappingsVersion: String by extra("$mcpVersion-$minecraftVersion")
val postProcessor: Map<String, Any> by extra(mapOf(
    "tool" to "net.minecraftforge:mcpcleanup:2.3.2:fatjar",
    "repo" to "https://maven.minecraftforge.net/",
    "args" to arrayOf("--input", "{input}", "--output", "{output}")
))
var jarSigner: Map<String, Any?> by extra(if (project.hasProperty("keystore")) { 
    mapOf(
        "storepass" to project.properties["keystoreStorePass"],
        "keypass" to project.properties["keystoreKeyPass"],
        "keystore" to project.properties["keystore"]
    ) 
} else emptyMap())
val extraTxts: FileCollection by extra(files(
    "CREDITS.md",
    "LICENSE-fml.txt",
    "LICENSE-MinecraftForge.txt",
    "LICENSE-Paulscode IBXM Library.txt",
    "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
))
val su5edMaven: String by extra("https://maven.su5ed.dev/releases")
val artifactRepositories: List<String> by extra(listOf("https://libraries.minecraft.net", "https://maven.minecraftforge.net", su5edMaven))

subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name == "jopt-simple") {
                useVersion("5.0.4")
            }
        }
        exclude(group = "org.ow2.asm", module = "asm-all")
    }
    
    repositories { 
        exclusiveContent { 
            forRepository {
                maven { 
                    name = "argo"
                    url = uri(su5edMaven)
                }
            }
            filter {
                includeGroup("net.sourceforge.argo")
            }
        }
    }
}

tasks.register("setup") {
    dependsOn(":clean:extractMapped")
    dependsOn(":forge:extractMapped") //These must be strings so that we can do lazy resolution. Else we need evaluationDependsOnChildren above
}
