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

import LZMA.LzmaInputStream;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.nothome.delta.GDiffPatcher;
import cpw.mods.fml.relauncher.Side;

import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ClassPatchManager {
    public static final ClassPatchManager INSTANCE = new ClassPatchManager();
    private static final Logger LOGGER = Logger.getLogger("ClassPatchManager");

    public static final boolean dumpPatched = Boolean.parseBoolean(System.getProperty("fml.dumpPatchedClasses", "false"));

    private final GDiffPatcher patcher = new GDiffPatcher();
    private ListMultimap<String, ClassPatch> patches;

    private final Map<String, byte[]> patchedClasses = Maps.newHashMap();
    private File tempDir;

    private ClassPatchManager() {
        if (dumpPatched) {
            tempDir = Files.createTempDir();
            LOGGER.info("Dumping patched classes to " + tempDir.getAbsolutePath());
        }
    }

    public byte[] applyPatch(String name, String mappedName, byte[] inputData) {
        if (patches == null) {
            return inputData;
        }
        if (patchedClasses.containsKey(name)) {
            return patchedClasses.get(name);
        }
        List<ClassPatch> list = patches.get(name);
        if (list.isEmpty()) {
            return inputData;
        }
        boolean ignoredError = false;
        int inputLength = inputData == null ? 0 : inputData.length;
        LOGGER.finest(String.format("Runtime patching class %s (input size %d), found %d patch%s", mappedName, inputLength, list.size(), list.size() != 1 ? "es" : ""));
        for (ClassPatch patch : list) {
            if (!patch.targetClassName.equals(mappedName) && !patch.sourceClassName.equals(name)) {
                LOGGER.warning(String.format("Binary patch found %s for wrong class %s", patch.targetClassName, mappedName));
            }
            if (!patch.existsAtTarget && inputLength == 0) {
                inputData = new byte[0];
            } else if (!patch.existsAtTarget) {
                LOGGER.warning(String.format("Patcher expecting empty class data file for %s, but received non-empty", patch.targetClassName));
            } else {
                int inputChecksum = Hashing.adler32().hashBytes(inputData).asInt();
                if (patch.inputChecksum != inputChecksum) {
                    LOGGER.severe(String.format("There is a binary discrepency between the expected input class %s (%s) and the actual class. Checksum on disk is %x, in patch %x. Things are probably about to go very wrong. Did you put something into the jar file?", mappedName, name, inputChecksum, patch.inputChecksum));
                    if (!Boolean.parseBoolean(System.getProperty("fml.ignorePatchDiscrepancies", "false"))) {
                        LOGGER.severe("The game is going to exit, because this is a critical error, and it is very improbable that the modded game will work, please obtain clean jar files.");
                        System.exit(1);
                    } else {
                        LOGGER.severe("FML is going to ignore this error, note that the patch will not be applied, and there is likely to be a malfunctioning behaviour, including not running at all");
                        ignoredError = true;
                        continue;
                    }
                }
            }
            synchronized (patcher) {
                try {
                    inputData = patcher.patch(inputData, patch.patch);
                } catch (IOException e) {
                    LOGGER.log(Level.FINEST, String.format("Encountered problem runtime patching class %s", name), e);
                }
            }
        }
        if (!ignoredError) {
            LOGGER.finest(String.format("Successfully applied runtime patches for %s (new size %d)", mappedName, inputLength));
        }
        if (dumpPatched) {
            try {
                Files.write(inputData, new File(tempDir, mappedName));
            } catch (IOException e) {
                LOGGER.log(Level.FINEST, String.format("Failed to write %s to %s", mappedName, tempDir.getAbsolutePath()), e);
            }
        }
        patchedClasses.put(name, inputData);
        return inputData;
    }

    public void setup(Side side) {
        Pattern binpatchMatcher = Pattern.compile(String.format("binpatch/%s/.*.binpatch", side.toString().toLowerCase(Locale.ROOT)));
        JarInputStream jis;
        try {
            InputStream binpatchesCompressed = getClass().getResourceAsStream("/binpatches.pack.lzma");
            if (binpatchesCompressed == null) {
                LOGGER.finest("The binary patch set is missing. Either you are in a development environment, or things are not going to work!");
                return;
            }
            LzmaInputStream binpatchesDecompressed = new LzmaInputStream(binpatchesCompressed);
            ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
            JarOutputStream jos = new JarOutputStream(jarBytes);
            Pack200.newUnpacker().unpack(binpatchesDecompressed, jos);
            jis = new JarInputStream(new ByteArrayInputStream(jarBytes.toByteArray()));
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Error occurred reading binary patches. Expect severe problems!", e);
            throw Throwables.propagate(e);
        }

        patches = ArrayListMultimap.create();

        while (true) {
            try {
                JarEntry entry = jis.getNextJarEntry();
                if (entry == null) break;
                
                if (binpatchMatcher.matcher(entry.getName()).matches()) {
                    ClassPatch cp = readPatch(entry, jis);
                    if (cp != null) {
                        patches.put(cp.sourceClassName, cp);
                    }
                } else {
                    jis.closeEntry();
                }
            } catch (IOException ignored) {
            }
        }
        
        LOGGER.finest(String.format("Read %d binary patches", patches.size()));
        LOGGER.finest(String.format("Patch list :\n\t%s", Joiner.on("\t\n").join(patches.asMap().entrySet())));
        patchedClasses.clear();
    }

    private ClassPatch readPatch(JarEntry patchEntry, JarInputStream jis) {
        LOGGER.finest(String.format("Reading patch data from %s", patchEntry.getName()));
        ByteArrayDataInput input;
        try {
            input = ByteStreams.newDataInput(ByteStreams.toByteArray(jis));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Unable to read binpatch file %s - ignoring", patchEntry.getName()), e);
            return null;
        }
        String name = input.readUTF();
        String sourceClassName = input.readUTF();
        String targetClassName = input.readUTF();
        boolean exists = input.readBoolean();
        int inputChecksum = 0;
        if (exists) {
            inputChecksum = input.readInt();
        }
        int patchLength = input.readInt();
        byte[] patchBytes = new byte[patchLength];
        input.readFully(patchBytes);

        return new ClassPatch(name, sourceClassName, targetClassName, exists, inputChecksum, patchBytes);
    }
}
