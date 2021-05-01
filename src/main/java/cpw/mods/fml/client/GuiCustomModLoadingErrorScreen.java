package cpw.mods.fml.client;

import net.minecraft.client.gui.GuiErrorScreen;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.WrongMinecraftVersionException;

public class GuiCustomModLoadingErrorScreen extends GuiErrorScreen
{
    private CustomModLoadingErrorDisplayException customException;
    public GuiCustomModLoadingErrorScreen(CustomModLoadingErrorDisplayException customException)
    {
        this.customException = customException;
    }
    @Override
    public void initGui()
    {
        super.initGui();
        this.customException.initGui(this, fontRenderer);
    }
    @Override
    public void drawScreen(int par1, int par2, float par3)
    {
        this.drawDefaultBackground();
        this.customException.drawScreen(this, fontRenderer, par1, par2, par3);
    }
}
