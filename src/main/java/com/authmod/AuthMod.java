package com.authmod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("authmod")
public class AuthMod {
    public static final String MODID = "authmod";

    public AuthMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, AuthConfig.SPEC);
    }
}
