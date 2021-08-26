/*
 * The FML Forge Mod Loader suite.
 * Copyright (C) 2012 cpw
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cpw.mods.fml.relauncher;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

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
        
        return "https://skins.minecraft.net/MinecraftSkins/" + profile.getName() + ".png";
    }
}
