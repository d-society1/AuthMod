package com.authmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AuthData {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();
    private static final Map<String, String> playerPasswords = new HashMap<>();
    private static File dataFile;

    public static void init() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            dataFile = new File(server.getServerDirectory(), "authmod_data.json");
            load();
        }
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        if (dataFile != null && dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) playerPasswords.putAll(loaded);
            } catch (IOException e) {
            }
        }
    }

    public static void save() {
        if (dataFile != null) {
            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(playerPasswords, writer);
            } catch (IOException e) {
            }
        }
    }

    public static Map<String, String> getPlayerPasswords() {
        return playerPasswords;
    }
}