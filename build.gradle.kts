import de.undercouch.gradle.tasks.download.DownloadAction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import net.minecraftforge.gradle.common.task.*
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader
import net.minecraftforge.gradle.common.util.MinecraftRepo
import net.minecraftforge.gradle.mcp.MCPExtension
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask
import net.minecraftforge.gradle.mcp.task.GenerateSRG
import net.minecraftforge.gradle.patcher.PatcherExtension
import net.minecraftforge.gradle.patcher.task.*
import org.apache.commons.io.FileUtils
import org.apache.tools.ant.filters.ReplaceTokens
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate
import java.util.zip.ZipFile

buildscript {
    repositories {
        maven {
            url = uri("https://su5ed.jfrog.io/artifactory/maven/")
        }
        mavenCentral()
        maven {
            url = uri("https://maven.minecraftforge.net/")
        }
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:4.1.legacy-SNAPSHOT")
        classpath("org.ow2.asm:asm:7.1")
        classpath("org.ow2.asm:asm-tree:7.1")
        classpath("org.eclipse.jgit:org.eclipse.jgit:5.10.0.202012080955-r")
        
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
    id("net.minecrell.licenser") version "0.4"
    id("de.undercouch.download") version "3.3.0"
    id("com.github.ben-manes.versions") version "0.22.0"
    id("com.github.johnrengelman.shadow") version "6.1.0" apply false
    eclipse
}

MinecraftRepo.MCP_URL = "https://su5ed.jfrog.io/artifactory/maven/"

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
        "CREDITS-fml.txt",
        "CREDITS-MinecraftForge.txt",
        "LICENSE-fml.txt",
        "LICENSE-MinecraftForge.txt",
        "LICENSE-Paulscode IBXM Library.txt",
        "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
)

project(":mcp") {
    apply(plugin = "net.minecraftforge.gradle.mcp")
    
    configure<MCPExtension> {
        setConfig(minecraftVersion)
        pipeline = "joined"
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
            filter = Predicate { zipEntry ->
                zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
            }
        }
    }

    repositories {
        exclusiveContent { 
            forRepository {
                maven {
                    name = "argo"
                    url = uri("https://su5ed.jfrog.io/artifactory/maven/")
                }
            }
            filter {
                includeGroup("net.sourceforge.argo")
            }
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
        parent = project(":mcp")
        mcVersion = minecraftVersion
        patchedSrc = file("src/main/java")
        
        mappings(mappingsChannel, mappingsVersion)
        processor(postProcessor)
            
        runs {
            named("clean_client") {
                taskName = "clean_client"
                            
                main = "mcp.client.Start"
                workingDirectory = project.file("run").absolutePath
                
                args(listOf(
                    "--gameDir", ".",
                    "--version", mcVersion,
                    "--assetsDir", tasks.getByName<DownloadAssets>("downloadAssets").output.absolutePath,
                    "--assetIndex", "{asset_index}",
                    "--accessToken", "O"
                ))
                
                property("java.library.path", tasks.getByName<ExtractNatives>("extractNatives").output.absolutePath)
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
    apply(plugin = "net.minecrell.licenser")
    apply(plugin = "de.undercouch.download")
    apply(plugin = "com.github.johnrengelman.shadow")

    group = "net.minecraftforge"

    configure<SourceSetContainer> {
        named("main") {
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
    val mcpArtifact = project(":mcp").extensions.getByName<MCPExtension>("mcp").config
    val versionJson = project(":mcp").file("build/mcp/downloadJson/version.json")
    val binpatchTool = "net.minecraftforge:binarypatcher:1.1.1:fatjar"
    val gitInfo = gitInfo()

    version = getVersion(gitInfo)

    configure<PatcherExtension> {
        exc(file("$rootDir/src/main/resources/forge.exc"))
        parent = project(":clean")
        patches = file("$rootDir/patches/minecraft")
        patchedSrc = file("src/main/java")
        srgPatches = true
        notchObf = true
        accessTransformer(file("$rootDir/src/main/resources/forge_dev_at.cfg"))
        //sideAnnotationStripper = file("$rootDir/src/main/resources/forge.sas")
        processor(postProcessor)

        runs {
            named("forge_client") {
                taskName = "forge_client"
                workingDirectory = project.file("run").absolutePath
                main = "net.minecraftforge.legacydev.MainClient"

                environment(
                    mapOf(
                        "mainClass" to "cpw.mods.fml.relauncher.wrapper.ClientLaunchWrapper",
                        "assetIndex" to "{asset_index}",
                        "assetDirectory" to tasks.getByName<DownloadAssets>("downloadAssets").output.absolutePath,
                        "nativesDirectory" to tasks.getByName<ExtractNatives>("extractNatives").output.absolutePath,
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
                    "FORGE_VERSION_RAW" to project.version.toString().split("-")[1],
                    "FORGE_GROUP" to project.group,
                    "FORGE_NAME" to project.name,
                    "MC_VERSION" to minecraftVersion,
                    "MCP_VERSION" to mcpVersion,
                    "MAPPING_CHANNEL" to mappingsChannel,
                    "MAPPING_VERSION" to mappingsVersion
            )
    )
    
    configurations {
        val installer = register("installer") {
            isTransitive = false //Don't pull all libraries, if we're missing something, add it to the installer list so the installer knows to download it.
        }.get()
    
        named("api") {
            extendsFrom(installer)
        }
    
        register("fmllauncherImplementation") {
            extendsFrom(installer)
        }
            
        val shade = register("shade")
        getByName("implementation").extendsFrom(shade.get())
        
        named("minecraftImplementation") {
            exclude(group = "net.minecraft", module = "launchwrapper")
        }
    }

    repositories {
        maven {
            name = "artifactory"
            url = uri("https://su5ed.jfrog.io/artifactory/maven/")
        }
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven {
                    name = "argo"
                    url = uri("https://su5ed.jfrog.io/artifactory/maven/")
                }
            }
            filter {
                includeGroup("net.sourceforge.argo")
            }
        }
    }

    dependencies {
        val installer = configurations.getByName("installer")
        val testImplementation = configurations.getByName("testImplementation")
        val implementation = configurations.getByName("implementation")

        installer("org.ow2.asm:asm-debug-all:5.2")
        installer("lzma:lzma:0.0.1")
        installer("net.sf.jopt-simple:jopt-simple:5.0.3")
        installer("com.google.guava:guava:14.0")
        installer("com.google.code.gson:gson:2.3")
        installer("net.sourceforge.argo:argo:2.25")
        installer("com.nothome:javaxdelta:2.0.1")

        //testImplementation("org.junit.jupiter:junit-jupiter-api:5.0.0")
        //testImplementation("org.junit.vintage:junit-vintage-engine:5.+")
        //testImplementation("org.opentest4j:opentest4j:1.0.0") // needed for junit 5
        //testImplementation("org.hamcrest:hamcrest-all:1.3") // needs advanced matching for list order

        implementation("net.minecraftforge:legacydev:0.2.3.+:fatjar")
        implementation("org.bouncycastle:bcprov-jdk15on:1.47")
    }
    
    val jsonFormat = Json { prettyPrint = true }

    tasks {
        named<JavaCompile>("compileJava") { // Need this here so eclipse task generates correctly.
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
        }
        
        named<TaskApplyPatches>("applyPatches") {
            maxFuzzOffset = 3
            originalPrefix = "../src-base/minecraft/"
            modifiedPrefix = "../src-work/minecraft/"
        }

        named<TaskGeneratePatches>("genPatches") {
            originalPrefix = "../src-base/minecraft/" // TODO Update prefix
            modifiedPrefix = "../src-work/minecraft/"
        }
        
        named<Jar>("jar") {
            finalizedBy("shadowJar")
        }
        
        named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
            configurations = emptyList()
            relocate("net/minecraft/src/", "")
            
            archiveClassifier.set("")
        }

        // We apply the bin patches we just created to make a jar that is JUST our changes
        named<GenerateBinPatches>("genClientBinPatches") {
            tool = binpatchTool
        }

        register<ApplyBinPatches>("applyClientBinPatches") {
            val genClientBinPatches = getByName<GenerateBinPatches>("genClientBinPatches")

            dependsOn(genClientBinPatches)
            clean = genClientBinPatches.cleanJar
            patch = genClientBinPatches.output
            tool = binpatchTool
        }

        named<GenerateBinPatches>("genServerBinPatches") {
            tool = binpatchTool
        }

        named<GenerateBinPatches>("genJoinedBinPatches") {
            tool = binpatchTool
        }

        register<GenerateBinPatches>("genRuntimeBinPatches") {
            dependsOn("genClientBinPatches", "genServerBinPatches")
            tool = binpatchTool
        }

        afterEvaluate {
            named<GenerateBinPatches>("genClientBinPatches") {
                cleanJar = MavenArtifactDownloader.generate(project, "net.minecraft:client:$minecraftVersion", true)
            }
            
            named<GenerateBinPatches>("genServerBinPatches") {
                cleanJar = MavenArtifactDownloader.generate(project, "net.minecraft:server:$minecraftVersion", true)
            }
            
            named<GenerateBinPatches>("genRuntimeBinPatches") {
                val genClientBinPatches = getByName<GenerateBinPatches>("genClientBinPatches")

                cleanJar = genClientBinPatches.cleanJar
                dirtyJar = genClientBinPatches.dirtyJar
                srg = genClientBinPatches.srg
                patchSets = genClientBinPatches.patchSets
                args = arrayOf(
                    "--output", "{output}",
                    "--patches", "{patches}",
                    "--srg", "{srg}",
                    "--legacy",

                    "--clean", "{clean}",
                    "--dirty", "{dirty}",
                    "--prefix", "binpatch/client",

                    "--clean", "{server}",
                    "--dirty", "{dirty}",
                    "--prefix", "binpatch/server"
                )
                addExtra("server", getByName<GenerateBinPatches>("genServerBinPatches").cleanJar)
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
            input = getByName<GenerateBinPatches>("genJoinedBinPatches").cleanJar
            doFirst {
                val artifacts = getLibArtifacts(versionJson)
                artifacts.forEach { art ->
                    val target = file("build/libraries/" + art["path"]?.jsonPrimitive?.content)
                    if (target.exists())
                        addLibrary(target)
                }
            }
        }
        
        register("checkATs") {
            dependsOn("genJoinedBinPatches")
            val cleanJar = getByName<GenerateBinPatches>("genJoinedBinPatches").cleanJar
            val patcher = project.extensions.getByType(PatcherExtension::class)
            inputs.file(cleanJar)
            inputs.files(patcher.accessTransformers)
            
            doLast {
                val vanilla: MutableMap<String, Int> = kotlin.collections.HashMap()
                val zip = ZipFile(cleanJar)
                zip.entries().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        ClassReader(zip.getInputStream(entry)).accept(object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM7) {
                            var name: String? = null
                            override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
                                this.name = name
                                vanilla[name!!] = access
                            }
                            override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor? {
                                vanilla[this.name + " " + name!!] = access
                                return null
                            }

                            override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                                vanilla[this.name + " " + name!! + desc!!] = access
                                return null
                            }
                        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    }
                patcher.accessTransformers.forEach { f ->
                    val lines: TreeMap<String, Map<String, String?>> = TreeMap()
                    f.forEachLine { l ->
                        var line = l
                        val idx = line.indexOf('#')
                        if (idx == 0 || line.isEmpty()) return@forEachLine
                        val comment = if (idx == -1) null else line.substring(idx)
                        if (idx != -1) line = line.substring(0, idx - 1)
                        val (modifier, cls, desc) = (line.trim() + "     ").split(" ", limit = -1)
                        val key = cls + if (desc.isEmpty()) "" else " $desc"
                        val access = vanilla[key.replace('.', '/')]
                        if (access == null) {
                            if ((desc == "*" || desc == "*()") && vanilla[cls.replace('.', '/')] != null)
                                println("Warning: $line")
                            else {
                                println("Invalid: $line")
                                return@forEachLine
                            }
                        }
                        //TODO: Check access actually changes, and expand inheretence?
                        lines[key] = mapOf(
                            "modifier" to modifier, 
                            "comment" to comment
                        )
                    }
                    f.writeText(lines.entries.joinToString("\n") { it.value["modifier"] + " " + it.key + (if (it.value["comment"] == null) "" else " ${it.value["comment"]}") })
                }
            }
        }
        
        register("checkSAS") {
            val extractInheritance = getByName<ExtractInheritance>("extractInheritance")
            val patcher = project.extensions.getByType(PatcherExtension::class)
            
            dependsOn(extractInheritance)
            inputs.file(extractInheritance.output)
            inputs.files(patcher.sideAnnotationStrippers)
            
            doLast { 
                val json = Json.decodeFromString<JsonObject>(extractInheritance.output.readText())
                
                patcher.sideAnnotationStrippers.forEach { f ->
                    val lines: MutableSet<String> = kotlin.collections.HashSet()
                    f.forEachLine { l ->
                        var line = l    
                        if (line[0] == '\t') return@forEachLine //Skip any tabed lines, those are ones we add
                        val idx = line.indexOf('#')
                        if (idx == 0 || line.isEmpty()) {
                            lines.add(line)
                            return@forEachLine
                        }
                        
                        val comment = if (idx == -1) null else line.substring(idx)
                        if (idx != -1) line = line.substring(0, idx - 1)
                        
                        var (cls, desc) = (line.trim() + "    ").split(" ", limit = -1)
                        cls = cls.replace(".", "/")
                        desc = desc.replace("(", " (")
                        val mtd = json[cls]?.jsonObject?.get("methods")?.jsonObject?.get(desc)
                        if (desc.isEmpty() || json[cls] == null || json[cls]?.jsonObject?.get("methods") == null || mtd == null) {
                            println("Invalid: $line")
                            return@forEachLine
                        }
                        
                        lines.add(cls + " " + desc.replace(" ","") + (if (comment == null) "" else " $comment"))
                        json.values.filter {
                            val methods = it.jsonObject["methods"]
                            val descriptor = methods?.jsonObject?.get("desc")
                            return@filter methods != null && descriptor != null && descriptor.jsonObject["override"]?.jsonPrimitive?.content == cls 
                        }
                            .map { it.jsonObject["name"]?.jsonPrimitive?.content + " " + desc.replace(" ", "") }
                            .forEach {
                                lines.add("\t" + it)
                            }
                    }
                    f.writeText(lines.joinToString(separator = "\n"))
                }
            }
        }
        
        named<Jar>("universalJar") {
            val genRuntimeBinPatches = getByName<GenerateBinPatches>("genRuntimeBinPatches")
            dependsOn(genRuntimeBinPatches)
            from(genRuntimeBinPatches.output) {
                rename { "binpatches.pack.lzma" }
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
        
        register("launcherJson") {
            val universalJar = getByName<Jar>("universalJar")
            dependsOn("signUniversalJar")
            inputs.file(universalJar.archiveFile.get().asFile)
            
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
                            "\${auth_player_name}}",
                            "\${auth_session}",
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
                                    val file = universalJar.archiveFile.get().asFile
                                    put("sha1", sha1(file))
                                    put("size", file.length())
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
        
        register("installerJson") {
            dependsOn("launcherJson", "genClientBinPatches")
            
            val universalJar = getByName<Jar>("universalJar").archiveFile.get().asFile
            val launcherJson = getByName("launcherJson")
            val output = file("build/install_profile.json")
            
            configure<ExtraPropertiesExtension> {
                set("output", output)
            }
            
            inputs.file(universalJar)
            inputs.file(getByName<GenerateBinPatches>("genClientBinPatches").toolJar)
            inputs.file(launcherJson.property("output") as File)
            outputs.file(output)
            
            doLast { 
                val libs = buildJsonObject { 
                    putJsonObject("${project.group}:${project.name}:${project.version}") {
                        put("name", "${project.group}:${project.name}:${project.version}")
                        putJsonObject("downloads") {
                            putJsonObject("artifact") {
                                put("path", "${project.group.toString().replace(".", "/")}/${project.name}/${project.version}/${project.name}-${project.version}.jar")
                                put("url", "") //Do not include the URL so that the installer/launcher won't grab it. This is also why we don't have the universal classifier
                                put("sha1", sha1(universalJar))
                                put("size", universalJar.length())
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
                    putJsonObject("data") {}
                    putJsonArray("processors") {}
                }.toMutableMap()
                
                getClasspath(project, libs, mcpArtifact.descriptor) //Tell it to download mcp_config
                json["libraries"] = JsonArray(libs.values.sortedBy { it.jsonObject["name"]?.jsonPrimitive?.content })
                        
                output.writeText(jsonFormat.encodeToString(json))
            }
        }
        
        register<ExtractMCPData>("extractObf2Srg") {
            dependsOn(":mcp:downloadConfig")
            config = project(":mcp").tasks.getByName<DownloadMCPConfigTask>("downloadConfig").output
        }
        
        register<DownloadMavenArtifact>("downloadInstaller") {
            artifact = "net.minecraftforge:installer:2.0.+:shrunk"
            changing = true
        }
        
        register<Zip>("installerJar") {
            val installerJson = getByName("installerJson")
            val launcherJson = getByName("launcherJson")
            dependsOn("downloadInstaller", installerJson, launcherJson, "genClientBinPatches", "genServerBinPatches", "signUniversalJar")
            archiveClassifier.set("installer")
            archiveExtension.set("jar") //Needs to be Zip task to not override Manifest, so set extension
            destinationDirectory.set(file("build/libs"))
            from(extraTxts, installerJson.property("output"), launcherJson.property("output"), rootProject.file("/src/main/resources/url.png"))
            from(rootProject.file("/src/main/resources/forge_logo.png")) {
                rename { "big_logo.png" }
            }
            from(getByName("universalJar")) {
                into("/maven/${project.group.toString().replace(".", "/")}/${project.name}/${project.version}/")
                rename {"${project.name}-${project.version}.jar" }
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
                setAlias("forge")
                setStorePass(jarSigner["storepass"])
                setKeyPass(jarSigner["keypass"])
                setKeyStore(jarSigner["keystore"])
                val archiveFile = t.archiveFile.get().asFile
                setInputFile(archiveFile)
                setOutputFile(archiveFile)
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
        
        named<ProcessResources>("processResources") {
            filesMatching("*.properties") {
                filter(ReplaceTokens::class, tokenMap)
            }
        }
        
        named<TaskGenerateUserdevConfig>("userdevConfig") {
            val artifacts = getArtifacts(project.configurations["installer"], true)
            artifacts.forEach { (_, lib) ->
                addLibrary(lib["name"]?.jsonPrimitive?.content)
            }
            addLibrary("net.minecraftforge:legacydev:0.2.3.+:fatjar")
            addLibrary("net.sourceforge.argo:argo:2.25")
            addLibrary("org.bouncycastle:bcprov-jdk15on:1.47")
            addLibrary("com.nothome:javaxdelta:2.0.1")
            addUniversalFilter("^(?!binpatches\\.pack\\.lzma\$).*\$")
            addSourceFilter("^(?!argo|org/bouncycastle).*")

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
        
        register<TaskReobfuscateJar>("userdevExtrasReobf") {
            val userdevExtras = getByName<Jar>("userdevExtras")
            val createMcp2Srg = getByName<GenerateSRG>("createMcp2Srg")
            dependsOn(userdevExtras, createMcp2Srg)
            input = userdevExtras.archiveFile.get().asFile
            classpath = project.configurations.getByName("compileClasspath")
            srg = createMcp2Srg.output
        }
        
        named<Jar>("userdevJar") {
            val userdevExtrasReobf = getByName<TaskReobfuscateJar>("userdevExtrasReobf")
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
        
        named<TaskGeneratePatches>("genPatches") {
            doLast {
                val outputPath = output.toPath()
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
            filter = Predicate { zipEntry ->
                zipEntry.name.startsWith("net/minecraft/") || zipEntry.name.startsWith("mcp/")
            }
        }
    }
    
    if (project.hasProperty("UPDATE_MAPPINGS")) {
        val srcDirs = extensions.getByType(SourceSetContainer::class).getByName("test").java.srcDirs
        tasks.named<TaskExtractRangeMap>("extractRangeMap") {
            sources = srcDirs
        }
        tasks.named<TaskApplyRangeMap>("applyRangeMap") {
            setSources(srcDirs)
        }
        
        tasks.named<TaskExtractExistingFiles>("extractMappedNew") {
            srcDirs.forEach { addTarget(it) }
        }
    }
    
    license {
        header = file("$rootDir/LICENSE-header.txt")
    
        include("cpw/mods/fml/")
            
        tasks {
            register("main") {
                files = files("$rootDir/src/main/java")
            }
            register("test") {
                files = files("$rootDir/src/test/java")
            }
        }
    }

    val changelog = rootProject.file("build/changelog.txt")
    if (changelog.exists())
        extraTxts.from(changelog)
    
    configure<PublishingExtension> {
        publications { 
            create<MavenPublication>("mavenJava") {
                artifact(tasks.getByName("universalJar"))
                if (changelog.exists()) {
                    artifact(changelog) {
                        classifier = "changelog"
                    }
                }
                
                artifact(tasks.getByName("installerJar"))
                artifact(tasks.getByName("makeMdk"))
                artifact(tasks.getByName("userdevJar"))
                artifact(tasks.getByName("sourcesJar"))
                
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
                    url = uri("https://su5ed.jfrog.io/artifactory/maven")
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

fun sha1(file: File): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
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
        var url = "https://libraries.minecraft.net/${path}"
        if (!checkExists(url)) {
            url = "https://maven.minecraftforge.net/${path}"
        }
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
                    put("sha1", sha1(it.file))
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
    val legacyBuild = 534 // Base build number to not conflict with existing build numbers
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
            "$minecraftVersion-FG4"        
        )
    )
        return "${minecraftVersion}-${info["tag"]}.${info["offset"]}"
    return "${minecraftVersion}-${info["tag"]}.${info["offset"]}-${branch}"
}
