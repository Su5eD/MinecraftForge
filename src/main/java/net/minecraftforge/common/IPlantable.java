package net.minecraftforge.common;

import net.minecraft.world.World;

public interface IPlantable {
    EnumPlantType getPlantType(World world, int x, int y, int z);

    int getPlantID(World world, int x, int y, int z);

    int getPlantMetadata(World world, int x, int y, int z);
}
