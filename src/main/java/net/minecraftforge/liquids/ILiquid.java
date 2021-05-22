/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 * <p>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package net.minecraftforge.liquids;

/**
 * Liquids implement this interface
 *
 */
public interface ILiquid {

    /**
     * The itemId of the liquid item
     * @return the itemId
     */
    int stillLiquidId();

    /**
     * Is this liquid a metadata based liquid
     * @return if this is a metadata liquid
     */
    boolean isMetaSensitive();

    /**
     * The item metadata of the liquid
     * @return the metadata of the liquid
     */
    int stillLiquidMeta();
}
