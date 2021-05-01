package cpw.mods.fml.relauncher;

public class CoreFMLLibraries implements ILibrarySet
{
    private static String[] libraries = { "net.sourceforge.argo:argo:2.25","com.google.guava:guava:12.0.1","org.ow2.asm:asm-all:4.0", "org.bouncycastle:bcprov-jdk15on:1.47" };
    private static String[] checksums = { "bb672829fde76cb163004752b86b0484bd0a7f4b", "b8e78b9af7bf45900e14c6f958486b6ca682195f", "2518725354c7a6a491a323249b9e86846b00df09", "b6f5d9926b0afbde9f4dbe3db88c5247be7794bb" };

    @Override
    public String[] getLibraries()
    {
        return libraries;
    }

    @Override
    public String[] getHashes()
    {
        return checksums;
    }

    @Override
    public String getRootURL()
    {
        return "https://repo1.maven.org/maven2/%s";
    }
}
