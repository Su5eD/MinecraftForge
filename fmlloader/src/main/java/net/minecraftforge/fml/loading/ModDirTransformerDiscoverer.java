/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.loading;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModDirTransformerDiscoverer implements ITransformerDiscoveryService {
    private static final List<String> SERVICE_SEARCH = Arrays.asList(
        "cpw.mods.modlauncher.api.ITransformationService",
        "net.minecraftforge.forgespi.locating.IModLocator"
    );
    
    @Override
    public List<NamedPath> candidates(final Path gameDirectory) {
        ModDirTransformerDiscoverer.scan(gameDirectory);
        return List.copyOf(found);
    }

    private final static List<NamedPath> found = new ArrayList<>();

    public static List<Path> allExcluded() {
        return found.stream().map(np->np.paths()[0]).toList();
    }

    private static void scan(final Path gameDirectory) {
        final Path modsDir = gameDirectory.resolve(FMLPaths.MODSDIR.relative()).toAbsolutePath().normalize();
        if (!Files.exists(modsDir)) {
            // Skip if the mods dir doesn't exist yet.
            return;
        }
        try (var walk = Files.walk(modsDir, 1)){
            walk.forEach(ModDirTransformerDiscoverer::visitFile);
        } catch (IOException | IllegalStateException ioe) {
            LogManager.getLogger().error("Error during early discovery", ioe);
        }
    }

    private static void visitFile(Path path) {
        if (Files.isRegularFile(path) && path.toString().endsWith(".jar") && LamdbaExceptionUtils.uncheck(() -> Files.size(path)) != 0) {
            SecureJar jar = SecureJar.from(path);
            jar.getProviders().stream()
                    .filter(p -> SERVICE_SEARCH.contains(p.serviceName()))
                    .forEach(p -> found.add(new NamedPath(jar.name(), path)));
        }
    }
}
