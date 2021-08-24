package cpw.mods.fml.common.asm.transformers.deobf;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FMLDeobfuscatingRemapper extends Remapper {
    public static final FMLDeobfuscatingRemapper INSTANCE = new FMLDeobfuscatingRemapper();
    private static final boolean DEBUG_REMAPPING = Boolean.parseBoolean(System.getProperty("fml.remappingDebug", "false"));
    private static final boolean DUMP_FIELD_MAPS = Boolean.parseBoolean(System.getProperty("fml.remappingDebug.dumpFieldMaps", "false")) && DEBUG_REMAPPING;
    private static final boolean DUMP_METHOD_MAPS = Boolean.parseBoolean(System.getProperty("fml.remappingDebug.dumpMethodMaps", "false")) && DEBUG_REMAPPING;
    /*
     * Cache the field descriptions for classes so we don't repeatedly reload the same data again and again
     */
    private final Map<String, Map<String, String>> fieldDescriptions = Maps.newHashMap();
    // Cache null values so we don't waste time trying to recompute classes with no field or method maps
    private final Set<String> negativeCacheMethods = Sets.newHashSet();
    private final Set<String> negativeCacheFields = Sets.newHashSet();
    private BiMap<String, String> classNameBiMap;
    private Map<String, Map<String, String>> rawFieldMaps;
    private Map<String, Map<String, String>> rawMethodMaps;
    private Map<String, Map<String, String>> fieldNameMaps;
    private Map<String, Map<String, String>> methodNameMaps;
    private RelaunchClassLoader classLoader;

    private FMLDeobfuscatingRemapper() {
        classNameBiMap = ImmutableBiMap.of();
    }

    public void setup() {
        String deobfFileName = "/deobfuscation_data-" + FMLInjectionData.getMcversion() + ".lzma";
        this.classLoader = FMLRelauncher.getClassLoader();
        try {
            List<String> srgList;
            final String gradleStartProp = System.getProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp");

            if (Strings.isNullOrEmpty(gradleStartProp)) {
                // get as a resource
                InputStream classData = getClass().getResourceAsStream(deobfFileName);
                LZMAInputSupplier zis = new LZMAInputSupplier(classData);
                CharSource srgSource = zis.asCharSource(StandardCharsets.UTF_8);
                srgList = srgSource.readLines();
                FMLLog.severe("Loading deobfuscation resource {} with {} records", deobfFileName, srgList.size());
            } else {
                srgList = Files.readLines(new File(gradleStartProp), StandardCharsets.UTF_8);
                FMLLog.severe("Loading deobfuscation resource {} with {} records", gradleStartProp, srgList.size());
            }

            rawMethodMaps = Maps.newHashMap();
            rawFieldMaps = Maps.newHashMap();
            Builder<String, String> builder = ImmutableBiMap.builder();
            Splitter splitter = Splitter.on(CharMatcher.anyOf(": ")).omitEmptyStrings().trimResults();
            for (String line : srgList) {
                String[] parts = Iterables.toArray(splitter.split(line), String.class);
                String typ = parts[0];
                if ("CL".equals(typ)) {
                    parseClass(builder, parts);
                } else if ("MD".equals(typ)) {
                    parseMethod(parts);
                } else if ("FD".equals(typ)) {
                    parseField(parts);
                }
            }
            classNameBiMap = builder.build();
        } catch (IOException ioe) {
            FMLLog.severe("An error occurred loading the deobfuscation map data", ioe);
        }
        methodNameMaps = Maps.newHashMapWithExpectedSize(rawMethodMaps.size());
        fieldNameMaps = Maps.newHashMapWithExpectedSize(rawFieldMaps.size());
    }

    private void parseField(String[] parts) {
        String oldSrg = parts[1];
        int lastOld = oldSrg.lastIndexOf('/');
        String cl = oldSrg.substring(0, lastOld);
        String oldName = oldSrg.substring(lastOld + 1);
        String newSrg = parts[2];
        int lastNew = newSrg.lastIndexOf('/');
        String newName = newSrg.substring(lastNew + 1);
        if (!rawFieldMaps.containsKey(cl)) {
            rawFieldMaps.put(cl, new HashMap<>());
        }
        String fieldType = getFieldType(cl, oldName);
        // We might be in mcp named land, where in fact the name is "new"
        if (fieldType == null) fieldType = getFieldType(cl, newName);
        rawFieldMaps.get(cl).put(oldName + ":" + fieldType, newName);
        rawFieldMaps.get(cl).put(oldName + ":null", newName);
    }

    private String getFieldType(String owner, String name) {
        if (fieldDescriptions.containsKey(owner)) {
            return fieldDescriptions.get(owner).get(name);
        }
        synchronized (fieldDescriptions) {
            try {
                byte[] classBytes = classLoader.getClassBytes(owner);
                if (classBytes == null) {
                    return null;
                }
                ClassReader cr = new ClassReader(classBytes);
                ClassNode classNode = new ClassNode();
                cr.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                Map<String, String> resMap = Maps.newHashMap();
                for (FieldNode fieldNode : classNode.fields) {
                    resMap.put(fieldNode.name, fieldNode.desc);
                }
                fieldDescriptions.put(owner, resMap);
                return resMap.get(name);
            } catch (IOException e) {
                FMLLog.severe("A critical exception occurred reading a class file {}", owner, e);
            }
            return null;
        }
    }

    private void parseClass(Builder<String, String> builder, String[] parts) {
        builder.put(parts[1], parts[2]);
    }

    private void parseMethod(String[] parts) {
        String oldSrg = parts[1];
        int lastOld = oldSrg.lastIndexOf('/');
        String cl = oldSrg.substring(0, lastOld);
        String oldName = oldSrg.substring(lastOld + 1);
        String sig = parts[2];
        String newSrg = parts[3];
        int lastNew = newSrg.lastIndexOf('/');
        String newName = newSrg.substring(lastNew + 1);
        if (!rawMethodMaps.containsKey(cl)) {
            rawMethodMaps.put(cl, new HashMap<>());
        }
        rawMethodMaps.get(cl).put(oldName + sig, newName);
    }

    String mapMemberFieldName(String owner, String name, String desc) {
        String remappedName = mapFieldName(owner, name, desc, true);
        storeMemberFieldMapping(owner, name, desc, remappedName);
        return remappedName;
    }

    private void storeMemberFieldMapping(String owner, String name, String desc, String remappedName) {
        Map<String, String> fieldMap = getRawFieldMap(owner);

        String key = name + ":" + desc;
        String altKey = name + ":null";

        if (!fieldMap.containsKey(key)) {
            fieldMap.put(key, remappedName);
            fieldMap.put(altKey, remappedName);

            // Alternatively, maps could be made mutable and we could just set the relevant entry, saving
            // the need to regenerate the super map each time
            fieldNameMaps.remove(owner);
        }
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        return mapFieldName(owner, name, desc, false);
    }

    String mapFieldName(String owner, String name, String desc, boolean raw) {
        if (classNameBiMap == null || classNameBiMap.isEmpty()) {
            return name;
        }
        Map<String, String> fieldMap = getFieldMap(owner, raw);
        return fieldMap != null && fieldMap.containsKey(name + ":" + desc) ? fieldMap.get(name + ":" + desc) : fieldMap != null && fieldMap.containsKey(name + ":null") ? fieldMap.get(name + ":null") : name;
    }

    @Override
    public String map(String typeName) {
        if (classNameBiMap == null || classNameBiMap.isEmpty()) {
            return typeName;
        }
        if (classNameBiMap.containsKey(typeName)) {
            return classNameBiMap.get(typeName);
        }
        int dollarIdx = typeName.lastIndexOf('$');
        if (dollarIdx > -1) {
            return map(typeName.substring(0, dollarIdx)) + "$" + typeName.substring(dollarIdx + 1);
        }
        return typeName;
    }

    public String unmap(String typeName) {
        if (classNameBiMap == null || classNameBiMap.isEmpty()) {
            return typeName;
        }

        if (classNameBiMap.containsValue(typeName)) {
            return classNameBiMap.inverse().get(typeName);
        }
        int dollarIdx = typeName.lastIndexOf('$');
        if (dollarIdx > -1) {
            return unmap(typeName.substring(0, dollarIdx)) + "$" + typeName.substring(dollarIdx + 1);
        }
        return typeName;
    }


    @Override
    public String mapMethodName(String owner, String name, String desc) {
        if (classNameBiMap == null || classNameBiMap.isEmpty()) {
            return name;
        }
        Map<String, String> methodMap = getMethodMap(owner);
        String methodDescriptor = name + desc;
        return methodMap != null && methodMap.containsKey(methodDescriptor) ? methodMap.get(methodDescriptor) : name;
    }

    @Override
    public String mapSignature(String signature, boolean typeSignature) {
        // JDT decorates some lambdas with this and SignatureReader chokes on it
        if (signature != null && signature.contains("!*")) {
            return null;
        }
        return super.mapSignature(signature, typeSignature);
    }

    private Map<String, String> getRawFieldMap(String className) {
        if (!rawFieldMaps.containsKey(className)) {
            rawFieldMaps.put(className, new HashMap<>());
        }
        return rawFieldMaps.get(className);
    }

    private Map<String, String> getFieldMap(String className, boolean raw) {
        if (raw) {
            return getRawFieldMap(className);
        }

        if (!fieldNameMaps.containsKey(className) && !negativeCacheFields.contains(className)) {
            findAndMergeSuperMaps(className);
            if (!fieldNameMaps.containsKey(className)) {
                negativeCacheFields.add(className);
            }

            if (DUMP_FIELD_MAPS) {
                FMLLog.finest("Field map for {} : {}", className, fieldNameMaps.get(className));
            }
        }
        return fieldNameMaps.get(className);
    }

    private Map<String, String> getMethodMap(String className) {
        if (!methodNameMaps.containsKey(className) && !negativeCacheMethods.contains(className)) {
            findAndMergeSuperMaps(className);
            if (!methodNameMaps.containsKey(className)) {
                negativeCacheMethods.add(className);
            }
            if (DUMP_METHOD_MAPS) {
                FMLLog.finest("Method map for {} : {}", className, methodNameMaps.get(className));
            }

        }
        return methodNameMaps.get(className);
    }

    private void findAndMergeSuperMaps(String name) {
        try {
            String superName = null;
            String[] interfaces = new String[0];
            byte[] classBytes = classLoader.getClassBytes(name);
            if (classBytes != null) {
                ClassReader cr = new ClassReader(classBytes);
                superName = cr.getSuperName();
                interfaces = cr.getInterfaces();
            }
            mergeSuperMaps(name, superName, interfaces);
        } catch (IOException e) {
            FMLLog.severe("Error getting patched resource:", e);
        }
    }

    public void mergeSuperMaps(String name, String superName, String[] interfaces) {
        if (classNameBiMap == null || classNameBiMap.isEmpty()) {
            return;
        }
        // Skip Object
        if (Strings.isNullOrEmpty(superName)) {
            return;
        }

        List<String> allParents = ImmutableList.<String>builder().add(superName).addAll(Arrays.asList(interfaces)).build();
        // generate maps for all parent objects
        for (String parentThing : allParents) {
            if (!fieldNameMaps.containsKey(parentThing)) {
                findAndMergeSuperMaps(parentThing);
            }
        }
        Map<String, String> methodMap = Maps.newHashMap();
        Map<String, String> fieldMap = Maps.newHashMap();
        for (String parentThing : allParents) {
            if (methodNameMaps.containsKey(parentThing)) {
                methodMap.putAll(methodNameMaps.get(parentThing));
            }
            if (fieldNameMaps.containsKey(parentThing)) {
                fieldMap.putAll(fieldNameMaps.get(parentThing));
            }
        }
        if (rawMethodMaps.containsKey(name)) {
            methodMap.putAll(rawMethodMaps.get(name));
        }
        if (rawFieldMaps.containsKey(name)) {
            fieldMap.putAll(rawFieldMaps.get(name));
        }
        methodNameMaps.put(name, ImmutableMap.copyOf(methodMap));
        fieldNameMaps.put(name, ImmutableMap.copyOf(fieldMap));
    }

    public String getStaticFieldType(String oldType, String newType, String newName) {
        String fType = getFieldType(newType, newName);
        if (oldType.equals(newType)) {
            return fType;
        }
        Map<String, String> newClassMap = fieldDescriptions.computeIfAbsent(newType, k -> Maps.newHashMap());
        newClassMap.put(newName, fType);
        return fType;
    }
}
