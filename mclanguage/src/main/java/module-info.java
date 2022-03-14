module net.minecraftforge.mclanguage {
    requires net.minecraftforge.fmlcore;
    requires net.minecraftforge.forgespi;
    requires org.apache.logging.log4j;
    
    provides net.minecraftforge.forgespi.language.IModLanguageProvider with net.minecraftforge.fml.mclanguageprovider.MinecraftModLanguageProvider;
}
