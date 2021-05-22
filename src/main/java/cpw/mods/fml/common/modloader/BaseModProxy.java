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

package cpw.mods.fml.common.modloader;

import cpw.mods.fml.common.TickType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Marker interface for BaseMod
 *
 * @author cpw
 */
public interface BaseModProxy {
    void modsLoaded();

    void load();

    String getName();

    String getPriorities();

    String getVersion();

    boolean doTickInGUI(TickType type, boolean end, Object... tickData);

    boolean doTickInGame(TickType type, boolean end, Object... tickData);

    void generateSurface(World w, Random random, int i, int j);

    void generateNether(World w, Random random, int i, int j);

    int addFuel(int itemId, int damage);

    void takenFromCrafting(EntityPlayer player, ItemStack item, IInventory craftMatrix);

    void takenFromFurnace(EntityPlayer player, ItemStack item);

    void onClientLogout(INetworkManager manager);

    void onClientLogin(EntityPlayer player);

    void serverDisconnect();

    void serverConnect(NetHandler handler);

    void receiveCustomPacket(Packet250CustomPayload packet);

    void clientChat(String text);

    void onItemPickup(EntityPlayer player, ItemStack item);

    void serverCustomPayload(NetServerHandler handler, Packet250CustomPayload packet);

    void serverChat(NetServerHandler source, String message);
}
