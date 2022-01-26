/*
 * Minecraft Forge - Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.loading;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.loading.IStartupMessageRenderer;
import net.minecraftforge.fml.client.loading.StartupMessageRendererManager;

import static org.lwjgl.opengl.GL30C.*;

public class EarlyLoaderGUI {
    private final Minecraft minecraft;
    private final Window window;
    private boolean handledElsewhere;
    
    private final IStartupMessageRenderer renderer;
    
    public EarlyLoaderGUI(final Minecraft minecraft) {
        this.minecraft = minecraft;
        this.window = minecraft.getWindow();
        renderer = StartupMessageRendererManager.getInstance().get().orElse(null);
        if (renderer != null) renderer.startup();
    }
    
    public void handleElsewhere() {
        this.handledElsewhere = true;
    }
    
    void renderFromGUI() {
        if (renderer != null) {
            renderer.render(window.getScreenWidth(), window.getScreenHeight(), 2, minecraft.options.darkMojangStudiosBackground);
        }
    }
    
    void renderTick() {
        if (handledElsewhere || renderer == null) {
            return;
        }
        int guiScale = window.calculateScale(0, false);
        window.setGuiScale(guiScale);
        
        boolean isDarkMode = minecraft.options.darkMojangStudiosBackground;
        glClearColor(isDarkMode ? 0 : (239F / 255F), isDarkMode ? 0 : (50F / 255F), isDarkMode ? 0 : (61F / 255F), 1);
        glClear(GL_COLOR_BUFFER_BIT);
        
        renderer.render(window.getScreenWidth(), window.getScreenHeight(), 2, isDarkMode);
        window.updateDisplay();
    }
}
