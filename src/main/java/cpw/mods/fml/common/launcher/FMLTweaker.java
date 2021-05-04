package cpw.mods.fml.common.launcher;

import cpw.mods.fml.common.patcher.ClassPatchManager;
import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

public class FMLTweaker implements ITweaker {
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {}

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        classLoader.addClassLoaderExclusion("org.apache.");
        classLoader.addClassLoaderExclusion("com.google.common.");
        classLoader.addClassLoaderExclusion("com.google.gson.");
        classLoader.addClassLoaderExclusion("LZMA.");
        classLoader.addClassLoaderExclusion("net.minecraftforge.classloading.");
        classLoader.addClassLoaderExclusion("cpw.mods.fml.common.asm.transformers");
        
        classLoader.addTransformerExclusion("cpw.mods.fml.repackage.");
        classLoader.addTransformerExclusion("cpw.mods.fml.relauncher.");
        classLoader.addTransformerExclusion("cpw.mods.fml.common.asm.transformers.");
        classLoader.addTransformerExclusion("cpw.mods.fml.common.patcher.");
        
        FMLRelauncher.configure("CLIENT");
        ClassPatchManager.INSTANCE.setup(Side.CLIENT);
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.PatchingTransformer");
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.AccessTransformer");
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.MarkerTransformer");
        classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.SideTransformer");
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
