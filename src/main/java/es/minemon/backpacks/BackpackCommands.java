package es.minemon.backpacks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public class BackpackCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        // Obtener comandos desde configuración
        String mainCommand = ConfigManager.getConfig().mainCommand;
        String playerCommand = ConfigManager.getConfig().playerCommand;

        // Comando principal para jugadores
        dispatcher.register(CommandManager.literal(playerCommand)
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.canUseBackpacks(player);
                })
                .executes(BackpackCommands::openBackpackMenu));

        // Comandos administrativos (CONSOLA + JUGADORES con permisos)
        dispatcher.register(CommandManager.literal(mainCommand)
                .requires(source -> {
                    // Permitir desde consola (source.getEntity() == null)
                    if (source.getEntity() == null) {
                        return true; // Consola siempre tiene permisos
                    }
                    // Para jugadores, verificar permisos
                    if (source.getEntity() instanceof ServerPlayerEntity player) {
                        return LuckPermsManager.isAdmin(player);
                    }
                    return false;
                })
                .then(CommandManager.literal("give")
                        .requires(source -> {
                            // Consola siempre puede dar mochilas
                            if (source.getEntity() == null) return true;
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canGiveBackpacks(player);
                            }
                            return false;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .then(CommandManager.argument("slots", IntegerArgumentType.integer(9, 54))
                                                .executes(BackpackCommands::giveBackpack)))))
                .then(CommandManager.literal("remove")
                        .requires(source -> {
                            // Consola siempre puede remover mochilas
                            if (source.getEntity() == null) return true;
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canRemoveBackpacks(player);
                            }
                            return false;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .executes(BackpackCommands::removeBackpack))))
                .then(CommandManager.literal("rename")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canAdminRename(player);
                            }
                            return false;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .then(CommandManager.argument("new_name", StringArgumentType.greedyString())
                                                .executes(BackpackCommands::adminRenameBackpack)))))
                .then(CommandManager.literal("info")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canViewOthers(player);
                            }
                            return false;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BackpackCommands::showPlayerInfo)))
                .then(CommandManager.literal("sync")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canSync(player);
                            }
                            return false;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BackpackCommands::syncPlayerData)))
                .then(CommandManager.literal("admin")
                        .requires(source -> {
                            // Solo jugadores para vista admin (requiere GUI)
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canViewOthers(player);
                        })
                        .then(CommandManager.literal("view")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(BackpackCommands::openAdminView))))
                // ELIMINADO: Comandos de backup
                .then(CommandManager.literal("permissions")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canModifyConfig(player);
                            }
                            return false;
                        })
                        .then(CommandManager.literal("info")
                                .executes(BackpackCommands::showPermissionInfo))
                        .then(CommandManager.literal("reload")
                                .executes(BackpackCommands::reloadPermissions))
                        .then(CommandManager.literal("check")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(BackpackCommands::checkPlayerPermissions))))
                // NUEVO: Comando para listar todos los jugadores con mochilas (útil para consola)
                .then(CommandManager.literal("list")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canViewOthers(player);
                            }
                            return false;
                        })
                        .executes(BackpackCommands::listAllPlayers))
                // NUEVO: Estadísticas del servidor
                .then(CommandManager.literal("stats")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canViewOthers(player);
                            }
                            return false;
                        })
                        .executes(BackpackCommands::showServerStats))
                // NUEVO: Comando para forzar guardado
                .then(CommandManager.literal("force-save")
                        .requires(source -> {
                            if (source.getEntity() == null) return true; // Consola
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                return LuckPermsManager.canModifyConfig(player);
                            }
                            return false;
                        })
                        .executes(BackpackCommands::forceSaveAll)));

        // Comando para jugadores - Renombrar sus propias mochilas
        dispatcher.register(CommandManager.literal("rename-backpack")
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.canRename(player);
                })
                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                        .then(CommandManager.argument("new_name", StringArgumentType.greedyString())
                                .executes(BackpackCommands::playerRenameBackpack))));
    }

    private static int openBackpackMenu(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            sendConsoleMessage(context.getSource(), "errorPlayerOnly");
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canUseBackpacks(player)) {
            LuckPermsManager.sendNoPermissionMessage(player, LuckPermsManager.USE_PERMISSION);
            return 0;
        }

        BackpackManager.loadPlayerDataAsync(player.getUuid()).thenRun(() -> {
            BackpackMenuScreenHandler.openBackpackMenu(player);
        }).exceptionally(throwable -> {
            LanguageManager.sendMessage(player, "errorLoadingData");
            return null;
        });

        return 1;
    }

    private static int giveBackpack(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String name = StringArgumentType.getString(context, "name");
            int slots = IntegerArgumentType.getInteger(context, "slots");

            // Validar slots
            if (slots % 9 != 0) {
                sendFeedback(context.getSource(), "commandSlotCountInvalid", false);
                return 0;
            }

            // Generar ID automáticamente
            int newId = BackpackManager.getNextAvailableId(targetPlayer.getUuid());

            BackpackManager.addBackpack(targetPlayer.getUuid(), newId, name, slots);

            // Mensaje diferente para consola vs jugador
            if (isConsole(context.getSource())) {
                String consoleMessage = String.format("[CONSOLE] Backpack '%s' (ID: %d) given to %s with %d slots",
                        name, newId, targetPlayer.getName().getString(), slots);
                context.getSource().sendFeedback(() -> Text.literal(consoleMessage), true);

                // Log adicional para consola
                BackpacksMod.LOGGER.info("Console gave backpack '{}' (ID: {}) to {} with {} slots",
                        name, newId, targetPlayer.getName().getString(), slots);
            } else {
                String adminMessage = String.format("§aBackpack '%s' (ID: %d) given to %s with %d slots",
                        name, newId, targetPlayer.getName().getString(), slots);
                context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);
            }

            // Notificar al jugador objetivo
            LanguageManager.sendMessage(targetPlayer, "backpackReceived",
                    String.format("%s (ID: %d, Slots: %d)", name, newId, slots));

            // Log para auditoría
            logGiveBackpack(context.getSource(), targetPlayer.getName().getString(), name, newId, slots);

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int removeBackpack(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            int id = IntegerArgumentType.getInteger(context, "id");

            MongoBackpackManager.PlayerBackpacks backpacks = BackpackManager.getPlayerBackpacks(targetPlayer.getUuid());

            if (!backpacks.hasBackpack(id)) {
                sendFeedback(context.getSource(), "errorBackpackNotFound", false);
                return 0;
            }

            // Obtener información de la mochila antes de eliminarla
            MongoBackpackManager.BackpackData backpackData = backpacks.getBackpack(id);
            String backpackName = backpackData != null ? backpackData.getName() : "Unknown";

            // ELIMINADO: Crear backup antes de eliminar

            BackpackManager.removeBackpack(targetPlayer.getUuid(), id);

            // Mensaje diferente para consola vs jugador
            if (isConsole(context.getSource())) {
                String consoleMessage = String.format("[CONSOLE] Backpack '%s' (ID: %d) removed from %s",
                        backpackName, id, targetPlayer.getName().getString());
                context.getSource().sendFeedback(() -> Text.literal(consoleMessage), true);

                // Log adicional para consola
                BackpacksMod.LOGGER.info("Console removed backpack '{}' (ID: {}) from {}",
                        backpackName, id, targetPlayer.getName().getString());
            } else {
                String adminMessage = String.format("§aBackpack '%s' (ID: %d) removed from %s",
                        backpackName, id, targetPlayer.getName().getString());
                context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);
            }

            // Notificar al jugador objetivo
            LanguageManager.sendMessage(targetPlayer, "backpackLost", id);

            // Log para auditoría
            logRemoveBackpack(context.getSource(), targetPlayer.getName().getString(), backpackName, id);

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int adminRenameBackpack(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            int id = IntegerArgumentType.getInteger(context, "id");
            String newName = StringArgumentType.getString(context, "new_name");

            // Validar nombre
            if (newName.trim().isEmpty()) {
                sendFeedback(context.getSource(), "commandNameEmpty", false);
                return 0;
            }

            if (newName.length() > 50) {
                sendFeedback(context.getSource(), "commandNameTooLong", false);
                return 0;
            }

            // Verificar que la mochila existe
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayer.getUuid(), id);
            if (backpack == null) {
                sendFeedback(context.getSource(), "errorBackpackNotFound", false);
                return 0;
            }

            String oldName = backpack.getName();
            boolean success = BackpackManager.renameBackpack(targetPlayer.getUuid(), id, newName.trim());

            if (success) {
                // Mensaje diferente para consola vs jugador
                if (isConsole(context.getSource())) {
                    String consoleMessage = String.format("[CONSOLE] Backpack ID %d of %s renamed from '%s' to '%s'",
                            id, targetPlayer.getName().getString(), oldName, newName.trim());
                    context.getSource().sendFeedback(() -> Text.literal(consoleMessage), true);

                    // Log adicional para consola
                    BackpacksMod.LOGGER.info("Console renamed backpack ID {} of {} from '{}' to '{}'",
                            id, targetPlayer.getName().getString(), oldName, newName.trim());
                } else {
                    String adminMessage = String.format("§aBackpack ID %d of %s renamed to: §f%s",
                            id, targetPlayer.getName().getString(), newName.trim());
                    context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);
                }

                // Notificar al jugador objetivo
                String playerMessage = String.format("§aAn administrator renamed your backpack ID %d to: §f%s",
                        id, newName.trim());
                targetPlayer.sendMessage(Text.literal(playerMessage), false);

                BackpackManager.forcePlayerSave(targetPlayer.getUuid());

                // Log para auditoría
                logRenameBackpack(context.getSource(), targetPlayer.getName().getString(), id, oldName, newName.trim());
            } else {
                sendFeedback(context.getSource(), "errorRenamingBackpack", false);
            }

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int openAdminView(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            sendConsoleMessage(context.getSource(), "errorPlayerOnly");
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canViewOthers(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_VIEW_PERMISSION);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            BackpackManager.loadPlayerDataAsync(targetPlayer.getUuid()).thenRun(() -> {
                BackpackAdminViewScreenHandler.openAdminView(admin, targetPlayer);
            }).exceptionally(throwable -> {
                admin.sendMessage(Text.literal("§cError loading player data"), false);
                return null;
            });

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int playerRenameBackpack(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            sendConsoleMessage(context.getSource(), "errorPlayerOnly");
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canRename(player)) {
            LuckPermsManager.sendNoPermissionMessage(player, LuckPermsManager.RENAME_PERMISSION);
            return 0;
        }

        try {
            int id = IntegerArgumentType.getInteger(context, "id");
            String newName = StringArgumentType.getString(context, "new_name");

            // Validar configuración
            if (!ConfigManager.getConfig().allowBackpackRename) {
                player.sendMessage(Text.literal("§cBackpack renaming is disabled"), false);
                return 0;
            }

            // Validar nombre
            if (newName.trim().isEmpty()) {
                LanguageManager.sendMessage(player, "nameCannotBeEmpty");
                return 0;
            }

            if (newName.length() > 50) {
                LanguageManager.sendMessage(player, "nameTooLong");
                return 0;
            }

            // Verificar que la mochila existe y pertenece al jugador
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), id);
            if (backpack == null) {
                LanguageManager.sendMessage(player, "errorBackpackNotFound");
                return 0;
            }

            boolean success = BackpackManager.renameBackpack(player.getUuid(), id, newName.trim());

            if (success) {
                LanguageManager.sendMessage(player, "backpackSuccessMessage", id, newName.trim());
                BackpackManager.forcePlayerSave(player.getUuid());
            } else {
                LanguageManager.sendMessage(player, "errorRenamingBackpack");
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int showPlayerInfo(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            BackpackManager.BackpackStats stats = BackpackManager.getPlayerStats(targetPlayer.getUuid());

            StringBuilder message = new StringBuilder();

            if (isConsole(context.getSource())) {
                // Formato para consola (sin colores)
                message.append("=== ").append(targetPlayer.getName().getString()).append(" Statistics ===\n");
                message.append("Total backpacks: ").append(stats.getTotalBackpacks()).append("\n");
                message.append("Items stored: ").append(stats.getTotalItems()).append("\n");
                message.append("Empty slots: ").append(stats.getEmptySlots()).append("\n");
                message.append("Total slots: ").append(stats.getTotalSlots()).append("\n");
                message.append("Space usage: ").append(String.format("%.1f", stats.getUsagePercentage())).append("%");
            } else {
                // Formato con colores para jugadores
                message.append("§6=== ").append(targetPlayer.getName().getString()).append(" Statistics ===\n");
                message.append("§eTotal backpacks: §a").append(stats.getTotalBackpacks()).append("\n");
                message.append("§eItems stored: §a").append(stats.getTotalItems()).append("\n");
                message.append("§eEmpty slots: §a").append(stats.getEmptySlots()).append("\n");
                message.append("§eTotal slots: §a").append(stats.getTotalSlots()).append("\n");
                message.append("§eSpace usage: §a").append(String.format("%.1f", stats.getUsagePercentage())).append("%");
            }

            context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    private static int syncPlayerData(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            String syncMessage = isConsole(context.getSource()) ?
                    "[CONSOLE] Synchronizing data for " + targetPlayer.getName().getString() + "..." :
                    "§eSynchronizing data...";

            context.getSource().sendFeedback(() -> Text.literal(syncMessage), false);

            BackpackManager.syncPlayerDataFromDatabase(targetPlayer.getUuid()).thenRun(() -> {
                // Verificar integridad después de la sincronización
                boolean integrityCheck = BackpackManager.verifyPlayerDataIntegrity(targetPlayer.getUuid());

                MongoBackpackManager.PlayerBackpacks backpacks = BackpackManager.getPlayerBackpacks(targetPlayer.getUuid());
                int backpackCount = backpacks.getAllBackpacks().size();

                String resultMessage;
                if (isConsole(context.getSource())) {
                    resultMessage = String.format("[CONSOLE] Data synchronized for %s - %d backpacks, integrity: %s",
                            targetPlayer.getName().getString(), backpackCount,
                            (integrityCheck ? "OK" : "ERROR"));
                } else {
                    resultMessage = String.format("§aData synchronized for %s - %d backpacks, integrity: %s",
                            targetPlayer.getName().getString(), backpackCount,
                            (integrityCheck ? "§aOK" : "§cERROR") + "§a");
                }

                context.getSource().sendFeedback(() -> Text.literal(resultMessage), false);

                targetPlayer.sendMessage(Text.literal("§eData synchronized"), false);

            }).exceptionally(throwable -> {
                String errorMessage = isConsole(context.getSource()) ?
                        "[CONSOLE] Error synchronizing data: " + throwable.getMessage() :
                        "§cError synchronizing data: " + throwable.getMessage();

                context.getSource().sendFeedback(() -> Text.literal(errorMessage), false);
                return null;
            });

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    // NUEVO: Comando para forzar guardado (reemplaza el backup)
    private static int forceSaveAll(CommandContext<ServerCommandSource> context) {
        try {
            String savingMessage = isConsole(context.getSource()) ?
                    "[CONSOLE] Forcing save of all backpack data..." :
                    "§eForcando guardado de datos...";

            context.getSource().sendFeedback(() -> Text.literal(savingMessage), false);

            BackpacksMod.getMongoManager().saveAllDirtyBackpacks();

            String successMessage = isConsole(context.getSource()) ?
                    "[CONSOLE] All backpack data saved to MongoDB" :
                    "§aTodos los datos guardados en MongoDB";

            context.getSource().sendFeedback(() -> Text.literal(successMessage), false);

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    // NUEVO: Comando para listar todos los jugadores con mochilas
    private static int listAllPlayers(CommandContext<ServerCommandSource> context) {
        try {
            String infoMessage = isConsole(context.getSource()) ?
                    "[CONSOLE] Listing players with backpacks..." :
                    "§eChecking player data...";

            context.getSource().sendFeedback(() -> Text.literal(infoMessage), false);

            // Obtener jugadores online con mochilas
            StringBuilder playerList = new StringBuilder();

            if (isConsole(context.getSource())) {
                playerList.append("=== Players with Backpacks ===\n");
            } else {
                playerList.append("§6=== Players with Backpacks ===\n");
            }

            int playerCount = 0;
            if (BackpacksMod.getServer() != null) {
                for (ServerPlayerEntity onlinePlayer : BackpacksMod.getServer().getPlayerManager().getPlayerList()) {
                    try {
                        BackpackManager.BackpackStats stats = BackpackManager.getPlayerStats(onlinePlayer.getUuid());
                        if (stats.getTotalBackpacks() > 0) {
                            playerCount++;
                            if (isConsole(context.getSource())) {
                                playerList.append(String.format("%d. %s - %d backpacks (%d items)\n",
                                        playerCount, onlinePlayer.getName().getString(),
                                        stats.getTotalBackpacks(), stats.getTotalItems()));
                            } else {
                                playerList.append(String.format("§e%d. §f%s §7- §a%d §7backpacks (§a%d §7items)\n",
                                        playerCount, onlinePlayer.getName().getString(),
                                        stats.getTotalBackpacks(), stats.getTotalItems()));
                            }
                        }
                    } catch (Exception e) {
                        // Skip player if error
                    }
                }
            }

            if (playerCount == 0) {
                String noPlayersMessage = isConsole(context.getSource()) ?
                        "No online players with backpacks found" :
                        "§7No online players with backpacks found";
                playerList.append(noPlayersMessage);
            }

            context.getSource().sendFeedback(() -> Text.literal(playerList.toString()), false);
            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    // NUEVO: Estadísticas del servidor (sin información de backups)
    private static int showServerStats(CommandContext<ServerCommandSource> context) {
        try {
            StringBuilder stats = new StringBuilder();

            if (isConsole(context.getSource())) {
                stats.append("=== BackpacksMod Server Statistics (NO BACKUPS) ===\n");
                stats.append("Server ID: ").append(ConfigManager.getConfig().serverId).append("\n");
                stats.append("Max backpacks per player: ").append(ConfigManager.getConfig().maxBackpacksPerPlayer).append("\n");
                stats.append("Language messages: ").append(LanguageManager.getTotalMessages()).append("\n");
                stats.append("Permission system: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");

                // Información del sistema VIP
                int maxVipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();
                stats.append("Max VIP backpacks: ").append(maxVipBackpacks).append("\n");
                stats.append("VIP system compatible: ").append(VipBackpackManager.isVipConfigurationValid() ? "Yes" : "No").append("\n");

                // ELIMINADO: Información de respaldo
                stats.append("Backup system: DISABLED for performance\n");
                stats.append("Data persistence: MongoDB only\n");

                // Información de MongoDB
                if (BackpacksMod.getMongoManager() != null) {
                    stats.append("MongoDB: Connected\n");
                    stats.append("Pending writes: ").append(BackpacksMod.getMongoManager().hasPendingWrites() ? "Yes" : "No").append("\n");
                }
            } else {
                stats.append("§6=== BackpacksMod Server Statistics (NO BACKUPS) ===\n");
                stats.append("§eServer ID: §a").append(ConfigManager.getConfig().serverId).append("\n");
                stats.append("§eMax backpacks per player: §a").append(ConfigManager.getConfig().maxBackpacksPerPlayer).append("\n");
                stats.append("§eLanguage messages: §a").append(LanguageManager.getTotalMessages()).append("\n");
                stats.append("§ePermission system: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");

                // Información del sistema VIP
                int maxVipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();
                stats.append("§eMax VIP backpacks: §a").append(maxVipBackpacks).append("\n");
                stats.append("§eVIP system compatible: ").append(VipBackpackManager.isVipConfigurationValid() ? "§aYes" : "§cNo").append("\n");

                // ELIMINADO: Información de respaldo
                stats.append("§eBackup system: §cDISABLED for performance\n");
                stats.append("§eData persistence: §aMongoDB only\n");

                // Información de MongoDB
                if (BackpacksMod.getMongoManager() != null) {
                    stats.append("§eMongoDB: §aConnected\n");
                    stats.append("§ePending writes: ").append(BackpacksMod.getMongoManager().hasPendingWrites() ? "§cYes" : "§aNo").append("\n");
                }
            }

            context.getSource().sendFeedback(() -> Text.literal(stats.toString()), false);
            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "Error getting server statistics: " + e.getMessage());
            return 0;
        }
    }

    // Nuevos métodos para gestión de permisos
    private static int showPermissionInfo(CommandContext<ServerCommandSource> context) {
        StringBuilder message = new StringBuilder();

        if (isConsole(context.getSource())) {
            message.append("=== Permission System Information ===\n");
            message.append("System: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
            message.append("LuckPerms Available: ").append(LuckPermsManager.isLuckPermsAvailable() ? "Yes" : "No").append("\n\n");

            message.append("User Permissions:\n");
            message.append("  - ").append(LuckPermsManager.USE_PERMISSION).append(" (use backpacks)\n");
            message.append("  - ").append(LuckPermsManager.VIEW_OWN_PERMISSION).append(" (view own backpacks)\n");
            message.append("  - ").append(LuckPermsManager.RENAME_PERMISSION).append(" (rename own backpacks)\n");
            message.append("  - ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" (change icons)\n");
            message.append("  - ").append(LuckPermsManager.VIEW_STATS_PERMISSION).append(" (view stats)\n\n");

            message.append("Admin Permissions:\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_PERMISSION).append(" (main admin permission)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" (view others' backpacks)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" (edit others' backpacks)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_GIVE_PERMISSION).append(" (give backpacks)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_REMOVE_PERMISSION).append(" (remove backpacks)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_RENAME_PERMISSION).append(" (rename any backpack)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_SYNC_PERMISSION).append(" (sync data)\n");
            message.append("  - ").append(LuckPermsManager.ADMIN_CONFIG_PERMISSION).append(" (modify config)\n\n");

            if (LuckPermsManager.isLuckPermsAvailable()) {
                message.append("LuckPerms is active! Use LuckPerms commands to manage permissions.\n");
                message.append("Example: /lp user <player> permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true");
            } else {
                message.append("Falling back to OP-based permissions.\n");
                message.append("Admin permissions require OP level ").append(ConfigManager.getConfig().adminPermissionLevel);
            }
        } else {
            message.append("§6=== Permission System Information ===\n");
            message.append("§eSystem: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
            message.append("§eLuckPerms Available: §a").append(LuckPermsManager.isLuckPermsAvailable() ? "Yes" : "No").append("\n\n");

            message.append("§eUser Permissions:\n");
            message.append("§7  - ").append(LuckPermsManager.USE_PERMISSION).append(" (use backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.VIEW_OWN_PERMISSION).append(" (view own backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.RENAME_PERMISSION).append(" (rename own backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" (change icons)\n");
            message.append("§7  - ").append(LuckPermsManager.VIEW_STATS_PERMISSION).append(" (view stats)\n\n");

            message.append("§eAdmin Permissions:\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_PERMISSION).append(" (main admin permission)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" (view others' backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" (edit others' backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_GIVE_PERMISSION).append(" (give backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_REMOVE_PERMISSION).append(" (remove backpacks)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_RENAME_PERMISSION).append(" (rename any backpack)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_SYNC_PERMISSION).append(" (sync data)\n");
            message.append("§7  - ").append(LuckPermsManager.ADMIN_CONFIG_PERMISSION).append(" (modify config)\n\n");

            if (LuckPermsManager.isLuckPermsAvailable()) {
                message.append("§aLuckPerms is active! Use LuckPerms commands to manage permissions.\n");
                message.append("§7Example: /lp user <player> permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true");
            } else {
                message.append("§eFalling back to OP-based permissions.\n");
                message.append("§7Admin permissions require OP level ").append(ConfigManager.getConfig().adminPermissionLevel);
            }
        }

        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int reloadPermissions(CommandContext<ServerCommandSource> context) {
        if (context.getSource().getEntity() instanceof ServerPlayerEntity admin) {
            boolean success = LuckPermsManager.reloadPermissions(admin);
            return success ? 1 : 0;
        } else {
            // Desde consola
            try {
                LuckPermsManager.forceReinitialization();
                context.getSource().sendFeedback(() ->
                        Text.literal("[CONSOLE] Permissions reloaded: " + LuckPermsManager.getPermissionSystemInfo()), true);
                return 1;
            } catch (Exception e) {
                context.getSource().sendFeedback(() ->
                        Text.literal("[CONSOLE] Error reloading permissions: " + e.getMessage()), false);
                return 0;
            }
        }
    }

    private static int checkPlayerPermissions(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String permissionInfo = LuckPermsManager.getPlayerPermissionInfo(targetPlayer);

            if (isConsole(context.getSource())) {
                context.getSource().sendFeedback(() ->
                        Text.literal("=== Permission Check for " + targetPlayer.getName().getString() + " ==="), false);
                context.getSource().sendFeedback(() ->
                        Text.literal(permissionInfo), false);
            } else {
                ServerPlayerEntity admin = (ServerPlayerEntity) context.getSource().getEntity();
                admin.sendMessage(Text.literal(""), false);
                admin.sendMessage(Text.literal("§6=== Permission Check for " + targetPlayer.getName().getString() + " ==="), false);
                admin.sendMessage(Text.literal(permissionInfo), false);
                admin.sendMessage(Text.literal(""), false);
            }

            return 1;
        } catch (Exception e) {
            sendErrorFeedback(context.getSource(), "An error occurred: " + e.getMessage());
            return 0;
        }
    }

    // ========== MÉTODOS DE UTILIDAD PARA CONSOLA ==========

    /**
     * Verifica si el comando proviene de la consola
     */
    private static boolean isConsole(ServerCommandSource source) {
        return source.getEntity() == null;
    }

    /**
     * Envía mensaje específico para consola
     */
    private static void sendConsoleMessage(ServerCommandSource source, String messageKey) {
        if (isConsole(source)) {
            source.sendFeedback(() -> Text.literal("[CONSOLE] This command requires a player"), false);
        } else {
            LanguageManager.sendFeedback(source, messageKey, false);
        }
    }

    /**
     * Envía feedback adaptado para consola o jugador
     */
    private static void sendFeedback(ServerCommandSource source, String messageKey, boolean broadcastToOps) {
        if (isConsole(source)) {
            // Para consola, usar texto plano sin colores
            String message = LanguageManager.getMessage(messageKey);
            String cleanMessage = message.replaceAll("§[0-9a-fklmnor]", ""); // Remover códigos de color
            source.sendFeedback(() -> Text.literal("[CONSOLE] " + cleanMessage), broadcastToOps);
        } else {
            // Para jugadores, usar sistema normal de idiomas
            LanguageManager.sendFeedback(source, messageKey, broadcastToOps);
        }
    }

    /**
     * Envía mensaje de error adaptado para consola o jugador
     */
    private static void sendErrorFeedback(ServerCommandSource source, String errorMessage) {
        if (isConsole(source)) {
            source.sendFeedback(() -> Text.literal("[CONSOLE] ERROR: " + errorMessage), false);
        } else {
            source.sendFeedback(() -> Text.literal("§cError: " + errorMessage), false);
        }
    }

    /**
     * Obtiene información del ejecutor del comando
     */
    private static String getCommandExecutorInfo(ServerCommandSource source) {
        if (isConsole(source)) {
            return "CONSOLE";
        } else if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player.getName().getString();
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Log mejorado para acciones administrativas
     */
    private static void logAdminAction(ServerCommandSource source, String action, String details) {
        String executor = getCommandExecutorInfo(source);
        BackpacksMod.LOGGER.info("[ADMIN ACTION] {} executed '{}' - {}", executor, action, details);
    }

    // ========== COMANDOS MEJORADOS CON LOGGING ==========

    /**
     * Wrapper mejorado para el comando give con logging completo
     */
    private static void logGiveBackpack(ServerCommandSource source, String playerName, String backpackName, int id, int slots) {
        String executor = getCommandExecutorInfo(source);
        String details = String.format("gave backpack '%s' (ID: %d, %d slots) to %s",
                backpackName, id, slots, playerName);
        logAdminAction(source, "give backpack", details);
    }

    /**
     * Wrapper mejorado para el comando remove con logging completo
     */
    private static void logRemoveBackpack(ServerCommandSource source, String playerName, String backpackName, int id) {
        String executor = getCommandExecutorInfo(source);
        String details = String.format("removed backpack '%s' (ID: %d) from %s",
                backpackName, id, playerName);
        logAdminAction(source, "remove backpack", details);
    }

    /**
     * Wrapper mejorado para el comando rename con logging completo
     */
    private static void logRenameBackpack(ServerCommandSource source, String playerName, int id, String oldName, String newName) {
        String executor = getCommandExecutorInfo(source);
        String details = String.format("renamed backpack ID %d of %s from '%s' to '%s'",
                id, playerName, oldName, newName);
        logAdminAction(source, "rename backpack", details);
    }

    // ========== INFORMACIÓN Y AYUDA PARA CONSOLA ==========

    /**
     * Muestra ayuda específica para uso desde consola (SIN comandos de backup)
     */
    public static void showConsoleHelp(ServerCommandSource source) {
        if (!isConsole(source)) return;

        StringBuilder help = new StringBuilder();
        help.append("=== BackpacksMod Console Commands (NO BACKUPS) ===\n");
        help.append("Available commands for console:\n\n");

        String cmd = ConfigManager.getConfig().mainCommand;

        help.append("Player Management:\n");
        help.append("  ").append(cmd).append(" give <player> <name> <slots> - Give backpack to player\n");
        help.append("  ").append(cmd).append(" remove <player> <id> - Remove backpack from player\n");
        help.append("  ").append(cmd).append(" rename <player> <id> <new_name> - Rename player's backpack\n");
        help.append("  ").append(cmd).append(" info <player> - Show player's backpack statistics\n");
        help.append("  ").append(cmd).append(" sync <player> - Synchronize player's data\n");
        help.append("  ").append(cmd).append(" list - List all players with backpacks\n");
        help.append("  ").append(cmd).append(" stats - Show server statistics\n\n");

        help.append("System Management:\n");
        help.append("  ").append(cmd).append(" force-save - Force save all data to MongoDB\n");
        help.append("  ").append(cmd).append(" permissions info - Show permission system info\n");
        help.append("  ").append(cmd).append(" permissions reload - Reload permission system\n");
        help.append("  ").append(cmd).append(" permissions check <player> - Check player permissions\n\n");

        help.append("Examples:\n");
        help.append("  ").append(cmd).append(" give Steve \"Storage Chest\" 27\n");
        help.append("  ").append(cmd).append(" remove Alex 1\n");
        help.append("  ").append(cmd).append(" info Steve\n");
        help.append("  ").append(cmd).append(" force-save\n");
        help.append("  ").append(cmd).append(" stats\n\n");

        help.append("Notes:\n");
        help.append("- Console has full administrative privileges\n");
        help.append("- All actions are logged to server console\n");
        help.append("- Player names are case-sensitive\n");
        help.append("- Backpack slots must be multiples of 9 (9-54)\n");
        help.append("- Use quotes for names with spaces: \"My Backpack\"\n");
        help.append("- BACKUP SYSTEM DISABLED - All data relies on MongoDB\n");
        help.append("- Use force-save for manual data persistence\n");

        source.sendFeedback(() -> Text.literal(help.toString()), false);
    }

    /**
     * Comando de ayuda específico para consola
     */
    public static void registerConsoleHelp(CommandDispatcher<ServerCommandSource> dispatcher) {
        String helpCommand = ConfigManager.getConfig().mainCommand + "-help";

        dispatcher.register(CommandManager.literal(helpCommand)
                .requires(source -> isConsole(source)) // Solo para consola
                .executes(context -> {
                    showConsoleHelp(context.getSource());
                    return 1;
                }));
    }
}