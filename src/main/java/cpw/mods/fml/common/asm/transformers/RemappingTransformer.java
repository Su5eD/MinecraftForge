package cpw.mods.fml.common.asm.transformers;

import com.google.common.collect.HashBiMap;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.util.Arrays;

public class RemappingTransformer implements IClassTransformer, IClassNameTransformer {
    private static final HashBiMap<String, String> mapping;
    private static final Remapper remapper;
    
    static {
        HashBiMap<String, String> map = HashBiMap.create();
        map.put("cpw/mods/fml/relauncher/RelaunchClassLoader", "net/minecraft/launchwrapper/LaunchClassLoader");

        map.put("net/minecraft/src/MLProp", "MLProp");
        map.put("net/minecraft/src/BaseMod", "BaseMod");
        map.put("net/minecraft/src/EntityRendererProxy", "EntityRendererProxy");
        map.put("net/minecraft/src/FMLRenderAccessLibrary", "FMLRenderAccessLibrary");
        map.put("net/minecraft/src/ModLoader", "ModLoader");
        map.put("net/minecraft/src/ModTextureAnimation", "ModTextureAnimation");
        map.put("net/minecraft/src/ModTextureStatic", "ModTextureStatic");
        map.put("net/minecraft/src/TradeEntry", "TradeEntry");

        mapping = map;
        remapper = new SimpleRemapper(map);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        ClassReader classReader = new ClassReader(bytes);

        ClassWriter oldClassWriter = new ClassWriter(0);
        classReader.accept(oldClassWriter, 0);
        byte[] oldBytes = oldClassWriter.toByteArray();

        ClassWriter classWriter = new ClassWriter(0);
        
        ClassRemapper classRemapper = new ClassRemapper(classWriter, remapper);
        classReader.accept(classRemapper, 0);
        byte[] newBytes = classWriter.toByteArray();

        return Arrays.equals(oldBytes, newBytes) ? bytes : newBytes;
    }

    @Override
    public String remapClassName(String name) {
        return mapping.getOrDefault(name.replace('.', '/'), name);
    }
        
    @Override
    public String unmapClassName(String name) {
        return mapping.inverse().getOrDefault(name.replace('.', '/'), name);
    }
}
