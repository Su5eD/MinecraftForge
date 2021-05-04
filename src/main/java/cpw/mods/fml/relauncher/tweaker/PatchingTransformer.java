package cpw.mods.fml.relauncher.tweaker;

import cpw.mods.fml.relauncher.tweaker.patcher.ClassPatchManager;
import net.minecraft.launchwrapper.IClassTransformer;

public class PatchingTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        return ClassPatchManager.INSTANCE.applyPatch(name, transformedName, bytes);
    }

}
