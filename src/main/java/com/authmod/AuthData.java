package com.authmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AuthData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();
    private static final Map<String, String> playerPasswords = new HashMap<>();
    private static File dataFile;

    private AuthData() {
    }

    public static void init() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            dataFile = new File(server.getServerDirectory(), "authmod_data.json");
            load();
        }
    }

    public static void load() {
        if (dataFile == null || !dataFile.exists()) return;
        try {
            Map<String, String> loaded = GSON.fromJson(
                    Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8), MAP_TYPE);
            if (loaded != null) {
                playerPasswords.clear();
                playerPasswords.putAll(loaded);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load auth data from {}: {}", dataFile, e.toString());
        }
    }

    public static void save() {
        if (dataFile == null) return;
        File tmp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            Files.writeString(tmp.toPath(), GSON.toJson(playerPasswords), StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to save auth data to {}: {}", dataFile, e.toString());
        }
    }

    public static boolean isRegistered(String username) {
        return playerPasswords.containsKey(username.toLowerCase());
    }

    public static String getStoredPassword(String username) {
        return playerPasswords.get(username.toLowerCase());
    }

    public static void setPlayerPassword(String username, String password) {
        playerPasswords.put(username.toLowerCase(), PasswordHasher.hash(password));
    }

    /**
     * Проверяет пароль; старые (незахешированные) записи автоматически
     * переводятся в новый формат при первом успешном входе.
     */
    public static boolean verifyPassword(String username, String password) {
        String stored = getStoredPassword(username);
        if (stored == null) return false;
        if (PasswordHasher.isLegacy(stored)) {
            if (!stored.equals(password)) return false;
            setPlayerPassword(username, password);
            save();
            return true;
        }
        return PasswordHasher.verify(password, stored);
    }

    public static boolean removePlayer(String username) {
        return playerPasswords.remove(username.toLowerCase()) != null;
    }

    /** Все зарегистрированные имена (в нижнем регистре) — для автодополнения. */
    public static Set<String> registeredPlayers() {
        return playerPasswords.keySet();
    }
}
