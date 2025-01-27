package net.minecraftforge.classloading;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

public class FMLForgePlugin implements IFMLLoadingPlugin
{
    @Override
    public String[] getLibraryRequestClass()
    {
        return null;
    }

    @Override
    public String[] getASMTransformerClass()
    {
        return new String[]{ 
                                "net.minecraftforge.transformers.ForgeAccessTransformer",
                                "net.minecraftforge.transformers.EventTransformer"
                           };
    }

    @Override
    public String getModContainerClass()
    {
        return "net.minecraftforge.common.ForgeDummyContainer";
    }

    @Override
    public String getSetupClass()
    {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data)
    {
    }
}
