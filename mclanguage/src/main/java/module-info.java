open module net.minecraftforge.mclanguage {
    requires net.minecraftforge.fmlcore;
    requires logging;
    requires org.slf4j;
    requires net.minecraftforge.forgespi;
    requires net.minecraftforge.eventbus;
    requires net.minecraftforge.fmlloader;
    requires org.apache.logging.log4j;
    requires cpw.mods.modlauncher;

    exports net.minecraftforge.fml.mclanguageprovider;
    
    provides net.minecraftforge.forgespi.language.IModLanguageProvider
        with net.minecraftforge.fml.mclanguageprovider.MinecraftModLanguageProvider;
}