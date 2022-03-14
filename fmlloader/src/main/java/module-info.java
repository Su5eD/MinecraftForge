module net.minecraftforge.fmlloader {
    requires accesstransformers;
    requires com.electronwill.nightconfig.core;
    requires com.electronwill.nightconfig.toml;
    requires com.google.common;
    requires com.google.gson;
    requires cpw.mods.modlauncher;
    requires cpw.mods.securejarhandler;
    requires jopt.simple;
    requires maven.artifact;
    requires net.minecraftforge.forgespi;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.tree.analysis;
    requires org.objectweb.asm.tree;
    requires org.objectweb.asm;
    requires org.slf4j;
    requires terminalconsoleappender;
    requires static org.jetbrains.annotations;

    exports net.minecraftforge.fml.common.asm;
    exports net.minecraftforge.fml.loading;
    exports net.minecraftforge.fml.loading.log4j;
    exports net.minecraftforge.fml.loading.moddiscovery;
    exports net.minecraftforge.fml.loading.progress;
    exports net.minecraftforge.fml.loading.targets;
    exports net.minecraftforge.fml.loading.toposort;
    exports net.minecraftforge.fml.server;
    
    uses net.minecraftforge.forgespi.coremod.ICoreModProvider;
    uses net.minecraftforge.forgespi.locating.IModLocator;
    uses net.minecraftforge.forgespi.language.IModLanguageProvider;

    provides cpw.mods.modlauncher.api.ILaunchHandlerService with net.minecraftforge.fml.loading.targets.FMLClientLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLClientDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLClientUserdevLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLServerLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLServerDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLServerUserdevLaunchHandler,
        net.minecraftforge.fml.loading.targets.FMLDataUserdevLaunchHandler,

        net.minecraftforge.fml.loading.targets.ForgeClientLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeClientDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeClientUserdevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeServerLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeServerDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeServerUserdevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeDataDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeDataUserdevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeGametestDevLaunchHandler,
        net.minecraftforge.fml.loading.targets.ForgeGametestUserdevLaunchHandler;

    provides cpw.mods.modlauncher.api.INameMappingService with net.minecraftforge.fml.loading.MCPNamingService;
    provides cpw.mods.modlauncher.api.ITransformationService with net.minecraftforge.fml.loading.FMLServiceProvider;
    provides cpw.mods.modlauncher.serviceapi.ILaunchPluginService with net.minecraftforge.fml.loading.log4j.SLF4JFixerLaunchPluginService,
        net.minecraftforge.fml.loading.RuntimeDistCleaner,
        net.minecraftforge.fml.common.asm.RuntimeEnumExtender,
        net.minecraftforge.fml.common.asm.ObjectHolderDefinalize,
        net.minecraftforge.fml.common.asm.CapabilityTokenSubclass;

    provides cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService with net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;

    provides net.minecraftforge.forgespi.locating.IModLocator with net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator,
        net.minecraftforge.fml.loading.moddiscovery.MavenDirectoryLocator,
        net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator,
        net.minecraftforge.fml.loading.moddiscovery.MinecraftLocator,
        net.minecraftforge.fml.loading.moddiscovery.ClasspathLocator;
}
