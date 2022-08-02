package cpw.mods.fml.relauncher.wrapper;

import java.io.File;

public final class FMLArgs {
    private final String[] args;
    private final File assetsDir;
    private final boolean extractResources;
    
    public FMLArgs(String[] args, File assetsDir, boolean extractResources) {
        this.args = args;
        this.assetsDir = assetsDir;
        this.extractResources = extractResources;
    }

    public String[] getArgs() {
        return args;
    }

    public File getAssetsDir() {
        return assetsDir;
    }

    public boolean shouldExtractResources() {
        return extractResources;
    }
}
