package com.authmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("authmod")
public class AuthMod {
    public static final String MODID = "authmod";

    public AuthMod() {
        MinecraftForge.EVENT_BUS.register(AuthEventHandler.class);
        MinecraftForge.EVENT_BUS.register(CommandEventHandler.class);
    }
}