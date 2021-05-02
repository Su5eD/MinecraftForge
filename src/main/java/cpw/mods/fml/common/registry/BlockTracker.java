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

package cpw.mods.fml.common.registry;

import java.util.BitSet;

import net.minecraft.block.Block;

class BlockTracker
{
    private static final BlockTracker INSTANCE = new BlockTracker();
    private BitSet allocatedBlocks;

    private BlockTracker()
    {
        allocatedBlocks = new BitSet(4096);
        allocatedBlocks.set(0, 4096);
        for (int i = 0; i < Block.blocksList.length; i++)
        {
            if (Block.blocksList[i]!=null)
            {
                allocatedBlocks.clear(i);
            }
        }
    }
    public static int nextBlockId()
    {
        return instance().getNextBlockId();
    }

    private int getNextBlockId()
    {
        int idx = allocatedBlocks.nextSetBit(0);
        allocatedBlocks.clear(idx);
        return idx;
    }
    private static BlockTracker instance()
    {
        return INSTANCE;
    }
    public static void reserveBlockId(int id)
    {
        instance().doReserveId(id);
    }
    private void doReserveId(int id)
    {
        allocatedBlocks.clear(id);
    }


}
