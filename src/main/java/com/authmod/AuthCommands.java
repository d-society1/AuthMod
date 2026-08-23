package com.authmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

public class AuthCommands {

    /** Автодополнение по всем зарегистрированным именам (включая офлайн). */
    private static final SuggestionProvider<CommandSourceStack> REGISTERED_PLAYERS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String name : AuthData.registeredPlayers()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("register")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Lang.msg(player, "authmod.usage.register"));
                    return 0;
                })
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);

                            if (password.length() < AuthConfig.minPasswordLength()) {
                                player.sendSystemMessage(Lang.msg(player, "authmod.password.too_short", AuthConfig.minPasswordLength()));
                                return 0;
                            }

                            if (AuthData.isRegistered(username)) {
                                player.sendSystemMessage(Lang.msg(player, "authmod.register.already"));
                                player.sendSystemMessage(Lang.msg(player, "authmod.register.hint_login"));
                                return 0;
                            }

                            AuthEventHandler.registerPlayer(username, password);
                            player.sendSystemMessage(Lang.msg(player, "authmod.register.success"));
                            player.sendSystemMessage(Lang.msg(player, "authmod.register.play"));
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("login")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Lang.msg(player, "authmod.usage.login"));
                    return 0;
                })
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);

                            if (!AuthData.isRegistered(username)) {
                                player.sendSystemMessage(Lang.msg(player, "authmod.login.not_registered"));
                                player.sendSystemMessage(Lang.msg(player, "authmod.login.hint_register"));
                                return 0;
                            }

                            if (LoginLimiter.isLocked(username)) {
                                player.sendSystemMessage(Lang.msg(player, "authmod.limiter.too_many_attempts"));
                                player.sendSystemMessage(Lang.msg(player, "authmod.limiter.cooldown", LoginLimiter.remainingSeconds(username)));
                                return 0;
                            }

                            if (AuthEventHandler.loginPlayer(username, password)) {
                                LoginLimiter.reset(username);
                                player.sendSystemMessage(Lang.msg(player, "authmod.login.success"));
                                player.sendSystemMessage(Lang.msg(player, "authmod.login.welcome"));
                                return 1;
                            } else {
                                LoginLimiter.recordFailure(username);
                                if (LoginLimiter.isLocked(username)) {
                                    player.sendSystemMessage(Lang.msg(player, "authmod.limiter.too_many_attempts"));
                                    player.sendSystemMessage(Lang.msg(player, "authmod.limiter.cooldown", LoginLimiter.remainingSeconds(username)));
                                } else {
                                    player.sendSystemMessage(Lang.msg(player, "authmod.login.wrong_password"));
                                }
                                return 0;
                            }
                        })
                )
        );

        // Смена собственного пароля игроком
        dispatcher.register(Commands.literal("changepassword")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Lang.msg(player, "authmod.usage.changepassword"));
                    return 0;
                })
                .then(Commands.argument("oldPassword", StringArgumentType.word())
                        .then(Commands.argument("newPassword", StringArgumentType.word())
                                .executes(AuthCommands::changeOwnPassword)
                        )
                )
        );

        // Административные команды: только операторы (permission level 2)
        dispatcher.register(Commands.literal("authmod")
                .executes(context -> {
                    reply(context.getSource(), "authmod.usage.authmod");
                    return 0;
                })
                .then(Commands.literal("changepassword")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(REGISTERED_PLAYERS)
                                .then(Commands.argument("newPassword", StringArgumentType.word())
                                        .executes(AuthCommands::adminChangePassword)
                                )
                        )
                )
                .then(Commands.literal("resetpassword")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(REGISTERED_PLAYERS)
                                .executes(AuthCommands::adminResetPassword)
                        )
                )
        );
    }

    private static int changeOwnPassword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String username = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        String oldPassword = StringArgumentType.getString(context, "oldPassword");
        String newPassword = StringArgumentType.getString(context, "newPassword");

        if (!AuthData.verifyPassword(username, oldPassword)) {
            player.sendSystemMessage(Lang.msg(player, "authmod.changepassword.wrong_old"));
            return 0;
        }
        if (newPassword.length() < AuthConfig.minPasswordLength()) {
            player.sendSystemMessage(Lang.msg(player, "authmod.password.too_short", AuthConfig.minPasswordLength()));
            return 0;
        }
        AuthData.setPlayerPassword(username, newPassword);
        AuthData.save();
        player.sendSystemMessage(Lang.msg(player, "authmod.changepassword.success"));
        return 1;
    }

    /** /authmod changepassword <player> <newPassword> — работает и с офлайн-игроками. */
    private static int adminChangePassword(CommandContext<CommandSourceStack> context) {
        String target = StringArgumentType.getString(context, "player").toLowerCase(Locale.ROOT);
        String newPassword = StringArgumentType.getString(context, "newPassword");

        if (!AuthData.isRegistered(target)) {
            reply(context.getSource(), "authmod.admin.player_not_registered", target);
            return 0;
        }
        if (newPassword.length() < AuthConfig.minPasswordLength()) {
            reply(context.getSource(), "authmod.password.too_short", AuthConfig.minPasswordLength());
            return 0;
        }
        AuthData.setPlayerPassword(target, newPassword);
        AuthData.save();
        reply(context.getSource(), "authmod.admin.changepassword.success", target);

        ServerPlayer targetPlayer = findOnline(target);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Lang.msg(targetPlayer, "authmod.admin.changepassword.notice"));
        }
        return 1;
    }

    /** /authmod resetpassword <player> — удаляет регистрацию, игрок заново делает /register. */
    private static int adminResetPassword(CommandContext<CommandSourceStack> context) {
        String target = StringArgumentType.getString(context, "player").toLowerCase(Locale.ROOT);

        if (!AuthData.isRegistered(target)) {
            reply(context.getSource(), "authmod.admin.player_not_registered", target);
            return 0;
        }
        AuthData.removePlayer(target);
        AuthData.save();
        reply(context.getSource(), "authmod.admin.resetpassword.success", target);

        ServerPlayer targetPlayer = findOnline(target);
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Lang.msg(targetPlayer, "authmod.admin.resetpassword.notice"));
            AuthEventHandler.forceRelock(targetPlayer);
        }
        return 1;
    }

    /** Ответ игроку его языком, либо в консоль языком сервера. */
    private static void reply(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.sendSystemMessage(Lang.msg(player, key, args));
        } else {
            source.sendSuccess(() -> Lang.consoleMsg(key, args), false);
        }
    }

    private static ServerPlayer findOnline(String username) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(username);
    }
}
