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

import net.minecraft.launchwrapper.LaunchClassLoader;

import javax.swing.*;
import java.applet.Applet;
import java.io.File;

public class FMLRelauncher {
    private static FMLRelauncher INSTANCE;
    public static String logFileNamePattern;
    private static String side;
    private LaunchClassLoader launchClassLoader;
    public File minecraftHome;
    public File assetsDir;
    private Object newApplet;
    private Class<? super Object> appletClass;

    JDialog popupWindow;

    public static void configureClient(File minecraftHome, File assetsDir, LaunchClassLoader classLoader) {
        side = "CLIENT";
        logFileNamePattern = "ForgeModLoader-client-%g.log";

        FMLRelauncher instance = instance();
        instance.launchClassLoader = classLoader;
        instance.minecraftHome = minecraftHome;
        instance.assetsDir = assetsDir;
        instance.setupHome(minecraftHome, assetsDir);
    }

    public static void configureServer(File minecraftHome, File assetsDir, LaunchClassLoader classLoader) {
        side = "SERVER";
        logFileNamePattern = "ForgeModLoader-server-%g.log";

        FMLRelauncher instance = instance();
        instance.launchClassLoader = classLoader;
        instance.minecraftHome = minecraftHome;
        instance.assetsDir = assetsDir;
        instance.setupHome(minecraftHome, assetsDir);
    }

    public static void handleClientRelaunch(ArgsWrapper wrap) {
        instance().relaunchClient(wrap);
    }

    public static void handleServerRelaunch(ArgsWrapper wrap) {
        instance().relaunchServer(wrap);
    }

    static FMLRelauncher instance() {
        if (INSTANCE == null) {
            INSTANCE = new FMLRelauncher();
        }
        return INSTANCE;
    }

    private FMLRelauncher() {
    }

    private void showWindow(boolean showIt) {
        if (RelaunchLibraryManager.downloadMonitor != null) {
            return;
        }
        try {
            if (showIt) {
                RelaunchLibraryManager.downloadMonitor = new Downloader();
                popupWindow = (JDialog) RelaunchLibraryManager.downloadMonitor.makeDialog();
            } else {
                RelaunchLibraryManager.downloadMonitor = new DummyDownloader();
            }
        } catch (Throwable e) {
            if (RelaunchLibraryManager.downloadMonitor == null) {
                RelaunchLibraryManager.downloadMonitor = new DummyDownloader();
                e.printStackTrace();
            } else {
                RelaunchLibraryManager.downloadMonitor.makeHeadless();
            }
            popupWindow = null;
        }
    }

    private void relaunchClient(ArgsWrapper wrap) {
        showWindow(true);
        // Now we re-inject the home into the "new" minecraft under our control
        Class<? super Object> client;

        File minecraftHome = computeExistingClientHome();
        client = setupNewClientHome(minecraftHome);

        try {
            ReflectionHelper.findMethod(client, null, new String[]{"fmlReentry"}, ArgsWrapper.class).invoke(null, wrap);
        } catch (Exception e) {
            e.printStackTrace();
            // Hmmm
        }
    }

    private Class<? super Object> setupNewClientHome(File minecraftHome) {
        Class<? super Object> client = ReflectionHelper.getClass(launchClassLoader, "net.minecraft.client.Minecraft");
        ReflectionHelper.setPrivateValue(client, null, minecraftHome, "minecraftDir", "an", "minecraftDir");
        return client;
    }

    private void relaunchServer(ArgsWrapper wrap) {
        showWindow(false);
        // Now we re-inject the home into the "new" minecraft under our control
        Class<? super Object> server;

        server = ReflectionHelper.getClass(launchClassLoader, "net.minecraft.server.MinecraftServer");
        try {
            ReflectionHelper.findMethod(server, null, new String[]{"fmlReentry"}, ArgsWrapper.class).invoke(null, wrap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupHome(File minecraftHome, File assetsDir) {
        FMLInjectionData.build(minecraftHome, launchClassLoader);
        FMLRelaunchLog.minecraftHome = minecraftHome;
        FMLRelaunchLog.info("Forge Mod Loader version %s for Minecraft %s loading", FMLInjectionData.fmlversion, FMLInjectionData.mccversion, FMLInjectionData.mcpversion);

        try {
            showWindow(true);
            RelaunchLibraryManager.handleLaunch(minecraftHome, assetsDir, launchClassLoader);
        } catch (Throwable t) {
            if (popupWindow != null) {
                try {
                    String logFile = new File(minecraftHome, "ForgeModLoader-client-0.log").getCanonicalPath();
                    JOptionPane.showMessageDialog(popupWindow, String.format(
                            "<html><div align=\"center\"><font size=\"+1\">There was a fatal error starting up minecraft and FML</font></div><br/>"
                                    + "Minecraft cannot launch in it's current configuration<br/>"
                                    + "Please consult the file <i><a href=\"file:///%s\">%s</a></i> for further information</html>", logFile, logFile),
                            "Fatal FML error", JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    // ah well, we tried
                }
            }
        } finally {
            if (popupWindow != null) {
                popupWindow.setVisible(false);
                popupWindow.dispose();
            }
        }
        
        if (RelaunchLibraryManager.downloadMonitor.shouldStopIt()) {
            System.exit(1);
        }
    }

    /**
     * @return the location of the client home
     */
    private File computeExistingClientHome() {
        Class<? super Object> mcMaster = ReflectionHelper.getClass(launchClassLoader, "net.minecraft.client.Minecraft");
        // If we get the system property we inject into the old MC, setup the
        // dir, then pull the value
        File mcHome;
        String str = System.getProperty("minecraft.applet.TargetDirectory");
        if (str == null) mcHome = this.minecraftHome;
        else {
            str = str.replace('/', File.separatorChar);
            mcHome = new File(str);
        }
        ReflectionHelper.setPrivateValue(mcMaster, null, mcHome, "minecraftDir", "an", "minecraftDir");
        return mcHome;
    }

    public static void appletEntry(Applet minecraftApplet) {
        side = "CLIENT";
        logFileNamePattern = "ForgeModLoader-client-%g.log";
        INSTANCE.relaunchApplet(minecraftApplet);
    }

    private void relaunchApplet(Applet minecraftApplet) {
        showWindow(true);

        appletClass = ReflectionHelper.getClass(launchClassLoader, "net.minecraft.client.MinecraftApplet");
        if (minecraftApplet.getClass().getClassLoader() == launchClassLoader) {
            if (popupWindow != null) {
                popupWindow.setVisible(false);
                popupWindow.dispose();
            }
            try {
                newApplet = minecraftApplet;
                ReflectionHelper.findMethod(appletClass, newApplet, new String[]{"fmlInitReentry"}).invoke(newApplet);
                return;
            } catch (Exception e) {
                System.out.println("FMLRelauncher.relaunchApplet");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        File mcDir = computeExistingClientHome();
        setupHome(mcDir, this.assetsDir);
        setupNewClientHome(mcDir);

        Class<? super Object> parentAppletClass = ReflectionHelper.getClass(launchClassLoader, "java.applet.Applet");

        try {
            newApplet = appletClass.newInstance();
            Object appletContainer = ReflectionHelper.getPrivateValue(ReflectionHelper.getClass(launchClassLoader, "java.awt.Component"),
                    minecraftApplet, "parent");

            String launcherClassName = System.getProperty("minecraft.applet.WrapperClass", "net.minecraft.Launcher");
            Class<? super Object> launcherClass = ReflectionHelper.getClass(launchClassLoader, launcherClassName);
            if (launcherClass.isInstance(appletContainer)) {
                ReflectionHelper.findMethod(ReflectionHelper.getClass(launchClassLoader, "java.awt.Container"), minecraftApplet,
                        new String[]{"removeAll"}).invoke(appletContainer);
                ReflectionHelper.findMethod(launcherClass, appletContainer, new String[]{"replace"}, parentAppletClass).invoke(appletContainer, newApplet);
            } else {
                FMLRelaunchLog.severe("Found unknown applet parent %s, unable to inject!\n", appletContainer.getClass().getName());
                throw new RuntimeException();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (popupWindow != null) {
                popupWindow.setVisible(false);
                popupWindow.dispose();
            }
        }
    }

    public static void appletStart(Applet applet) {
        INSTANCE.startApplet(applet);
    }

    private void startApplet(Applet applet) {
        if (applet.getClass().getClassLoader() == launchClassLoader) {
            if (popupWindow != null) {
                popupWindow.setVisible(false);
                popupWindow.dispose();
            }
            if (RelaunchLibraryManager.downloadMonitor.shouldStopIt()) {
                System.exit(1);
            }
            try {
                ReflectionHelper.findMethod(appletClass, newApplet, new String[]{"fmlStartReentry"}).invoke(newApplet);
            } catch (Exception e) {
                System.out.println("FMLRelauncher.startApplet");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public static String side() {
        return side;
    }
}
