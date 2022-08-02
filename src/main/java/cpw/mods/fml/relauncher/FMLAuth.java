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

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FMLAuth {
    public static FMLAuth instance;
    
    private final Map<String, Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>> textures = new HashMap<>();
    private final GameProfileRepository profileRepository;
    private final MinecraftSessionService sessionService;
    
    public FMLAuth(GameProfileRepository profileRepository, MinecraftSessionService sessionService) {
        this.profileRepository = profileRepository;
        this.sessionService = sessionService;
    }
    
    public String getTextureURL(String username, MinecraftProfileTexture.Type type) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = this.textures.computeIfAbsent(username, this::getProfileTextures);
        if (textures.containsKey(type)) return textures.get(type).getUrl();

        return "https://skins.minecraft.net/MinecraftSkins/" + username + ".png";
    }
    
    private Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getProfileTextures(String username) {
        GameProfile profile = getProfile(username);
        if (profile != null) {
            this.sessionService.fillProfileProperties(profile, false);
            return this.sessionService.getTextures(profile, false);
        }
        return Collections.emptyMap();
    }
    
    private GameProfile getProfile(String username) {
        FMLProfileLookup callback = new FMLProfileLookup();
        this.profileRepository.findProfilesByNames(new String[]{ username }, Agent.MINECRAFT, callback);
        return callback.getProfile();
    }
    
    private static class FMLProfileLookup implements ProfileLookupCallback {
        private GameProfile profile;
        
        @Override
        public void onProfileLookupSucceeded(GameProfile profile) {
            this.profile = profile;
        }

        @Override
        public void onProfileLookupFailed(GameProfile profile, Exception exception) {

        }

        public GameProfile getProfile() {
            return this.profile;
        }
    }
}
