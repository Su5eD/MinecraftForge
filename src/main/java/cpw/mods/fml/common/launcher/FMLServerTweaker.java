package cpw.mods.fml.common.launcher;

import cpw.mods.fml.common.patcher.ClassPatchManager;
import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class FMLServerTweaker extends FMLTweaker {
    @Override
    public String getLaunchTarget() {
        return "net.minecraft.server.MinecraftServer";
    }

    @Override
    protected void configureTweaker(LaunchClassLoader classLoader) {
        FMLRelauncher.configureServer(getGameDir(), assetsDir, classLoader);
        ClassPatchManager.INSTANCE.setup(Side.SERVER);
    }
}
