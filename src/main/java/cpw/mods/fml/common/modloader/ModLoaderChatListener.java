package cpw.mods.fml.common.modloader;

import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.*;
import cpw.mods.fml.common.network.IChatListener;

public class ModLoaderChatListener implements IChatListener
{

    private BaseModProxy mod;

    public ModLoaderChatListener(BaseModProxy mod)
    {
        this.mod = mod;
    }

    @Override
    public Packet3Chat serverChat(NetHandler handler, Packet3Chat message)
    {
        mod.serverChat((NetServerHandler)handler, message.message);
        return message;
    }

    @Override
    public Packet3Chat clientChat(NetHandler handler, Packet3Chat message)
    {
        mod.clientChat(message.message);
        return message;
    }

}
