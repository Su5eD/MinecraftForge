package cpw.mods.fml.common.asm.transformers;

import cpw.mods.fml.common.patcher.ClassPatchManager;
import net.minecraft.launchwrapper.IClassTransformer;

public class PatchingTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        return ClassPatchManager.INSTANCE.applyPatch(name, transformedName, bytes);
    }

}
