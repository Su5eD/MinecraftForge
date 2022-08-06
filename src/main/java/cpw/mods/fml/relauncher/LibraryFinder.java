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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryFinder {
    
    // Credit: MinecraftForge
    
    static Path getLibrariesRoot() {
        Path asm = findJarPathFor("org/objectweb/asm/Opcodes.class", "asm");
        Path libs = asm.getParent().getParent().getParent().getParent().getParent().getParent();
        FMLRelaunchLog.finest("Found probable library path %s", libs);
        return libs;
    }

    public static Path findJarPathFor(String className, String jarName) {
        URL resource = LibraryFinder.class.getClassLoader().getResource(className);
        return findJarPathFor(className, jarName, resource);
    }

    public static Path findJarPathFor(String resourceName, String jarName, URL resource) {
        try {
            URI uri = resource.toURI();
            Path path;
            if (uri.getRawSchemeSpecificPart().contains("!")) {
                path = Paths.get(new URI(uri.getRawSchemeSpecificPart().split("!")[0]));
            } else {
                path = Paths.get(new URI("file://" + uri.getRawSchemeSpecificPart().substring(0, uri.getRawSchemeSpecificPart().length() - resourceName.length())));
            }

            FMLRelaunchLog.finest("Found JAR %s at path %s", jarName, path.toString());
            return path;
        } catch (URISyntaxException | NullPointerException var5) {
            FMLRelaunchLog.severe("Failed to find JAR for class %s - %s", resourceName, jarName);
            throw new RuntimeException("Unable to locate " + resourceName + " - " + jarName, var5);
        }
    }
    
    public static List<Path> getMcDeps(Path root, String side) {
        Map<String, String> mcDeps = new HashMap<>();
        mcDeps.put("MC SLIM", getJarMavenCoordinates("net.minecraft", side, FMLInjectionData.mcversion + "-" +  FMLInjectionData.mcpversion, "slim"));
        mcDeps.put("MC EXTRA", getJarMavenCoordinates("net.minecraft", side, FMLInjectionData.mcversion + "-" +  FMLInjectionData.mcpversion, "extra"));
        mcDeps.put("Forge Patches", getJarMavenCoordinates("net.minecraftforge", "forge", FMLInjectionData.mcversion + "-" + ForgeVersion.version, side));

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
    
    private static String getJarMavenCoordinates(String group, String name, String version, String classifier) {
        String cls = !classifier.isEmpty() ? "-" + classifier : "";
        
        return group.replace('.', '/') + "/" + name + "/" + version + "/" + name + "-" + version + cls + ".jar";
    }
}
