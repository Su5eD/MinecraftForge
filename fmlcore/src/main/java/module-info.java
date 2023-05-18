open module net.minecraftforge.fmlcore {
    requires org.jetbrains.annotations;
    requires net.minecraftforge.eventbus;
    requires com.electronwill.nightconfig.core;
    requires org.slf4j;
    requires logging;
    requires net.minecraftforge.fmlloader;
    requires org.apache.commons.io;
    requires com.electronwill.nightconfig.toml;
    requires com.google.common;
    requires net.minecraftforge.forgespi;
    requires cpw.mods.modlauncher;
    requires org.apache.logging.log4j;
    requires maven.artifact;
    requires java.net.http;
    requires com.google.gson;

    exports net.minecraftforge.fml;
    exports net.minecraftforge.fml.config;
    exports net.minecraftforge.fml.event;
    exports net.minecraftforge.fml.util;
    exports net.minecraftforge.fml.util.thread;

    uses net.minecraftforge.fml.IBindingsProvider;
    uses net.minecraftforge.fml.IModStateProvider;
}