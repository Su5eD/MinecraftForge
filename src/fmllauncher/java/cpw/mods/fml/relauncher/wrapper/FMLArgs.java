package cpw.mods.fml.relauncher.wrapper;

import java.io.File;

public final class FMLArgs {
    private final String[] args;
    private final File assetsDir;
    
    public FMLArgs(String[] args, File assetsDir) {
        this.args = args;
        this.assetsDir = assetsDir;
    }

    public String[] getArgs() {
        return args;
    }

    public File getAssetsDir() {
        return assetsDir;
    }
}
