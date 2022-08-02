plugins {
    id("net.minecraftforge.gradle-legacy.mcp")
}

val minecraftVersion = "1.4.7"

mcp {
    setConfig(minecraftVersion)
    pipeline.set("joined")
}
