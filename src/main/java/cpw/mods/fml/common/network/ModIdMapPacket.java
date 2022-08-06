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

package cpw.mods.fml.common.network;

import com.google.common.collect.MapDifference;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedBytes;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.ItemData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.NetHandler;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;

import static cpw.mods.fml.common.network.FMLPacket.Type.MOD_IDMAP;

public class ModIdMapPacket extends FMLPacket {
    private byte[][] partials;

    public ModIdMapPacket()
    {
        super(MOD_IDMAP);
    }

    @Override
    public byte[] generatePacket(Object... data)
    {
        NBTTagList completeList = (NBTTagList) data[0];
        NBTTagCompound wrap = new NBTTagCompound();
        wrap.setTag("List", completeList);
        try
        {
            return CompressedStreamTools.compress(wrap);
        }
        catch (Exception e)
        {
            FMLLog.log(Level.SEVERE, e, "A critical error writing the id map");
            throw new FMLNetworkException(e);
        }
    }

    @Override
    public FMLPacket consumePacket(byte[] data)
    {
        ByteArrayDataInput bdi = ByteStreams.newDataInput(data);
        int chunkIdx = UnsignedBytes.toInt(bdi.readByte());
        int chunkTotal = UnsignedBytes.toInt(bdi.readByte());
        int chunkLength = bdi.readInt();
        if (partials == null)
        {
            partials = new byte[chunkTotal][];
        }
        partials[chunkIdx] = new byte[chunkLength];
        bdi.readFully(partials[chunkIdx]);
        for (int i = 0; i < partials.length; i++)
        {
            if (partials[i] == null)
            {
                return null;
            }
        }
        return this;
    }

    @Override
    public void execute(INetworkManager network, FMLNetworkHandler handler, NetHandler netHandler, String userName)
    {
        byte[] allData = Bytes.concat(partials);
        GameData.initializeServerGate(1);
        try
        {
            NBTTagCompound serverList = CompressedStreamTools.decompress(allData);
            NBTTagList list = serverList.getTagList("List");
            Set<ItemData> itemData = GameData.buildWorldItemData(list);
            GameData.validateWorldSave(itemData);
            MapDifference<Integer, ItemData> serverDifference = GameData.gateWorldLoadingForValidation();
            if (serverDifference!=null)
            {
                FMLCommonHandler.instance().disconnectIDMismatch(serverDifference, netHandler, network);

            }
        }
        catch (IOException e)
        {
        }
    }

}
