package com.authmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Конфиг сервера (файл authmod-server.toml создаётся автоматически).
 * Значения кешируются на загрузке/перезагрузке конфига.
 */
@Mod.EventBusSubscriber(modid = AuthMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AuthConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue MIN_PASSWORD_LENGTH;
    private static final ForgeConfigSpec.IntValue MAX_LOGIN_ATTEMPTS;
    private static final ForgeConfigSpec.IntValue LOCKOUT_SECONDS;
    private static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;

    private static int minPasswordLength = 6;
    private static int maxLoginAttempts = 3;
    private static int lockoutSeconds = 60;
    private static String language = "auto";

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("AuthMod server configuration").push("auth");
        MIN_PASSWORD_LENGTH = builder.comment("Minimum password length for /register and password changes")
                .defineInRange("minPasswordLength", 6, 4, 64);
        MAX_LOGIN_ATTEMPTS = builder.comment("Failed /login attempts before a temporary lockout")
                .defineInRange("maxLoginAttempts", 3, 1, 100);
        LOCKOUT_SECONDS = builder.comment("Lockout duration in seconds after too many failed attempts")
                .defineInRange("lockoutSeconds", 60, 1, 3600);
        LANGUAGE = builder.comment("Message language: auto (client language), ru or en")
                .define("language", "auto");
        builder.pop();
        SPEC = builder.build();
    }

    private AuthConfig() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) cache();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) cache();
    }

    private static void cache() {
        minPasswordLength = MIN_PASSWORD_LENGTH.get();
        maxLoginAttempts = MAX_LOGIN_ATTEMPTS.get();
        lockoutSeconds = LOCKOUT_SECONDS.get();
        language = LANGUAGE.get();
    }

    public static int minPasswordLength() {
        return minPasswordLength;
    }

    public static int maxLoginAttempts() {
        return maxLoginAttempts;
    }

    public static int lockoutSeconds() {
        return lockoutSeconds;
    }

    public static String language() {
        return language;
    }
}
