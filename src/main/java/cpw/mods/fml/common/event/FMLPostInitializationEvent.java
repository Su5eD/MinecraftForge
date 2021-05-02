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

package cpw.mods.fml.common.event;

import com.google.common.base.Throwables;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState.ModState;

public class FMLPostInitializationEvent extends FMLStateEvent
{
    public FMLPostInitializationEvent(Object... data)
    {
        super(data);
    }

    @Override
    public ModState getModState()
    {
        return ModState.POSTINITIALIZED;
    }

    public Object buildSoftDependProxy(String modId, String className)
    {
        if (Loader.isModLoaded(modId))
        {
            try
            {
                Class<?> clz = Class.forName(className,true,Loader.instance().getModClassLoader());
                return clz.newInstance();
            }
            catch (Exception e)
            {
                Throwables.propagateIfPossible(e);
                return null;
            }
        }
        return null;
    }
}
