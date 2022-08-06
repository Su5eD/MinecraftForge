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

package cpw.mods.fml.client.registry;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class ClientRegistry
{
    /**
     * 
     * Utility method for registering a tile entity and it's renderer at once - generally you should register them separately
     * 
     * @param tileEntityClass
     * @param id
     * @param specialRenderer
     */
    public static void registerTileEntity(Class <? extends TileEntity > tileEntityClass, String id, TileEntitySpecialRenderer specialRenderer)
    {
        GameRegistry.registerTileEntity(tileEntityClass, id);
        bindTileEntitySpecialRenderer(tileEntityClass, specialRenderer);
    }
    
    public static void bindTileEntitySpecialRenderer(Class <? extends TileEntity> tileEntityClass, TileEntitySpecialRenderer specialRenderer)
    {
        TileEntityRenderer.instance.specialRendererMap.put(tileEntityClass, specialRenderer);
        specialRenderer.setTileEntityRenderer(TileEntityRenderer.instance);
    }
}
