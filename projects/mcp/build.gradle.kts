plugins {
    id("net.minecraftforge.gradle-legacy.mcp")
}

val minecraftVersion: String by rootProject.extra
val mcpVersion: String by rootProject.extra

mcp {
    setConfig("$minecraftVersion-$mcpVersion")
    pipeline.set("joined")
}
