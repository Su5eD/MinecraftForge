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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LibraryFinder {
    
    public static Path getLibrariesRoot() {
        if (FMLInjectionData.minecraftHome == null) {
            throw new IllegalStateException("minecraftHome hasn't been set up yet, can't resolve libraries root");
        }
        
        return FMLInjectionData.minecraftHome.toPath().resolve("libraries");
    }
    
    public static Path getForgePath(Path root) {
        String rawPath = getJarMavenCoordinates("net.minecraftforge", "forge", FMLInjectionData.mcversion + "-" + ForgeVersion.getVersion(), "universal");
        Path path = root.resolve(rawPath).toAbsolutePath();
        
        if (!Files.exists(path)) throw new RuntimeException("Cannot find Forge dependency");
        else FMLRelaunchLog.finest("Found Forge dependency at " + path);
        
        return path;
    }
    
    public static Path[] getMcDeps(Path root, String side) {
        Map<String, String> mcDeps = new HashMap<>();
        mcDeps.put("MC SLIM", getJarMavenCoordinates("net.minecraft", side, FMLInjectionData.mcversion + "-" +  FMLInjectionData.mcpversion, "slim"));
        mcDeps.put("MC EXTRA", getJarMavenCoordinates("net.minecraft", side, FMLInjectionData.mcversion + "-" +  FMLInjectionData.mcpversion, "extra"));
        mcDeps.put("Forge Patches", getJarMavenCoordinates("net.minecraftforge", "forge", FMLInjectionData.mcversion + "-" + ForgeVersion.getVersion(), side));
        
        return mcDeps.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    String rawPath = entry.getValue();
                    Path path = root.resolve(rawPath);
                    
                    if (!Files.exists(path)) throw new RuntimeException("Cannot find MC dependency " + name);
                    
                    FMLRelaunchLog.finest("Found MC dep " + name + " at " + path);
                    
                    return path.toAbsolutePath();
                })
                .toArray(Path[]::new);
    }
    
    private static String getJarMavenCoordinates(String group, String name, String version, String classifier) {
        String cls = !classifier.isEmpty() ? "-" + classifier : "";
        
        return group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + cls + ".jar";
    }
}
