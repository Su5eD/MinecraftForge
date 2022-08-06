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

import net.minecraft.client.gui.GuiErrorScreen;

public class GuiCustomModLoadingErrorScreen extends GuiErrorScreen
{
    private CustomModLoadingErrorDisplayException customException;
    public GuiCustomModLoadingErrorScreen(CustomModLoadingErrorDisplayException customException)
    {
        this.customException = customException;
    }
    @Override
    public void initGui()
    {
        super.initGui();
        this.customException.initGui(this, fontRenderer);
    }
    @Override
    public void drawScreen(int p_73863_1_, int p_73863_2_, float p_73863_3_)
    {
        this.drawDefaultBackground();
        this.customException.drawScreen(this, fontRenderer, p_73863_1_, p_73863_2_, p_73863_3_);
    }
}
