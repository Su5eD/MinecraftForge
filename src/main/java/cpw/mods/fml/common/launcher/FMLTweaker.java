package cpw.mods.fml.common.launcher;

import cpw.mods.fml.common.patcher.ClassPatchManager;
import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

public class FMLTweaker implements ITweaker {
    private File gameDir;
    protected File assetsDir;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.gameDir = gameDir;
        this.assetsDir = assetsDir;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        classLoader.addClassLoaderExclusion("org.apache.");
        classLoader.addClassLoaderExclusion("com.google.gson.");
        classLoader.addClassLoaderExclusion("LZMA.");
        classLoader.addClassLoaderExclusion("net.minecraftforge.classloading.");
        classLoader.addClassLoaderExclusion("cpw.mods.fml.common.asm.transformers.");
        classLoader.addClassLoaderExclusion("cpw.mods.fml.relauncher.");

        classLoader.addTransformerExclusion("com.google.common.");
        classLoader.addTransformerExclusion("cpw.mods.fml.repackage.");
        classLoader.addTransformerExclusion("cpw.mods.fml.relauncher.");
        classLoader.addTransformerExclusion("cpw.mods.fml.common.asm.transformers.");
        classLoader.addTransformerExclusion("cpw.mods.fml.common.patcher.");
        
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.PatchingTransformer");
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.RemappingTransformer");

        configureTweaker(classLoader);
    }

    protected void configureTweaker(LaunchClassLoader classLoader) {
        FMLRelauncher.configureClient(getGameDir(), assetsDir, classLoader);
        ClassPatchManager.INSTANCE.setup(Side.CLIENT);
    }

    protected File getGameDir() {
        return gameDir == null ? new File(".") : gameDir;
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.Minecraft";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
