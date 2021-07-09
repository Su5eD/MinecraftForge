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

package cpw.mods.fml.common.patcher;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.nothome.delta.Delta;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class GenDiffSet {

    public static void main(String[] args) throws IOException {
        String vanillaMinecraftJar = args[0];
        String targetJar = args[1];
        String reobfuscationOutputPath = args[2];
        //String deobfFileName = args[3];
        String binPatchOutputDir = args[4];

        Delta delta = new Delta();
        //FMLDeobfuscatingRemapper remapper = FMLDeobfuscatingRemapper.INSTANCE;
        //remapper.setupLoadOnly(deobfFileName, false);
        JarFile originalJarFile = new JarFile(vanillaMinecraftJar);
        JarFile targetJarFile = new JarFile(targetJar);

        File f = new File(binPatchOutputDir);
        f.mkdirs();

        for (JarEntry e : Collections.list(originalJarFile.entries())) {
            String name = e.getName();
//            Logger.getLogger("GENDIFF").info(String.format("Evaluating path for data :%s",name));
            File reobfOutput = new File(reobfuscationOutputPath, name);
            if (reobfOutput.exists()) {
                String sourceClassName = name.substring(0, name.lastIndexOf(".")).replace('/', '.');
                String targetClassName = sourceClassName; //remapper.map(name.substring(0,name.lastIndexOf("."))).replace('/', '.');
                JarEntry entry = targetJarFile.getJarEntry(name);

                byte[] vanillaBytes = entry != null ? ByteStreams.toByteArray(targetJarFile.getInputStream(entry)) : new byte[0];
                byte[] patchedBytes = Files.toByteArray(reobfOutput);

                byte[] diff = delta.compute(vanillaBytes, patchedBytes);

                ByteArrayDataOutput diffOut = ByteStreams.newDataOutput(diff.length + 50);
                // Original name
                diffOut.writeUTF(name);
                // Source name
                diffOut.writeUTF(sourceClassName);
                // Target name
                diffOut.writeUTF(targetClassName);
                // exists at original
                diffOut.writeBoolean(entry != null);
                // length of patch
                diffOut.writeInt(diff.length);
                // patch
                diffOut.write(diff);

                File target = new File(binPatchOutputDir, targetClassName + ".binpatch");
                target.getParentFile().mkdirs();
                Files.write(diffOut.toByteArray(), target);
                Logger.getLogger("GENDIFF").info(String.format("Wrote patch for %s (%s) at %s", name, targetClassName, target.getAbsolutePath()));
            }
        }
    }

}
