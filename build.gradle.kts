import de.undercouch.gradle.tasks.download.DownloadAction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import lzma.streams.LzmaOutputStream
import net.minecraftforge.forge.tasks.CheckATs
import net.minecraftforge.forge.tasks.CheckSAS
import net.minecraftforge.gradle.common.tasks.*
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.mcp.MCPExtension
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPConfig
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.patcher.PatcherExtension
import net.minecraftforge.gradle.patcher.tasks.*
import net.minecraftforge.srgutils.IMappingFile
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.filters.ReplaceTokens
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://su5ed.jfrog.io/artifactory/maven/")
        }
        maven {
            url = uri("https://maven.minecraftforge.net/")
        }
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:5.1-legacy.+")
        classpath("org.ow2.asm:asm:7.1")
        classpath("org.ow2.asm:asm-tree:7.1")
        classpath("org.eclipse.jgit:org.eclipse.jgit:5.10.0.202012080955-r")
        classpath("com.github.jponge:lzma-java:1.3")
        
        classpath(kotlin("gradle-plugin", version = "1.5.0"))
        classpath(kotlin("serialization", version = "1.5.0"))
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.0")
    }
    
    extra.apply {
        set("JAR_SIGNER", null)
        if (project.hasProperty("keystore")) {
            set("JAR_SIGNER", mapOf(
                "storepass" to project.properties["keystoreStorePass"],
                "keypass" to project.properties["keystoreKeyPass"],
                "keystore" to project.properties["keystore"]
            ))
        }
    }
}

plugins {
    id("org.cadixdev.licenser") version "0.6.1"
    id("de.undercouch.download") version "3.3.0"
    id("com.github.ben-manes.versions") version "0.22.0"
    eclipse
}

val minecraftVersion = "1.4.7"
val mappingsChannel = "stable"
val mcpVersion = "7.26"
val mappingsVersion = "$mcpVersion-$minecraftVersion"
val postProcessor = mapOf(
    "tool" to "net.minecraftforge:mcpcleanup:2.3.2:fatjar",
    "repo" to "https://maven.minecraftforge.net/",
    "args" to arrayOf("--input", "{input}", "--output", "{output}")
)
var jarSigner: Map<String, Any?> = if (project.hasProperty("keystore")) { 
    mapOf(
        "storepass" to project.properties["keystoreStorePass"],
        "keypass" to project.properties["keystoreKeyPass"],
        "keystore" to project.properties["keystore"]
    ) 
} else emptyMap()
val extraTxts = files(
        "CREDITS.md",
        "LICENSE-fml.txt",
        "LICENSE-MinecraftForge.txt",
        "LICENSE-Paulscode IBXM Library.txt",
        "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
)
val SU5ED_MAVEN = "https://su5ed.jfrog.io/artifactory/maven/"
val artifactRepositories = listOf("https://libraries.minecraft.net", "https://maven.minecraftforge.net", SU5ED_MAVEN)

subprojects { 
    repositories { 
        exclusiveContent { 
            forRepository {
                maven { 
                    name = "argo"
                    url = uri(SU5ED_MAVEN)
                }
            }
            filter {
                includeGroup("net.sourceforge.argo")
            }
        }
    }
}

project(":mcp") {
    apply(plugin = "net.minecraftforge.gradle.mcp")
    configure<MCPExtension> {
        setConfig(minecraftVersion)
        pipeline.set("joined")
    }
}

project(":clean") {
    evaluationDependsOn(":mcp")
    apply(plugin = "eclipse")
    apply(plugin = "net.minecraftforge.gradle.patcher")
    
    tasks {
        named<JavaCompile>("compileJava") { // Need this here so eclipse task generates correctly.
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
        
        named<ExtractZip>("extractMapped") {
            filter.set(Predicate { zipEntry ->
                zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
            })
        }
    }

    dependencies {
        "implementation"("net.minecraftforge:mergetool:0.2.3.3:cpw")
        "implementation"("org.bouncycastle:bcprov-jdk15on:1.47")
        "implementation"("net.sourceforge.argo:argo:2.25")
    }
    
    configurations {
        named("minecraftImplementation") {
            exclude(group = "net.minecraft", module = "launchwrapper")
        }
    }

    configure<PatcherExtension> {
        parent.set(project(":mcp"))
        mcVersion.set(minecraftVersion)
        patchedSrc.set(file("src/main/java"))
        
        mappings(mappingsChannel, mappingsVersion)
        processor(postProcessor)
            
        runs {
            named("clean_client") {
                taskName = "clean_client"
                            
                main = "mcp.client.Start"
                workingDirectory = project.file("run").absolutePath
                
                property("java.library.path", tasks.getByName<ExtractNatives>("extractNatives").output.get().asFile.absolutePath)
            }
            
            named("clean_server") {
                taskName = "clean_server"
                
                main = "net.minecraft.server.MinecraftServer"
                workingDirectory = project.file("run").absolutePath
            }
        }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name == "jopt-simple") {
                useVersion("5.0.4")
            }
            
            if (requested.name == "asm-all") {
                useVersion("5.2")
            }
        }
    }
}

project(":forge") {
    evaluationDependsOn(":clean")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "eclipse")
    apply(plugin = "net.minecraftforge.gradle.patcher")
    apply(plugin = "org.cadixdev.licenser")
    apply(plugin = "de.undercouch.download")

    group = "net.minecraftforge"

    configure<SourceSetContainer> {
        val fmllauncher = create("fmllauncher") {
            java {
                srcDirs("$rootDir/src/fmllauncher/java")
            }
            resources {
                srcDirs("$rootDir/src/fmllauncher/resources")
            }
        }
        named("main") {
            compileClasspath += fmllauncher.runtimeClasspath
            runtimeClasspath += fmllauncher.runtimeClasspath
            java {
                srcDirs("$rootDir/src/main/java")
            }
            resources {
                srcDirs("$rootDir/src/main/resources")
            }
        }
        named("test") {
            val runtime = getByName("main").runtimeClasspath
            compileClasspath += runtime
            runtimeClasspath += runtime
            java {
                srcDirs("$rootDir/src/test/java")
            }
            resources {
                srcDirs("$rootDir/src/test/resources")
            }
        }
        create("userdev") {
            val runtime = getByName("main").runtimeClasspath
            compileClasspath += runtime
            runtimeClasspath += runtime
            java {
                srcDirs("$rootDir/src/userdev/java")
            }
            resources {
                srcDirs("$rootDir/src/userdev/resources")
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
    val mcpArtifact = project(":mcp").extensions.getByName<MCPExtension>("mcp").config.get()
    val versionJson = project(":mcp").file("build/mcp/downloadJson/version.json")
    val binpatchTool = "net.minecraftforge:binarypatcher:1.1.1:fatjar"
    val installerTools = "net.minecraftforge:installertools:1.1.11"
    val gitInfo = gitInfo()

    version = getVersion(gitInfo)

    configure<PatcherExtension> {
        excs.from(file("$rootDir/src/main/resources/forge.exc"))
        parent.set(project(":clean"))
        patches.set(file("$rootDir/patches/minecraft"))
        patchedSrc.set(file("src/main/java"))
        isSrgPatches = true
        notchObf = true
        accessTransformers(file("$rootDir/src/main/resources/fml_at.cfg"), file("$rootDir/src/main/resources/forge_at.cfg"))
        //sideAnnotationStripper = file("$rootDir/src/main/resources/forge.sas")
        processor(postProcessor)

        runs {
            named("forge_client") {
                taskName = "forge_client"
                workingDirectory = project.file("run").absolutePath
                main = "net.minecraftforge.legacydev.MainClient"
                arg("--extractResources")

                environment(
                    mapOf(
                        "mainClass" to "cpw.mods.fml.relauncher.wrapper.ClientLaunchWrapper",
                        "assetIndex" to "{asset_index}",
                        "assetDirectory" to tasks.getByName<DownloadAssets>("downloadAssets").output.absolutePath,
                        "nativesDirectory" to tasks.getByName<ExtractNatives>("extractNatives").output.get().asFile.absolutePath,
                        "MC_VERSION" to minecraftVersion,
                        "MCP_MAPPINGS" to "{mcp_mappings}",
                        "MCP_TO_SRG" to "{mcp_to_srg}",
                        "FORGE_GROUP" to project.group,
                        "FORGE_VERSION" to (project.version as String).substring(minecraftVersion.length + 1)
                    )
                )
            }

            named("forge_server") {
                taskName = "forge_server"
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
    
    val tokenMap = mapOf(
            "tokens" to mapOf(
                    "FORGE_VERSION" to project.version,
                    "FORGE_VERSION_RAW" to project.version.toString().split("-", limit = 2)[1],
                    "FORGE_GROUP" to project.group,
                    "FORGE_NAME" to project.name,
                    "MC_VERSION" to minecraftVersion,
                    "MCP_VERSION" to mcpVersion,
                    "MAPPING_CHANNEL" to mappingsChannel,
                    "MAPPING_VERSION" to mappingsVersion
            )
    )
    
    configurations {
        val installer = create("installer") {
            isTransitive = false //Don't pull all libraries, if we're missing something, add it to the installer list so the installer knows to download it.
        }
        val implementation = getByName("implementation")
    
        named("api") {
            extendsFrom(installer)
        }
    
        named("fmllauncherImplementation") {
            extendsFrom(installer, implementation)
        }
            
        val shade = register("shade")
        implementation.extendsFrom(shade.get())
        
        named("minecraftImplementation") {
            exclude(group = "net.minecraft", module = "launchwrapper")
        }
    }

    repositories {
        maven {
            name = "Su5eD"
            url = uri(SU5ED_MAVEN)
            content { 
                includeGroup("de.oceanlabs.mcp")
                includeModule("net.minecraftforge", "legacydev")
            }
        }
    }

    dependencies {
        val installer = configurations.getByName("installer")
        val implementation = configurations.getByName("implementation")

        installer("org.ow2.asm:asm-debug-all:5.2")
        installer("lzma:lzma:0.0.1")
        installer("net.sf.jopt-simple:jopt-simple:5.0.3")
        installer("com.google.guava:guava:14.0")
        installer("com.google.code.gson:gson:2.3")
        installer("net.sourceforge.argo:argo:2.25")
        installer("com.mojang:authlib:2.1.28")
        installer("org.apache.logging.log4j:log4j-core:2.5")
        installer("org.apache.logging.log4j:log4j-api:2.5")
        installer("org.apache.commons:commons-lang3:3.12.0")
        installer("commons-io:commons-io:2.10.0")
        installer("commons-codec:commons-codec:1.15")

        implementation("net.minecraftforge:legacydev:0.2.4.+:fatjar")
        implementation("org.bouncycastle:bcprov-jdk15on:1.47")
    }
    
    val jsonFormat = Json { prettyPrint = true }

    tasks {
        named<JavaCompile>("compileJava") { // Need this here so eclipse task generates correctly.
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
        
        named<ApplyPatches>("applyPatches") {
            maxFuzzOffset = 3
        }

        sequenceOf("client", "server", "joined").forEach { side ->
            val name = side.capitalize()
            val gen = getByName<GenerateBinPatches>("gen${name}BinPatches")
            register<ApplyBinPatches>("apply${name}BinPatches") {
                dependsOn(gen)
                clean.set(gen.cleanJar)
                patch.set(gen.output)
            }
            
            afterEvaluate { 
                gen.cleanJar.set(MavenArtifactDownloader.generate(project, "net.minecraft:$side:$minecraftVersion", true))
            }
        }

        register("downloadLibraries") {
            dependsOn(":mcp:setupMCP")
            inputs.file(versionJson)

            doLast {
                val artifacts = getLibArtifacts(versionJson)

                artifacts.forEach { art ->
                    val target = file("build/libraries/" + art["path"]?.jsonPrimitive?.content)
                    if (!target.exists()) {
                        download {
                            this.configure(closureOf<DownloadAction> {
                                src(art["url"]?.jsonPrimitive?.content)
                                dest(target.absolutePath)
                            })
                        }
                    }
                }
            }
        }

        register<ExtractInheritance>("extractInheritance") {
            dependsOn("genJoinedBinPatches", "downloadLibraries")
            input.set(getByName<GenerateBinPatches>("genJoinedBinPatches").cleanJar)
            doFirst {
                val artifacts = getLibArtifacts(versionJson)
                artifacts.forEach { art ->
                    val target = file("build/libraries/" + art["path"]?.jsonPrimitive?.content)
                    if (target.exists())
                        libraries.add(target)
                }
            }
        }
        
        register<CheckATs>("checkATs") {
            val extractInheritance = getByName<ExtractInheritance>("extractInheritance")
            val createSrg2Mcp = getByName<GenerateSRG>("createSrg2Mcp")
            dependsOn(extractInheritance, createSrg2Mcp)
            
            ats.from(project.extensions.getByType(PatcherExtension::class).accessTransformers)
            mappings.set(createSrg2Mcp.output)
        }
        
        register<CheckSAS>("checkSAS") {
            val extractInheritance = getByName<ExtractInheritance>("extractInheritance")
            dependsOn(extractInheritance)
            inheritance.set(extractInheritance.output)
            sass.from(project.extensions.getByType(PatcherExtension::class).sideAnnotationStrippers)
        }

        register<ExtractMCPData>("extractObf2Srg") {
            dependsOn(":mcp:downloadConfig")
            config.set(project(":mcp").tasks.getByName<DownloadMCPConfig>("downloadConfig").output)
        }

        register("deobfDataLzma") {
            val extractObf2Srg = getByName<ExtractMCPData>("extractObf2Srg")
            dependsOn(extractObf2Srg)

            val outputSrg = file("$buildDir/deobfDataLzma/data.srg")
            val output = file("$buildDir/deobfDataLzma/data.lzma")

            inputs.file(extractObf2Srg.output)
            outputs.file(output)

            doLast {
                IMappingFile.load(extractObf2Srg.output.get().asFile)
                    .reverse()
                    .write(outputSrg.toPath(), IMappingFile.Format.SRG, false)

                val ins = outputSrg.inputStream()
                val os = output.outputStream()

                val lzma = LzmaOutputStream.Builder(os)
                    .useEndMarkerMode(true)
                    .build()

                ins.copyTo(lzma)

                lzma.close()
            }
        }
        
        named<Jar>("universalJar") {
            val deobfDataLzma = getByName("deobfDataLzma")
            dependsOn(deobfDataLzma)
            from(deobfDataLzma.outputs){
                rename{"deobfuscation_data-${minecraftVersion}.lzma"}
            }
            
            from(extraTxts)
            
            filesMatching("*.properties") {
                filter(ReplaceTokens::class, tokenMap)
            }
            
            doFirst {
                val classpath = StringBuilder()
                val artifacts = getArtifacts(project.configurations.getByName("installer"), false)
                artifacts.forEach { (_, lib) ->
                    classpath.append("libraries/${lib.jsonObject["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("path")?.jsonPrimitive?.content} ")
                }
                classpath.append("minecraft_server.$minecraftVersion.jar")
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
        
        register<Jar>("launcherJar") {
            archiveClassifier.set("launcher")
            from(project.extensions.getByType(SourceSetContainer::class).getByName("fmllauncher").output)
            
            doFirst { 
                val classpath = StringBuilder()
                val artifacts = getArtifacts(project.configurations.getByName("installer"), false)
                artifacts.forEach { (_, lib) -> classpath.append("libraries/${lib["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("path")?.jsonPrimitive?.content} ") }
                classpath.append("libraries/net/minecraft/server/${minecraftVersion}-${mcpVersion}/server-${minecraftVersion}-${mcpVersion}-extra.jar")
                
                manifests.forEach { (pkg, values) -> 
                    if (pkg == "/") {
                        manifest.attributes(
                                "Main-Class" to "cpw.mods.fml.relauncher.wrapper.ServerLaunchWrapper",
                                "Class-Path" to classpath.toString()
                        )
                    } else {
                        manifest.attributes(values, pkg)
                    }
                }
            }
        }
        
        val launcherJar = getByName<Jar>("launcherJar")
        val launcherJarFile = launcherJar.archiveFile.get().asFile
        val names = project.extensions.getByType(PatcherExtension::class).runs.map(RunConfig::getTaskName)
        whenTaskAdded { 
            if (this is JavaExec && names.contains(this.name)) {
                dependsOn(launcherJar)
                classpath(launcherJarFile)
            }
        }
        
        register("launcherJson") {
            dependsOn(launcherJar)
            
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
            
            configure<ExtraPropertiesExtension> {
                set("output", output)
                set("comment", comment)
                set("id", id)
            }
            
            inputs.file(launcherJarFile)
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
                    put("minecraftArguments", listOf(
                            "\${auth_player_name}",
                            "\${auth_session}",
                            "--uuid", "\${auth_uuid}",
                            "--gameDir", "\${game_directory}",
                            "--assetsDir", "\${game_assets}"
                    ).joinToString(separator = " "))
                    putJsonArray("libraries") {
                        addJsonObject { 
                            put("name", "${project.group}:${project.name}:${project.version}")
                            putJsonObject("downloads") {
                                putJsonObject("artifact") {
                                    put("path", "${project.group.toString().replace(".", "/")}/${project.name}/${project.version}/${project.name}-${project.version}.jar")
                                    put("url", "") //Do not include the URL so that the installer/launcher won't grab it. This is also why we don't have the universal classifier
                                    put("sha1", launcherJarFile.sha1())
                                    put("size", launcherJarFile.length())
                                }
                            }
                        }
                        
                        val artifacts = getArtifacts(project.configurations.getByName("installer"), false)
                        artifacts.forEach { (_, lib) -> add(lib) }
                    }
                }
                
                output.writeText(jsonFormat.encodeToString(json))
            }
        }
        
        val installerJson = create("installerJson") {
            val applyClientBinPatches = getByName<ApplyBinPatches>("applyClientBinPatches")
            val applyServerBinPatches = getByName<ApplyBinPatches>("applyServerBinPatches")
            
            val universalJar = getByName<Jar>("universalJar")
            val universalJarFile = universalJar.archiveFile.get().asFile
            val launcherJson = getByName("launcherJson")
            val output = file("build/install_profile.json")
            val jarSplitter = "net.minecraftforge:jarsplitter:1.1.2"
            val binPatcher = binpatchTool.substring(0, binpatchTool.length - 1 - binpatchTool.split(':')[3].length)
            
            dependsOn("launcherJson", universalJar, applyClientBinPatches, applyServerBinPatches)
            
            configure<ExtraPropertiesExtension> {
                set("output", output)
            }
            
            inputs.file(universalJarFile)
            inputs.file(getByName<GenerateBinPatches>("genClientBinPatches").toolJar)
            inputs.file(launcherJson.property("output") as File)
            outputs.file(output)
            
            doLast {
                val libs = buildJsonObject { 
                    putJsonObject("${project.group}:${project.name}:${project.version}:universal") {
                        put("name", "${project.group}:${project.name}:${project.version}:universal")
                        putJsonObject("downloads") {
                            putJsonObject("artifact") {
                                put("path", "${project.group.toString().replace('.', '/')}/${project.name}/${project.version}/${project.name}-${project.version}-universal.jar")
                                put("url", "") //Do not include the URL so that the installer/launcher won't grab it.
                                put("sha1", universalJarFile.sha1())
                                put("size", universalJarFile.length())
                            }
                        }
                    }
                }.toMutableMap()
                
                val json = buildJsonObject { 
                    put("_comment_", launcherJson.extra["comment"] as JsonArray)
                    put("spec", 0)
                    put("profile", project.name)
                    put("version", launcherJson.extra["id"] as String)
                    put("icon", "data:image/png;base64," + String(Base64.getEncoder().encode(Files.readAllBytes(rootProject.file("icon.ico").toPath()))))
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
                            put("client", "'${getByName<DownloadMavenArtifact>("downloadClientSlim").output.get().asFile.sha1()}'")
                            put("server", "'${getByName<DownloadMavenArtifact>("downloadServerSlim").output.get().asFile.sha1()}'")
                        }
                        putJsonObject("MC_EXTRA") {
                            put("client", "[net.minecraft:client:${minecraftVersion}-${mcpVersion}:extra]")
                            put("server", "[net.minecraft:server:${minecraftVersion}-${mcpVersion}:extra]")
                        }
                        putJsonObject("MC_EXTRA_SHA") {
                            put("client", "'${getByName<DownloadMavenArtifact>("downloadClientExtra").output.get().asFile.sha1()}'")
                            put("server", "'${getByName<DownloadMavenArtifact>("downloadServerExtra").output.get().asFile.sha1()}'")
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
                            put("client", "'${applyClientBinPatches.output.get().asFile.sha1()}'")
                            put("server", "'${applyServerBinPatches.output.get().asFile.sha1()}'")
                        }
                        putJsonObject("MCP_VERSION") {
                            put("client", "'${mcpVersion}'")
                            put("server", "'${mcpVersion}'")
                        }
                    }
                    putJsonArray("processors") {
                        addJsonObject { 
                            put("jar", installerTools)
                            putJsonArray("classpath") {
                                getClasspath(project, libs, installerTools)
                                        .forEach { add(it) }
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
                                getClasspath(project, libs, jarSplitter)
                                        .forEach { add(it) }
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
                        /*addJsonObject { 
                            put("jar", specialSourceMcp)
                            putJsonArray("classpath") {
                                getClasspath(project, libs, specialSourceMcp)
                                        .forEach { add(it) }
                            }
                            putJsonArray("args") {
                                add("--in-jar"); add("{MC_SLIM}")
                                add("--out-jar"); add("{MC_SRG}")
                                add("--srg-in"); add("{MAPPINGS}")
                            }
                        }*/
                        addJsonObject { 
                            put("jar", binPatcher)
                            putJsonArray("classpath") {
                                getClasspath(project, libs, binPatcher)
                                    .forEach { add(it) }
                            }
                            putJsonArray("args") {
                                add("--clean"); add("{MC_SLIM}")
                                add("--output"); add("{PATCHED}")
                                add("--apply"); add("{BINPATCH}")
                            }
                            putJsonObject("outputs") {
                                put("{PATCHED}", "{PATCHED_SHA}")
                            }
                        }
                    }
                }.toMutableMap()
                
                getClasspath(project, libs, mcpArtifact.descriptor) //Tell it to download mcp_config
                json["libraries"] = JsonArray(libs.values.sortedBy { it.jsonObject["name"]?.jsonPrimitive?.content })
                        
                output.writeText(jsonFormat.encodeToString(json))
            }
        }
        
        sequenceOf("client", "server").forEach { side ->
            sequenceOf("slim", "extra").forEach { type -> 
                val name = "download${side.capitalize()}${type.capitalize()}"
                val task = create<DownloadMavenArtifact>(name) {
                    setArtifact("net.minecraft:${side}:${minecraftVersion}:${type}")
                }
                installerJson.dependsOn(name)
                installerJson.inputs.file(task.output)
            }
        }
        
        register<DownloadMavenArtifact>("downloadInstaller") {
            setArtifact("net.minecraftforge:installer:2.0.+:shrunk")
            changing = true
        }
        
        register<Zip>("installerJar") {
            val launcherJson = getByName("launcherJson")
            val genClientBinPatches = getByName("genClientBinPatches")
            val genServerBinPatches = getByName("genServerBinPatches")
            val universalJar = getByName("universalJar")
            
            dependsOn("downloadInstaller", installerJson, launcherJson, genClientBinPatches, genServerBinPatches, universalJar)
            archiveClassifier.set("installer")
            archiveExtension.set("jar") //Needs to be Zip task to not override Manifest, so set extension
            destinationDirectory.set(file("build/libs"))
            from(extraTxts, installerJson.property("output"), launcherJson.property("output"), rootProject.file("/src/main/resources/url.png"))
            from(rootProject.file("/src/main/resources/forge_logo.png")) {
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
            }
            from(launcherJar) {
                into("/maven/${project.group.toString().replace('.', '/')}/${project.name}/${project.version}/")
                rename { "${project.name}-${project.version}.jar" }
            }
            from(zipTree(getByName<DownloadMavenArtifact>("downloadInstaller").output)) {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
        
        setOf(getByName<Jar>("universalJar"), getByName<Zip>("installerJar")).forEach { t ->
            register<SignJar>("sign${t.name.capitalize()}") {
                dependsOn(t)
                onlyIf {
                    jarSigner.isNotEmpty() && t.state.failure == null
                }
                alias.set("forge")
                storePass.set(jarSigner["storepass"] as String?)
                keyPass.set(jarSigner["keypass"] as String?)
                keyStore.set(jarSigner["keystore"] as String?)
                val archiveFile = t.archiveFile.get().asFile
                inputFile.set(archiveFile)
                outputFile.set(archiveFile)
                doFirst {
                    project.logger.lifecycle("Signing: $inputFile")
                }
                t.finalizedBy(getByName("sign${t.name.capitalize()}"))
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
            from(rootProject.file("gradle/")){
                into("gradle/")
            }
            
            from(rootProject.file("mdk/")){ 
                rootProject.file("mdk/gitignore.txt").forEachLine { 
                    if (it.trim().isNotEmpty() && !it.trim().startsWith('#'))
                        exclude(it)
                }
                filter(ReplaceTokens::class, tokenMap)
                rename("gitignore\\.txt", ".gitignore")
            }
        }
        
        named<ProcessResources>("processFmllauncherResources") {
            inputs.property("version", project.version)
            
            filesMatching("*.properties") {
                filter(ReplaceTokens::class, tokenMap)
            }
        }
        
        named<GenerateUserdevConfig>("userdevConfig") {
            val artifacts = getArtifacts(project.configurations["installer"], true)
            artifacts.forEach { (_, lib) ->
                libraries.add(lib["name"]?.jsonPrimitive?.content!!)
            }
            libraries.add("net.minecraftforge:legacydev:0.2.4.+:fatjar")
            libraries.add("net.sourceforge.argo:argo:2.25")
            libraries.add("org.bouncycastle:bcprov-jdk15on:1.47")
            libraries.add("${project.group}:${project.name}:${project.version}:launcher")
            sourceFilters.set(listOf("^(?!argo|org/bouncycastle).*"))

            runs {
                register("client") {
                    main = "net.minecraftforge.legacydev.MainClient"
                    arg("--extractResources")

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
                            "FORGE_VERSION" to project.version.toString().substring(minecraftVersion.length + 1)
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
        
        register<Jar>("userdevExtras") {
            dependsOn("classes")
            from(project.extensions.getByType(SourceSetContainer::class).getByName("userdev").output)
            archiveClassifier.set("userdev-temp")
        }
        
        register<ReobfuscateJar>("userdevExtrasReobf") {
            val userdevExtras = getByName<Jar>("userdevExtras")
            val createMcp2Srg = getByName<GenerateSRG>("createMcp2Srg")
            dependsOn(userdevExtras, createMcp2Srg)
            input.set(userdevExtras.archiveFile.get().asFile)
            classpath.setFrom(project.configurations.getByName("compileClasspath"))
            srg.set(createMcp2Srg.output)
        }
        
        named<Jar>("userdevJar") {
            val userdevExtrasReobf = getByName<ReobfuscateJar>("userdevExtrasReobf")
            dependsOn(userdevExtrasReobf)
            from(zipTree(userdevExtrasReobf.output)) {
                into("/inject/")
            }
            project.extensions.getByType(SourceSetContainer::class).getByName("userdev").output.resourcesDir?.let {
                from (it) {
                    into("/inject/")
                }
            }
            archiveClassifier.set("userdev3")
        }
        
        named("eclipse") {
            dependsOn("genEclipseRuns")
        }
        
        named<GeneratePatches>("genPatches") {
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
        
        named<ExtractZip>("extractMapped") {
            filter.set(Predicate { zipEntry ->
                zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
            })
        }
    }
    
    if (project.hasProperty("UPDATE_MAPPINGS")) {
        val srcDirs = extensions.getByType(SourceSetContainer::class).getByName("test").java.srcDirs
        tasks.named<ExtractRangeMap>("extractRangeMap") {
            sources.from(srcDirs)
        }
        tasks.named<ApplyRangeMap>("applyRangeMap") {
            sources.from(srcDirs)
        }
        
        tasks.named<ExtractExistingFiles>("extractMappedNew") {
            srcDirs.forEach { targets.from(it) }
        }
    }
    
    license {
        header.set(resources.text.fromFile(file("$rootDir/LICENSE-header.txt")))
    
        include("cpw/mods/fml/")
            
        tasks {
            register("fmlllauncher") {
                files.from("$rootDir/src/fmlllauncher/java")
            }
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
    
    configure<PublishingExtension> {
        publications { 
            create<MavenPublication>("mavenJava") {
                if (changelog.exists()) {
                    artifact(changelog) {
                        classifier = "changelog"
                    }
                }
                
                sequenceOf("universalJar", "installerJar", "makeMdk", "userdevJar", "sourcesJar", "launcherJar")
                    .map(tasks::getByName)
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
                    url = uri(SU5ED_MAVEN)
                    credentials { 
                        username = project.properties["artifactoryUser"] as String
                        password = project.properties["artifactoryPassword"] as String
                    }
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
    val ret: MutableMap<String, JsonObject> = kotlin.collections.HashMap()
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

tasks.register("setup") {
    dependsOn(":clean:extractMapped")
    dependsOn(":forge:extractMapped") //These must be strings so that we can do lazy resolution. Else we need evaluationDependsOnChildren above
}

fun checkExists(url: String): Boolean {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "HEAD"
    conn.connect()
    return conn.responseCode == 200
}

fun getClasspath(project: Project, libs: MutableMap<String, JsonElement>, artifact: String): Set<String> {
    val ret: MutableSet<String> = kotlin.collections.HashSet()
    artifactTree(project, artifact).forEach { (_, lib) ->
        val libName = lib["name"]?.jsonPrimitive?.content
        libName?.let { 
            libs[it] = lib
            if (it != artifact)
                ret.add(it)
        }
    }
    return ret
}

fun artifactTree(project: Project, artifact: String): Map<String, JsonObject> {
    if (!project.ext.has("tree_resolver")) project.ext["tree_resolver"] = 1
    
    val treeResolver = project.ext["tree_resolver"] as Int
    val cfg = project.configurations.create("tree_resolver_$treeResolver")
    project.ext.set("tree_resolver", treeResolver + 1)
    
    val dep = project.dependencies.create(artifact)
    cfg.dependencies.add(dep)
    return getArtifacts(cfg, true)
}

fun gitInfo(): Map<String, String> {
    val legacyBuild = 543 // Base build number to not conflict with existing build numbers
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
    if (branch != null && branch.startsWith("pulls/"))
        branch = "pr" + branch.split("/", limit = 2)[1]
    if (branch in setOf(
            null,
            "master",
            "HEAD",
            minecraftVersion,
            "$minecraftVersion.0",
            minecraftVersion.substring(0, minecraftVersion.lastIndexOf(".")) + ".x",
            "$minecraftVersion-FG5"        
        )
    )
        return "${minecraftVersion}-${info["tag"]}.${info["offset"]}"
    return "${minecraftVersion}-${info["tag"]}.${info["offset"]}-${branch}"
}
