package cpw.mods.fml.client;

import java.io.File;
import java.util.Map.Entry;

import net.minecraft.client.gui.GuiErrorScreen;

import cpw.mods.fml.common.DuplicateModsFoundException;
import cpw.mods.fml.common.MissingModsException;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ArtifactVersion;

public class GuiDupesFound extends GuiErrorScreen
{

    private DuplicateModsFoundException dupes;

    public GuiDupesFound(DuplicateModsFoundException dupes)
    {
        this.dupes = dupes;
    }

    @Override
    public void initGui()
    {
        super.initGui();
    }
    @Override
    public void drawScreen(int par1, int par2, float par3)
    {
        this.drawDefaultBackground();
        int offset = Math.max(85 - dupes.dupes.size() * 10, 10);
        this.drawCenteredString(this.fontRenderer, "Forge Mod Loader has found a problem with your minecraft installation", this.width / 2, offset, 0xFFFFFF);
        offset+=10;
        this.drawCenteredString(this.fontRenderer, "You have mod sources that are duplicate within your system", this.width / 2, offset, 0xFFFFFF);
        offset+=10;
        this.drawCenteredString(this.fontRenderer, "Mod Id : File name", this.width / 2, offset, 0xFFFFFF);
        offset+=5;
        for (Entry<ModContainer, File> mc : dupes.dupes.entries())
        {
            offset+=10;
            this.drawCenteredString(this.fontRenderer, String.format("%s : %s", mc.getKey().getModId(), mc.getValue().getName()), this.width / 2, offset, 0xEEEEEE);
        }
    }
}
