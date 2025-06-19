package es.minemon.backpacks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ConfigCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        String configCommand = ConfigManager.getConfig().mainCommand + "-config";

        dispatcher.register(CommandManager.literal(configCommand)
                .requires(source -> source.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel))

                .then(CommandManager.literal("reload")
                        .executes(ConfigCommands::reloadConfig))

                .then(CommandManager.literal("show")
                        .executes(ConfigCommands::showConfig))

                // CONFIGURACIÓN BÁSICA (SIN BACKUP)
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("max-backpacks")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(1, 150))
                                        .executes(ctx -> setConfigInt(ctx, "maxBackpacksPerPlayer", "value"))))

                        // ELIMINADO: backup-interval y max-backup-files

                        .then(CommandManager.literal("admin-permission")
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> setConfigInt(ctx, "adminPermissionLevel", "level"))))

                        .then(CommandManager.literal("allow-rename")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setConfigBool(ctx, "allowBackpackRename"))))

                        .then(CommandManager.literal("show-stats")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setConfigBool(ctx, "showBackpackStats"))))

                        .then(CommandManager.literal("server-id")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "serverId", "id"))))

                        .then(CommandManager.literal("database-name")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "databaseName", "name")))))

                // MOCHILAS POR DEFECTO
                .then(CommandManager.literal("default")
                        .then(CommandManager.literal("enable")
                                .executes(ConfigCommands::enableDefaultBackpacks))
                        .then(CommandManager.literal("disable")
                                .executes(ConfigCommands::disableDefaultBackpacks))
                        .then(CommandManager.literal("count")
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 50))
                                        .executes(ConfigCommands::setDefaultBackpackCount)))
                        .then(CommandManager.literal("slots")
                                .then(CommandManager.argument("slots", IntegerArgumentType.integer(9, 54))
                                        .executes(ConfigCommands::setDefaultBackpackSlots))))

                .then(CommandManager.literal("validate")
                        .executes(ConfigCommands::validateConfig)));
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        try {
            ConfigManager.reloadConfig();
            LanguageManager.reloadLanguage();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aConfiguración recargada correctamente (SIN backups)"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError recargando configuración: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showConfig(CommandContext<ServerCommandSource> context) {
        try {
            BackpackConfig config = ConfigManager.getConfig();
            StringBuilder summary = new StringBuilder();

            summary.append("§6=== Configuración BackpacksMod (SIN BACKUPS) ===\n");
            summary.append("§eServidor: §a").append(config.serverId).append("\n");
            summary.append("§eMax mochilas/jugador: §a").append(config.maxBackpacksPerPlayer).append("\n");
            summary.append("§eRenombrado: §a").append(config.allowBackpackRename ? "Habilitado" : "Deshabilitado").append("\n");
            summary.append("§eEstadísticas: §a").append(config.showBackpackStats ? "Habilitadas" : "Deshabilitadas").append("\n");
            summary.append("§eComando principal: §a/").append(config.mainCommand).append("\n");
            summary.append("§eComando jugador: §a/").append(config.playerCommand).append("\n");
            summary.append("§eMongoDB: §a").append(config.databaseName).append("\n");
            // ELIMINADO: Información de backup
            summary.append("§eSistema de backup: §cDESHABILITADO para rendimiento\n");
            summary.append("§ePersistencia: §aSolo MongoDB\n");

            context.getSource().sendFeedback(() -> Text.literal(summary.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError mostrando configuración: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // MÉTODOS DE CONFIGURACIÓN SIMPLIFICADOS
    private static int enableDefaultBackpacks(CommandContext<ServerCommandSource> context) {
        try {
            BackpackConfig config = ConfigManager.getConfig();
            config.giveDefaultBackpacks = true;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aMochilas por defecto habilitadas"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int disableDefaultBackpacks(CommandContext<ServerCommandSource> context) {
        try {
            BackpackConfig config = ConfigManager.getConfig();
            config.giveDefaultBackpacks = false;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§cMochilas por defecto deshabilitadas"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultBackpackCount(CommandContext<ServerCommandSource> context) {
        try {
            int count = IntegerArgumentType.getInteger(context, "amount");
            BackpackConfig config = ConfigManager.getConfig();
            config.defaultBackpackCount = count;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aCantidad de mochilas por defecto: " + count),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultBackpackSlots(CommandContext<ServerCommandSource> context) {
        try {
            int slots = IntegerArgumentType.getInteger(context, "slots");

            if (slots % 9 != 0) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cLos slots deben ser múltiplos de 9"),
                        false);
                return 0;
            }

            BackpackConfig config = ConfigManager.getConfig();
            config.defaultBackpackSlots = slots;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aSlots por defecto configurados: " + slots),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // ACTUALIZADO: Método setConfigInt sin referencias a backup
    private static int setConfigInt(CommandContext<ServerCommandSource> context, String field, String argName) {
        try {
            int value = IntegerArgumentType.getInteger(context, argName);
            BackpackConfig config = ConfigManager.getConfig();

            switch (field) {
                case "maxBackpacksPerPlayer" -> config.maxBackpacksPerPlayer = value;
                case "adminPermissionLevel" -> config.adminPermissionLevel = value;
                // ELIMINADO: casos de backup
                default -> throw new IllegalArgumentException("Campo desconocido: " + field);
            }

            config.validateUserConfig();
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + " configurado: " + value),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setConfigBool(CommandContext<ServerCommandSource> context, String field) {
        try {
            boolean value = BoolArgumentType.getBool(context, "enabled");
            BackpackConfig config = ConfigManager.getConfig();

            switch (field) {
                case "allowBackpackRename" -> config.allowBackpackRename = value;
                case "showBackpackStats" -> config.showBackpackStats = value;
                default -> throw new IllegalArgumentException("Campo desconocido: " + field);
            }

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + ": " + (value ? "habilitado" : "deshabilitado")),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setConfigString(CommandContext<ServerCommandSource> context, String field, String argName) {
        try {
            String value = StringArgumentType.getString(context, argName);
            BackpackConfig config = ConfigManager.getConfig();

            switch (field) {
                case "serverId" -> config.serverId = value;
                case "databaseName" -> config.databaseName = value;
                default -> throw new IllegalArgumentException("Campo desconocido: " + field);
            }

            config.validateUserConfig();
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + " configurado: " + value),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int validateConfig(CommandContext<ServerCommandSource> context) {
        try {
            boolean isValid = ConfigManager.validateConfiguration();

            if (isValid) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§aConfiguración válida (optimizada sin backups)"),
                        false);
            } else {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cProblemas en la configuración"),
                        false);
            }

            return isValid ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError validando: " + e.getMessage()),
                    false);
            return 0;
        }
    }
}