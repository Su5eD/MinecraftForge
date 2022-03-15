module net.minecraftforge.javafmllanguage {
    requires cpw.mods.modlauncher;
    requires cpw.mods.securejarhandler;
    requires eventbus;
    requires net.minecraftforge.fmlcore;
    requires net.minecraftforge.fmlloader;
    requires net.minecraftforge.forgespi;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm;
    
    exports net.minecraftforge.fml.common;
    exports net.minecraftforge.fml.javafmlmod;
    
    provides net.minecraftforge.forgespi.language.IModLanguageProvider with net.minecraftforge.fml.javafmlmod.FMLJavaModLanguageProvider;
}
