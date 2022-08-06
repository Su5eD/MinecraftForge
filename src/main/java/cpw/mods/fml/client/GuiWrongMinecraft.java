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

package cpw.mods.fml.client;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.WrongMinecraftVersionException;
import net.minecraft.client.gui.GuiErrorScreen;

public class GuiWrongMinecraft extends GuiErrorScreen
{
    private WrongMinecraftVersionException wrongMC;
    public GuiWrongMinecraft(WrongMinecraftVersionException wrongMC)
    {
        this.wrongMC = wrongMC;
    }
    @Override
    public void initGui()
    {
        super.initGui();
    }
    @Override
    public void drawScreen(int p_73863_1_, int p_73863_2_, float p_73863_3_)
    {
        this.drawDefaultBackground();
        int offset = 75;
        this.drawCenteredString(this.fontRenderer, "Forge Mod Loader has found a problem with your minecraft installation", this.width / 2, offset, 0xFFFFFF);
        offset+=10;
        this.drawCenteredString(this.fontRenderer, String.format("The mod listed below does not want to run in Minecraft version %s", Loader.instance().getMinecraftModContainer().getVersion()), this.width / 2, offset, 0xFFFFFF);
        offset+=5;
        offset+=10;
        this.drawCenteredString(this.fontRenderer, String.format("%s (%s) wants Minecraft %s", wrongMC.mod.getName(), wrongMC.mod.getModId(), wrongMC.mod.acceptableMinecraftVersionRange()), this.width / 2, offset, 0xEEEEEE);
        offset+=20;
        this.drawCenteredString(this.fontRenderer, "The file 'ForgeModLoader-client-0.log' contains more information", this.width / 2, offset, 0xFFFFFF);
    }
}
