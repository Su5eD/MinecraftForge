rootProject.name = "fml"

include(":mcp")
include(":clean")
include(":forge")

project(":mcp").projectDir = file("projects/mcp")
project(":clean").projectDir = file("projects/clean")
project(":forge").projectDir = file("projects/forge")
