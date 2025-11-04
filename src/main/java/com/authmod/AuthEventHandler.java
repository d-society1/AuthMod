package com.authmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = AuthMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AuthEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> authenticatedPlayers = new HashMap<>();
    private static final Map<String, String> playerPasswords = AuthData.getPlayerPasswords();
    private static final WeakHashMap<ServerPlayer, double[]> lastPosition = new WeakHashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AuthData.init();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AuthData.save();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String username = player.getGameProfile().getName().toLowerCase();
        LOGGER.info("=== Игрок зашёл: {} ===", username);

        authenticatedPlayers.put(username, false);
        lastPosition.put(player, new double[]{player.getX(), player.getY(), player.getZ()});

        fullSync(player, false);
        player.setGameMode(GameType.SURVIVAL);

        sendAuthMessage(player);
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String username = player.getGameProfile().getName().toLowerCase();
            authenticatedPlayers.remove(username);
            lastPosition.remove(player);
        }
    }

    // === БЛОКИРОВКА ДО АВТОРИЗАЦИИ ===
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (isAuthenticated(player)) return;

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
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
            sendReminder(player);
            // Принудительная синхронизация
            player.connection.send(new ClientboundContainerSetContentPacket(
                    player.inventoryMenu.containerId,
                    player.inventoryMenu.incrementStateId(),
                    player.getInventory().items,
                    ItemStack.EMPTY
            ));
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
            // Возвращаем предмет в инвентарь
            ItemStack stack = event.getEntity().getItem();
            if (!stack.isEmpty()) {
                player.getInventory().add(stack);
                player.connection.send(new ClientboundContainerSetContentPacket(
                        player.inventoryMenu.containerId,
                        player.inventoryMenu.incrementStateId(),
                        player.getInventory().items,
                        ItemStack.EMPTY
                ));
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

    // === УТИЛИТЫ ===
    public static boolean registerPlayer(String username, String password) {
        username = username.toLowerCase();
        if (AuthData.getPlayerPasswords().containsKey(username)) return false;

        AuthData.getPlayerPasswords().put(username, password);
        authenticatedPlayers.put(username, true);
        AuthData.save();

        ServerPlayer player = getPlayer(username);
        if (player != null) {
            fullSync(player, true);
        }
        return true;
    }

    public static boolean loginPlayer(String username, String password) {
        username = username.toLowerCase();
        String stored = AuthData.getPlayerPasswords().get(username);
        if (stored != null && stored.equals(password)) {
            authenticatedPlayers.put(username, true);

            ServerPlayer player = getPlayer(username);
            if (player != null) {
                fullSync(player, true);
                lastPosition.remove(player);
            }
            return true;
        }
        return false;
    }

    public static boolean isAuthenticated(ServerPlayer player) {
        return authenticatedPlayers.getOrDefault(player.getGameProfile().getName().toLowerCase(), false);
    }

    // === ПОЛНАЯ СИНХРОНИЗАЦИЯ ===
    public static void fullSync(ServerPlayer player, boolean auth) {
        var caps = player.getAbilities();
        caps.mayBuild = auth;
        caps.instabuild = false;
        caps.invulnerable = !auth;

        player.onUpdateAbilities();
        player.setGameMode(GameType.SURVIVAL);

        // Пакеты
        player.connection.send(new ClientboundPlayerAbilitiesPacket(caps));
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));

        // Полный инвентарь
        NonNullList<ItemStack> items = player.getInventory().items;
        player.connection.send(new ClientboundContainerSetContentPacket(
                player.inventoryMenu.containerId,
                player.inventoryMenu.incrementStateId(),
                items,
                ItemStack.EMPTY
        ));

        LOGGER.info("Полная синхронизация для {}: mayBuild = {}", player.getName().getString(), auth);
    }

    private static ServerPlayer getPlayer(String username) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(username);
    }

    private static void sendAuthMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendSystemMessage(Component.literal("§e§lАВТОРИЗАЦИЯ"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§eВведи:"));
        player.sendSystemMessage(Component.literal("   §a/login <пароль>  §7или"));
        player.sendSystemMessage(Component.literal("   §a/register <пароль>"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§7(минимум 4 символа)"));
        player.sendSystemMessage(Component.literal("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private static void sendReminder(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§cСначала авторизуйся!"));
    }
}