/*
 * The FML Forge Mod Loader suite.
 * Copyright (C) 2012 cpw
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cpw.mods.fml.common.asm.transformers;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import cpw.mods.fml.relauncher.IClassNameMapper;
import cpw.mods.fml.relauncher.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.util.Arrays;
import java.util.stream.Stream;

public class MLClassNameMapper implements IClassTransformer, IClassNameMapper {
    private static final BiMap<String, String> MAPPING;
    private static final Remapper REMAPPER;

    static {
        BiMap<String, String> map = HashBiMap.create();
        Stream.of("MLProp", "BaseMod", "EntityRendererProxy", "FMLRenderAccessLibrary", "ModLoader", "ModTextureAnimation", "ModTextureStatic", "TradeEntry")
                .forEach(str -> map.put("net/minecraft/src/" + str, str));

        MAPPING = map;
        REMAPPER = new SimpleRemapper(MAPPING);
    }

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;

        ClassReader classReader = new ClassReader(bytes);

        ClassWriter oldClassWriter = new ClassWriter(0);
        classReader.accept(oldClassWriter, 0);
        byte[] oldBytes = oldClassWriter.toByteArray();

        ClassWriter classWriter = new ClassWriter(0);

        ClassRemapper classRemapper = new ClassRemapper(classWriter, REMAPPER);
        classReader.accept(classRemapper, 0);
        byte[] newBytes = classWriter.toByteArray();

        return Arrays.equals(oldBytes, newBytes) ? bytes : newBytes;
    }

    @Override
    public String mapClassName(String name) {
        return MAPPING.getOrDefault(name.replace('.', '/'), name);
    }

    @Override
    public String unmapClassName(String name) {
        return MAPPING.inverse().getOrDefault(name.replace('.', '/'), name);
    }
}
