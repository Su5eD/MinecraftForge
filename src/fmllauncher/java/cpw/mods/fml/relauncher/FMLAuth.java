package cpw.mods.fml.relauncher;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.GameProfile;

import java.util.Map;

public class FMLAuth {
    public static FMLAuth instance;
    
    private final GameProfile profile;
    private final MinecraftSessionService sessionService;
    
    public FMLAuth(GameProfile profile, MinecraftSessionService sessionService) {
        this.profile = profile;
        this.sessionService = sessionService;
        
        sessionService.fillProfileProperties(profile, false);
    }
    
    public String getTextureURL(MinecraftProfileTexture.Type type) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = sessionService.getTextures(profile, false);
        if (textures.containsKey(type)) return textures.get(type).getUrl();
        
        return "unknown";
    }
}
