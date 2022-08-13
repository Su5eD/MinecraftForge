package com.example.examplemod;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import net.minecraft.block.Block;

import java.util.logging.Logger;

@Mod(modid = ExampleMod.MODID, useMetadata = true)
@NetworkMod(clientSideRequired = true, serverSideRequired = true)
public class ExampleMod {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = Logger.getLogger(MODID);
    
    @Init
    public void init(FMLInitializationEvent event) {
        LOGGER.setParent(FMLLog.getLogger());
        
        // some example code
        LOGGER.info("DIRT BLOCK >> " + Block.dirt.getBlockName());
    }
}
