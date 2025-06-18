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

        // Comandos administrativos (SOLO para jugadores online con permisos)
        dispatcher.register(CommandManager.literal(mainCommand)
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.isAdmin(player);
                })
                .then(CommandManager.literal("give")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canGiveBackpacks(player);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .then(CommandManager.argument("slots", IntegerArgumentType.integer(9, 54))
                                                .executes(BackpackCommands::giveBackpack)))))
                .then(CommandManager.literal("remove")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canRemoveBackpacks(player);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .executes(BackpackCommands::removeBackpack))))
                .then(CommandManager.literal("rename")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canAdminRename(player);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .then(CommandManager.argument("new_name", StringArgumentType.greedyString())
                                                .executes(BackpackCommands::adminRenameBackpack)))))
                .then(CommandManager.literal("info")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canViewOthers(player);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BackpackCommands::showPlayerInfo)))
                .then(CommandManager.literal("sync")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canSync(player);
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(BackpackCommands::syncPlayerData)))
                .then(CommandManager.literal("admin")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canViewOthers(player);
                        })
                        .then(CommandManager.literal("view")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(BackpackCommands::openAdminView))))
                .then(CommandManager.literal("backup")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canManageBackups(player);
                        })
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                        .executes(BackpackCommands::createManualBackup)))
                        .then(CommandManager.literal("list")
                                .executes(BackpackCommands::listBackups))
                        .then(CommandManager.literal("force-save")
                                .executes(BackpackCommands::forceBackupAll)))
                .then(CommandManager.literal("permissions")
                        .requires(source -> {
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                            return LuckPermsManager.canModifyConfig(player);
                        })
                        .then(CommandManager.literal("info")
                                .executes(BackpackCommands::showPermissionInfo))
                        .then(CommandManager.literal("reload")
                                .executes(BackpackCommands::reloadPermissions))
                        .then(CommandManager.literal("check")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(BackpackCommands::checkPlayerPermissions)))));

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
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
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
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canGiveBackpacks(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_GIVE_PERMISSION);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String name = StringArgumentType.getString(context, "name");
            int slots = IntegerArgumentType.getInteger(context, "slots");

            // Validar slots
            if (slots % 9 != 0) {
                LanguageManager.sendFeedback(context.getSource(), "commandSlotCountInvalid", false);
                return 0;
            }

            // Generar ID automáticamente
            int newId = BackpackManager.getNextAvailableId(targetPlayer.getUuid());

            BackpackManager.addBackpack(targetPlayer.getUuid(), newId, name, slots);

            String adminMessage = String.format("§aBackpack '%s' (ID: %d) given to %s with %d slots",
                    name, newId, targetPlayer.getName().getString(), slots);
            context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);

            LanguageManager.sendMessage(targetPlayer, "backpackReceived",
                    String.format("%s (ID: %d, Slots: %d)", name, newId, slots));

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int removeBackpack(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canRemoveBackpacks(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_REMOVE_PERMISSION);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            int id = IntegerArgumentType.getInteger(context, "id");

            MongoBackpackManager.PlayerBackpacks backpacks = BackpackManager.getPlayerBackpacks(targetPlayer.getUuid());

            if (!backpacks.hasBackpack(id)) {
                LanguageManager.sendFeedback(context.getSource(), "errorBackpackNotFound", false);
                return 0;
            }

            // Crear backup antes de eliminar si está habilitado
            if (ConfigManager.isFeatureEnabled("backup")) {
                BackpacksMod.getBackupManager().createManualBackup("Removing backpack ID " + id + " from " + targetPlayer.getName().getString());
            }

            BackpackManager.removeBackpack(targetPlayer.getUuid(), id);

            String adminMessage = String.format("§aBackpack ID %d removed from %s",
                    id, targetPlayer.getName().getString());
            context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);

            LanguageManager.sendMessage(targetPlayer, "backpackLost", id);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int adminRenameBackpack(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        // Verificar permisos
        if (!LuckPermsManager.canAdminRename(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_RENAME_PERMISSION);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            int id = IntegerArgumentType.getInteger(context, "id");
            String newName = StringArgumentType.getString(context, "new_name");

            // Validar nombre
            if (newName.trim().isEmpty()) {
                LanguageManager.sendFeedback(context.getSource(), "commandNameEmpty", false);
                return 0;
            }

            if (newName.length() > 50) {
                LanguageManager.sendFeedback(context.getSource(), "commandNameTooLong", false);
                return 0;
            }

            // Verificar que la mochila existe
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayer.getUuid(), id);
            if (backpack == null) {
                LanguageManager.sendFeedback(context.getSource(), "errorBackpackNotFound", false);
                return 0;
            }

            boolean success = BackpackManager.renameBackpack(targetPlayer.getUuid(), id, newName.trim());

            if (success) {
                String adminMessage = String.format("§aBackpack ID %d of %s renamed to: §f%s",
                        id, targetPlayer.getName().getString(), newName.trim());
                context.getSource().sendFeedback(() -> Text.literal(adminMessage), true);

                String playerMessage = String.format("§aAn administrator renamed your backpack ID %d to: §f%s",
                        id, newName.trim());
                targetPlayer.sendMessage(Text.literal(playerMessage), false);

                BackpackManager.forcePlayerSave(targetPlayer.getUuid());
            } else {
                LanguageManager.sendFeedback(context.getSource(), "errorRenamingBackpack", false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int openAdminView(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
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
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int playerRenameBackpack(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
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
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showPlayerInfo(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            BackpackManager.BackpackStats stats = BackpackManager.getPlayerStats(targetPlayer.getUuid());

            StringBuilder message = new StringBuilder();
            message.append("§6=== ").append(targetPlayer.getName().getString()).append(" Statistics ===\n");
            message.append("§eTotal backpacks: §a").append(stats.getTotalBackpacks()).append("\n");
            message.append("§eItems stored: §a").append(stats.getTotalItems()).append("\n");
            message.append("§eEmpty slots: §a").append(stats.getEmptySlots()).append("\n");
            message.append("§eTotal slots: §a").append(stats.getTotalSlots()).append("\n");
            message.append("§eSpace usage: §a").append(String.format("%.1f", stats.getUsagePercentage())).append("%");

            context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int syncPlayerData(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            context.getSource().sendFeedback(() ->
                            Text.literal("§eSynchronizing data..."),
                    false);

            BackpackManager.syncPlayerDataFromDatabase(targetPlayer.getUuid()).thenRun(() -> {
                // Verificar integridad después de la sincronización
                boolean integrityCheck = BackpackManager.verifyPlayerDataIntegrity(targetPlayer.getUuid());

                MongoBackpackManager.PlayerBackpacks backpacks = BackpackManager.getPlayerBackpacks(targetPlayer.getUuid());
                int backpackCount = backpacks.getAllBackpacks().size();

                String resultMessage = String.format("§aData synchronized for %s - %d backpacks, integrity: %s",
                        targetPlayer.getName().getString(), backpackCount,
                        (integrityCheck ? "§aOK" : "§cERROR") + "§a");

                context.getSource().sendFeedback(() -> Text.literal(resultMessage), false);

                targetPlayer.sendMessage(Text.literal("§eData synchronized"), false);

            }).exceptionally(throwable -> {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cError synchronizing data: " + throwable.getMessage()),
                        false);
                return null;
            });

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int createManualBackup(CommandContext<ServerCommandSource> context) {
        try {
            String reason = StringArgumentType.getString(context, "reason");

            context.getSource().sendFeedback(() ->
                            Text.literal("§eCreating backup..."),
                    false);

            BackpacksMod.getBackupManager().createManualBackup(reason);

            context.getSource().sendFeedback(() ->
                            Text.literal("§aBackup created successfully"),
                    false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError creating backup: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int listBackups(CommandContext<ServerCommandSource> context) {
        try {
            var backups = BackpacksMod.getBackupManager().getAvailableBackups();

            if (backups.isEmpty()) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§eNo backups available"),
                        false);
                return 0;
            }

            StringBuilder message = new StringBuilder("§6=== Available Backups ===\n");
            int count = 0;
            for (String backup : backups) {
                message.append("§e").append(++count).append(". §a").append(backup).append("\n");
                if (count >= 10) {
                    message.append("§7... and ").append(backups.size() - 10).append(" more");
                    break;
                }
            }

            context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int forceBackupAll(CommandContext<ServerCommandSource> context) {
        try {
            context.getSource().sendFeedback(() ->
                            Text.literal("§eSaving..."),
                    false);

            BackpacksMod.getMongoManager().saveAllDirtyBackpacks();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aForced save completed"),
                    false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // Nuevos métodos para gestión de permisos
    private static int showPermissionInfo(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        if (!LuckPermsManager.canModifyConfig(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_CONFIG_PERMISSION);
            return 0;
        }

        StringBuilder message = new StringBuilder();
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
        message.append("§7  - ").append(LuckPermsManager.ADMIN_BACKUP_PERMISSION).append(" (manage backups)\n");
        message.append("§7  - ").append(LuckPermsManager.ADMIN_CONFIG_PERMISSION).append(" (modify config)\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            message.append("§aLuckPerms is active! Use LuckPerms commands to manage permissions.\n");
            message.append("§7Example: /lp user <player> permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true");
        } else {
            message.append("§eFalling back to OP-based permissions.\n");
            message.append("§7Admin permissions require OP level ").append(ConfigManager.getConfig().adminPermissionLevel);
        }

        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int reloadPermissions(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        boolean success = LuckPermsManager.reloadPermissions(admin);
        return success ? 1 : 0;
    }

    private static int checkPlayerPermissions(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        if (!LuckPermsManager.canModifyConfig(admin)) {
            LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_CONFIG_PERMISSION);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String permissionInfo = LuckPermsManager.getPlayerPermissionInfo(targetPlayer);

            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(Text.literal("§6=== Permission Check for " + targetPlayer.getName().getString() + " ==="), false);
            admin.sendMessage(Text.literal(permissionInfo), false);
            admin.sendMessage(Text.literal(""), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cAn error occurred: " + e.getMessage()),
                    false);
            return 0;
        }
    }
}