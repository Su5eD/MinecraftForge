/**
 * This software is provided under the terms of the Minecraft Forge Public
 * License v1.0.
 */

package net.minecraftforge.common;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

public class ForgeVersion {
    public static final String version;

    static {
        InputStream stream = ForgeVersion.class.getClassLoader().getResourceAsStream("forgeversion.properties");
        Properties properties = new Properties();

        if (stream != null) {
            try {
                properties.load(stream);
            } catch (IOException ex) {
                FMLRelaunchLog.log(Level.SEVERE, ex, "Could not get Minecraft Forge version information - corrupted installation detected!");
            }
        }

        version = properties.getProperty("forge.version", "missing");
    }
}

