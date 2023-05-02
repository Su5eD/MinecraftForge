import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:5.10.+")
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0-RC")
    }
}

plugins {
    id("net.minecraftforge.gradle-legacy") version "5.1.+" apply false
    id("org.cadixdev.licenser") version "0.6.1" apply false
    id("com.github.ben-manes.versions") version "0.22.0" apply false
    eclipse
}

val minecraftVersion: String by extra("1.4.7")
val mappingsChannel: String by extra("stable")
val mcpVersion: String by extra("20230502.202159")
val mappingsVersion: String by extra("7.26-$minecraftVersion")
val postProcessor: Map<String, Any> by extra(
    mapOf(
        "tool" to "net.minecraftforge:mcpcleanup:2.3.2:fatjar",
        "repo" to "https://maven.minecraftforge.net/",
        "args" to arrayOf("--input", "{input}", "--output", "{output}")
    )
)
val jarSigner: Map<String, Any?> by extra(
    if (project.hasProperty("keystore")) {
        mapOf(
            "storepass" to project.properties["keystoreStorePass"],
            "keypass" to project.properties["keystoreKeyPass"],
            "keystore" to project.properties["keystore"]
        )
    } else emptyMap()
)
val extraTxts: FileCollection by extra(
    files(
        "CREDITS.md",
        "LICENSE-fml.txt",
        "LICENSE-MinecraftForge.txt",
        "LICENSE-Paulscode IBXM Library.txt",
        "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
    )
)
val su5edMaven: String by extra("https://maven.su5ed.dev/releases")
val artifactRepositories: List<String> by extra(listOf("https://libraries.minecraft.net", "https://maven.minecraftforge.net", su5edMaven))
val gitInfo: Map<String, String> by extra(gitInfo())
val gitVersion = getVersion(gitInfo)

subprojects {
    apply(plugin = "java-library")
    
    tasks.named<JavaCompile>("compileJava") {
        sourceCompatibility = "1.7"
        targetCompatibility = "1.7"
    }

    group = "net.minecraftforge"
    version = gitVersion

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name == "jopt-simple") {
                useVersion("5.0.4")
            }
        }
    }

    repositories {
        mavenCentral()
        maven {
            name = "Su5eD"
            url = uri(su5edMaven)
        }
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

fun gitInfo(): Map<String, String> {
    val legacyBuild = 580 // Base build number to not conflict with existing build numbers
    val git: Git
    try {
        git = Git.open(rootProject.file("."))
    } catch (e: RepositoryNotFoundException) {
        return mapOf(
            "tag" to "0.0",
            "offset" to "0",
            "hash" to "00000000",
            "branch" to "master",
            "commit" to "0000000000000000000000",
            "abbreviatedId" to "00000000"
        )
    }
    val desc = git.describe().setLong(true).setTags(true).call().split("-", limit = 3)
    val head = git.repository.resolve("HEAD")

    val ret: MutableMap<String, String> = kotlin.collections.HashMap()
    ret["tag"] = desc[0]
    ret["offset"] = (desc[1].toInt() + legacyBuild).toString()
    ret["hash"] = desc[2]
    ret["branch"] = git.repository.branch
    ret["commit"] = ObjectId.toString(head)
    ret["abbreviatedId"] = head.abbreviate(8).name()

    return ret
}

fun getVersion(info: Map<String, String>): String {
    var branch = info["branch"]
    if (branch != null && branch.startsWith("pulls/")) branch = "pr" + branch.split("/", limit = 2)[1]
    if (branch in setOf(
            null,
            "master",
            "HEAD",
            minecraftVersion,
            "$minecraftVersion.0",
            minecraftVersion.substring(0, minecraftVersion.lastIndexOf(".")) + ".x",
            "$minecraftVersion-FG5"
        )
    ) return "${minecraftVersion}-${info["tag"]}.${info["offset"]}"

    return "${minecraftVersion}-${info["tag"]}.${info["offset"]}-${branch}"
}
