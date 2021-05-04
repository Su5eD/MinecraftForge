package cpw.mods.fml.relauncher.tweaker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.tweaker.patcher.ClassPatchManager;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

public class FMLTweaker implements ITweaker {
    public static LaunchClassLoader classLoader;
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {}

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        FMLTweaker.classLoader = classLoader;
        classLoader.addClassLoaderExclusion("org.apache.");
        classLoader.addClassLoaderExclusion("com.google.common.");
        classLoader.addTransformerExclusion("cpw.mods.fml.repackage.");
        classLoader.addTransformerExclusion("cpw.mods.fml.relauncher.");
        classLoader.addTransformerExclusion("cpw.mods.fml.common.asm.transformers.");
        classLoader.addClassLoaderExclusion("LZMA.");
        
        classLoader.registerTransformer("cpw.mods.fml.relauncher.tweaker.PatchingTransformer");

        ClassPatchManager.INSTANCE.setup(Side.CLIENT);
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
