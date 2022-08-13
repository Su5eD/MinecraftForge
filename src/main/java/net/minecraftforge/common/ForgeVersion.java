/**
 * This software is provided under the terms of the Minecraft Forge Public
 * License v1.0.
 */

package net.minecraftforge.common;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Level;

public class ForgeVersion
{
    //This number is incremented every time we remove deprecated code/major API changes, never reset
    public static final int majorVersion;
    //This number is incremented every minecraft release, never reset
    public static final int minorVersion;
    //This number is incremented every time a interface changes or new major feature is added, and reset every Minecraft version
    public static final int revisionVersion;
    //This number is incremented every time Jenkins builds Forge, and never reset. Should always be 0 in the repo code.
    public static final int buildVersion;
    
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
        
        majorVersion = getOrDefault(properties, "forge.major.number", 0);
        minorVersion = getOrDefault(properties, "forge.minor.number", 0);
        revisionVersion = getOrDefault(properties, "forge.revision.number", 0);
        buildVersion = getOrDefault(properties, "forge.build.number", 0);
    }

    public static int getMajorVersion()
    {
        return majorVersion;
    }

    public static int getMinorVersion()
    {
        return minorVersion;
    }

    public static int getRevisionVersion()
    {
        return revisionVersion;
    }

    public static int getBuildVersion()
    {
        return buildVersion;
    }

    public static String getVersion()
    {
        return String.format("%d.%d.%d.%d", majorVersion, minorVersion, revisionVersion, buildVersion);
    }
    
    private static int getOrDefault(Hashtable<Object, Object> properties, String name, int def) {
        Object val = properties.get(name);
        return val instanceof String ? Integer.parseInt((String) val) : def;
    }
}

