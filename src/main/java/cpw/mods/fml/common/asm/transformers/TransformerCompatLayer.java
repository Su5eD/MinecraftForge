package cpw.mods.fml.common.asm.transformers;

import cpw.mods.fml.relauncher.IClassTransformer;

import java.util.ArrayList;
import java.util.List;

public class TransformerCompatLayer implements net.minecraft.launchwrapper.IClassTransformer {
    private static TransformerCompatLayer INSTANCE;
    
    private final List<IClassTransformer> transformers = new ArrayList<>();
    
    @SuppressWarnings("unused")
    public TransformerCompatLayer() {
        INSTANCE = this;
    }
    
    public static void registerTransformer(IClassTransformer transformer) {
        if (INSTANCE != null) INSTANCE.transformers.add(transformer);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        for (IClassTransformer transformer : transformers) {
            basicClass = transformer.transform(name, basicClass);
        }
        
        return basicClass;
    }
}
