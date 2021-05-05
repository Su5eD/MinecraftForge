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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet250CustomPayload;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import static cpw.mods.fml.common.network.FMLPacket.Type.*;

public class ModListResponsePacket extends FMLPacket
{
    private Map<String,String> modVersions;
    private List<String> missingMods;

    public ModListResponsePacket()
    {
        super(MOD_LIST_RESPONSE);
    }

    @Override
    public byte[] generatePacket(Object... data)
    {
        Map<String,String> modVersions = (Map<String, String>) data[0];
        List<String> missingMods = (List<String>) data[1];
        ByteArrayDataOutput dat = ByteStreams.newDataOutput();
        dat.writeInt(modVersions.size());
        for (Entry<String, String> version : modVersions.entrySet())
        {
            dat.writeUTF(version.getKey());
            dat.writeUTF(version.getValue());
        }
        dat.writeInt(missingMods.size());
        for (String missing : missingMods)
        {
            dat.writeUTF(missing);
        }
        return dat.toByteArray();
    }

    @Override
    public FMLPacket consumePacket(byte[] data)
    {
        ByteArrayDataInput dat = ByteStreams.newDataInput(data);
        int versionListSize = dat.readInt();
        modVersions = Maps.newHashMapWithExpectedSize(versionListSize);
        for (int i = 0; i < versionListSize; i++)
        {
            String modName = dat.readUTF();
            String modVersion = dat.readUTF();
            modVersions.put(modName, modVersion);
        }

        int missingModSize = dat.readInt();
        missingMods = Lists.newArrayListWithExpectedSize(missingModSize);

        for (int i = 0; i < missingModSize; i++)
        {
            missingMods.add(dat.readUTF());
        }
        return this;
    }

    @Override
    public void execute(INetworkManager network, FMLNetworkHandler handler, NetHandler netHandler, String userName)
    {
        Map<String, ModContainer> indexedModList = Maps.newHashMap(Loader.instance().getIndexedModList());
        List<String> missingClientMods = Lists.newArrayList();
        List<String> versionIncorrectMods = Lists.newArrayList();

        for (String m : missingMods)
        {
            ModContainer mc = indexedModList.get(m);
            NetworkModHandler networkMod = handler.findNetworkModHandler(mc);
            if (networkMod.requiresClientSide())
            {
                missingClientMods.add(m);
            }
        }

        for (Entry<String,String> modVersion : modVersions.entrySet())
        {
            ModContainer mc = indexedModList.get(modVersion.getKey());
            NetworkModHandler networkMod = handler.findNetworkModHandler(mc);
            if (!networkMod.acceptVersion(modVersion.getValue()))
            {
                versionIncorrectMods.add(modVersion.getKey());
            }
        }

        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = "FML";
        if (missingClientMods.size()>0 || versionIncorrectMods.size() > 0)
        {
            pkt.data = FMLPacket.makePacket(MOD_MISSING, missingClientMods, versionIncorrectMods);
            Logger.getLogger("Minecraft").info(String.format("User %s connection failed: missing %s, bad versions %s", userName, missingClientMods, versionIncorrectMods));
            FMLLog.info("User %s connection failed: missing %s, bad versions %s", userName, missingClientMods, versionIncorrectMods);
            // Mark this as bad
            FMLNetworkHandler.setHandlerState((NetLoginHandler) netHandler, FMLNetworkHandler.MISSING_MODS_OR_VERSIONS);
            pkt.length = pkt.data.length;
            network.addToSendQueue(pkt);
        }
        else
        {
            pkt.data = FMLPacket.makePacket(MOD_IDENTIFIERS, netHandler);
            Logger.getLogger("Minecraft").info(String.format("User %s connecting with mods %s", userName, modVersions.keySet()));
            FMLLog.info("User %s connecting with mods %s", userName, modVersions.keySet());
            pkt.length = pkt.data.length;
            network.addToSendQueue(pkt);
            NBTTagList itemList = new NBTTagList();
            GameData.writeItemData(itemList);
            byte[][] registryPackets = FMLPacket.makePacketSet(MOD_IDMAP, itemList);
            for (int i = 0; i < registryPackets.length; i++)
            {
                network.addToSendQueue(PacketDispatcher.getPacket("FML", registryPackets[i]));
            }
        }

        // reset the continuation flag - we have completed extra negotiation and the login should complete now
        NetLoginHandler.func_72531_a((NetLoginHandler) netHandler, true);
    }

}
