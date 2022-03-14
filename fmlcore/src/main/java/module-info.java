module net.minecraftforge.fmlcore {
    requires com.electronwill.nightconfig.core;
    requires com.electronwill.nightconfig.toml;
    requires com.google.common;
    requires com.google.gson;
    requires cpw.mods.modlauncher;
    requires eventbus;
    requires java.net.http;
    requires maven.artifact;
    requires net.minecraftforge.fmlloader;
    requires net.minecraftforge.forgespi;
    requires org.apache.commons.io;
    requires org.apache.logging.log4j;
    requires static org.jetbrains.annotations;
    
    exports net.minecraftforge.fml;
    exports net.minecraftforge.fml.config;
    exports net.minecraftforge.fml.event;
    exports net.minecraftforge.fml.util;
    exports net.minecraftforge.fml.util.thread;
    
    uses net.minecraftforge.fml.IModStateProvider;
    uses net.minecraftforge.fml.IBindingsProvider;
}
