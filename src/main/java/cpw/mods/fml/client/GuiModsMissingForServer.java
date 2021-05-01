package cpw.mods.fml.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSmallButton;
import net.minecraft.util.StringTranslate;
import cpw.mods.fml.common.network.ModMissingPacket;
import cpw.mods.fml.common.versioning.ArtifactVersion;

public class GuiModsMissingForServer extends GuiScreen
{
    private ModMissingPacket modsMissing;

    public GuiModsMissingForServer(ModMissingPacket modsMissing)
    {
        this.modsMissing = modsMissing;
    }

    @Override
    public void initGui()
    {
        StringTranslate translations = StringTranslate.getInstance();
        this.controlList.add(new GuiSmallButton(1, this.width / 2 - 75, this.height - 38, translations.translateKey("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton par1GuiButton)
    {
        if (par1GuiButton.enabled && par1GuiButton.id == 1)
        {
            FMLClientHandler.instance().getClient().displayGuiScreen(null);
        }
    }
    @Override
    public void drawScreen(int par1, int par2, float par3)
    {
        this.drawDefaultBackground();
        int offset = Math.max(85 - modsMissing.getModList().size() * 10, 10);
        this.drawCenteredString(this.fontRenderer, "Forge Mod Loader could not connect to this server", this.width / 2, offset, 0xFFFFFF);
        offset += 10;
        this.drawCenteredString(this.fontRenderer, "The mods and versions listed below could not be found", this.width / 2, offset, 0xFFFFFF);
        offset += 10;
        this.drawCenteredString(this.fontRenderer, "They are required to play on this server", this.width / 2, offset, 0xFFFFFF);
        offset += 5;
        for (ArtifactVersion v : modsMissing.getModList())
        {
            offset += 10;
            this.drawCenteredString(this.fontRenderer, String.format("%s : %s", v.getLabel(), v.getRangeString()), this.width / 2, offset, 0xEEEEEE);
        }
        super.drawScreen(par1, par2, par3);
    }
}
