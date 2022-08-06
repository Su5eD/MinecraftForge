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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Simple intermod communications to receive simple messages directed at you
 * from other mods
 *
 * @author cpw
 *
 */
public class FMLInterModComms {
    private static final ImmutableList<IMCMessage> emptyIMCList = ImmutableList.<IMCMessage>of();
    private static ArrayListMultimap<String, IMCMessage> modMessages = ArrayListMultimap.create();

    /**
     * Subscribe to this event to receive your messages (they are sent between
     * {@link Init} and {@link PostInit})
     *
     * @author cpw
     *
     */
    public static class IMCEvent extends FMLEvent {
        private ModContainer activeContainer;

        @Override
        public void applyModContainer(ModContainer activeContainer)
        {
            this.activeContainer = activeContainer;
            FMLLog.finest("Attempting to deliver %d IMC messages to mod %s", modMessages.get(activeContainer.getModId()).size(), activeContainer.getModId());
        }

        private ImmutableList<IMCMessage> currentList;

        public ImmutableList<IMCMessage> getMessages()
        {
            if (currentList == null)
            {
                currentList = ImmutableList.copyOf(modMessages.removeAll(activeContainer.getModId()));
            }
            return currentList;
        }
    }

    /**
     * You will receive an instance of this for each message sent
     *
     * @author cpw
     *
     */
    public static final class IMCMessage {
        /**
         * This is the modid of the mod that sent you the message
         */
        private String sender;
        /**
         * This field, and {@link #value} are both at the mod's discretion
         */
        public final String key;
        /**
         * This field, and {@link #key} are both at the mod's discretion
         */
        private Object value;

        private IMCMessage(String key, Object value)
        {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString()
        {
            return sender;
        }

        public String getSender()
        {
            return this.sender;
        }

        void setSender(ModContainer activeModContainer)
        {
            this.sender = activeModContainer.getModId();
        }

        public String getStringValue()
        {
            return (String) value;
        }

        public NBTTagCompound getNBTValue()
        {
            return (NBTTagCompound) value;
        }

        public ItemStack getItemStackValue()
        {
            return (ItemStack) value;
        }

        public Class<?> getMessageType()
        {
            return value.getClass();
        }

        public boolean isStringMessage()
        {
            return String.class.isAssignableFrom(getMessageType());
        }

        public boolean isItemStackMessage()
        {
            return ItemStack.class.isAssignableFrom(getMessageType());
        }

        public boolean isNBTMessage()
        {
            return NBTTagCompound.class.isAssignableFrom(getMessageType());
        }
    }

    public static boolean sendMessage(String modId, String key, NBTTagCompound value)
    {
        return enqueueStartupMessage(modId, new IMCMessage(key, value));
    }
    public static boolean sendMessage(String modId, String key, ItemStack value)
    {
        return enqueueStartupMessage(modId, new IMCMessage(key, value));
    }
    public static boolean sendMessage(String modId, String key, String value)
    {
        return enqueueStartupMessage(modId, new IMCMessage(key, value));
    }

    public static void sendRuntimeMessage(Object sourceMod, String modId, String key, NBTTagCompound value)
    {
        enqueueMessage(sourceMod, modId, new IMCMessage(key, value));
    }

    public static void sendRuntimeMessage(Object sourceMod, String modId, String key, ItemStack value)
    {
        enqueueMessage(sourceMod, modId, new IMCMessage(key, value));
    }

    public static void sendRuntimeMessage(Object sourceMod, String modId, String key, String value)
    {
        enqueueMessage(sourceMod, modId, new IMCMessage(key, value));
    }

    private static boolean enqueueStartupMessage(String modTarget, IMCMessage message)
    {
        if (Loader.instance().activeModContainer() == null)
        {
            return false;
        }
        enqueueMessage(Loader.instance().activeModContainer(), modTarget, message);
        return Loader.isModLoaded(modTarget) && !Loader.instance().hasReachedState(LoaderState.POSTINITIALIZATION);

    }
    private static void enqueueMessage(Object sourceMod, String modTarget, IMCMessage message)
    {
        ModContainer mc;
        if (sourceMod instanceof ModContainer) {
            mc = (ModContainer) sourceMod;
        }
        else
        {
            mc = FMLCommonHandler.instance().findContainerFor(sourceMod);
        }
        if (mc != null && Loader.isModLoaded(modTarget))
        {
            message.setSender(mc);
            modMessages.put(modTarget, message);
        }
    }

    /**
     * Retrieve any pending runtime messages for the mod
     * @param forMod The {@link Instance} of the Mod to fetch messages for
     * @return any messages - the collection will never be null
     */
    public static ImmutableList<IMCMessage> fetchRuntimeMessages(Object forMod)
    {
        ModContainer mc = FMLCommonHandler.instance().findContainerFor(forMod);
        if (mc != null)
        {
            return ImmutableList.copyOf(modMessages.removeAll(mc.getModId()));
        }
        else
        {
            return emptyIMCList;
        }
    }
}
