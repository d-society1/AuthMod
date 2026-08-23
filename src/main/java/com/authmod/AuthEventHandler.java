package com.authmod;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = AuthMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AuthEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> authenticatedPlayers = new HashMap<>();
    private static final WeakHashMap<ServerPlayer, double[]> lastPosition = new WeakHashMap<>();
    private static final Map<UUID, GameType> previousGameMode = new HashMap<>();
    private static final Map<UUID, String> greetedLocale = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AuthData.init();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AuthData.save();
    }

    // === ВХОД / ВЫХОД ===

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        LOGGER.info("[AuthMod] Player joined: {}", username);

        authenticatedPlayers.put(username, false);
        LoginLimiter.reset(username);
        lastPosition.put(player, new double[]{player.getX(), player.getY(), player.getZ()});

        lockPlayer(player);
        sendAuthMessage(player, Lang.joinLocale(player));
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
            authenticatedPlayers.remove(username);
            LoginLimiter.reset(username);
            lastPosition.remove(player);
            previousGameMode.remove(player.getUUID());
            greetedLocale.remove(player.getUUID());
        }
    }

    // === БЛОКИРОВКА ДО АВТОРИЗАЦИИ ===

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (isAuthenticated(player)) return;

        // Замок позиции
        double[] last = lastPosition.get(player);
        if (last != null) {
            double dist = player.distanceToSqr(last[0], last[1], last[2]);
            if (dist > 0.01) {
                player.teleportTo(last[0], last[1], last[2]);
                sendReminder(player);
            } else {
                lastPosition.put(player, new double[]{player.getX(), player.getY(), player.getZ()});
            }
        }

        // Когда от клиента дошёл его язык — повторяем приветствие на нём
        if (player.tickCount >= 40 && player.tickCount % 40 == 0) {
            String locale = Lang.localeOf(player);
            if (!locale.equals(greetedLocale.get(player.getUUID()))) {
                sendAuthMessage(player, locale);
            }
        }
    }

    /** До авторизации доступны только /login и /register. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ServerPlayer player = event.getParseResults().getContext().getSource().getPlayer();
        if (player == null || isAuthenticated(player)) return;

        String command = event.getParseResults().getReader().getString().trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        String head = command.split("\\s+", 2)[0];
        if (head.equals("login") || head.equals("register")) return;

        event.setCanceled(true);
        player.sendSystemMessage(Lang.msg(player, "authmod.command.blocked"));
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!isAuthenticated(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Lang.msg(player, "authmod.chat.blocked"));
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
        }
    }

    // === ДРОП ПРЕДМЕТА — ВОЗВРАЩАЕМ В ИНВЕНТАРЬ ===

    @SubscribeEvent
    public static void onItemDrop(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            ItemStack stack = event.getEntity().getItem();
            if (!stack.isEmpty()) {
                player.getInventory().add(stack);
                player.inventoryMenu.broadcastChanges();
            }
        }
    }

    // === ПОДБОР ПРЕДМЕТА — БЛОКИРУЕМ ===

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
        }
    }

    // === РЕГИСТРАЦИЯ / ВХОД ===

    public static boolean registerPlayer(String username, String password) {
        username = username.toLowerCase(Locale.ROOT);
        if (AuthData.isRegistered(username)) return false;

        AuthData.setPlayerPassword(username, password);
        authenticatedPlayers.put(username, true);
        AuthData.save();

        ServerPlayer player = getPlayer(username);
        if (player != null) {
            restorePlayer(player);
        }
        return true;
    }

    public static boolean loginPlayer(String username, String password) {
        username = username.toLowerCase(Locale.ROOT);
        if (AuthData.verifyPassword(username, password)) {
            authenticatedPlayers.put(username, true);

            ServerPlayer player = getPlayer(username);
            if (player != null) {
                restorePlayer(player);
            }
            return true;
        }
        return false;
    }

    /** Повторная блокировка онлайн-игрока (например, после сброса пароля админом). */
    public static void forceRelock(ServerPlayer player) {
        String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        authenticatedPlayers.put(username, false);
        LoginLimiter.reset(username);
        lastPosition.put(player, new double[]{player.getX(), player.getY(), player.getZ()});
        lockPlayer(player);
        sendAuthMessage(player, Lang.localeOf(player));
    }

    public static boolean isAuthenticated(ServerPlayer player) {
        return authenticatedPlayers.getOrDefault(player.getGameProfile().getName().toLowerCase(Locale.ROOT), false);
    }

    // === РЕЖИМ НАБЛЮДАТЕЛЯ ДО АВТОРИЗАЦИИ ===

    private static void lockPlayer(ServerPlayer player) {
        GameType current = player.gameMode.getGameModeForPlayer();
        previousGameMode.put(player.getUUID(), current == GameType.SPECTATOR ? GameType.SURVIVAL : current);
        player.setGameMode(GameType.SPECTATOR);
    }

    private static void restorePlayer(ServerPlayer player) {
        GameType mode = previousGameMode.remove(player.getUUID());
        player.setGameMode(mode == null ? GameType.SURVIVAL : mode);
        player.inventoryMenu.broadcastChanges();
        lastPosition.remove(player);
    }

    private static ServerPlayer getPlayer(String username) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(username);
    }

    private static void sendAuthMessage(ServerPlayer player, String locale) {
        greetedLocale.put(player.getUUID(), locale);
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.title")));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.header")));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.choose")));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.login_hint")));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.register_hint")));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.min_length", AuthConfig.minPasswordLength())));
        player.sendSystemMessage(Component.literal(Lang.text(locale, "authmod.welcome.title")));
    }

    private static void sendReminder(ServerPlayer player) {
        player.sendSystemMessage(Lang.msg(player, "authmod.locked.reminder"));
    }
}
