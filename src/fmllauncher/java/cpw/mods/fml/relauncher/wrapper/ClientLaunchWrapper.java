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

package cpw.mods.fml.relauncher.wrapper;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.UserAuthentication;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import cpw.mods.fml.relauncher.FMLAuth;
import cpw.mods.fml.relauncher.FMLRelauncher;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import org.lwjgl.Sys;

import java.io.File;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class ClientLaunchWrapper {
    
    public static void main(String[] args) {
        FMLRelauncher.handleClientRelaunch(processArgs(args));
    }

    /**
     * Basic implementation of Mojang's 'Yggdrasil' login system, purely intended as a dev time bare bones login.
     * Login errors are not handled.
     * Do not use this unless you know what you are doing and must use it to debug things REQUIRING authentication.
     * Forge is not responsible for any auth information passed in, saved to logs, run configs, etc...
     * BE CAREFUL WITH YOUR LOGIN INFO
     *
     * <p>Javadoc Credit: MinecraftForge</p>
     */
    public static FMLArgs processArgs(String[] args) {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts("extractResources");
        Stream.of("version", "assetIndex", "gameDir", "accessToken", "userProperties", "uuid")
                .map(parser::accepts)
                .forEach(OptionSpecBuilder::withRequiredArg);
        OptionSpec<String> username = parser.accepts("username").withRequiredArg();
        OptionSpec<String> password = parser.accepts("password").withRequiredArg();
        OptionSpec<String> assetsDirOpt = parser.accepts("assetsDir").withRequiredArg();
        OptionSpec<String> nonOptions = parser.nonOptions();
        OptionSet optionSet = parser.parse(args);
        File assetsDir = new File(assetsDirOpt.value(optionSet));
        boolean extractResources = optionSet.has("extractResources");
        List<String> values = new ArrayList<>(nonOptions.values(optionSet));

        YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY, "1");
        GameProfileRepository profileRepository = authService.createProfileRepository();
        MinecraftSessionService sessionService = authService.createMinecraftSessionService();
        FMLAuth.instance = new FMLAuth(profileRepository, sessionService);
        
        if (optionSet.has(username) && optionSet.has(password)) {
            String usernameValue = username.value(optionSet);
            UserAuthentication auth = authService.createUserAuthentication(Agent.MINECRAFT);
            auth.setUsername(usernameValue);
            auth.setPassword(password.value(optionSet));

            try {
                auth.logIn();
            } catch (AuthenticationException e) {
                throw new RuntimeException("Login failed!", e);
            }

            GameProfile profile = auth.getSelectedProfile();
            UUID uuidValue = profile.getId();
            String uuidString = uuidValue.toString().replace("-", "");
            String[] newArgs = Stream.concat(Stream.of(profile.getName(), "token:" + auth.getAuthenticatedToken() + ":" + uuidString), values.stream())
                    .toArray(String[]::new); 
            return new FMLArgs(newArgs, assetsDir, extractResources);
        }

        if (values.size() < 1) values.add("Player" + getSystemTime() % 1000L);
        
        return new FMLArgs(values.toArray(new String[0]), assetsDir, extractResources);
    }
    
    private static long getSystemTime() {
        return Sys.getTime() * 1000L / Sys.getTimerResolution();
    }
}
