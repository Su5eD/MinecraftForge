import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.minecraftforge.forge.tasks.CheckATs
import net.minecraftforge.forge.tasks.CheckSAS
import net.minecraftforge.forge.tasks.DownloadLibraries
import net.minecraftforge.gradle.common.tasks.*
import net.minecraftforge.gradle.common.util.Artifact
import net.minecraftforge.gradle.mcp.MCPExtension
import net.minecraftforge.gradle.patcher.tasks.GenerateBinPatches
import net.minecraftforge.gradle.userdev.tasks.RenameJar
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    eclipse
    id("net.minecraftforge.gradle-legacy.patcher")
    id("org.cadixdev.licenser")
}

evaluationDependsOn(":clean")

val minecraftVersion: String by rootProject.extra
val mappingsChannel: String by rootProject.extra
val mcpVersion: String by rootProject.extra
val mappingsVersion: String by rootProject.extra
val postProcessor: Map<String, Any> by rootProject.extra
val jarSigner: Map<String, Any?> by rootProject.extra
val extraTxts: ConfigurableFileCollection by rootProject.extra
val su5edMaven: String by rootProject.extra
val artifactRepositories: List<String> by rootProject.extra
val gitInfo: Map<String, String> by rootProject.extra

sourceSets {
    main {
        java {
            srcDirs("$rootDir/src/main/java")
        }
        resources {
            srcDirs("$rootDir/src/main/resources")
        }
    }
    test {
        val runtime = main.get().runtimeClasspath
        compileClasspath += runtime
        runtimeClasspath += runtime
        java {
            srcDirs("$rootDir/src/test/java")
        }
        resources {
            srcDirs("$rootDir/src/test/resources")
        }
    }
}

//Eclipse adds the sourceSets twice, once where we tell it to, once in the projects folder. No idea why. So delete them
eclipse.classpath.file.whenMerged {
    val cls = this as org.gradle.plugins.ide.eclipse.model.Classpath
    cls.entries.removeIf {
        it is SourceFolder && it.path.startsWith("src/") && !it.path.startsWith("src/main/")
    }
}

val specVersion = "23.5" // This is overwritten by git tag, but here so dev time doesnt explode
val mcpArtifact: Artifact = project(":mcp").extensions.getByType(MCPExtension::class).config.get()
val versionJson = project(":mcp").file("build/mcp/downloadJson/version.json")
val binpatchTool = "net.minecraftforge:binarypatcher:1.1.1"
val installerTools = "net.minecraftforge:installertools:1.1.11"
val jarSplitter = "net.minecraftforge:jarsplitter:1.1.2"
val specialSource = "net.md-5:SpecialSource:1.11.1-fixed"
val jarFilterTool = "dev.su5ed:JarFilter:1.0.0"
val protectedPackages = listOf("argo/", "org/bouncycastle/")

patcher {
    excs.from(rootProject.file("src/main/resources/forge.exc"))
    excludedReobfPackages.addAll(protectedPackages)
    parent.set(project(":clean"))
    patches.set(rootProject.file("patches/minecraft"))
    patchedSrc.set(file("src/main/java"))
    isSrgPatches = true
    notchObf = true
    accessTransformers(rootProject.file("src/main/resources/fml_at.cfg"), rootProject.file("src/main/resources/forge_at.cfg"))
    //sideAnnotationStripper = rootProject.file("src/main/resources/forge.sas")
    processor(postProcessor)

    runs { 
        "forge_client" {
            taskName = "forge_client"
            ideaModule = "${rootProject.name}.${project.name}.main"
            workingDirectory = project.file("run").absolutePath
            main = "net.minecraftforge.legacydev.MainClient"

            environment(
                mapOf(
                    "mainClass" to "cpw.mods.fml.relauncher.wrapper.ClientLaunchWrapper",
                    "assetIndex" to "{asset_index}",
                    "assetDirectory" to tasks.downloadAssets.get().output.absolutePath,
                    "nativesDirectory" to tasks.extractNatives.get().output.get().asFile.absolutePath,
                    "MC_VERSION" to minecraftVersion,
                    "MCP_MAPPINGS" to "{mcp_mappings}",
                    "MCP_TO_SRG" to "{mcp_to_srg}",
                    "FORGE_GROUP" to project.group,
                    "FORGE_VERSION" to (project.version as String).substring(minecraftVersion.length + 1),
                    "extractResources" to "true"
                )
            )
        }

        "forge_server" {
            taskName = "forge_server"
            ideaModule = "${rootProject.name}.${project.name}.main"
            main = "net.minecraftforge.legacydev.MainServer"

            environment(
                mapOf(
                    "mainClass" to "cpw.mods.fml.relauncher.wrapper.ServerLaunchWrapper",
                    "MC_VERSION" to minecraftVersion,
                    "MCP_MAPPINGS" to "{mcp_mappings}",
                    "MCP_TO_SRG" to "{mcp_to_srg}",
                    "FORGE_GROUP" to project.group,
                    "FORGE_VERSION" to (project.version as String).substring(minecraftVersion.length + 1)
                )
            )
        }
    }
}

val manifests = mutableMapOf(
    "/" to mutableMapOf<String, Any>(
        "Timestamp" to LocalDateTime.now(),
        "GitCommit" to gitInfo["abbreviatedId"]!!,
        "Git-Branch" to gitInfo["branch"]!!
    ),
    "net/minecraftforge/common/" to mutableMapOf(
        "Specification-Title" to "Forge",
        "Specification-Vendor" to "Forge Development LLC",
        "Specification-Version" to specVersion,
        "Implementation-Title" to project.group,
        "Implementation-Version" to (project.version as String).substring(minecraftVersion.length + 1),
        "Implementation-Vendor" to "Forge Development LLC"
    )
)

val versionRaw = project.version.toString().split("-", limit = 2)[1]
val versionParts = versionRaw.split(".")
val tokenMap = mapOf(
    "tokens" to mapOf(
        "FORGE_VERSION" to project.version,
        "FORGE_MAJOR_NUMBER" to versionParts[0],
        "FORGE_MINOR_NUMBER" to versionParts[1],
        "FORGE_REVISION_NUMBER" to versionParts[2],
        "FORGE_BUILD_NUMBER" to versionParts[3],
        "FORGE_GROUP" to project.group,
        "FORGE_NAME" to project.name,
        "MC_VERSION" to minecraftVersion,
        "MCP_VERSION" to mcpVersion,
        "MAPPING_CHANNEL" to mappingsChannel,
        "MAPPING_VERSION" to mappingsVersion
    )
)

val installer: Configuration by configurations.creating {
    isTransitive = false //Don't pull all libraries, if we're missing something, add it to the installer list so the installer knows to download it.
}

configurations {
    api {
        extendsFrom(installer)
    }
}

dependencies {
    installer("org.ow2.asm:asm-all:4.0")
    installer("net.sf.jopt-simple:jopt-simple:5.0.4")
    installer("com.google.guava:guava:12.0.1")
    installer("net.sourceforge.argo:argo:2.25")
    installer("org.bouncycastle:bcprov-jdk15on:1.47")

    implementation("net.minecraftforge:legacydev:0.2.4-legacy.+:fatjar")
}

val jsonFormat = Json { prettyPrint = true }

tasks {
    applyPatches {
        maxFuzzOffset = 3
    }
    
    sequenceOf("Client", "Server", "Joined").forEach { side ->
        val downloadSlim = if (side == "Joined") {
            register<DownloadMavenArtifact>("downloadJoined") {
                setArtifact("net.minecraft:joined:$minecraftVersion")
            }
        } else {
            listOf("slim", "extra").map { type ->
                register<DownloadMavenArtifact>("download${side}${type.capitalize()}") {
                    setArtifact("net.minecraft:${side.toLowerCase()}:$minecraftVersion:$type")
                }
            }.first()
        }
        
        val extractSrg = project(":clean").tasks.named<ExtractMCPData>("extractSrg")
        val createSRG = register<RenameJar>("create${side}SRG") {
            dependsOn(downloadSlim, extractSrg)
            
            tool.set("$specialSource:shaded")
            args.set(listOf(
                "--in-jar", "{input}",
                "--out-jar", "{output}",
                "--srg-in", "{mappings}",
                "--remap-only", protectedPackages.joinToString(separator = ",")
            ))
            input.set(downloadSlim.flatMap(DownloadMavenArtifact::getOutput))
            mappings.set(extractSrg.flatMap(ExtractMCPData::getOutput))
            output.set(file("build/$name/output.jar"))
        }
        
        val gen = named<GenerateBinPatches>("gen${side}BinPatches")
        gen.configure {
            dependsOn(createSRG)
            cleanJar.set(createSRG.flatMap(RenameJar::getOutput))
        }
        
        register<ApplyBinPatches>("apply${side}BinPatches") {
            dependsOn(gen)
            clean.set(gen.flatMap(GenerateBinPatches::getCleanJar))
            patch.set(gen.flatMap(GenerateBinPatches::getOutput))
        }
    }

    val downloadLibraries by registering(DownloadLibraries::class) {
        dependsOn(":mcp:setupMCP")
        input.set(versionJson)
        output.set(rootProject.file("build/libraries/"))
    }

    val extractInheritance by registering(ExtractInheritance::class) {
        dependsOn("genJoinedBinPatches", downloadLibraries)
        input.set(genJoinedBinPatches.get().cleanJar)
        doFirst {
            getLibArtifacts(versionJson)
                .map { file("build/libraries/" + it["path"]?.jsonPrimitive?.content) }
                .filter(File::exists)
                .forEach(libraries::add)
        }
    }

    val checkATs by registering(CheckATs::class) {
        dependsOn(extractInheritance, "createSrg2Mcp")
        
        ats.from(patcher.accessTransformers)
        mappings.set(createSrg2Mcp.get().output)
    }

    val checkSAS by registering(CheckSAS::class) {
        dependsOn(extractInheritance)
        inheritance.set(extractInheritance.flatMap(ExtractInheritance::getOutput))
        sass.from(patcher.sideAnnotationStrippers)
    }
    
    register("checkAll") {
        dependsOn(checkATs, checkSAS)
    }

    universalJar {
        from(extraTxts)

        doFirst {
            val classpath = StringBuilder()
            val artifacts = getArtifacts(installer, false)
            artifacts.forEach { (_, lib) ->
                val artifactPath = lib.jsonObject["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("path")?.jsonPrimitive?.content
                classpath.append("libraries/$artifactPath ")
            }
            classpath.append("libraries/net/minecraft/server/$minecraftVersion-$mcpVersion/server-$minecraftVersion-$mcpVersion-extra.jar")
            manifests.forEach { (pkg, values) ->
                if (pkg == "/") {
                    values += mutableMapOf(
                        "Main-Class" to "cpw.mods.fml.relauncher.wrapper.ServerLaunchWrapper",
                        "Class-Path" to classpath.toString()
                    )
                    manifest.attributes(values)
                } else {
                    manifest.attributes(values, pkg)
                }
            }
        }
    }

    val launcherJson by registering {
        dependsOn("signUniversalJar")

        val vanilla = project(":mcp").file("build/mcp/downloadJson/version.json")
        val version = project.version as String
        val idx = version.indexOf('-')
        val id = version.substring(0, idx) + "-${project.name}" + version.substring(idx)
        val timestamp = dateToIso8601(Date())
        val output = file("build/version.json")
        val comment = buildJsonArray {
            add("Please do not automate the download and installation of Forge.")
            add("Our efforts are supported by ads from the download page.")
            add("If you MUST automate this, please consider supporting the project through https://www.patreon.com/LexManos/")
        }

        extra.apply {
            set("comment", comment)
            set("id", id)
        }

        inputs.file(universalJar.flatMap(Jar::getArchiveFile))
        inputs.file(vanilla)
        outputs.file(output)

        doLast {
            val json = buildJsonObject {
                put("_comment_", comment)
                put("id", id)
                put("time", timestamp)
                put("releaseTime", timestamp)
                put("type", "release")
                put("mainClass", "cpw.mods.fml.relauncher.wrapper.ClientLaunchWrapper")
                put("inheritsFrom", minecraftVersion)
                putJsonObject("logging") {}
                put(
                    "minecraftArguments", listOf(
                        "\${auth_player_name}",
                        "\${auth_session}",
                        "--uuid", "\${auth_uuid}",
                        "--gameDir", "\${game_directory}",
                        "--assetsDir", "\${game_assets}"
                    ).joinToString(separator = " ")
                )
                putJsonArray("libraries") {
                    addJsonObject {
                        put("name", "${project.group}:${project.name}:${project.version}")
                        putJsonObject("downloads") {
                            putJsonObject("artifact") {
                                put(
                                    "path",
                                    "${
                                        project.group.toString().replace(".", "/")
                                    }/${project.name}/${project.version}/${project.name}-${project.version}.jar"
                                )
                                put(
                                    "url",
                                    ""
                                ) //Do not include the URL so that the installer/launcher won't grab it. This is also why we don't have the universal classifier
                                universalJar.get().archiveFile.get().asFile.apply { 
                                    put("sha1", sha1())
                                    put("size", length())   
                                }
                            }
                        }
                    }

                    val artifacts = getArtifacts(installer, false)
                    artifacts.forEach { (_, lib) -> add(lib) }
                }
            }

            output.writeText(jsonFormat.encodeToString(json))
        }
    }

    val installerJson by registering {
        val applyClientBinPatches = named<ApplyBinPatches>("applyClientBinPatches")
        val applyServerBinPatches = named<ApplyBinPatches>("applyServerBinPatches")
        val genClientBinPatches = named<GenerateBinPatches>("genClientBinPatches")
        val downloadClientSlim = named<DownloadMavenArtifact>("downloadClientSlim")
        val downloadServerSlim = named<DownloadMavenArtifact>("downloadServerSlim")
        val downloadClientExtra = named<DownloadMavenArtifact>("downloadClientExtra")
        val downloadServerExtra = named<DownloadMavenArtifact>("downloadServerExtra")
        
        dependsOn(launcherJson, universalJar, applyClientBinPatches, applyServerBinPatches, genClientBinPatches,
            downloadClientSlim, downloadServerSlim, downloadClientExtra, downloadServerExtra)

        val universalJarFile = universalJar.get().archiveFile.get().asFile
        val output = file("build/install_profile.json")

        inputs.files(
            universalJarFile,
            genClientBinPatches.flatMap(GenerateBinPatches::getToolJar),
            launcherJson.map { it.outputs.files }
        )
        outputs.file(output)

        doLast {
            val libs = buildJsonObject {
                putJsonObject("${project.group}:${project.name}:${project.version}:universal") {
                    put("name", "${project.group}:${project.name}:${project.version}:universal")
                    putJsonObject("downloads") {
                        putJsonObject("artifact") {
                            put(
                                "path",
                                "${
                                    project.group.toString().replace('.', '/')
                                }/${project.name}/${project.version}/${project.name}-${project.version}-universal.jar"
                            )
                            put("url", "") //Do not include the URL so that the installer/launcher won't grab it.
                            put("sha1", universalJarFile.sha1())
                            put("size", universalJarFile.length())
                        }
                    }
                }
            }.toMutableMap()

            val json = buildJsonObject {
                put("_comment_", launcherJson.get().extra["comment"] as JsonArray)
                put("spec", 0)
                put("profile", project.name)
                put("version", launcherJson.get().extra["id"] as String)
                put(
                    "icon",
                    "data:image/png;base64," + String(
                        Base64.getEncoder().encode(Files.readAllBytes(rootProject.file("icon.ico").toPath()))
                    )
                )
                put("json", "/version.json")
                put("path", "${project.group}:${project.name}:${project.version}")
                put("logo", "/big_logo.png")
                put("minecraft", minecraftVersion)
                put("welcome", "Welcome to the simple ${project.name.capitalize()} installer.")
                putJsonObject("data") {
                    putJsonObject("MAPPINGS") {
                        put("client", "[${mcpArtifact.group}:${mcpArtifact.name}:${mcpArtifact.version}:mappings@txt]")
                        put("server", "[${mcpArtifact.group}:${mcpArtifact.name}:${mcpArtifact.version}:mappings@txt]")
                    }
                    putJsonObject("BINPATCH") {
                        put("client", "/data/client.lzma")
                        put("server", "/data/server.lzma")
                    }
                    putJsonObject("MC_SLIM") {
                        put("client", "[net.minecraft:client:${minecraftVersion}-${mcpVersion}:slim]")
                        put("server", "[net.minecraft:server:${minecraftVersion}-${mcpVersion}:slim]")
                    }
                    putJsonObject("MC_SLIM_SHA") {
                        put(
                            "client",
                            "'${downloadClientSlim.get().output.get().asFile.sha1()}'"
                        )
                        put(
                            "server",
                            "'${downloadServerSlim.get().output.get().asFile.sha1()}'"
                        )
                    }
                    putJsonObject("MC_EXTRA") {
                        put("client", "[net.minecraft:client:${minecraftVersion}-${mcpVersion}:extra]")
                        put("server", "[net.minecraft:server:${minecraftVersion}-${mcpVersion}:extra]")
                    }
                    putJsonObject("MC_EXTRA_SHA") {
                        put(
                            "client",
                            "'${downloadClientExtra.get().output.get().asFile.sha1()}'"
                        )
                        put(
                            "server",
                            "'${downloadServerExtra.get().output.get().asFile.sha1()}'"
                        )
                    }
                    putJsonObject("MC_LIB_SRG") {
                        put("client", "[net.minecraft:client:${minecraftVersion}-${mcpVersion}:srg-all]")
                        put("server", "[net.minecraft:server:${minecraftVersion}-${mcpVersion}:srg-all]")
                    }
                    putJsonObject("MC_SRG") {
                        put("client", "[net.minecraft:client:${minecraftVersion}-${mcpVersion}:srg]")
                        put("server", "[net.minecraft:server:${minecraftVersion}-${mcpVersion}:srg]")
                    }
                    putJsonObject("PATCHED") {
                        put("client", "[${project.group}:${project.name}:${project.version}:client]")
                        put("server", "[${project.group}:${project.name}:${project.version}:server]")
                    }
                    putJsonObject("PATCHED_SHA") {
                        put("client", "'${applyClientBinPatches.get().output.get().asFile.sha1()}'")
                        put("server", "'${applyServerBinPatches.get().output.get().asFile.sha1()}'")
                    }
                }
                putJsonArray("processors") {
                    addJsonObject {
                        put("jar", installerTools)
                        putJsonArray("classpath") {
                            getClasspath(libs, installerTools).forEach(::add)
                        }
                        putJsonArray("args") {
                            add("--task"); add("MCP_DATA")
                            add("--input"); add("[${mcpArtifact.descriptor}]")
                            add("--output"); add("{MAPPINGS}")
                            add("--key"); add("mappings")
                        }
                    }
                    addJsonObject {
                        put("jar", jarSplitter)
                        putJsonArray("classpath") {
                            getClasspath(libs, jarSplitter).forEach(::add)
                        }
                        putJsonArray("args") {
                            add("--input"); add("{MINECRAFT_JAR}")
                            add("--slim"); add("{MC_SLIM}")
                            add("--extra"); add("{MC_EXTRA}")
                            add("--srg"); add("{MAPPINGS}")
                        }
                        putJsonObject("outputs") {
                            put("{MC_SLIM}", "{MC_SLIM_SHA}")
                            put("{MC_EXTRA}", "{MC_EXTRA_SHA}")
                        }
                    }
                    addJsonObject {
                        put("jar", specialSource)
                        putJsonArray("classpath") {
                            getClasspath(libs, specialSource).forEach(::add)
                        }
                        putJsonArray("args") {
                            add("--in-jar"); add("{MC_SLIM}")
                            add("--out-jar"); add("{MC_LIB_SRG}")
                            add("--srg-in"); add("{MAPPINGS}")
                            add("--remap-only"); add(protectedPackages.joinToString(separator = ","))
                        }
                    }
                    addJsonObject {
                        put("jar", jarFilterTool)
                        putJsonArray("classpath") {
                            getClasspath(libs, jarFilterTool).forEach(::add)
                        }
                        putJsonArray("args") {
                            add("--input"); add("{MC_LIB_SRG}")
                            add("--output"); add("{MC_SRG}")
                            add("--filter"); add(protectedPackages.joinToString(separator = ","))
                        }
                    }
                    addJsonObject {
                        put("jar", binpatchTool)
                        putJsonArray("classpath") {
                            getClasspath(libs, binpatchTool).forEach(::add)
                        }
                        putJsonArray("args") {
                            add("--clean"); add("{MC_SRG}")
                            add("--output"); add("{PATCHED}")
                            add("--apply"); add("{BINPATCH}")
                        }
                        putJsonObject("outputs") {
                            put("{PATCHED}", "{PATCHED_SHA}")
                        }
                    }
                }
            }.toMutableMap()

            getClasspath(libs, mcpArtifact.descriptor) //Tell it to download mcp_config
            json["libraries"] = JsonArray(libs.values.sortedBy { it.jsonObject["name"]?.jsonPrimitive?.content })

            output.writeText(jsonFormat.encodeToString(json))
        }
    }

    val downloadInstaller by registering(DownloadMavenArtifact::class) {
        setArtifact("net.minecraftforge:installer:2.1.+:shrunk")
        changing = true
    }

    val installerJar by registering(Zip::class) {
        val genClientBinPatches = named<GenerateBinPatches>("genClientBinPatches")
        val genServerBinPatches = named<GenerateBinPatches>("genServerBinPatches")

        dependsOn(downloadInstaller, installerJson, launcherJson, genClientBinPatches, genServerBinPatches, universalJar)
        archiveClassifier.set("installer")
        archiveExtension.set("jar") //Needs to be Zip task to not override Manifest, so set extension
        destinationDirectory.set(file("build/libs"))
        from(
            extraTxts,
            installerJson.map(Task::getOutputs),
            launcherJson.map(Task::getOutputs),
            rootProject.file("src/main/resources/url.png")
        )
        from(rootProject.file("src/main/resources/forge_logo.png")) {
            rename { "big_logo.png" }
        }
        from(genClientBinPatches) {
            rename { "data/client.lzma" }
        }
        from(genServerBinPatches) {
            rename { "data/server.lzma" }
        }
        from(universalJar) {
            into("/maven/${project.group.toString().replace('.', '/')}/${project.name}/${project.version}/")
            rename { "${project.name}-${project.version}.jar" }
        }
        from(zipTree(downloadInstaller.flatMap(DownloadMavenArtifact::getOutput))) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }

    setOf(universalJar, installerJar).forEach { task ->
        val signTask = register<SignJar>("sign${task.name.capitalize()}") {
            dependsOn(task)
            onlyIf { jarSigner.isNotEmpty() && task.get().state.failure == null }
            
            alias.set("forge")
            storePass.set(jarSigner["storepass"] as String?)
            keyPass.set(jarSigner["keypass"] as String?)
            keyStore.set(jarSigner["keystore"] as String?)
            
            val archiveFile = task.flatMap(Zip::getArchiveFile)
            inputFile.set(archiveFile)
            outputFile.set(archiveFile)
            
            doFirst { project.logger.lifecycle("Signing: $inputFile") }
        }
        task.configure { 
            finalizedBy(signTask)
        }
    }

    register<Zip>("makeMdk") {
        archiveBaseName.set(project.name)
        archiveClassifier.set("mdk")
        archiveVersion.set(project.version as String)
        destinationDirectory.set(file("build/libs"))
        from(rootProject.file("gradlew"))
        from(rootProject.file("gradlew.bat"))
        from(extraTxts)
        from(rootProject.file("gradle/")) {
            into("gradle/")
        }

        from(rootProject.file("mdk/")) {
            rootProject.file("mdk/gitignore.txt").forEachLine {
                if (it.trim().isNotEmpty() && !it.trim().startsWith('#')) exclude(it)
            }
            filter(ReplaceTokens::class, tokenMap)
            rename("gitignore\\.txt", ".gitignore")
        }
    }

    processResources {
        inputs.property("version", project.version)

        filesMatching("*.properties") {
            filter(ReplaceTokens::class, tokenMap)
        }
    }

    userdevConfig {
        val artifacts = getArtifacts(installer, true)
        artifacts.values.forEach { lib ->
            libraries.add(lib["name"]!!.jsonPrimitive.content)
        }
        libraries.add("net.minecraftforge:legacydev:0.2.4-legacy.+:fatjar")
        javaRecompileTarget.set(7)

        runs {
            register("client") {
                main = "net.minecraftforge.legacydev.MainClient"

                environment(
                    mapOf(
                        "mainClass" to "cpw.mods.fml.relauncher.wrapper.ClientLaunchWrapper",
                        "assetIndex" to "{asset_index}",
                        "assetDirectory" to "{assets_root}",
                        "nativesDirectory" to "{natives}",
                        "MC_VERSION" to minecraftVersion,
                        "MCP_MAPPINGS" to "{mcp_mappings}",
                        "MCP_TO_SRG" to "{mcp_to_srg}",
                        "FORGE_GROUP" to project.group,
                        "FORGE_VERSION" to project.version.toString().substring(minecraftVersion.length + 1),
                        "extractResources" to "true"
                    )
                )
            }

            register("server") {
                main = "net.minecraftforge.legacydev.MainServer"

                environment(
                    mapOf(
                        "mainClass" to "cpw.mods.fml.relauncher.wrapper.ServerLaunchWrapper",
                        "MC_VERSION" to minecraftVersion,
                        "MCP_MAPPINGS" to "{mcp_mappings}",
                        "MCP_TO_SRG" to "{mcp_to_srg}",
                        "FORGE_GROUP" to project.group,
                        "FORGE_VERSION" to project.version.toString().substring(minecraftVersion.length + 1)
                    )
                )
            }
        }
    }

    userdevJar {
        archiveClassifier.set("userdev3")
    }

    "eclipse" {
        dependsOn("genEclipseRuns")
    }

    genPatches {
        lineEnding.set("\n")
        doLast {
            val outputPath = output.get().asFile.toPath()
            Files.walk(outputPath)
                .filter { path ->
                    val relative = outputPath.relativize(path).toString()
                    relative.isNotEmpty() && !relative.startsWith("net") && path.toFile().isDirectory
                }
                .forEach { path ->
                    FileUtils.deleteDirectory(path.toFile())
                }
        }
    }
    
    if (project.hasProperty("UPDATE_MAPPINGS")) {
        val srcDirs = sourceSets.test.get().java.srcDirs
        extractRangeMap {
            sources.from(srcDirs)
        }
        applyRangeMap {
            sources.from(srcDirs)
        }
    
        named<ExtractExistingFiles>("extractMappedNew") {
            srcDirs.forEach(targets::from)
        }
    }
}

license {
    header.set(rootProject.resources.text.fromFile("LICENSE-header.txt"))

    include("cpw/mods/fml/")
    exclude("cpw/mods/fml/common/asm/transformers/ClassRemapper.java")

    tasks {
        register("main") {
            files.from("$rootDir/src/main/java")
        }
        register("test") {
            files.from("$rootDir/src/test/java")
        }
    }
}

val changelog = rootProject.file("build/changelog.txt")
if (changelog.exists())
    extraTxts.from(changelog)

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            if (changelog.exists()) {
                artifact(changelog) {
                    classifier = "changelog"
                }
            }

            sequenceOf("universalJar", "installerJar", "makeMdk", "userdevJar", "sourcesJar")
                .map(tasks::named)
                .forEach(::artifact)

            pom {
                name.set("forge")
                description.set("Modifactions to Minecraft to enable mod developers.")
                url.set("https://github.com/MinecraftForge/MinecraftForge")

                scm {
                    url.set("https://github.com/MinecraftForge/MinecraftForge")
                    connection.set("scm:git:git://github.com/MinecraftForge/MinecraftForge.git")
                    developerConnection.set("scm:git:git@github.com:MinecraftForge/MinecraftForge.git")
                }

                issueManagement {
                    system.set("github")
                    url.set("https://github.com/MinecraftForge/MinecraftForge/issues")
                }

                licenses {
                    license {
                        name.set("LGPL 2.1")
                        url.set("https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/LICENSE.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
    }
    repositories {
        mavenLocal()
        maven {
            if (project.hasProperty("forgeMavenPassword")) {
                credentials {
                    username = project.properties["forgeMavenUser"] as String
                    password = project.properties["forgeMavenPassword"] as String
                }
                url = uri("https://files.minecraftforge.net/maven/manage/upload")
            } else {
                url = uri("file://${rootProject.file("repo").absolutePath}")
            }
        }

        if (project.hasProperty("artifactoryPassword")) {
            maven {
                name = "artifactory"
                url = uri(su5edMaven)
                credentials {
                    username = project.properties["artifactoryUser"] as String
                    password = project.properties["artifactoryPassword"] as String
                }
            }
        }
    }
}

fun getLibArtifacts(versionJson: File): Set<JsonObject> {
    val json = Json.decodeFromString<JsonObject>(versionJson.readText())
    json["libraries"]?.jsonArray?.forEach { lib ->
        val artifacts: MutableSet<JsonObject> = kotlin.collections.HashSet()
        lib.jsonObject["downloads"]?.jsonObject?.run {
            get("artifact")?.jsonObject?.run { artifacts.add(this) }
            get("classifier")?.jsonObject?.forEach { _, e -> artifacts.add(e.jsonObject) }
        }
        return artifacts
    }
    
    return emptySet()
}

fun dateToIso8601(date: Date): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
    val result = format.format(date)
    return result.substring(0..21) + ":" + result.substring(22)
}

fun File.sha1(): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(readBytes())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun getArtifacts(config: Configuration, classifiers: Boolean): Map<String, JsonObject> {
    val ret: MutableMap<String, JsonObject> = HashMap()
    config.resolvedConfiguration.resolvedArtifacts.forEach {
        val name = it.moduleVersion.id.name
        val group = it.moduleVersion.id.group
        var version = it.moduleVersion.id.version
        var classifier = it.classifier
        var extension = it.extension
        val key = "$group:$name"
        val folder = "${group.replace(".", "/")}/${name}/${version}/"
        var filename = "${name}-${version}"
        
        if (classifier != null) filename += "-${classifier}"
        filename += ".${extension}"
        
        var path = "${folder}${filename}"
        val url = artifactRepositories
                .map { repo -> "$repo/$path" }
                .firstOrNull(::checkExists)
                ?: throw kotlin.RuntimeException("Could not find repository containing library $path")
        //TODO remove when Mojang launcher is updated
        if (!classifiers && classifier != null) { //Mojang launcher doesn't currently support classifiers, so... move it to part of the version, and force the extension to 'jar'
            version = "${version}-${classifier}"
            classifier = null
            extension = "jar"
            path = "${group.replace('.', '/')}/${name}/${version}/${name}-${version}.jar"
        }
        ret[key] = buildJsonObject { 
            put("name", "${group}:${name}:${version}" + (if (classifier == null) "" else ":${classifier}") + (if (extension == "jar") "" else "@${extension}"))
            putJsonObject("downloads") {
                putJsonObject("artifact") {
                    put("path", path)
                    put("url", url)
                    put("sha1", it.file.sha1())
                    put("size", it.file.length())
                }
            }
        }
    }
    return ret
}

fun checkExists(url: String): Boolean {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "HEAD"
    conn.connect()
    return conn.responseCode == 200
}

fun Project.getClasspath(libs: MutableMap<String, JsonElement>, artifact: String): Set<String> {
    return artifactTree(artifact).values
        .mapNotNull { lib ->
            lib["name"]?.jsonPrimitive?.content
                ?.also { 
                    libs[it] = lib
                }
        }
        .toMutableSet()
}

fun Project.artifactTree(artifact: String): Map<String, JsonObject> {
    val dep = dependencies.create(artifact)
    val cfg = configurations.detachedConfiguration(dep)
    return getArtifacts(cfg, true)
}
