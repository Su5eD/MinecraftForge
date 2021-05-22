package cpw.mods.fml.common.asm.transformers;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.util.Arrays;

public class ClassLoaderTransformer implements IClassTransformer {

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
        Remapper remapper = new SimpleRemapper("cpw/mods/fml/relauncher/RelaunchClassLoader", "net/minecraft/launchwrapper/LaunchClassLoader");
        ClassRemapper classRemapper = new ClassRemapper(classWriter, remapper);
        classReader.accept(classRemapper, 0);
        byte[] newBytes = classWriter.toByteArray();

        return Arrays.equals(oldBytes, newBytes) ? bytes : newBytes;
    }
}
