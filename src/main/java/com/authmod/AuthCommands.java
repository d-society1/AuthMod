package com.authmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AuthCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            String username = player.getGameProfile().getName().toLowerCase();

                            if (password.length() < 4) {
                                player.sendSystemMessage(Component.literal("§cПароль ≥ 4 символа!"));
                                return 0;
                            }

                            if (AuthData.getPlayerPasswords().containsKey(username)) {
                                player.sendSystemMessage(Component.literal("§cТы уже зарегистрирован!"));
                                player.sendSystemMessage(Component.literal("§e/login <пароль>"));
                                return 0;
                            }

                            AuthEventHandler.registerPlayer(username, password);
                            player.sendSystemMessage(Component.literal("§aРегистрация успешна!"));
                            player.sendSystemMessage(Component.literal("§aИграй!"));
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String password = StringArgumentType.getString(context, "password");
                            String username = player.getGameProfile().getName().toLowerCase();

                            if (!AuthData.getPlayerPasswords().containsKey(username)) {
                                player.sendSystemMessage(Component.literal("§cТы не зарегистрирован!"));
                                player.sendSystemMessage(Component.literal("§e/register <пароль>"));
                                return 0;
                            }

                            if (AuthEventHandler.loginPlayer(username, password)) {
                                player.sendSystemMessage(Component.literal("§aВход успешен!"));
                                player.sendSystemMessage(Component.literal("§aДобро пожаловать!"));
                                return 1;
                            } else {
                                player.sendSystemMessage(Component.literal("§cНеверный пароль!"));
                                return 0;
                            }
                        })
                )
        );
    }
}