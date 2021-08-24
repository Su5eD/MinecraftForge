package cpw.mods.fml.common.asm.transformers;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.common.asm.transformers.deobf.FMLRemappingAdapter;
import cpw.mods.fml.relauncher.IClassNameMapper;
import cpw.mods.fml.relauncher.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

public class DeobfuscationTransformer implements IClassTransformer, IClassNameMapper {
    
    public DeobfuscationTransformer() {
        FMLDeobfuscatingRemapper.INSTANCE.setup();
    }
        
    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassRemapper classRemapper = new FMLRemappingAdapter(classWriter);
        classReader.accept(classRemapper, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }

    @Override
    public String mapClassName(String name) {
        return FMLDeobfuscatingRemapper.INSTANCE.map(name.replace('.', '/')).replace('/', '.');
    }

    @Override
    public String unmapClassName(String name) {
        return FMLDeobfuscatingRemapper.INSTANCE.unmap(name.replace('.', '/')).replace('/', '.');
    }
}
