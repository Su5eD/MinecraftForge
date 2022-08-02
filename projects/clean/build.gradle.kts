import net.minecraftforge.gradle.common.tasks.ExtractNatives
import java.util.function.Predicate

plugins {
    eclipse
    id("net.minecraftforge.gradle-legacy.patcher")
}

evaluationDependsOn(":mcp")

tasks {
    compileJava { // Need this here so eclipse task generates correctly.
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    extractMapped {
        filter.set(Predicate { zipEntry ->
            zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
        })
    }
}

val minecraftVersion: String by rootProject.extra
val mappingsChannel: String by rootProject.extra
val mcpVersion: String by rootProject.extra
val mappingsVersion: String by rootProject.extra
val postProcessor: Map<String, Any> by rootProject.extra

dependencies {
    implementation("net.minecraftforge:mergetool:0.2.3.3:cpw")
    implementation("org.bouncycastle:bcprov-jdk15on:1.47")
    implementation("net.sourceforge.argo:argo:2.25")
}

configurations {
    minecraftImplementation {
        exclude(group = "net.minecraft", module = "launchwrapper")
    }
}

patcher {
    parent.set(project(":mcp"))
    mcVersion.set(minecraftVersion)
    patchedSrc.set(file("src/main/java"))

    mappings(mappingsChannel, mappingsVersion)
    processor(postProcessor)

    runs {
        "clean_client" {
            taskName = "clean_client"

            main = "mcp.client.Start"
            workingDirectory = project.file("run").absolutePath

            property(
                "java.library.path",
                tasks.getByName<ExtractNatives>("extractNatives").output.get().asFile.absolutePath
            )
        }

        "clean_server" {
            taskName = "clean_server"

            main = "net.minecraft.server.MinecraftServer"
            workingDirectory = project.file("run").absolutePath
        }
    }
}
