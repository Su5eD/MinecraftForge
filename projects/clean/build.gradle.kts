import net.minecraftforge.gradle.common.tasks.ExtractNatives

plugins {
    eclipse
    id("net.minecraftforge.gradle-legacy.patcher")
}

evaluationDependsOn(":mcp")

tasks {
    extractMapped {
        filter.set { zipEntry ->
            zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
        }
    }
}

val minecraftVersion: String by rootProject.extra
val mappingsChannel: String by rootProject.extra
val mcpVersion: String by rootProject.extra
val mappingsVersion: String by rootProject.extra
val postProcessor: Map<String, Any> by rootProject.extra

dependencies {
    implementation("net.minecraftforge:mergetool:1.1.6-legacy:cpw")
    implementation("org.bouncycastle:bcprov-jdk15on:1.47")
    implementation("net.sourceforge.argo:argo:2.25")
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
