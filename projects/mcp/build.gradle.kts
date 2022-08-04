plugins {
    id("net.minecraftforge.gradle-legacy.mcp")
}

val minecraftVersion: String by rootProject.extra

mcp {
    setConfig(minecraftVersion)
    pipeline.set("joined")
}
