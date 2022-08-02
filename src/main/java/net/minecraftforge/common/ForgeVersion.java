/**
 * This software is provided under the terms of the Minecraft Forge Public
 * License v1.0.
 */

package net.minecraftforge.common;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ForgeVersion {
    public static final int majorVersion;
    public static final int minorVersion;
    public static final int revisionVersion;
    public static final int buildVersion;
    public static final String classifier;

    public static int getMajorVersion() {
        return majorVersion;
    }

    public static int getMinorVersion() {
        return minorVersion;
    }

    public static int getRevisionVersion() {
        return revisionVersion;
    }

    public static int getBuildVersion() {
        return buildVersion;
    }
    
    public static String getVersion() {
        return String.format("%d.%d.%d.%d%s", majorVersion, minorVersion, revisionVersion, buildVersion, !classifier.isEmpty() ? "-" + classifier : "");
    }

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

        String version = properties.getProperty("forge.version", "missing");
        if (version.contains("-")) {
            String[] parts = version.split("-");
            version = parts[0];
            classifier = parts[1];
        } else classifier = "";
        
        List<Integer> parts = Arrays.stream(version.split("\\."))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        
        majorVersion = parts.get(0);
        minorVersion = parts.get(1);
        revisionVersion = parts.get(2);
        buildVersion = parts.get(3);
    }
}

