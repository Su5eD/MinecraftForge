open module net.minecraftforge.javafmllanguage {
    requires net.minecraftforge.forgespi;
    requires net.minecraftforge.eventbus;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm;
    requires cpw.mods.modlauncher;
    requires cpw.mods.securejarhandler;
    requires net.minecraftforge.fmlcore;
    requires net.minecraftforge.fmlloader;

    exports net.minecraftforge.fml.common;
    exports net.minecraftforge.fml.javafmlmod;
    
    provides net.minecraftforge.forgespi.language.IModLanguageProvider
        with net.minecraftforge.fml.javafmlmod.FMLJavaModLanguageProvider;
}