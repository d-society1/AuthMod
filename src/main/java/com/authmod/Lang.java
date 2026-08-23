package com.authmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Мод серверный: lang-файлов на клиенте может не быть, поэтому сервер
 * переводит сообщение сам по языку клиента и отправляет готовый текст.
 */
public final class Lang {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();
    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();

    static {
        load("en_us");
        load("ru_ru");
    }

    private Lang() {
    }

    private static void load(String locale) {
        String path = "/assets/authmod/lang/" + locale + ".json";
        try (InputStream in = Lang.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.error("Lang file not found in jar: {}", path);
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                TRANSLATIONS.put(locale, GSON.fromJson(reader, MAP_TYPE));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load lang file {}: {}", path, e.toString());
        }
    }

    public static Component msg(ServerPlayer player, String key, Object... args) {
        return Component.literal(text(localeOf(player), key, args));
    }

    /** Для консоли: язык из конфига, либо русский. */
    public static Component consoleMsg(String key, Object... args) {
        String forced = forcedLocale();
        return Component.literal(text(forced != null ? forced : "ru_ru", key, args));
    }

    public static String text(String locale, String key, Object... args) {
        String template = lookup(locale, key);
        if (template == null) return key;
        return args.length == 0 ? template : String.format(template, args);
    }

    private static String lookup(String locale, String key) {
        Map<String, String> table = TRANSLATIONS.get(locale);
        if (table != null && table.containsKey(key)) return table.get(key);
        table = TRANSLATIONS.get("en_us");
        return table != null ? table.get(key) : null;
    }

    /** Язык игрока: принудительный из конфига, иначе язык клиента. */
    public static String localeOf(ServerPlayer player) {
        String forced = forcedLocale();
        if (forced != null) return forced;
        String lang = player.getLanguage();
        if (lang != null) {
            lang = lang.toLowerCase(Locale.ROOT);
            if (TRANSLATIONS.containsKey(lang)) return lang;
        }
        return "ru_ru";
    }

    /**
     * Язык приветствия в момент входа: язык клиента серверу ещё не успел
     * дойти, поэтому до его получения используем язык конфига (или русский).
     */
    public static String joinLocale(ServerPlayer player) {
        String forced = forcedLocale();
        return forced != null ? forced : "ru_ru";
    }

    private static String forcedLocale() {
        String forced = AuthConfig.language().toLowerCase(Locale.ROOT);
        if ("ru".equals(forced) || "ru_ru".equals(forced)) return "ru_ru";
        if ("en".equals(forced) || "en_us".equals(forced)) return "en_us";
        return null; // "auto" — определять по клиенту
    }
}
