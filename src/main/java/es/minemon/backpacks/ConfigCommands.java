package es.minemon.backpacks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class ConfigCommands {

    // Sugerencias para rangos VIP
    private static final List<String> VIP_RANKS = List.of("cristal", "ruby", "esmeralda", "perla", "platino");

    private static final SuggestionProvider<ServerCommandSource> VIP_RANK_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String rank : VIP_RANKS) {
            if (rank.startsWith(remaining)) {
                builder.suggest(rank);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        String configCommand = ConfigManager.getConfig().mainCommand + "-config";

        dispatcher.register(CommandManager.literal(configCommand)
                .requires(source -> source.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel))

                .then(CommandManager.literal("reload")
                        .executes(ConfigCommands::reloadConfig))

                .then(CommandManager.literal("show")
                        .executes(ConfigCommands::showConfig))

                .then(CommandManager.literal("list")
                        .executes(ConfigCommands::listConfigurableSettings))

                .then(CommandManager.literal("commands")
                        .executes(ConfigCommands::showCommandInfo))

                // COMANDOS DE IDIOMA
                .then(CommandManager.literal("language")
                        .then(CommandManager.literal("info")
                                .executes(ConfigCommands::showLanguageInfo))
                        .then(CommandManager.literal("reload")
                                .executes(ConfigCommands::reloadLanguage))
                        .then(CommandManager.literal("stats")
                                .executes(ConfigCommands::showLanguageStats)))

                // NUEVOS: COMANDOS DE MOCHILAS POR DEFECTO
                .then(CommandManager.literal("default")
                        .then(CommandManager.literal("info")
                                .executes(ConfigCommands::showDefaultBackpackInfo))
                        .then(CommandManager.literal("enable")
                                .executes(ConfigCommands::enableDefaultBackpacks))
                        .then(CommandManager.literal("disable")
                                .executes(ConfigCommands::disableDefaultBackpacks))
                        .then(CommandManager.literal("count")
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 50))
                                        .executes(ConfigCommands::setDefaultBackpackCount)))
                        .then(CommandManager.literal("slots")
                                .then(CommandManager.argument("slots", IntegerArgumentType.integer(9, 54))
                                        .executes(ConfigCommands::setDefaultBackpackSlots)))
                        .then(CommandManager.literal("pattern")
                                .then(CommandManager.argument("pattern", StringArgumentType.greedyString())
                                        .executes(ConfigCommands::setDefaultNamePattern)))
                        .then(CommandManager.literal("icon")
                                .then(CommandManager.argument("icon", StringArgumentType.string())
                                        .executes(ConfigCommands::setDefaultIcon))))

                // NUEVOS: COMANDOS VIP
                .then(CommandManager.literal("vip")
                        .then(CommandManager.literal("info")
                                .executes(ConfigCommands::showVipInfo))
                        .then(CommandManager.literal("list")
                                .executes(ConfigCommands::listVipRanks))
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .executes(ConfigCommands::enableVipRank)))
                        .then(CommandManager.literal("disable")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .executes(ConfigCommands::disableVipRank)))
                        .then(CommandManager.literal("count")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 50))
                                                .executes(ConfigCommands::setVipCount))))
                        .then(CommandManager.literal("slots")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .then(CommandManager.argument("slots", IntegerArgumentType.integer(9, 54))
                                                .executes(ConfigCommands::setVipSlots))))
                        .then(CommandManager.literal("name")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .executes(ConfigCommands::setVipDisplayName))))
                        .then(CommandManager.literal("pattern")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .then(CommandManager.argument("pattern", StringArgumentType.greedyString())
                                                .executes(ConfigCommands::setVipNamePattern))))
                        .then(CommandManager.literal("icon")
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTIONS)
                                        .then(CommandManager.argument("icon", StringArgumentType.string())
                                                .executes(ConfigCommands::setVipIcon)))))

                .then(CommandManager.literal("set")

                        .then(CommandManager.literal("max-backpacks")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(1, 150))
                                        .executes(ctx -> setConfigInt(ctx, "maxBackpacksPerPlayer", "value"))))

                        .then(CommandManager.literal("backup-interval")
                                .then(CommandManager.argument("minutes", IntegerArgumentType.integer(5, 60))
                                        .executes(ctx -> setConfigInt(ctx, "backupIntervalMinutes", "minutes"))))

                        .then(CommandManager.literal("max-backup-files")
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(10, 200))
                                        .executes(ctx -> setConfigInt(ctx, "maxBackupFiles", "count"))))

                        .then(CommandManager.literal("admin-permission")
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> setConfigInt(ctx, "adminPermissionLevel", "level"))))

                        .then(CommandManager.literal("connection-timeout")
                                .then(CommandManager.argument("ms", IntegerArgumentType.integer(5000, 30000))
                                        .executes(ctx -> setConfigInt(ctx, "mongoConnectionTimeoutMs", "ms"))))

                        .then(CommandManager.literal("allow-rename")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setConfigBool(ctx, "allowBackpackRename"))))

                        .then(CommandManager.literal("show-stats")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setConfigBool(ctx, "showBackpackStats"))))

                        .then(CommandManager.literal("server-id")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "serverId", "id"))))

                        .then(CommandManager.literal("mongo-connection")
                                .then(CommandManager.argument("connection", StringArgumentType.greedyString())
                                        .executes(ctx -> setConfigString(ctx, "mongoConnectionString", "connection"))))

                        .then(CommandManager.literal("database-name")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "databaseName", "name"))))

                        .then(CommandManager.literal("backup-directory")
                                .then(CommandManager.argument("directory", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "backupDirectory", "directory"))))

                        .then(CommandManager.literal("main-command")
                                .then(CommandManager.argument("command", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "mainCommand", "command"))))

                        .then(CommandManager.literal("player-command")
                                .then(CommandManager.argument("command", StringArgumentType.string())
                                        .executes(ctx -> setConfigString(ctx, "playerCommand", "command")))))

                .then(CommandManager.literal("validate")
                        .executes(ConfigCommands::validateConfig))

                .then(CommandManager.literal("reset")
                        .executes(ConfigCommands::resetConfig))

                .then(CommandManager.literal("fixed")
                        .executes(ConfigCommands::showFixedConfig))

                // NUEVO: Comando para aplicar cambios a jugadores online
                .then(CommandManager.literal("apply")
                        .executes(ConfigCommands::applyConfigToOnlinePlayers)));
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        try {
            String oldMainCommand = ConfigManager.getConfig().mainCommand;
            String oldPlayerCommand = ConfigManager.getConfig().playerCommand;

            // Recargar tanto configuración como idiomas
            ConfigManager.reloadConfig();
            LanguageManager.reloadLanguage();

            String newMainCommand = ConfigManager.getConfig().mainCommand;
            String newPlayerCommand = ConfigManager.getConfig().playerCommand;

            context.getSource().sendFeedback(() ->
                            Text.literal("§aConfiguration and language files reloaded successfully"),
                    true);

            context.getSource().sendFeedback(() ->
                            Text.literal("§eLanguage: " + LanguageManager.getLanguageInfo()),
                    false);

            if (!oldMainCommand.equals(newMainCommand) || !oldPlayerCommand.equals(newPlayerCommand)) {
                context.getSource().sendFeedback(() ->
                                Text.literal(String.format("§eCommands updated! Main: §a/%s §e| Player: §a/%s",
                                        newMainCommand, newPlayerCommand)),
                        false);
                context.getSource().sendFeedback(() ->
                                Text.literal("§c⚠ Server restart required for command changes to take effect"),
                        false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError reloading configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // === COMANDOS DE MOCHILAS POR DEFECTO ===

    private static int showDefaultBackpackInfo(CommandContext<ServerCommandSource> context) {
        String info = DefaultBackpackManager.getConfigurationSummary();
        context.getSource().sendFeedback(() -> Text.literal(info), false);
        return 1;
    }

    private static int enableDefaultBackpacks(CommandContext<ServerCommandSource> context) {
        try {
            BackpackConfig config = ConfigManager.getConfig();
            config.giveDefaultBackpacks = true;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aDefault backpacks enabled! New players will receive " +
                                    config.defaultBackpackCount + " starter backpacks"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError enabling default backpacks: " + e.getMessage()),
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
                            Text.literal("§cDefault backpacks disabled! New players will not receive starter backpacks"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError disabling default backpacks: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultBackpackCount(CommandContext<ServerCommandSource> context) {
        try {
            int count = IntegerArgumentType.getInteger(context, "amount");
            BackpackConfig config = ConfigManager.getConfig();

            // Validar que no exceda límites
            if (!DefaultBackpackManager.validateAndApplyNewConfig(count, config.defaultBackpackSlots,
                    config.defaultBackpackNamePattern, config.defaultBackpackIcon)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid configuration - would exceed backpack limits or cause conflicts"),
                        false);
                return 0;
            }

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aDefault backpack count set to: §e" + count),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting default backpack count: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultBackpackSlots(CommandContext<ServerCommandSource> context) {
        try {
            int slots = IntegerArgumentType.getInteger(context, "slots");

            if (slots % 9 != 0) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cSlot count must be a multiple of 9 (9, 18, 27, 36, 45, 54)"),
                        false);
                return 0;
            }

            BackpackConfig config = ConfigManager.getConfig();

            if (!DefaultBackpackManager.validateAndApplyNewConfig(config.defaultBackpackCount, slots,
                    config.defaultBackpackNamePattern, config.defaultBackpackIcon)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid configuration"),
                        false);
                return 0;
            }

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aDefault backpack slots set to: §e" + slots),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting default backpack slots: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultNamePattern(CommandContext<ServerCommandSource> context) {
        try {
            String pattern = StringArgumentType.getString(context, "pattern");

            if (!pattern.contains("%d")) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cName pattern must contain %d for the backpack number"),
                        false);
                return 0;
            }

            BackpackConfig config = ConfigManager.getConfig();

            if (!DefaultBackpackManager.validateAndApplyNewConfig(config.defaultBackpackCount,
                    config.defaultBackpackSlots, pattern, config.defaultBackpackIcon)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid name pattern"),
                        false);
                return 0;
            }

            ConfigManager.saveConfig();

            // Mostrar ejemplos
            String[] examples = DefaultBackpackManager.getNameExamples(3);
            StringBuilder exampleText = new StringBuilder("§aName pattern updated! Examples: ");
            for (int i = 0; i < examples.length; i++) {
                exampleText.append("§e").append(examples[i]);
                if (i < examples.length - 1) exampleText.append("§a, ");
            }

            context.getSource().sendFeedback(() -> Text.literal(exampleText.toString()), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting name pattern: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setDefaultIcon(CommandContext<ServerCommandSource> context) {
        try {
            String icon = StringArgumentType.getString(context, "icon");
            BackpackConfig config = ConfigManager.getConfig();

            if (!DefaultBackpackManager.validateAndApplyNewConfig(config.defaultBackpackCount,
                    config.defaultBackpackSlots, config.defaultBackpackNamePattern, icon)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid icon - must be a valid Minecraft item ID (e.g., minecraft:chest)"),
                        false);
                return 0;
            }

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aDefault icon set to: §e" + icon),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting default icon: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // === COMANDOS VIP ===

    private static int showVipInfo(CommandContext<ServerCommandSource> context) {
        String info = VipBackpackManager.getVipConfigurationSummary();
        context.getSource().sendFeedback(() -> Text.literal(info), false);
        return 1;
    }

    private static int listVipRanks(CommandContext<ServerCommandSource> context) {
        BackpackConfig config = ConfigManager.getConfig();
        StringBuilder list = new StringBuilder();
        list.append("§6=== VIP Rank Configuration ===\n\n");

        addVipRankInfo(list, "Cristal", config.cristalConfig);
        addVipRankInfo(list, "Ruby", config.rubyConfig);
        addVipRankInfo(list, "Esmeralda", config.esmeraldaConfig);
        addVipRankInfo(list, "Perla", config.perlaConfig);
        addVipRankInfo(list, "Platino", config.platinoConfig);

        context.getSource().sendFeedback(() -> Text.literal(list.toString()), false);
        return 1;
    }

    private static void addVipRankInfo(StringBuilder sb, String rankName, BackpackConfig.VipRankConfig config) {
        sb.append(config.enabled ? "§a✓ " : "§c✗ ").append("§e").append(rankName).append(":\n");
        sb.append("  §7Display Name: §f").append(config.displayName).append("\n");
        sb.append("  §7Backpack Count: §a").append(config.backpackCount).append("\n");
        sb.append("  §7Slots per Backpack: §a").append(config.slotsPerBackpack).append("\n");
        sb.append("  §7Name Pattern: §f").append(config.namePattern).append("\n");
        sb.append("  §7Default Icon: §f").append(config.defaultIcon).append("\n");
        sb.append("  §7Colors: ").append(config.primaryColor).append(" / ").append(config.secondaryColor).append("\n\n");
    }

    private static int enableVipRank(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            BackpackConfig config = ConfigManager.getConfig();

            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.enabled = true;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a✓ VIP " + rank + " rank enabled"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError enabling VIP rank: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int disableVipRank(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            BackpackConfig config = ConfigManager.getConfig();

            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.enabled = false;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§c✗ VIP " + rank + " rank disabled"),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError disabling VIP rank: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setVipCount(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            int count = IntegerArgumentType.getInteger(context, "count");
            BackpackConfig config = ConfigManager.getConfig();

            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.backpackCount = count;
            config.validateUserConfig(); // Validar compatibilidad
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aVIP " + rank + " backpack count set to: §e" + count),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting VIP count: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setVipSlots(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            int slots = IntegerArgumentType.getInteger(context, "slots");

            if (slots % 9 != 0) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cSlot count must be a multiple of 9 (9, 18, 27, 36, 45, 54)"),
                        false);
                return 0;
            }

            BackpackConfig config = ConfigManager.getConfig();
            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.slotsPerBackpack = slots;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aVIP " + rank + " slots per backpack set to: §e" + slots),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting VIP slots: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setVipDisplayName(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            String name = StringArgumentType.getString(context, "name");
            BackpackConfig config = ConfigManager.getConfig();

            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.displayName = name;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aVIP " + rank + " display name set to: §e" + name),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting VIP display name: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setVipNamePattern(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            String pattern = StringArgumentType.getString(context, "pattern");

            if (!pattern.contains("%s") || !pattern.contains("%d")) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cPattern must contain both %s (display name) and %d (number)"),
                        false);
                return 0;
            }

            BackpackConfig config = ConfigManager.getConfig();
            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            vipConfig.namePattern = pattern;
            ConfigManager.saveConfig();

            // Mostrar ejemplo
            String example = String.format(pattern, vipConfig.displayName, 1);
            context.getSource().sendFeedback(() ->
                            Text.literal("§aVIP " + rank + " name pattern updated! Example: §e" + example),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting VIP name pattern: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setVipIcon(CommandContext<ServerCommandSource> context) {
        try {
            String rank = StringArgumentType.getString(context, "rank").toLowerCase();
            String icon = StringArgumentType.getString(context, "icon");
            BackpackConfig config = ConfigManager.getConfig();

            BackpackConfig.VipRankConfig vipConfig = getVipConfigByRank(config, rank);
            if (vipConfig == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cUnknown VIP rank. Available: " + String.join(", ", VIP_RANKS)),
                        false);
                return 0;
            }

            // Validar que el item existe
            try {
                net.minecraft.util.Identifier.tryParse(icon);
            } catch (Exception e) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid item ID. Use format: minecraft:item_name"),
                        false);
                return 0;
            }

            vipConfig.defaultIcon = icon;
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aVIP " + rank + " icon set to: §e" + icon),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting VIP icon: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static BackpackConfig.VipRankConfig getVipConfigByRank(BackpackConfig config, String rank) {
        return switch (rank.toLowerCase()) {
            case "cristal" -> config.cristalConfig;
            case "ruby" -> config.rubyConfig;
            case "esmeralda" -> config.esmeraldaConfig;
            case "perla" -> config.perlaConfig;
            case "platino" -> config.platinoConfig;
            default -> null;
        };
    }

    // === NUEVO: COMANDO PARA APLICAR CAMBIOS ===

    private static int applyConfigToOnlinePlayers(CommandContext<ServerCommandSource> context) {
        try {
            if (BackpacksMod.getServer() == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cServer not available"),
                        false);
                return 0;
            }

            context.getSource().sendFeedback(() ->
                            Text.literal("§eApplying configuration changes to all online players..."),
                    false);

            // Aplicar cambios a todos los jugadores online
            BackpacksMod.getServer().getPlayerManager().getPlayerList().forEach(player -> {
                try {
                    // Actualizar mochilas VIP basándose en la nueva configuración
                    VipBackpackManager.updatePlayerVipBackpacks(player);

                    // Si las mochilas por defecto están habilitadas y el jugador no tiene mochilas,
                    // intentar dar mochilas por defecto
                    PlayerEventsHandler.forceGiveDefaultBackpacks(player);

                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Error applying config to player: " + player.getName().getString());
                }
            });

            int playerCount = BackpacksMod.getServer().getCurrentPlayerCount();
            context.getSource().sendFeedback(() ->
                            Text.literal("§aConfiguration applied to " + playerCount + " online players"),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError applying configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    // === COMANDOS ORIGINALES ===

    private static int showLanguageInfo(CommandContext<ServerCommandSource> context) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("§6=== Language System Information ===\n");
            message.append("§eCurrent Language: §a").append(LanguageManager.getLanguageInfo()).append("\n");
            message.append("§eTotal Messages: §a").append(LanguageManager.getTotalMessages()).append("\n");
            message.append("§eLanguage File: §aconfig/backpacks/lang.json\n");
            message.append("§eConfig File: §aconfig/backpacks/config.json\n");
            message.append("\n§7Edit lang.json to customize all messages and GUI text");
            message.append("\n§7Support for hex colors: §a<#ff5555>text</>");
            message.append("\n§7Support for gradients: §a<gradient:#ff0000:#00ff00>text</gradient>");

            context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError showing language info: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int reloadLanguage(CommandContext<ServerCommandSource> context) {
        try {
            LanguageManager.reloadLanguage();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aLanguage file reloaded successfully"),
                    true);

            context.getSource().sendFeedback(() ->
                            Text.literal("§eCurrent: " + LanguageManager.getLanguageInfo()),
                    false);

            context.getSource().sendFeedback(() ->
                            Text.literal("§eTotal messages: " + LanguageManager.getTotalMessages()),
                    false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError reloading language: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showLanguageStats(CommandContext<ServerCommandSource> context) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("§6=== Language Statistics ===\n");
            message.append("§eCurrent Language Pack:\n");
            message.append("  §7").append(LanguageManager.getLanguageInfo()).append("\n");
            message.append("  §7Total messages: §a").append(LanguageManager.getTotalMessages()).append("\n");
            message.append("\n§eFile Locations:\n");
            message.append("  §7Language: §aconfig/backpacks/lang.json\n");
            message.append("  §7Config: §aconfig/backpacks/config.json\n");
            message.append("\n§eMessage Categories:\n");
            message.append("  §7• messages - Basic mod messages\n");
            message.append("  §7• gui - Interface and menu text\n");
            message.append("  §7• commands - Command help and responses\n");
            message.append("  §7• admin - Administrator messages\n");
            message.append("  §7• vip - VIP system messages\n");
            message.append("  §7• misc - Miscellaneous messages\n");
            message.append("\n§eCustomization:\n");
            message.append("  §7Edit lang.json to translate or customize any message\n");
            message.append("  §7Supports hex colors and gradients\n");
            message.append("  §7Use '/").append(ConfigManager.getConfig().mainCommand).append("-config language reload' to apply changes");

            context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError showing language stats: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showConfig(CommandContext<ServerCommandSource> context) {
        try {
            String configSummary = ConfigManager.getConfigSummary();
            context.getSource().sendFeedback(() -> Text.literal(configSummary), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError showing configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showCommandInfo(CommandContext<ServerCommandSource> context) {
        try {
            String commandInfo = ConfigManager.getConfig().getCommandInfo();
            context.getSource().sendFeedback(() -> Text.literal(commandInfo), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError showing command info: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int listConfigurableSettings(CommandContext<ServerCommandSource> context) {
        try {
            StringBuilder configurableList = new StringBuilder();
            configurableList.append("§6=== Modifiable Configurations ===\n");
            configurableList.append("§eGeneral:\n");
            configurableList.append("  §7• serverId - Server ID\n");
            configurableList.append("  §7• maxBackpacksPerPlayer - Backpack limit per player (1-150)\n");
            configurableList.append("  §7• allowBackpackRename - Allow backpack renaming\n");
            configurableList.append("  §7• showBackpackStats - Show statistics\n");
            configurableList.append("\n§eCommands:\n");
            configurableList.append("  §7• mainCommand - Main admin command (default: backpack)\n");
            configurableList.append("  §7• playerCommand - Player command (default: backpacks)\n");
            configurableList.append("  §7  Commands use letters, numbers, - and _ only (max 20 chars)\n");
            configurableList.append("  §7  ⚠ Server restart required for command changes\n");
            configurableList.append("\n§eMongoDB:\n");
            configurableList.append("  §7• mongoConnectionString - Connection string\n");
            configurableList.append("  §7• databaseName - Database name\n");
            configurableList.append("  §7• mongoConnectionTimeoutMs - Connection timeout (5000-30000)\n");
            configurableList.append("\n§eBackup:\n");
            configurableList.append("  §7• backupIntervalMinutes - Backup interval (5-60)\n");
            configurableList.append("  §7• maxBackupFiles - Maximum backup files (10-200)\n");
            configurableList.append("  §7• backupDirectory - Backup directory\n");
            configurableList.append("\n§ePermissions:\n");
            configurableList.append("  §7• adminPermissionLevel - Admin permission level (1-4)\n");
            configurableList.append("\n§eNEW - Default Backpacks:\n");
            configurableList.append("  §7• Use '/").append(ConfigManager.getConfig().mainCommand).append("-config default info' for details\n");
            configurableList.append("  §7• Enable/disable automatic starter backpacks\n");
            configurableList.append("  §7• Configure count, slots, names, and icons\n");
            configurableList.append("\n§eNEW - VIP System:\n");
            configurableList.append("  §7• Use '/").append(ConfigManager.getConfig().mainCommand).append("-config vip info' for details\n");
            configurableList.append("  §7• Customize all 5 VIP ranks individually\n");
            configurableList.append("  §7• Configure names, icons, slots, and counts per rank\n");
            configurableList.append("\n§eLanguage:\n");
            configurableList.append("  §7• All messages are now in config/backpacks/lang.json\n");
            configurableList.append("  §7• Support for hex colors: <#ff5555>text</>\n");
            configurableList.append("  §7• Gradients: <gradient:#ff0000:#00ff00>text</gradient>\n");
            configurableList.append("  §7• Current: " + LanguageManager.getLanguageInfo() + "\n");
            configurableList.append("  §7• Total messages: " + LanguageManager.getTotalMessages() + "\n");

            context.getSource().sendFeedback(() -> Text.literal(configurableList.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError listing configurations: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showFixedConfig(CommandContext<ServerCommandSource> context) {
        StringBuilder message = new StringBuilder();
        message.append("§6=== FIXED Configurations (Optimized) ===\n");
        message.append("§7These configurations are optimized and cannot be modified:\n\n");

        message.append("§eSecurity and Integrity:\n");
        message.append("  §7• autoSaveOnClose: §aEnabled §7(Critical for data safety)\n");
        message.append("  §7• enableMongoDB: §aEnabled §7(Core functionality)\n");
        message.append("  §7• enableBackupSystem: §aEnabled §7(Data security)\n");
        message.append("  §7• createEmergencyBackup: §aEnabled §7(Prevent data loss)\n");
        message.append("  §7• validateDataIntegrity: §aEnabled §7(Check corruption)\n");
        message.append("  §7• backupOnPlayerDisconnect: §aEnabled §7(Security)\n");

        message.append("\n§eOptimized Performance:\n");
        message.append("  §7• asyncOperations: §aEnabled §7(Don't block server)\n");
        message.append("  §7• cacheTimeoutSeconds: §a60 §7(Optimized for performance)\n");
        message.append("  §7• cacheMaxSize: §a500 §7(Optimized for memory)\n");
        message.append("  §7• maxConcurrentWrites: §a4 §7(Don't overload DB)\n");
        message.append("  §7• autoSaveIntervalSeconds: §a30 §7(Performance/security balance)\n");
        message.append("  §7• forceReloadOnJoin: §aEnabled §7(Cross-server sync)\n");

        message.append("\n§eCore Functionality:\n");
        message.append("  §7• backpackSlots: §a27 §7(Minecraft standard)\n");
        message.append("  §7• allowCustomIcons: §aEnabled §7(Main feature)\n");
        message.append("  §7• allowCustomSlots: §aEnabled §7(Flexibility)\n");
        message.append("  §7• showItemCount: §aEnabled §7(Useful info)\n");
        message.append("  §7• showUsagePercentage: §aEnabled §7(Useful info)\n");

        message.append("\n§eLanguage System:\n");
        message.append("  §7• All messages: §aconfig/backpacks/lang.json\n");
        message.append("  §7• Current language: §a").append(LanguageManager.getLanguageInfo()).append("\n");
        message.append("  §7• Total messages: §a").append(LanguageManager.getTotalMessages()).append("\n");
        message.append("  §7• Hex color support: §aEnabled\n");
        message.append("  §7• Gradient support: §aEnabled\n");

        message.append("\n§eStability:\n");
        message.append("  §7• mongoAutoReconnect: §aEnabled §7(Fault resistance)\n");
        message.append("  §7• syncRetryAttempts: §a3 §7(Reasonable retries)\n");
        message.append("  §7• logSyncOperations: §cDisabled §7(Avoid log spam)\n");
        message.append("  §7• playOpenSound: §cDisabled §7(Avoid sound spam)\n");

        message.append("\n§c¡IMPORTANT!§7 These configurations were optimized by experts\n");
        message.append("§7to guarantee maximum performance and stability. Modifying them\n");
        message.append("§7could cause serious performance issues or data loss.\n");
        message.append("\n§eCustomization: §7Edit §aconfig/backpacks/lang.json §7for all messages\n");
        message.append("§eNEW Features: §7Use §a/").append(ConfigManager.getConfig().mainCommand).append("-config default/vip §7for new options");

        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int setConfigInt(CommandContext<ServerCommandSource> context, String field, String argName) {
        try {
            if (!ConfigManager.isConfigurable(field)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§c" + field + " is not a modifiable configuration for security"),
                        false);
                return 0;
            }

            int value = IntegerArgumentType.getInteger(context, argName);
            BackpackConfig config = ConfigManager.getConfig();

            switch (field) {
                case "maxBackpacksPerPlayer":
                    config.maxBackpacksPerPlayer = value;
                    break;
                case "backupIntervalMinutes":
                    config.backupIntervalMinutes = value;
                    break;
                case "maxBackupFiles":
                    config.maxBackupFiles = value;
                    break;
                case "adminPermissionLevel":
                    config.adminPermissionLevel = value;
                    break;
                case "mongoConnectionTimeoutMs":
                    config.mongoConnectionTimeoutMs = value;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field: " + field);
            }

            config.validateUserConfig();
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + " set to: §e" + value + " §7(validated)"),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setConfigBool(CommandContext<ServerCommandSource> context, String field) {
        try {
            if (!ConfigManager.isConfigurable(field)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§c" + field + " is not a modifiable configuration for security"),
                        false);
                return 0;
            }

            boolean value = BoolArgumentType.getBool(context, "enabled");
            BackpackConfig config = ConfigManager.getConfig();

            switch (field) {
                case "allowBackpackRename":
                    config.allowBackpackRename = value;
                    break;
                case "showBackpackStats":
                    config.showBackpackStats = value;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field: " + field);
            }

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + " set to: §e" + (value ? "enabled" : "disabled")),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int setConfigString(CommandContext<ServerCommandSource> context, String field, String argName) {
        try {
            if (!ConfigManager.isConfigurable(field)) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§c" + field + " is not a modifiable configuration for security"),
                        false);
                return 0;
            }

            String value = StringArgumentType.getString(context, argName);
            BackpackConfig config = ConfigManager.getConfig();
            String oldMainCommand = config.mainCommand;
            String oldPlayerCommand = config.playerCommand;

            switch (field) {
                case "serverId":
                    config.serverId = value;
                    break;
                case "mongoConnectionString":
                    if (!value.startsWith("mongodb://")) {
                        context.getSource().sendFeedback(() ->
                                        Text.literal("§cMongoDB connection must start with 'mongodb://'"),
                                false);
                        return 0;
                    }
                    config.mongoConnectionString = value;
                    break;
                case "databaseName":
                    config.databaseName = value;
                    break;
                case "backupDirectory":
                    config.backupDirectory = value;
                    break;
                case "mainCommand":
                    if (!value.matches("[a-zA-Z0-9_-]+") || value.length() > 20) {
                        context.getSource().sendFeedback(() ->
                                        Text.literal("§cInvalid command name. Use only letters, numbers, - and _ (max 20 chars)"),
                                false);
                        return 0;
                    }
                    config.mainCommand = value;
                    break;
                case "playerCommand":
                    if (!value.matches("[a-zA-Z0-9_-]+") || value.length() > 20) {
                        context.getSource().sendFeedback(() ->
                                        Text.literal("§cInvalid command name. Use only letters, numbers, - and _ (max 20 chars)"),
                                false);
                        return 0;
                    }
                    config.playerCommand = value;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field: " + field);
            }

            config.validateUserConfig();
            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§a" + field + " set to: §e" + value + " §7(validated)"),
                    true);

            if ((field.equals("mainCommand") && !oldMainCommand.equals(config.mainCommand)) ||
                    (field.equals("playerCommand") && !oldPlayerCommand.equals(config.playerCommand))) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§c⚠ Server restart required for command changes to take effect"),
                        false);
                context.getSource().sendFeedback(() ->
                                Text.literal("§eNew commands will be: §a/" + config.mainCommand + " §eand §a/" + config.playerCommand),
                        false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError setting configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int validateConfig(CommandContext<ServerCommandSource> context) {
        try {
            boolean isValid = ConfigManager.validateConfiguration();

            if (isValid) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§aValid configuration - no issues found\n" +
                                        "§7All critical configurations are optimized\n" +
                                        "§7Language: " + LanguageManager.getLanguageInfo() + "\n" +
                                        "§7Default backpacks: " + (ConfigManager.getConfig().giveDefaultBackpacks ? "Enabled" : "Disabled") + "\n" +
                                        "§7VIP system: " + (VipBackpackManager.isVipConfigurationValid() ? "Compatible" : "Issues detected")),
                        false);
            } else {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cConfiguration issues found\n" +
                                        "§7Check logs for more details"),
                        false);
            }

            return isValid ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError validating configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int resetConfig(CommandContext<ServerCommandSource> context) {
        try {
            BackpackConfig newConfig = new BackpackConfig();
            newConfig.validateUserConfig();

            ConfigManager.getConfig().serverId = newConfig.serverId;
            ConfigManager.getConfig().maxBackpacksPerPlayer = newConfig.maxBackpacksPerPlayer;
            ConfigManager.getConfig().allowBackpackRename = newConfig.allowBackpackRename;
            ConfigManager.getConfig().showBackpackStats = newConfig.showBackpackStats;
            ConfigManager.getConfig().mainCommand = newConfig.mainCommand;
            ConfigManager.getConfig().playerCommand = newConfig.playerCommand;
            ConfigManager.getConfig().mongoConnectionString = newConfig.mongoConnectionString;
            ConfigManager.getConfig().databaseName = newConfig.databaseName;
            ConfigManager.getConfig().mongoConnectionTimeoutMs = newConfig.mongoConnectionTimeoutMs;
            ConfigManager.getConfig().backupIntervalMinutes = newConfig.backupIntervalMinutes;
            ConfigManager.getConfig().maxBackupFiles = newConfig.maxBackupFiles;
            ConfigManager.getConfig().backupDirectory = newConfig.backupDirectory;
            ConfigManager.getConfig().adminPermissionLevel = newConfig.adminPermissionLevel;

            // Resetear configuración de mochilas por defecto
            DefaultBackpackManager.resetToDefaults();

            ConfigManager.saveConfig();

            context.getSource().sendFeedback(() ->
                            Text.literal("§aConfiguration reset to default values\n" +
                                    "§7Only modifiable configurations were reset\n" +
                                    "§7Optimized configurations remained intact\n" +
                                    "§7Language files were NOT affected\n" +
                                    "§7Default backpacks and VIP settings reset\n" +
                                    "§c⚠ Server restart required for command changes"),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError resetting configuration: " + e.getMessage()),
                    false);
            return 0;
        }
    }
}