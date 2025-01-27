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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

public class KeyBindingRegistry
{
    /**
     * Register a KeyHandler to the game. This handler will be called on certain tick events
     * if any of its key is inactive or has recently changed state
     *
     * @param handler
     */
    public static void registerKeyBinding(KeyHandler handler) {
        instance().keyHandlers.add(handler);
        if (!handler.isDummy)
        {
            TickRegistry.registerTickHandler(handler, Side.CLIENT);
        }
    }


    /**
     * Extend this class to register a KeyBinding and recieve callback
     * when the key binding is triggered
     *
     * @author cpw
     *
     */
    public static abstract class KeyHandler implements ITickHandler
    {
        protected KeyBinding[] keyBindings;
        protected boolean[] keyDown;
        protected boolean[] repeatings;
        private boolean isDummy;

        /**
         * Pass an array of keybindings and a repeat flag for each one
         *
         * @param keyBindings
         * @param repeatings
         */
        public KeyHandler(KeyBinding[] keyBindings, boolean[] repeatings)
        {
            assert keyBindings.length == repeatings.length : "You need to pass two arrays of identical length";
            this.keyBindings = keyBindings;
            this.repeatings = repeatings;
            this.keyDown = new boolean[keyBindings.length];
        }


        /**
         * Register the keys into the system. You will do your own keyboard management elsewhere. No events will fire
         * if you use this method
         *
         * @param keyBindings
         */
        public KeyHandler(KeyBinding[] keyBindings)
        {
            this.keyBindings = keyBindings;
            this.isDummy = true;
        }

        public KeyBinding[] getKeyBindings()
        {
            return this.keyBindings;
        }

        /**
         * Not to be overridden - KeyBindings are tickhandlers under the covers
         */
        @Override
        public final void tickStart(EnumSet<TickType> type, Object... tickData)
        {
            keyTick(type, false);
        }

        /**
         * Not to be overridden - KeyBindings are tickhandlers under the covers
         */
        @Override
        public final void tickEnd(EnumSet<TickType> type, Object... tickData)
        {
            keyTick(type, true);
        }

        private void keyTick(EnumSet<TickType> type, boolean tickEnd)
        {
            for (int i = 0; i < keyBindings.length; i++)
            {
                KeyBinding keyBinding = keyBindings[i];
                int keyCode = keyBinding.keyCode;
                boolean state = (keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode));
                if (state != keyDown[i] || (state && repeatings[i]))
                {
                    if (state)
                    {
                        keyDown(type, keyBinding, tickEnd, state!=keyDown[i]);
                    }
                    else
                    {
                        keyUp(type, keyBinding, tickEnd);
                    }
                    if (tickEnd)
                    {
                        keyDown[i] = state;
                    }
                }

            }
        }

        /**
         * Called when the key is first in the down position on any tick from the {@link #ticks()}
         * set. Will be called subsequently with isRepeat set to true
         *
         * @see #keyUp(EnumSet, KeyBinding, boolean)
         *
         * @param types the type(s) of tick that fired when this key was first down
         * @param tickEnd was it an end or start tick which fired the key
         * @param isRepeat is it a repeat key event
         */
        public abstract void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat);
        /**
         * Fired once when the key changes state from down to up
         *
         * @see #keyDown(EnumSet, KeyBinding, boolean, boolean)
         *
         * @param types the type(s) of tick that fired when this key was first down
         * @param tickEnd was it an end or start tick which fired the key
         */
        public abstract void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd);


        /**
         * This is the list of ticks for which the key binding should trigger. The only
         * valid ticks are client side ticks, obviously.
         *
         * @see cpw.mods.fml.common.ITickHandler#ticks()
         */
        public abstract EnumSet<TickType> ticks();
    }

    private static final KeyBindingRegistry INSTANCE = new KeyBindingRegistry();

    private Set<KeyHandler> keyHandlers = Sets.newLinkedHashSet();

    @Deprecated
    public static KeyBindingRegistry instance()
    {
        return INSTANCE;
    }


    public void uploadKeyBindingsToGame(GameSettings settings)
    {
        ArrayList<KeyBinding> harvestedBindings = Lists.newArrayList();
        for (KeyHandler key : keyHandlers)
        {
            for (KeyBinding kb : key.keyBindings)
            {
                harvestedBindings.add(kb);
            }
        }
        KeyBinding[] modKeyBindings = harvestedBindings.toArray(new KeyBinding[harvestedBindings.size()]);
        KeyBinding[] allKeys = new KeyBinding[settings.keyBindings.length + modKeyBindings.length];
        System.arraycopy(settings.keyBindings, 0, allKeys, 0, settings.keyBindings.length);
        System.arraycopy(modKeyBindings, 0, allKeys, settings.keyBindings.length, modKeyBindings.length);
        settings.keyBindings = allKeys;
        settings.loadOptions();
    }
}
