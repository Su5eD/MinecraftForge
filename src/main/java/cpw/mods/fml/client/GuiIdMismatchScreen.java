package cpw.mods.fml.client;

import java.util.List;
import java.util.Map.Entry;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.StringTranslate;

import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;

import cpw.mods.fml.common.registry.ItemData;
import cpw.mods.fml.common.versioning.ArtifactVersion;

public class GuiIdMismatchScreen extends GuiYesNo {
    private List<String> missingIds = Lists.newArrayList();
    private List<String> mismatchedIds = Lists.newArrayList();
    private boolean allowContinue;

    public GuiIdMismatchScreen(MapDifference<Integer, ItemData> idDifferences, boolean allowContinue)
    {
        super(null,"ID mismatch", "Should I continue?", 1);
        parentScreen = this;
        for (Entry<Integer, ItemData> entry : idDifferences.entriesOnlyOnLeft().entrySet())
        {
            missingIds.add(String.format("ID %d (ModID: %s, type %s) is missing", entry.getValue().getItemId(), entry.getValue().getModId(), entry.getValue().getItemType()));
        }
        for (Entry<Integer, ValueDifference<ItemData>> entry : idDifferences.entriesDiffering().entrySet())
        {
            ItemData world = entry.getValue().leftValue();
            ItemData game = entry.getValue().rightValue();
            mismatchedIds.add(String.format("ID %d is mismatched. World: (ModID: %s, type %s, ordinal %d) Game (ModID: %s, type %s, ordinal %d)", world.getItemId(), world.getModId(), world.getItemType(), world.getOrdinal(), game.getModId(), game.getItemType(), game.getOrdinal()));
        }
        this.allowContinue = allowContinue;
    }

    @Override
    public void confirmClicked(boolean choice, int par2)
    {
        FMLClientHandler.instance().callbackIdDifferenceResponse(choice);
    }

    @Override
    public void drawScreen(int par1, int par2, float par3)
    {
        this.drawDefaultBackground();
        if (!allowContinue && controlList.size() == 2)
        {
            controlList.remove(0);
        }
        int offset = Math.max(85 - missingIds.size() * 10 + mismatchedIds.size() * 30, 10);
        this.drawCenteredString(this.fontRenderer, "Forge Mod Loader has found world ID mismatches", this.width / 2, offset, 0xFFFFFF);
        offset += 10;
        for (String s: missingIds) {
            this.drawCenteredString(this.fontRenderer, s, this.width / 2, offset, 0xEEEEEE);
            offset += 10;
        }
        for (String s: mismatchedIds) {
            this.drawCenteredString(this.fontRenderer, s, this.width / 2, offset, 0xEEEEEE);
            offset += 10;
        }
        offset += 10;
        if (allowContinue)
        {
            this.drawCenteredString(this.fontRenderer, "Do you wish to continue loading?", this.width / 2, offset, 0xFFFFFF);
            offset += 10;
        }
        else
        {
            this.drawCenteredString(this.fontRenderer, "You cannot connect to this server", this.width / 2, offset, 0xFFFFFF);
            offset += 10;
        }
        // super.super. Grrr
        for (int var4 = 0; var4 < this.controlList.size(); ++var4)
        {
            GuiButton var5 = (GuiButton)this.controlList.get(var4);
            var5.yPosition = Math.min(offset + 10, this.height - 20);
            if (!allowContinue)
            {
                var5.xPosition = this.width / 2 - 75;
                var5.displayString = StringTranslate.getInstance().translateKey("gui.done");
            }
            var5.drawButton(this.mc, par1, par2);
        }
    }
}
