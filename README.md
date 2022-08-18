# MinecraftForge

Forge is a free, open-source modding API *most* of your favourite mods use!

This repository contains MinecraftForge 1.4.7 updated to use the modern data-driven toolchain. It works for both
developing as well as running mods, and is fully backwards-compatible with original releases.

## Download

Get the latest release from maven [here](https://maven.su5ed.dev/#/releases/net/minecraftforge/forge).

Mod developers should download the **MDK**, while users will want the **installer**.

## Installation

### Users

Download the installer, which will install Forge into your vanilla launcher environment, where you can then create a new
profile using that version and play the game.

### Mod Developers

Download and extract the MDK into a new folder, then follow the instructions in
its [README](https://github.com/Su5eD/MinecraftForge/blob/1.4.7-FG5/mdk/README.md)

# Updating the pre-ForgeGradle toolchain

The new data-driver MinecraftForge toolchain allows updating old versions to use modern technologies. This document
explains what changes had to be done to get 1.4.7 working and the issues I have encountered.

## Migrating Data

### Source code

The used source code originates from the latest available 1.4.7 Forge release version `1.4.7-6.6.2.534`. Minor changes
have been applied to make it compatible with the new toolchain, all of which are described below.

### Patches

Patches were probably the hardest part to port. The old toolchain uses 2 separate sets of patches - both FML and Forge
have their own. While FML patches use SRG names, Forge patches are applied after the source is remapped and therefore
use MCP names instead, as well as include javadocs from the mappings. This introduces a couple of issues:

1. It is technically impossible to remap MCP patch files to SRG
2. Javadocs are not added to code by FG anymore
3. Minor hunk line differences caused by code formatting

These issue invalidate the patch hunks' location and context, making porting the patches very hard. I decided to skip
automating this process, and instead deobfuscated the FML patches using
a [python script](https://github.com/Nearata/minecraft-remapper), then manually applied both sets manually. This process
is far from perfect, and there were a few cases where I missed a hunk or applied it in the wrong place.

### Buildscript

The project's buildscript was initially based on Forge 1.12, which is the oldest available version that uses FG3. Some
parts were later ported from newer versions, namely install-time patching and stable zip times. It also underwent a
large overhaul when I converted it to the Kotlin DSL and later split it into separate buildscripts for each subproject
to enable [Type-safe model accessors](https://docs.gradle.org/current/userguide/kotlin_dsl.html#type-safe-accessors).

## Libraries

### ForgeFlower

The originally used MCP version `7.26a` used an old, obfuscated build of FernFlower from around 2012. Thankfully, the
decompiler was later open-sourced 2 years later. It is unclear how different the code was from the older build, but the
two's outputs were similar in comparison.

MinecraftForge already maintains a patched version of FernFlower called ForgeFlower, which is built by applying a set of
patches to a specific upstream commit. By rebasing it onto the first working commit I could find in the history of the
repo ([ddffcf6](https://github.com/MinecraftForge/FernFlower/tree/ddffcf6f9471b52977fbe61b3cf88c1a26a06753)) and
backporting a few future commits as [patches](https://github.com/Su5eD/ForgeFlower/tree/Legacy-0.8.4/FernFlower-Patches),
I was able to put together a version whose output closely matched that of the original. However, a few disprenancies
still remain - mainly in code style, enums and parameter names.

Maven coordinates: `net.minecraftforge:forgeflower-legacy`
Repo link: [Su5eD/ForgeFlower](https://github.com/Su5eD/ForgeFlower/tree/Legacy-0.8.4)

### ForgeGradle

Some parts of ForgeGradle are still hardcoded and cannot be changed without modifying FG itself, plus we also need to
handle a few special cases. I have only made the minimal changes necessary to the fork, while still keeping it
compatible with modern versions.

#### Excluding packages from reobfuscation

Old minecraft versions come with embedded libraries which are also obfuscated. In our case, these
are [Argo](http://argo.sourceforge.net/) - the JSON parser, and [BouncyCastle](https://www.bouncycastle.org/) provider -
an encryption library. However, MCP never provided patches for their decompiled output. Instead, they were stripped and
replaced with their original, non-obfuscated versions from maven.

The SRG mappings contain entries for these libraries so that they can be deobfuscated during setup, but since we're
replacing them later on, we need a to exclude them from being reobfuscated along with minecraft. Fortunately,
SpecialSource accepts an `excluded-packages` argument for this purpose. I've implemented a frontend for it
in [`PatcherExtension`](https://github.com/Su5eD/ForgeGradle/blob/d1ab8903224036c748a84405c0317f9b4c68fca0/src/patcher/java/net/minecraftforge/gradle/patcher/PatcherExtension.java#L148),
from which the values are passed to respective reobf tasks in both forgedev and userdev.

#### Notch deobfuscation

Before around 1.6(?), mods were obfuscated to notch names rather than SRG. While ForgeGradle already has a config option to
switch the reobfuscation target to notch, it doesn't apply to userdev deobf dependencies. Those are still hardcoded to
map SRG->MCP.

As a simple yet a bit hacky solution we check for the `notch` userdev config flag in the dependency deobfuscator and
switch to using MCP->OBF mappings if it's set to `true`. We also generate the mappings if they don't exist yet.

#### Custom recompilation target

Minecraft 1.4.7 uses ASM version 4.0, which supports reading class files up to Java 7. MCPConfig allows us to configure
the java version that will be used to run setup functions in ForgeGradle. This needs to be set to at least Java 8 which
is required by the functions' tool libraries. However, FG also uses this variable for setting
the [minecraft recompilation target](https://github.com/MinecraftForge/ForgeGradle/blob/1fce46ef54e42adb69176edb0e8c3401a707b39c/src/userdev/java/net/minecraftforge/gradle/userdev/MinecraftUserRepo.java#L1360)
during userdev setup. That creates a **conflict** - we need **java 8** to run MCP functions, but we also have to
target **java 7** during recompilation. Recompiling minecraft with java 8 would result in lots of errors from the ASM
mod scanner at runtime.

To solve this, we add a new `javaRecompileTarget` option to the userdev config that will be prioritized over the
MCPConfig variable for recompilation.

#### Access Transformer obfuscation

The old MCP toolchain used a different, obfuscated AccessTransformer format that is not suitable for use with
ForgeGradle. The difference between the
new [specification](https://github.com/MinecraftForge/AccessTransformers/blob/master/FMLAT.md) and the old format is
mainly in class names and member name separators: while the new format uses **canonical** names for classes and **
spaces** to separate member descriptors, the old one uses **internal** class names and **dots** instead.

For example, the following line in the new format

```
public net.minecraft.world.World isValid(Lnet/minecraft/util/math/BlockPos;)Z
```

would translate to the old format like this

```
public net/minecraft/world/World.isValid(Lnet/minecraft/util/math/BlockPos;)Z
```

as well as being obfuscated for use in production.

ForgeGradle would require significant modifications to the way it applies dev-time ATs in order to make it compatible
with the old format, which is not ideal. Instead, we can use a gradle task to convert and obfuscate access transformers
when the project is built. Deobfuscated project dependencies will also have their ATs deobfuscated so that they can be
applied at runtime.

Unfortunately, before the introduction of the `FMLAT` manifest attribute, there was no guaranteed way of detecting
access transformers without running the mod. Our best bet is searching for files whose name ends with `_at.cfg`, a
convention that all mods seem to follow.

#### Userdev runtime resources patch

ForgeGradle is primarily built for the new FML, which has a different way of locating mod sources. That's why gradle run
tasks are configured to use SourceSet outputs for their runtime classpath, which have classes and resources **
separated** in **different folders** (`build/classes` and `build/resources`). FML picks up the mod from the first valid
source, in this case that's `build/classes`, resulting in resources being **ignored completely**. This issue affects all
versions **lower than 1.13** which use the old FML.

To replicate FG2 behavior, we replace SourceSet outputs on each run task's classpath with the project jar artifact.

Plugin ID:  `net.minecraftforge.gradle-legacy`
Repo link: [Su5eD/ForgeGradle](https://github.com/Su5eD/ForgeGradle/tree/FG_5.1-legacy)

### JarFilter

JarFilter is a new micro-library used by MCPConfig to strip libraries from minecraft after it's renamed, namely Argo and
BouncyCastle Provider.

Repo link: [Su5eD/JarFilter](https://github.com/Su5eD/JarFilter/tree/master)

### LegacyDev

LegacyDev is a dev-only library that helps fix a few issues when running the game in a dev environment, such as loading
natives or filtering arguments. However, 1.4.7 requires a few additional fixes.

#### Extracting Assets

Normally, minecraft would download additional client assets at runtime. However, their download links don't work
anymore, and repeatedly trying to fetch the resources will result in flooding your console with errors like this:

```
java.io.IOException: Server returned HTTP response code: 403 for URL: http://s3.amazonaws.com/MinecraftResources/sound3/ambient/cave/cave1.ogg
```

ForgeGradle already downloads these assets for us in a hashed format. Their location is accessible at runtime via
the `assetDirectory` environment variable. We only need
to [extract](https://github.com/Su5eD/LegacyDev/blob/e7848fdef130756649ea59f12dc9e4bf9bf94c83/src/main/java/net/minecraftforge/legacydev/MainClient.java#L68)
and copy them to the `resources` folder inside the game directory. With the patch, this is now done automatically when
you launch the game.

#### Mod Dependency Coremods

By default, mods on the classpath will not be searched for FML Plugins. Instead, they need to be supplied via a system
property. In legacydev, we search classpath entries for the `FMLCorePlugin` manifest attribute, and then pass the
results to FML.

Repo link: [Su5eD/LegacyDev](https://github.com/Su5eD/LegacyDev/tree/legacy)

### MCPConfig

MCPConfig is constantly evolving, and as a result its build configuration is not always backwards-compatible with
previous versions. For example, the task for dumping static methods was removed and required exceptor files (exceptions,
constructors, access) are no longer included in releases. However, it was easy to get these features back from previous
commits.

#### Converting MCP Data

The RetroGradle team has created a handy tool for converting the old MCP data formats to MCPConfig-compatible ones
called [MCPConvert](https://github.com/RetroGradle/MCPConvert). Using it, I was able to get all the required MCPConfig
data, except for static methods, which had to be generated using MCPConfig's `dumpStatic` task.

#### Filtering Embedded Libraries

A new step was added that strips libraries shipped with minecraft (Argo and BouncyCastle) using
the [JarFilter](#JarFilter) tool. This is done right after the `rename` step on all sides.

Repo link: [Su5eD/MCPConfig](https://github.com/Su5eD/MCPConfig/tree/1.4.7)

### MCP Mappings

I've run into an issue with ForgeGradle's `createExc` task where the exceptor didn't include parameter names of methods
that had no SRG mapping. This resulted in the parameters not being renamed and creating unnecessary changes in patches.

I've taken a look at 1.12's parameter mappings and found out they no longer include non-srg methods' parameter names. So
as a solution, I've excluded all non-srg methods' parameters from the 1.4.7 MCP mappings `params.csv` as well. Worked
like a charm.

### MergeTool

MergeTool is responsible for merging the client and server jars, as well as adding `@SideOnly` annotations so sided
classes and members. While the marker annotations usually come from forge, they need to be added separately to vanilla
minecraft environments, specifically the `clean` forgedev subproject. For this purpose, a jar with marker-only classes
is published along with mergetool.

To make it compatible with 1.4.7, the jar's compile target
was [lowered](https://github.com/Su5eD/MergeTool/blob/8f688161297cd387c602a5c93f0909e6cd851827/build.gradle#L38) to Java
6, which was the original FML's target java version.

Repo link: [Su5eD/MergeTool](https://github.com/Su5eD/MergeTool/tree/legacy)

### MinecraftForge

Parts of the old source code, mainly the FML relauncher, are not compatible with the way the new toolchain handles
setup. Minor modifications must've been done to make it launch properly.

In the first place, the updated toolchain uses install-time binpatches as opposed to the old way of manually copying
files into the minecraft jar (don't forget to delete META-INF!). Due to this, a few changes need to be made to handling
launch and loading patches at runtime.

#### Launch "Wrapper"

The old FML used Minecraft's own entry points which were already patched in the jar and would immediately redirect to
FML so that it could perform setup before returning to Minecraft.

However, patched classes generated by the installer are packaged as a separate jar that we need to locate at runtime and
inject it onto the classpath. For this, we have to create our
own [entry points](https://github.com/Su5eD/MinecraftForge/tree/1.4.7-FG5/src/main/java/cpw/mods/fml/relauncher/wrapper)
for each side that will run FML setup instead of Minecraft's ones.

#### LibraryFinder

`LibraryFinder` is a new class inspired by the 1.19
Forge [equivalent](https://github.com/MinecraftForge/MinecraftForge/blob/7a6843fa4afb232a7e3c1b0a789985507c3067c1/fmlloader/src/main/java/net/minecraftforge/fml/loading/LibraryFinder.java).
It's responsible for locating installer output libraries and adding them onto the classpath. The `RelaunchClassLoader`
has been modified to include a "child" `URLClassLoader`  that wraps the located libraries and is used for priority
loading of resources.

#### Minor Fixes

##### MC Home Computation

Made the minecraft home computation method use the current directory as a fallback if
the `minecraft.applet.TargetDirectory` system property is missing.

##### ModDiscoverer

`ModContainer` contains an `isMinecraft` field which is used by FML to exclude itself from being warned about classes
in `net.minecraft.src`. `ModDiscoverer` previously relied on the assumption that the jar containing minecraft classes
was always first on the classpath. However, this doesn't apply to IDE runs which put JRE jars first.

As a workaround, I've replaced the logic with a method that looks for the `net/minecraft/server/MinecraftServer.class`
jar entry (or file, if the mod is a directory).

##### Unhardcoded ForgeVersion data

The `ForgeVersion` class has been patched to fetch version data from `forgeversion.properties` instead of being
hardcoded.

### SpecialSource

SpecialSource already supported 2 of the features I needed, though I found out they were broken.

#### Excluding Packages

The `excluded-packages` argument allows excluding certain packages from being reobfuscated, and it's applied in the
mappings parser rather than the processing stage. As I found out, this functionality
was [missing](https://github.com/md-5/SpecialSource/issues/84) from the T/CSRG parser method, although I was able to fix
it by reusing existing code from the SRG parser.

#### Package Renaming

The SRG mappings format allows defining mappings for entire packages as a fallback if no class mapping is present. The
old FML used this feature to remap all unmapped classes in the default package to `net.minecraft.src`, specifically **
ModLoader** classes.

Due to a [bug](https://github.com/md-5/SpecialSource/issues/85), package renaming doesn't currently work in reverse.
However, I was able to easily fix it by reusing existing handler code from the SRG parser again.

#### Rename Target Whitelist

During installation, we need to deobfuscate minecraft's embedded libraries without touching the rest of the classes. To
do that, we need a way of restricting which classes we are mapping **to** rather than from.

I decided to implement this as a new feature to SpecialSource under the `remap-only` argument. It's applied as a filter
in the mappings parser just like `exclude-packages`, except it acts as a whitelist rather than blacklist, filtering
by **mapped** names instead of unmapped ones.

Repo link: [Su5eD/SpecialSource](https://github.com/Su5eD/SpecialSource/tree/fixes)

### Argo

Argo is rehosted on my maven because MavenCentral's version has an
invalid [POM](https://repo1.maven.org/maven2/net/sourceforge/argo/argo/2.25/argo-2.25.pom)  that breaks Gradle.