plugins {
    id("dev.su5ed.minecraftforge-legacy.gradle.mcp")
}

val minecraftVersion: String by rootProject.extra
val mcpVersion: String by rootProject.extra

mcp {
    setConfig("$minecraftVersion-$mcpVersion")
    pipeline.set("joined")
}
