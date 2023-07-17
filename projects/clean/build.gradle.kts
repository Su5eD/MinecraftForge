import net.minecraftforge.gradle.common.tasks.ExtractNatives

plugins {
    eclipse
    id("dev.su5ed.minecraftforge-legacy.gradle.patcher")
}

evaluationDependsOn(":mcp")

val minecraftVersion: String by rootProject.extra
val mappingsChannel: String by rootProject.extra
val mcpVersion: String by rootProject.extra
val mappingsVersion: String by rootProject.extra
val postProcessor: Map<String, Any> by rootProject.extra

dependencies {
    implementation("dev.su5ed.minecraftforge-legacy:mergetool:1.1.6:cpw")
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
            ideaModule = "${rootProject.name}.${project.name}.main"

            main = "mcp.client.Start"
            workingDirectory = project.file("run").absolutePath

            property(
                "java.library.path",
                tasks.getByName<ExtractNatives>("extractNatives").output.get().asFile.absolutePath
            )
        }

        "clean_server" {
            taskName = "clean_server"
            ideaModule = "${rootProject.name}.${project.name}.main"

            main = "net.minecraft.server.MinecraftServer"
            workingDirectory = project.file("run").absolutePath
        }
    }
}
