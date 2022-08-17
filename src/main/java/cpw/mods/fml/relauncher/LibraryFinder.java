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

package cpw.mods.fml.relauncher;

import net.minecraftforge.common.ForgeVersion;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspired by MinecraftForge's 1.19 <a href="https://github.com/MinecraftForge/MinecraftForge/blob/1.19.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/LibraryFinder.java">LibraryFinder</a>
 */
public class LibraryFinder {
    
    public static List<Path> getMcDeps(File mcHome, String side) {
        Path root = getLibrariesRoot(mcHome);
        Object[] data = FMLInjectionData.data();
        String mcversion = (String) data[4];
        Map<String, String> mcDeps = new HashMap<>();
        mcDeps.put("MC SRG", getJarMavenCoordinates("net.minecraft", side, mcversion + "-" +  FMLInjectionData.mcpversion, "srg"));
        mcDeps.put("MC EXTRA", getJarMavenCoordinates("net.minecraft", side, mcversion + "-" +  FMLInjectionData.mcpversion, "extra-stable"));
        mcDeps.put("Forge Patches", getJarMavenCoordinates("net.minecraftforge", "forge", mcversion + "-" + ForgeVersion.getVersion(), side));

        List<Path> paths = new ArrayList<>();
        for (Map.Entry<String, String> entry : mcDeps.entrySet()) {
            String name = entry.getKey();
            String rawPath = entry.getValue();
            Path path = root.resolve(rawPath);

            if (!Files.exists(path)) throw new RuntimeException("Cannot find MC dependency " + name);

            FMLRelaunchLog.finest("Found MC dep " + name + " at " + path);
            paths.add(path.toAbsolutePath());
        }
        return paths;
    }
    
    private static Path getLibrariesRoot(File mcHome) {
        String libraryDirectory = System.getProperty("libraryDirectory");
        Path libs = libraryDirectory != null ? Paths.get(libraryDirectory) : mcHome.toPath().resolve("libraries");
        if (!Files.isDirectory(libs)) {
            throw new IllegalStateException("Missing libraryDirectory system property, cannot continue");
        }
        FMLRelaunchLog.finest("Found probable library path %s", libs);
        return libs;
    }
    
    private static String getJarMavenCoordinates(String group, String name, String version, String classifier) {
        String cls = !classifier.isEmpty() ? "-" + classifier : "";
        
        return group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + cls + ".jar";
    }
}
