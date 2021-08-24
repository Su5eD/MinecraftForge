package cpw.mods.fml.relauncher;

public interface IExtendedClassTransformer extends IClassTransformer {
    byte[] transform(String name, String transformedName, byte[] bytes);
    
    default byte[] transform(String name, byte[] bytes) {
        return transform(name, name, bytes);
    }
}
